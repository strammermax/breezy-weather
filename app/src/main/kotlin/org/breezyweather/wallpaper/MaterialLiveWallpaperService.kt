/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.wallpaper

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.service.wallpaper.WallpaperService
import android.view.OrientationEventListener
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.annotation.Size
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import breezyweather.data.location.LocationRepository
import breezyweather.data.weather.WeatherRepository
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.reference.WeatherCode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import org.breezyweather.BreezyWeather
import org.breezyweather.common.extensions.isLandscape
import org.breezyweather.common.extensions.sensorManager
import org.breezyweather.common.utils.helpers.AsyncHelper
import org.breezyweather.domain.location.model.isDaylight
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.common.images.MoonDrawable
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.breezyweather.ui.theme.weatherView.WeatherView.WeatherKindRule
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.ui.theme.weatherView.materialWeatherView.DelayRotateController
import org.breezyweather.ui.theme.weatherView.materialWeatherView.IntervalComputer
import org.breezyweather.ui.theme.weatherView.materialWeatherView.MaterialWeatherView
import org.breezyweather.ui.theme.weatherView.materialWeatherView.WeatherImplementorFactory
import org.breezyweather.BuildConfig
import org.breezyweather.wallpaper.photo.WallpaperImageStore
import org.breezyweather.wallpaper.photo.WallpaperRepository
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// Parallax travel per layer as a fraction of the screen width. The same constants drive the
// extra layer width (updateLayerBounds), the foreground bitmap width (buildPhotoForeground)
// and the per-frame offsets, so bitmap, bounds and offsets can never disagree.
private const val PARALLAX_BG_FACTOR = 0.05f
private const val PARALLAX_FG_FACTOR = 0.15f
private const val PARALLAX_CELESTIAL_FACTOR = 0.02f

@AndroidEntryPoint
class MaterialLiveWallpaperService : WallpaperService() {
    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    private enum class DeviceOrientation {
        TOP,
        LEFT,
        BOTTOM,
        RIGHT,
    }

    override fun onCreateEngine(): Engine {
        return WeatherEngine(locationRepository, weatherRepository)
    }

    companion object {
        private const val ROTATING_WEATHER_INTERVAL_MILLIS = 20_000L
        private val ROTATING_WEATHER_KINDS = intArrayOf(
            WeatherView.WEATHER_KIND_CLEAR,
            WeatherView.WEATHER_KIND_CLOUD,
            WeatherView.WEATHER_KIND_CLOUDY,
            WeatherView.WEATHER_KIND_RAINY,
            WeatherView.WEATHER_KIND_SNOW,
            WeatherView.WEATHER_KIND_SLEET,
            WeatherView.WEATHER_KIND_HAIL,
            WeatherView.WEATHER_KIND_FOG,
            WeatherView.WEATHER_KIND_HAZE,
            WeatherView.WEATHER_KIND_THUNDER,
            WeatherView.WEATHER_KIND_THUNDERSTORM,
            WeatherView.WEATHER_KIND_WIND,
        )
    }

    private inner class WeatherEngine(
        private val locationRepository: LocationRepository,
        private val weatherRepository: WeatherRepository,
    ) : Engine() {

        private var mHolder: SurfaceHolder? = null
        private var mIntervalComputer: IntervalComputer? = null
        private var mRotators: Array<MaterialWeatherView.RotateController>? = null
        private var mImplementor: MaterialWeatherView.WeatherAnimationImplementor? = null
        private var mCurrentEffectRenderer: WallpaperWeatherEffectRenderer? = null
        private var mOutgoingEffectRenderer: WallpaperWeatherEffectRenderer? = null
        private var mCurrentEffectFamily: WallpaperWeatherFamily? = null
        private var mCurrentRendererWeatherKind: Int? = null
        private var mCurrentRendererWindFactor = Float.NaN
        private var mHasSceneTarget = false
        private val mTransitionManager = TransitionManager()
        private var mBackground: Drawable? = null
        // The processed location photo is the middle layer: sky and celestial body behind it,
        // weather effects in front of it.
        private var mForeground: Drawable? = null
        private var mCelestialStartMillis: Long? = null
        private var mCelestialEndMillis: Long? = null
        private var mSunriseMillis: Long? = null
        private var mSunsetMillis: Long? = null
        private var mMoonriseMillis: Long? = null
        private var mMoonsetMillis: Long? = null
        private var mAutomaticDayNight = false
        private var mLastDayNightCheckMinute = Long.MIN_VALUE
        private var mOpenGravitySensor = false
        private var mGravitySensor: Sensor? = null

        private var mParallaxEnabled = false
        private var mXOffset = 0.5f

        // Identity of the currently built photo foreground (path|mtime|size|parallax).
        // A null key with photo enabled means "needs (re)build"; ensureForeground() recovers
        // automatically once the surface has a real size and a cached photo exists.
        private var mForegroundKey: String? = null
        private var mForegroundNightTint = Float.NaN
        private var mCurrentLocationData: Location? = null
        private var mLoggedForegroundMissing = false

        /** Debug-only logging (single tag), compiled out of release builds. */
        private inline fun lwwLog(message: () -> String) {
            if (BuildConfig.DEBUG) android.util.Log.d("LWW", message())
        }

        // The renderer only reads the photo cache. Fetching is owned by the app data layer.
        private val mWallpaperImageStore = WallpaperImageStore(applicationContext)
        private val mWallpaperRepository = WallpaperRepository(applicationContext)
        private val mRotatingLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mSunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mSunCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 255, 246)
        }
        private val mMoonDrawable = MoonDrawable()
        private var mCelestialPaintMinute = Long.MIN_VALUE
        private var mSunPaintCenterX = Float.NaN
        private var mSunPaintCenterY = Float.NaN
        private var mSunPaintAlpha = -1
        private val photoHeightFraction = 0.52f
        private val celestialSizeFraction = 0.14f
        private val dayMillis = 24L * 60L * 60L * 1000L
        private val fallbackCelestialDuration = 12L * 60L * 60L * 1000L

        @Size(2)
        private var mSizes: IntArray = intArrayOf(0, 0)

        @Size(2)
        private var mAdaptiveSize: IntArray = intArrayOf(0, 0)
        private var mRotation2D = 0f
        private var mRotation3D = 0f

        @WeatherKindRule
        private var mWeatherKind = 0
        private var mDaytime = false
        private var mSceneState = WallpaperSceneStateFactory.create(
            weatherKind = WeatherView.WEATHER_KIND_CLEAR,
            daylight = 1f,
        )
        private var mVisible = false
        private var mAnimate = false
        private var mRotatingWeather = false
        private var mRotatingWeatherIndex = 0
        private var hasDrawn = false
        private var mDeviceOrientation: DeviceOrientation = DeviceOrientation.TOP
        private var mIntervalController: AsyncHelper.Controller? = null
        private var mHandlerThread: HandlerThread? = null
        private var mHandler: Handler? = null
        private val mDrawableRunnable = Runnable {
            if (mBackground == null ||
                mRotators == null ||
                mHandler == null
            ) {
                return@Runnable
            }
            // Log only on the missing->present transition of the photo layer, never per frame.
            val foregroundMissing = mWallpaperImageStore.photoBackgroundEnabled && mForeground == null
            if (foregroundMissing != mLoggedForegroundMissing) {
                mLoggedForegroundMissing = foregroundMissing
                lwwLog { "photo foreground ${if (foregroundMissing) "missing" else "present"}" }
            }
            mIntervalComputer?.invalidate()
            if (mRotators != null && mIntervalComputer != null) {
                mRotators!![0].updateRotation(mRotation2D.toDouble(), mIntervalComputer!!.interval)
                mRotators!![1].updateRotation(mRotation3D.toDouble(), mIntervalComputer!!.interval)
            }

            var canvas: Canvas? = null
            try {
                canvas = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    (mRotatingWeather || mCurrentEffectRenderer != null || mOutgoingEffectRenderer != null)
                ) {
                    mHolder?.lockHardwareCanvas()
                } else {
                    mHolder?.lockCanvas()
                }
                canvas?.let {
                    val width = it.width
                    val height = it.height
                    if (mSizes[0] != width || mSizes[1] != height || mBackground == null) {
                        mSizes[0] = width
                        mSizes[1] = height
                        mAdaptiveSize[0] = width
                        mAdaptiveSize[1] = height

                        setWeatherBackgroundDrawable()
                    }
                    refreshAutomaticDayNight(System.currentTimeMillis())
                    // Cheap per-frame check (string compare); rebuilds the photo layer only when
                    // its identity changed (new photo, new size, parallax/daytime toggle) and
                    // recovers automatically once a size or cached photo becomes available.
                    ensureForeground()
                    // Offsets are clamped to the extra layer width so parallax can never push a
                    // layer past its own bounds, whatever the factors are set to.
                    val bgOffset = parallaxOffset(PARALLAX_BG_FACTOR)
                    val fgOffset = parallaxOffset(PARALLAX_FG_FACTOR)
                    val celestialOffset = parallaxOffset(PARALLAX_CELESTIAL_FACTOR)

                    it.withTranslation(-bgOffset, 0f) {
                        mBackground?.draw(it)
                    }
                    it.withTranslation(-celestialOffset, 0f) {
                        drawCelestialBody(it)
                    }
                    val transitionProgress = mTransitionManager.transitionProgress()
                    if (transitionProgress == null && mOutgoingEffectRenderer != null) {
                        // Transition finished: drop the outgoing renderer (keep at most one).
                        mOutgoingEffectRenderer = null
                    }
                    if (transitionProgress != null && mOutgoingEffectRenderer != null) {
                        mOutgoingEffectRenderer?.drawBackgroundWeatherPass(it, 1f - transitionProgress)
                        mCurrentEffectRenderer?.drawBackgroundWeatherPass(it, transitionProgress)
                    } else {
                        mCurrentEffectRenderer?.drawBackgroundWeatherPass(it)
                    }
                    it.withTranslation(-fgOffset, 0f) {
                        updateForegroundNightTint()
                        mForeground?.draw(it)
                    }
                    if (mIntervalComputer != null && mRotators != null) {
                        var interval = mIntervalComputer!!.interval
                        if (!mAnimate) {
                            if (hasDrawn) {
                                interval = 0.0
                            } else {
                                hasDrawn = true
                            }
                        }
                        mImplementor?.updateData(
                            mAdaptiveSize,
                            interval.toLong(),
                            mRotators!![0].rotation.toFloat(),
                            mRotators!![1].rotation.toFloat()
                        )
                    }
                    if (mImplementor != null && mRotators != null) {
                        it.withTranslation(
                            (mSizes[0] - mAdaptiveSize[0]) / 2f,
                            (mSizes[1] - mAdaptiveSize[1]) / 2f
                        ) {
                            mImplementor!!.draw(
                                mAdaptiveSize,
                                this,
                                0f,
                                mRotators!![0].rotation.toFloat(),
                                mRotators!![1].rotation.toFloat()
                            )
                        }
                    }
                    val frameInterval = mIntervalComputer?.interval?.toLong() ?: 0L
                    mCurrentEffectRenderer?.update(frameInterval, mAnimate)
                    if (transitionProgress != null && mOutgoingEffectRenderer != null) {
                        mOutgoingEffectRenderer?.update(frameInterval, mAnimate)
                        mOutgoingEffectRenderer?.drawForegroundWeatherPass(it, 1f - transitionProgress)
                        mCurrentEffectRenderer?.drawForegroundWeatherPass(it, transitionProgress)
                        mOutgoingEffectRenderer?.drawGlassRainDrops(it, 1f - transitionProgress)
                        mCurrentEffectRenderer?.drawGlassRainDrops(it, transitionProgress)
                    } else {
                        mCurrentEffectRenderer?.drawForegroundWeatherPass(it)
                        mCurrentEffectRenderer?.drawGlassRainDrops(it)
                    }
                    drawRotatingWeatherLabel(it)
                }
            } catch (e: Throwable) {
                if (BreezyWeather.instance.debugMode) {
                    e.printStackTrace()
                }
            } finally {
                canvas?.let { mHolder?.unlockCanvasAndPost(it) }
            }
        }

        private val mRotatingWeatherRunnable = object : Runnable {
            override fun run() {
                if (!mVisible || !mRotatingWeather) return
                mRotatingWeatherIndex = (mRotatingWeatherIndex + 1) % ROTATING_WEATHER_KINDS.size
                setWeather(ROTATING_WEATHER_KINDS[mRotatingWeatherIndex], mDaytime)
                setWeatherImplementor(SceneTransitionReason.ROTATING_TEST)
                mHandler?.post(mDrawableRunnable)
                mHandler?.postDelayed(this, ROTATING_WEATHER_INTERVAL_MILLIS)
            }
        }

        private val mGravityListener: SensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                // x : (+) fall to the left / (-) fall to the right.
                // y : (+) stand / (-) head stand.
                // z : (+) look down / (-) look up.
                // rotation2D : (+) anticlockwise / (-) clockwise.
                // rotation3D : (+) look down / (-) look up.
                if (mOpenGravitySensor) {
                    val aX = ev.values[0]
                    val aY = ev.values[1]
                    val aZ = ev.values[2]
                    val g2D = sqrt((aX * aX + aY * aY).toDouble())
                    val g3D = sqrt((aX * aX + aY * aY + aZ * aZ).toDouble())
                    val cos2D = max(min(1.0, aY / g2D), -1.0)
                    val cos3D = max(min(1.0, g2D * (if (aY >= 0) 1 else -1) / g3D), -1.0)
                    mRotation2D = Math.toDegrees(acos(cos2D)).toFloat() * if (aX >= 0) 1 else -1
                    mRotation3D = Math.toDegrees(acos(cos3D)).toFloat() * if (aZ >= 0) 1 else -1
                    when (mDeviceOrientation) {
                        DeviceOrientation.TOP -> {}
                        DeviceOrientation.LEFT -> mRotation2D -= 90f
                        DeviceOrientation.RIGHT -> mRotation2D += 90f
                        DeviceOrientation.BOTTOM -> if (mRotation2D > 0) {
                            mRotation2D -= 180f
                        } else {
                            mRotation2D += 180f
                        }
                    }
                    if (60 < abs(mRotation3D) && abs(mRotation3D) < 120) {
                        mRotation2D *= (abs(abs(mRotation3D) - 90) / 30.0).toFloat()
                    }
                } else {
                    mRotation2D = 0f
                    mRotation3D = 0f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, i: Int) {
                // do nothing.
            }
        }
        private val mOrientationListener: OrientationEventListener =
            object : OrientationEventListener(
                applicationContext
            ) {
                override fun onOrientationChanged(orientation: Int) {
                    val newOrientation = getDeviceOrientation(orientation)
                    if (newOrientation != mDeviceOrientation) {
                        // LogHelper.log(msg = "[LiveWallpaper] Orientation: $mDeviceOrientation -> $newOrientation")
                        mDeviceOrientation = newOrientation
                        if (!mAnimate) {
                            mHandler?.post(mDrawableRunnable)
                        }
                    }
                }

                private fun getDeviceOrientation(orientation: Int): DeviceOrientation {
                    return if (applicationContext.isLandscape) {
                        if (0 < orientation && orientation < 180) DeviceOrientation.RIGHT else DeviceOrientation.LEFT
                    } else {
                        if (270 < orientation || orientation < 90) DeviceOrientation.TOP else DeviceOrientation.BOTTOM
                    }
                }
            }

        private fun setWeather(
            @WeatherKindRule weatherKind: Int,
            daytime: Boolean,
            submitTarget: Boolean = true,
        ) {
            mWeatherKind = weatherKind
            mDaytime = daytime
            mHasSceneTarget = submitTarget
            rebuildSceneState()
        }

        private fun rebuildSceneState(now: Long = System.currentTimeMillis()) {
            val wind = mCurrentLocationData?.weather?.current?.wind
            mSceneState = WallpaperSceneStateFactory.create(
                weatherKind = mWeatherKind,
                daylight = if (mAutomaticDayNight) {
                    sunVisibility(now)
                } else if (mDaytime) {
                    1f
                } else {
                    0f
                },
                windSpeedMetersPerSecond = wind?.speed?.inMetersPerSecond?.toFloat() ?: 0f,
                windGustMetersPerSecond = wind?.gusts?.inMetersPerSecond?.toFloat() ?: 0f,
                windDirectionDegrees = wind?.degree?.toFloat(),
                sunriseMillis = mSunriseMillis,
                sunsetMillis = mSunsetMillis,
                moonriseMillis = mMoonriseMillis,
                moonsetMillis = mMoonsetMillis,
            )
        }

        private fun drawRotatingWeatherLabel(canvas: Canvas) {
            if (!mRotatingWeather) return

            val label = "Rotating: ${rotatingWeatherName(mWeatherKind)}"
            val textSize = (canvas.height * 0.022f).coerceIn(34f, 60f)
            mRotatingLabelPaint.textSize = textSize
            mRotatingLabelPaint.textAlign = Paint.Align.CENTER
            mRotatingLabelPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD

            val centerX = canvas.width / 2f
            val baseline = canvas.height * 0.31f
            val paddingX = textSize * 0.65f
            val paddingY = textSize * 0.38f
            val textWidth = mRotatingLabelPaint.measureText(label)
            val metrics = mRotatingLabelPaint.fontMetrics
            val bounds = RectF(
                centerX - textWidth / 2f - paddingX,
                baseline + metrics.ascent - paddingY,
                centerX + textWidth / 2f + paddingX,
                baseline + metrics.descent + paddingY,
            )

            mRotatingLabelPaint.color = 0x99000000.toInt()
            canvas.drawRoundRect(bounds, textSize * 0.45f, textSize * 0.45f, mRotatingLabelPaint)
            mRotatingLabelPaint.color = Color.WHITE
            canvas.drawText(label, centerX, baseline, mRotatingLabelPaint)
        }

        private fun rotatingWeatherName(@WeatherKindRule weatherKind: Int): String = when (weatherKind) {
            WeatherView.WEATHER_KIND_CLEAR -> "Clear"
            WeatherView.WEATHER_KIND_CLOUD -> "Cloud"
            WeatherView.WEATHER_KIND_CLOUDY -> "Cloudy"
            WeatherView.WEATHER_KIND_RAINY -> "Rain"
            WeatherView.WEATHER_KIND_SNOW -> "Snow"
            WeatherView.WEATHER_KIND_SLEET -> "Sleet"
            WeatherView.WEATHER_KIND_HAIL -> "Hail"
            WeatherView.WEATHER_KIND_FOG -> "Fog"
            WeatherView.WEATHER_KIND_HAZE -> "Haze"
            WeatherView.WEATHER_KIND_THUNDER -> "Thunder"
            WeatherView.WEATHER_KIND_THUNDERSTORM -> "Thunderstorm"
            WeatherView.WEATHER_KIND_WIND -> "Wind"
            else -> "Unknown"
        }

        private fun setWeatherImplementor(
            reason: SceneTransitionReason = SceneTransitionReason.WEATHER_DATA_CHANGED,
        ) {
            if (!mHasSceneTarget) return
            hasDrawn = false
            val sceneState = mSceneState
            val rendererMatchesTarget = mCurrentEffectRenderer != null &&
                mCurrentRendererWeatherKind == sceneState.weatherKind &&
                abs(mCurrentRendererWindFactor - sceneState.windFactor) < 0.001f
            if (rendererMatchesTarget) {
                updateRendererDaylight(sceneState.daylight)
                return
            }
            val newRenderer = if (WallpaperWeatherEffectRenderer.supports(sceneState.weatherKind)) {
                WallpaperWeatherEffectRenderer(
                    sceneState.weatherKind,
                    sceneState.daylight,
                    sceneState.windFactor,
                    cloudField = CloudFieldFactory.cloudFieldParams(
                        family = sceneState.weatherFamily,
                        cloudDensity = sceneState.cloudDensity,
                        cloudDarkness = sceneState.cloudDarkness,
                        windFactor = sceneState.windFactor,
                        windDirectionDegrees = sceneState.windDirectionDegrees,
                    ),
                    fogField = FogFieldFactory.fogFieldParams(
                        fogIntensity = sceneState.fogIntensity,
                        hazeIntensity = sceneState.hazeIntensity,
                        windFactor = sceneState.windFactor,
                        windDirectionDegrees = sceneState.windDirectionDegrees,
                    ),
                )
            } else {
                null
            }
            val newFamily = sceneState.weatherFamily
            val duration = transitionDurationMillis(
                from = mCurrentEffectFamily,
                to = newFamily,
                reason = reason,
                animationsEnabled = mAnimate,
            )

            if (mCurrentEffectRenderer != null && newRenderer != null && duration > 0L &&
                newFamily != mCurrentEffectFamily
            ) {
                // Keep at most two renderers. If a transition is already running, the renderer
                // with the larger current contribution becomes the new outgoing renderer.
                val activeProgress = mTransitionManager.transitionProgress()
                mOutgoingEffectRenderer = if (activeProgress != null && activeProgress >= 0.5f) {
                    mCurrentEffectRenderer
                } else {
                    mOutgoingEffectRenderer ?: mCurrentEffectRenderer
                }
                mCurrentEffectRenderer = newRenderer
                mTransitionManager.startTransition(duration)
                lwwLog { "transition $mCurrentEffectFamily -> $newFamily duration=${duration}ms reason=$reason" }
            } else {
                mOutgoingEffectRenderer = null
                mCurrentEffectRenderer = newRenderer
                mTransitionManager.cancelTransition()
            }
            mCurrentEffectFamily = newFamily
            mCurrentRendererWeatherKind = sceneState.weatherKind
            mCurrentRendererWindFactor = sceneState.windFactor

            // The scene layer draws its own time-positioned sun. Avoid the old fixed clear-day sun.
            mImplementor = if (mCurrentEffectRenderer != null ||
                sceneState.weatherKind == WeatherView.WEATHER_KIND_CLEAR && sceneState.daytime
            ) {
                null
            } else {
                WeatherImplementorFactory.getWeatherImplementor(
                    applicationContext,
                    sceneState.weatherKind,
                    sceneState.daytime,
                    mAdaptiveSize,
                    mAnimate
                )
            }
            mRotators = arrayOf(
                DelayRotateController(mRotation2D.toDouble()),
                DelayRotateController(mRotation3D.toDouble())
            )
        }

        private fun updateRendererDaylight(daylight: Float = mSceneState.daylight) {
            mCurrentEffectRenderer?.setDaylight(daylight)
            mOutgoingEffectRenderer?.setDaylight(daylight)
        }

        private fun setWeatherBackgroundDrawable() {
            mBackground = buildSkyBackground()
            // Invalidate the photo identity so ensureForeground() rebuilds it (e.g. after a
            // daytime change or a fresh download); the keyed path is the only decode site.
            mForegroundKey = null
            ensureForeground()
            updateLayerBounds()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                notifyColorsChanged()
            }
        }

        /**
         * Keeps [mForeground] in sync with the cached photo without depending on event order:
         * a 0x0 surface, a missing cache or a parallax/daytime toggle all self-heal here. The
         * photo is decoded only when its identity key changes, never per frame. A missing cache
         * remains empty until the app data layer stores a new processed photo.
         */
        private fun ensureForeground() {
            if (!mWallpaperImageStore.photoBackgroundEnabled) {
                if (mForeground != null) {
                    mForeground = null
                    mForegroundKey = null
                }
                return
            }
            if (mSizes[0] <= 0 || mSizes[1] <= 0) return // recovers when a real size arrives

            val path = mWallpaperImageStore.cachedPhotoPath
            val file = path?.let(::File)
            if (file == null || !file.exists()) {
                if (file != null) {
                    lwwLog { "stale cachedPhotoPath $path, clearing" }
                    mWallpaperImageStore.cachedPhotoPath = null
                }
                lwwLog { "no cached photo; waiting for app data layer" }
                return
            }

            val key = "$path|${file.lastModified()}|${mSizes[0]}x${mSizes[1]}|$mParallaxEnabled"
            if (key == mForegroundKey && mForeground != null) return

            mForeground = buildPhotoForeground()
            mForegroundKey = if (mForeground != null) key else null
            mForegroundNightTint = Float.NaN
            updateForegroundNightTint()
            updateLayerBounds()
            lwwLog { "foreground rebuilt success=${mForeground != null} key=$key" }
        }

        /** Parallax shift for a layer, clamped so it can never exceed the layer's extra width. */
        private fun parallaxOffset(factor: Float): Float {
            if (!mParallaxEnabled) return 0f
            val extra = mSizes[0] * factor
            return ((mXOffset - 0.5f) * mSizes[0] * factor).coerceIn(-extra, extra)
        }

        private fun updateLayerBounds() {
            val width = mSizes[0]
            val height = mSizes[1]
            if (width <= 0 || height <= 0) return

            if (mParallaxEnabled) {
                // Make layers wider to allow shifting
                val bgExtra = (width * PARALLAX_BG_FACTOR).toInt()
                val fgExtra = (width * PARALLAX_FG_FACTOR).toInt()
                mBackground?.setBounds(-bgExtra, 0, width + bgExtra, height)
                mForeground?.setBounds(-fgExtra, 0, width + fgExtra, height)
            } else {
                mBackground?.setBounds(0, 0, width, height)
                mForeground?.setBounds(0, 0, width, height)
            }
            lwwLog { "layer bounds updated ${width}x$height parallax=$mParallaxEnabled" }
        }

        /**
         * Places the complete processed photo at the bottom of a transparent full-screen bitmap,
         * covering the full width (cropping sides for wide photos, or the top for narrow/tall
         * photos) so no transparent gaps appear next to the photo.
         */
        private fun buildPhotoForeground(): Drawable? {
            if (!mWallpaperImageStore.photoBackgroundEnabled) {
                lwwLog { "buildPhotoForeground skipped: disabled" }
                return null
            }
            if (mSizes[0] <= 0 || mSizes[1] <= 0) {
                lwwLog { "buildPhotoForeground skipped: size=${mSizes[0]}x${mSizes[1]}" }
                return null
            }
            val source = mWallpaperRepository.loadCachedBitmap()
            if (source == null) {
                lwwLog { "buildPhotoForeground skipped: no cached bitmap (path=${mWallpaperImageStore.cachedPhotoPath})" }
                return null
            }
            return try {
                // Width must match the parallax bounds (1 + 2 * factor) so the BitmapDrawable
                // is never stretched into wider bounds.
                val width = if (mParallaxEnabled) {
                    (mSizes[0] * (1f + 2 * PARALLAX_FG_FACTOR)).toInt()
                } else {
                    mSizes[0]
                }
                val positioned = positionPhotoAtBottom(source, width, mSizes[1])
                lwwLog { "buildPhotoForeground ok: src=${source.width}x${source.height} -> ${width}x${mSizes[1]}" }
                BitmapDrawable(resources, positioned)
            } catch (e: Throwable) {
                lwwLog { "buildPhotoForeground failed: ${e.message}" }
                null
            } finally {
                source.recycle()
            }
        }

        private fun updateForegroundNightTint() {
            val foreground = mForeground ?: return
            val nightTint = mSceneState.photoNightTint.coerceIn(0f, 1f)
            if (abs(nightTint - mForegroundNightTint) < 0.001f) return

            foreground.colorFilter = if (nightTint <= 0.001f) {
                null
            } else {
                ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        setScale(
                            lerp(1f, 0.58f, nightTint),
                            lerp(1f, 0.62f, nightTint),
                            lerp(1f, 0.72f, nightTint),
                            1f,
                        )
                    }
                )
            }
            mForegroundNightTint = nightTint
        }

        private fun positionPhotoAtBottom(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            // Cover the full width even if that makes the photo taller than
            // photoHeightFraction; excess height is cropped off the top since
            // the photo stays anchored to the bottom edge.
            val scale = maxOf(
                targetWidth.toFloat() / source.width,
                (targetHeight * photoHeightFraction) / source.height,
            )
            val photoWidth = source.width * scale
            val photoHeight = source.height * scale
            val left = (targetWidth - photoWidth) / 2f
            canvas.drawBitmap(
                source,
                null,
                RectF(left, targetHeight - photoHeight, left + photoWidth, targetHeight.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            return result
        }

        private fun buildSkyBackground(): Drawable = object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var cachedMinute = Long.MIN_VALUE
            private var cachedBounds = RectF()

            override fun draw(canvas: Canvas) {
                val now = System.currentTimeMillis()
                val minute = now / 60_000L
                val drawBounds = RectF(bounds)
                if (minute != cachedMinute || drawBounds != cachedBounds) {
                    val colors = skyColors(now)
                    paint.shader = LinearGradient(
                        0f,
                        drawBounds.top,
                        0f,
                        drawBounds.bottom,
                        colors[0],
                        colors[1],
                        Shader.TileMode.CLAMP,
                    )
                    cachedMinute = minute
                    cachedBounds = drawBounds
                }
                canvas.drawRect(drawBounds, paint)
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                paint.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Android")
            override fun getOpacity(): Int = PixelFormat.OPAQUE
        }

        private fun skyColors(now: Long): IntArray {
            val night = intArrayOf(Color.rgb(3, 12, 35), Color.rgb(31, 55, 94))
            val dawn = intArrayOf(Color.rgb(78, 78, 126), Color.rgb(242, 145, 104))
            val day = intArrayOf(Color.rgb(42, 125, 196), Color.rgb(174, 221, 244))
            val dusk = intArrayOf(Color.rgb(54, 61, 108), Color.rgb(230, 113, 76))

            if (!mAutomaticDayNight) return if (mDaytime) day else night
            val sunrise = mSunriseMillis ?: return if (mDaytime) day else night
            val sunset = mSunsetMillis ?: return if (mDaytime) day else night
            val transition = 45L * 60L * 1000L

            return when {
                now < sunrise - transition -> night
                now < sunrise -> blendSky(night, dawn, fraction(now, sunrise - transition, sunrise))
                now < sunrise + transition -> blendSky(dawn, day, fraction(now, sunrise, sunrise + transition))
                now < sunset - transition -> day
                now < sunset -> blendSky(day, dusk, fraction(now, sunset - transition, sunset))
                now < sunset + transition -> blendSky(dusk, night, fraction(now, sunset, sunset + transition))
                else -> night
            }
        }

        private fun blendSky(from: IntArray, to: IntArray, amount: Float): IntArray = intArrayOf(
            blendColor(from[0], to[0], amount),
            blendColor(from[1], to[1], amount),
        )

        private fun blendColor(from: Int, to: Int, amount: Float): Int = Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt(),
        )

        private fun fraction(value: Long, start: Long, end: Long): Float =
            ((value - start).toFloat() / (end - start)).coerceIn(0f, 1f)

        private fun drawCelestialBody(canvas: Canvas) {
            val width = mSizes[0]
            val height = mSizes[1]
            if (width <= 0 || height <= 0) return

            val now = System.currentTimeMillis()
            val horizonY = height * 0.48f
            val peakY = height * 0.12f
            val shortestSide = min(width, height).toFloat()
            val sunAlpha = sunVisibility(now)
            val moonAlpha = 1f - sunAlpha
            val positionTime = now / 60_000L * 60_000L

            if (sunAlpha > 0.01f) {
                val sunProgress = celestialProgress(positionTime, mSunriseMillis, mSunsetMillis)
                val sunX = width * (0.12f + 0.76f * sunProgress)
                val sunY = horizonY - sin(Math.PI * sunProgress).toFloat() * (horizonY - peakY)
                drawSun(canvas, sunX, sunY, shortestSide, sunAlpha)
            }
            if (moonAlpha > 0.01f) {
                val moonProgress = celestialProgress(positionTime, mMoonriseMillis, mMoonsetMillis)
                val moonX = width * (0.12f + 0.76f * moonProgress)
                val moonY = horizonY - sin(Math.PI * moonProgress).toFloat() * (horizonY - peakY)
                val size = (shortestSide * celestialSizeFraction).toInt()
                val halfSize = size / 2
                mMoonDrawable.alpha = (moonAlpha * 255).toInt()
                mMoonDrawable.setBounds(
                    (moonX - halfSize).toInt(),
                    (moonY - halfSize).toInt(),
                    (moonX + halfSize).toInt(),
                    (moonY + halfSize).toInt(),
                )
                mMoonDrawable.draw(canvas)
            }
        }

        private fun drawSun(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            shortestSide: Float,
            visibility: Float,
        ) {
            val glowRadius = shortestSide * 0.345f
            val coreRadius = shortestSide * 0.0525f
            val alpha = (visibility * 255).toInt()
            val minute = System.currentTimeMillis() / 60_000L
            if (minute != mCelestialPaintMinute || centerX != mSunPaintCenterX ||
                centerY != mSunPaintCenterY || alpha != mSunPaintAlpha
            ) {
                mSunGlowPaint.shader = RadialGradient(
                    centerX,
                    centerY,
                    glowRadius,
                    intArrayOf(
                        Color.argb(245, 255, 255, 244),
                        Color.argb(178, 255, 252, 226),
                        Color.argb(82, 255, 247, 205),
                        Color.argb(24, 255, 242, 190),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 0.13f, 0.34f, 0.68f, 1f),
                    Shader.TileMode.CLAMP,
                )
                mCelestialPaintMinute = minute
                mSunPaintCenterX = centerX
                mSunPaintCenterY = centerY
                mSunPaintAlpha = alpha
            }
            mSunGlowPaint.alpha = alpha
            mSunCorePaint.alpha = alpha
            canvas.drawCircle(centerX, centerY, glowRadius, mSunGlowPaint)
            canvas.drawCircle(centerX, centerY, coreRadius, mSunCorePaint)
        }

        private fun sunVisibility(now: Long): Float {
            if (!mAutomaticDayNight) return if (mDaytime) 1f else 0f
            val sunrise = mSunriseMillis ?: return if (mDaytime) 1f else 0f
            val sunset = mSunsetMillis ?: return if (mDaytime) 1f else 0f
            val crossFade = 25L * 60L * 1000L
            return when {
                now < sunrise - crossFade -> 0f
                now < sunrise + crossFade -> fraction(now, sunrise - crossFade, sunrise + crossFade)
                now < sunset - crossFade -> 1f
                now < sunset + crossFade -> 1f - fraction(now, sunset - crossFade, sunset + crossFade)
                else -> 0f
            }
        }

        private fun celestialProgress(now: Long, preferredStart: Long?, preferredEnd: Long?): Float {
            val start = preferredStart ?: mCelestialStartMillis ?: return 0.5f
            val end = preferredEnd ?: mCelestialEndMillis ?: return 0.5f
            if (end <= start) return 0.5f
            return ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
        }

        private fun visualDaytime(now: Long): Boolean {
            if (!mAutomaticDayNight) return mDaytime
            val sunrise = mSunriseMillis ?: return mDaytime
            val sunset = mSunsetMillis ?: return mDaytime
            return now in sunrise..sunset
        }

        private fun refreshAutomaticDayNight(now: Long) {
            if (!mAutomaticDayNight) return
            val minute = now / 60_000L
            if (minute == mLastDayNightCheckMinute) return
            mLastDayNightCheckMinute = minute

            val daytime = visualDaytime(now)
            rebuildSceneState(now)
            updateRendererDaylight()
            if (daytime == mDaytime) return
            mDaytime = daytime
            rebuildSceneState(now)
            updateRendererDaylight()
            setWeatherImplementor(SceneTransitionReason.AUTO_DAY_NIGHT)
            lwwLog { "automatic day/night changed daytime=$daytime" }
        }

        private fun updateCelestialTiming(location: Location?) {
            val now = System.currentTimeMillis()
            val daily = location?.weather?.dailyForecast.orEmpty()
            val sunIntervals = daily.mapNotNull { day ->
                astroInterval(day.sun?.riseDate?.time, day.sun?.setDate?.time, now)
            }
            val moonIntervals = daily.mapNotNull { day ->
                astroInterval(day.moon?.riseDate?.time, day.moon?.setDate?.time, now)
            }
            val sun = closestAstroInterval(sunIntervals, now)
                ?: location?.let { approximateSunInterval(it, now) }
            val moon = closestAstroInterval(moonIntervals, now)
            mSunriseMillis = sun?.first
            mSunsetMillis = sun?.second
            mMoonriseMillis = moon?.first
            mMoonsetMillis = moon?.second
            rebuildSceneState(now)
            lwwLog {
                "celestial timing sunrise=${mSunriseMillis?.let(::formatDebugTime)} " +
                    "sunset=${mSunsetMillis?.let(::formatDebugTime)} sourceIntervals=${sunIntervals.size}"
            }
            val intervals = if (mDaytime) {
                sunIntervals
            } else {
                moonIntervals
            }
            val active = closestAstroInterval(intervals, now)

            if (active != null) {
                mCelestialStartMillis = active.first
                mCelestialEndMillis = active.second
                return
            }

            // Daily astro data can be absent for some providers. Keep a stable 12-hour arc.
            mCelestialStartMillis = now - fallbackCelestialDuration / 2
            mCelestialEndMillis = now + fallbackCelestialDuration / 2
        }

        private fun approximateSunInterval(location: Location, now: Long): Pair<Long, Long>? {
            if (!location.isCurrentPosition && location.latitude == 0.0 && location.longitude == 0.0) return null
            val timeZone = TimeZone.getDefault()
            val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = now }
            val dayOfYear = calendar[Calendar.DAY_OF_YEAR]
            val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1)
            val equationOfTime = 229.18 * (
                0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                    0.014615 * cos(2.0 * gamma) - 0.040849 * sin(2.0 * gamma)
                )
            val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2.0 * gamma) + 0.000907 * sin(2.0 * gamma) -
                0.002697 * cos(3.0 * gamma) + 0.00148 * sin(3.0 * gamma)
            val latitudeRadians = Math.toRadians(location.latitude.coerceIn(-89.0, 89.0))
            val zenith = Math.toRadians(90.833)
            val hourAngleCos = (
                cos(zenith) / (cos(latitudeRadians) * cos(declination)) -
                    tan(latitudeRadians) * tan(declination)
                ).coerceIn(-1.0, 1.0)
            val hourAngleDegrees = Math.toDegrees(acos(hourAngleCos))
            val offsetMinutes = timeZone.getOffset(now) / 60_000.0
            val solarNoonMinutes = 720.0 - 4.0 * location.longitude - equationOfTime + offsetMinutes
            val sunriseMinutes = solarNoonMinutes - hourAngleDegrees * 4.0
            val sunsetMinutes = solarNoonMinutes + hourAngleDegrees * 4.0
            val midnight = Calendar.getInstance(timeZone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return (midnight + (sunriseMinutes * 60_000.0).toLong()) to
                (midnight + (sunsetMinutes * 60_000.0).toLong())
        }

        private fun formatDebugTime(timeMillis: Long): String =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.US).format(timeMillis)

        private fun closestAstroInterval(intervals: List<Pair<Long, Long>>, now: Long): Pair<Long, Long>? =
            intervals.firstOrNull { now in it.first..it.second }
                ?: intervals.minByOrNull { interval -> min(abs(now - interval.first), abs(now - interval.second)) }

        private fun astroInterval(rise: Long?, set: Long?, now: Long): Pair<Long, Long>? {
            if (rise == null || set == null) return null
            var start = rise
            var end = set
            if (end <= start) end += dayMillis
            if (now < start && now + dayMillis <= end) {
                start -= dayMillis
                end -= dayMillis
            }
            return start to end
        }

        private fun setIntervalComputer() {
            val animationsEnabled = LiveWallpaperConfigManager(applicationContext).animationsEnabled
            if (animationsEnabled) {
                mIntervalComputer?.reset() ?: run {
                    mIntervalComputer = IntervalComputer()
                }
            } else {
                mIntervalComputer?.reset()
            }
        }

        private fun setOpenGravitySensor(openGravitySensor: Boolean) {
            mOpenGravitySensor = openGravitySensor
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            mDeviceOrientation = DeviceOrientation.TOP
            mHandlerThread = HandlerThread(
                System.currentTimeMillis().toString(),
                Process.THREAD_PRIORITY_FOREGROUND
            ).also {
                it.start()
            }.also {
                mHandler = Handler(it.looper)
            }
            mSizes = intArrayOf(0, 0)
            mAdaptiveSize = intArrayOf(0, 0)
            // Read parallax before the first surfaceChanged so the very first foreground build
            // already uses the right bitmap width/bounds (it used to be read only on visibility).
            mParallaxEnabled = LiveWallpaperConfigManager(applicationContext).parallaxEnabled
            mHolder = surfaceHolder.apply {
                addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        lwwLog { "surfaceCreated" }
                    }
                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        lwwLog { "surfaceChanged ${width}x$height valid=${holder.surface.isValid}" }
                        if (holder.surface.isValid) {
                            val sizeChanged = mSizes[0] != width || mSizes[1] != height
                            mSizes[0] = width
                            mSizes[1] = height
                            mAdaptiveSize[0] = mSizes[0]
                            mAdaptiveSize[1] = mSizes[1]

                            val configManager = LiveWallpaperConfigManager(this@MaterialLiveWallpaperService)
                            mAnimate = configManager.animationsEnabled
                            mParallaxEnabled = configManager.parallaxEnabled
                            setWeatherImplementor(SceneTransitionReason.SURFACE_RECREATED)

                            // Drawables are owned by the render thread; serialize mutations there
                            // instead of racing it from this (main-thread) callback.
                            mHandler?.post {
                                if (sizeChanged || mForeground == null) {
                                    setWeatherBackgroundDrawable()
                                } else {
                                    updateLayerBounds()
                                }
                            }
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        lwwLog { "surfaceDestroyed" }
                    }
                })
                setFormat(PixelFormat.RGBA_8888)
            }
            sensorManager?.let {
                mOpenGravitySensor = true
                mGravitySensor = it.getDefaultSensor(Sensor.TYPE_GRAVITY)
            }
            mVisible = false
            setWeather(WeatherView.WEATHER_KIND_NULL, true, submitTarget = false)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (mVisible == visible) return

            mVisible = visible
            if (!visible) {
                mIntervalController?.let {
                    it.cancel()
                    mIntervalController = null
                }
                mHandler?.removeCallbacksAndMessages(null)
                sensorManager?.unregisterListener(mGravityListener, mGravitySensor)
                mOrientationListener.disable()
                return
            }

            val settingsManager = SettingsManager.getInstance(applicationContext)
            val configManager = LiveWallpaperConfigManager(this@MaterialLiveWallpaperService)
            mAnimate = configManager.animationsEnabled
            mRotatingWeather = configManager.weatherKind == "rotating"
            mRotatingWeatherIndex = 0
            mRotation2D = 0f
            mRotation3D = 0f
            if (mOrientationListener.canDetectOrientation()) {
                mOrientationListener.enable()
            }

            // Celestial positions still need the real location and daily astro data while
            // weather/day-night are forced for visual testing (including Rotating mode).
            val location: Location? = runBlocking {
                locationRepository.getFirstLocation(withParameters = false)
                    ?.let {
                        it.copy(
                            weather = weatherRepository.getWeatherByLocationId(
                                it.formattedId,
                                withDaily = true,
                                withHourly = false,
                                withMinutely = false,
                                withAlerts = false,
                                withNormals = false
                            )
                        )
                    }
            }
            val weatherKind = when (configManager.weatherKind) {
                "auto" -> location?.weather?.current?.weatherCode
                "rotating" -> null
                else -> WeatherCode.getInstance(configManager.weatherKind)
            }
            val daytime = when (configManager.dayNightType) {
                "day" -> true
                "night" -> false
                else -> location?.isDaylight ?: true
            }
            mAutomaticDayNight = configManager.dayNightType == "auto"
            mLastDayNightCheckMinute = Long.MIN_VALUE
            mParallaxEnabled = configManager.parallaxEnabled
            mCurrentLocationData = location
            lwwLog {
                "onVisibilityChanged visible=true parallax=$mParallaxEnabled " +
                    "photoEnabled=${mWallpaperImageStore.photoBackgroundEnabled} " +
                    "hasCachedPhoto=${mWallpaperRepository.hasCachedPhoto()} " +
                    "location=${location != null}"
            }
            setWeather(
                if (mRotatingWeather) {
                    ROTATING_WEATHER_KINDS[mRotatingWeatherIndex]
                } else {
                    WeatherViewController.getWeatherKind(weatherKind)
                },
                daytime
            )
            updateCelestialTiming(location)

            val transitionReason = when {
                mCurrentEffectFamily == null -> SceneTransitionReason.INITIAL
                configManager.weatherKind != "auto" || configManager.dayNightType != "auto" ->
                    SceneTransitionReason.USER_FORCED_MODE
                else -> SceneTransitionReason.WEATHER_DATA_CHANGED
            }
            setWeatherImplementor(transitionReason)
            setIntervalComputer()
            setOpenGravitySensor(settingsManager.isGravitySensorEnabled)
            if (mOpenGravitySensor) {
                sensorManager?.registerListener(
                    mGravityListener,
                    mGravitySensor,
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            } else {
                sensorManager?.unregisterListener(mGravityListener, mGravitySensor)
            }

            mHandler?.post { setWeatherBackgroundDrawable() }
            if (mRotatingWeather) {
                mHandler?.postDelayed(mRotatingWeatherRunnable, ROTATING_WEATHER_INTERVAL_MILLIS)
            }
            if (mAnimate) {
                val screenRefreshRate = ContextCompat.getDisplayOrDefault(this@MaterialLiveWallpaperService)
                    .refreshRate.let {
                        if (it > 30f) 30f else it
                    }
                mIntervalController = AsyncHelper.intervalRunOnUI(
                    { mHandler?.post(mDrawableRunnable) },
                    (1000.0 / screenRefreshRate).toLong(),
                    0
                )
            } else {
                mHandler?.post(mDrawableRunnable)
                // Run again 1 sec later in case the canvas size was not correctly set the first time on preview screen
                AsyncHelper.delayRunOnUI(
                    { mHandler?.post(mDrawableRunnable) },
                    1000
                )
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            if (mParallaxEnabled && mXOffset != xOffset) {
                if (abs(mXOffset - xOffset) > 0.1f) {
                    lwwLog { "onOffsetsChanged x=$xOffset" }
                }
                mXOffset = xOffset
                if (!mAnimate) {
                    mHandler?.post(mDrawableRunnable)
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors? {
            return if (mBackground != null) {
                WallpaperColors.fromDrawable(mBackground)
            } else {
                null
            }
        }

        override fun onDestroy() {
            onVisibilityChanged(false)
            mHandlerThread?.quit()
        }
    }
}
