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
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
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
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import breezyweather.data.location.LocationRepository
import breezyweather.data.weather.WeatherRepository
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.reference.WeatherCode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.breezyweather.BreezyWeather
import org.breezyweather.common.extensions.isLandscape
import org.breezyweather.common.extensions.sensorManager
import org.breezyweather.common.utils.helpers.AsyncHelper
import org.breezyweather.domain.location.model.isDaylight
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.breezyweather.ui.theme.weatherView.WeatherView.WeatherKindRule
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.ui.theme.weatherView.materialWeatherView.DelayRotateController
import org.breezyweather.ui.theme.weatherView.materialWeatherView.IntervalComputer
import org.breezyweather.ui.theme.weatherView.materialWeatherView.MaterialWeatherView
import org.breezyweather.ui.theme.weatherView.materialWeatherView.WeatherImplementorFactory
import org.breezyweather.radar.BuienradarNowcastSource
import org.breezyweather.wallpaper.photo.PlaceQuery
import org.breezyweather.wallpaper.photo.WallpaperImageStore
import org.breezyweather.wallpaper.photo.WallpaperRepository
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

    private inner class WeatherEngine(
        private val locationRepository: LocationRepository,
        private val weatherRepository: WeatherRepository,
    ) : Engine() {

        private var mHolder: SurfaceHolder? = null
        private var mIntervalComputer: IntervalComputer? = null
        private var mRotators: Array<MaterialWeatherView.RotateController>? = null
        private var mImplementor: MaterialWeatherView.WeatherAnimationImplementor? = null
        private var mBackground: Drawable? = null
        // When the cached photo has a transparent (erased) sky, it is drawn as a foreground over
        // the weather animation, so our sky/clouds/rain/snow show through where the sky was.
        private var mForeground: Drawable? = null
        private var mPhotoHasTransparentSky = false
        private var mOpenGravitySensor = false
        private var mGravitySensor: Sensor? = null

        // Location-photo background (Unsplash / curated LocationData), drawn beneath the
        // weather animation so e.g. clouds render over a photo of where you are.
        private val mWallpaperImageStore = WallpaperImageStore(applicationContext)
        private val mWallpaperRepository = WallpaperRepository(applicationContext)
        private val mPhotoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Precipitation nowcast (Buienradar) drawn as a subtle trend strip at the bottom,
        // only when rain is expected. Empty = nothing drawn.
        private var mRainIntensities: FloatArray = FloatArray(0)
        private val mRainPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        @Size(2)
        private var mSizes: IntArray = intArrayOf(0, 0)

        @Size(2)
        private var mAdaptiveSize: IntArray = intArrayOf(0, 0)
        private var mRotation2D = 0f
        private var mRotation3D = 0f

        @WeatherKindRule
        private var mWeatherKind = 0
        private var mDaytime = false
        private var mVisible = false
        private var mAnimate = false
        private var hasDrawn = false
        private var mDeviceOrientation: DeviceOrientation = DeviceOrientation.TOP
        private var mIntervalController: AsyncHelper.Controller? = null
        private var mHandlerThread: HandlerThread? = null
        private var mHandler: Handler? = null
        private val mDrawableRunnable = Runnable {
            if (mImplementor == null ||
                mBackground == null ||
                mRotators == null ||
                mHandler == null
            ) {
                return@Runnable
            }
            // LogHelper.log(msg = "[LiveWallpaper] Runnable is running")
            mIntervalComputer?.invalidate()
            if (mRotators != null && mIntervalComputer != null) {
                mRotators!![0].updateRotation(mRotation2D.toDouble(), mIntervalComputer!!.interval)
                mRotators!![1].updateRotation(mRotation3D.toDouble(), mIntervalComputer!!.interval)
            }

            try {
                mHolder?.lockCanvas()?.let { canvas ->
                    if (mSizes[0] != canvas.width || mSizes[1] != canvas.height) {
                        mSizes[0] = canvas.width
                        mSizes[1] = canvas.height
                        mAdaptiveSize[0] = mSizes[0]
                        mAdaptiveSize[1] = mSizes[1]
                        mBackground?.setBounds(0, 0, mSizes[0], mSizes[1])
                        mForeground?.setBounds(0, 0, mSizes[0], mSizes[1])
                    }
                    mBackground?.draw(canvas)
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
                        canvas.withTranslation(
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
                    // Photo foreground (with transparent sky) over the weather animation.
                    mForeground?.draw(canvas)
                    drawRainTrend(canvas)
                    mHolder?.unlockCanvasAndPost(canvas)
                }
            } catch (e: Throwable) {
                if (BreezyWeather.instance.debugMode) {
                    e.printStackTrace()
                }
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

        private fun setWeather(@WeatherKindRule weatherKind: Int, daytime: Boolean) {
            mWeatherKind = weatherKind
            mDaytime = daytime
        }

        private fun setWeatherImplementor() {
            hasDrawn = false
            mImplementor = WeatherImplementorFactory.getWeatherImplementor(
                applicationContext,
                mWeatherKind,
                mDaytime,
                mAdaptiveSize,
                mAnimate
            )
            mRotators = arrayOf(
                DelayRotateController(mRotation2D.toDouble()),
                DelayRotateController(mRotation3D.toDouble())
            )
        }

        private fun setWeatherBackgroundDrawable() {
            val gradient = {
                ResourcesCompat.getDrawable(
                    resources,
                    WeatherImplementorFactory.getBackgroundId(mWeatherKind, mDaytime),
                    null
                )
            }
            val photo = buildPhotoBackground()
            if (photo != null && mPhotoHasTransparentSky) {
                // Sky was erased: gradient sky behind, weather animation, then the photo (with
                // its transparent sky) on top — so weather shows through the sky hole.
                mBackground = gradient()
                mForeground = photo
            } else {
                // Opaque photo (or no photo): keep it as the background; weather draws over it.
                mBackground = photo ?: gradient()
                mForeground = null
            }
            mBackground?.setBounds(0, 0, mSizes[0], mSizes[1])
            mForeground?.setBounds(0, 0, mSizes[0], mSizes[1])
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                notifyColorsChanged()
            }
        }

        /**
         * Builds a center-cropped drawable from the cached location photo, or null when the
         * photo background is disabled / unavailable so the caller falls back to the gradient.
         * Sets [mPhotoHasTransparentSky] from the source bitmap's alpha: when the sky has been
         * erased (PNG with alpha) the photo is used as a foreground rather than a background.
         */
        private fun buildPhotoBackground(): Drawable? {
            mPhotoHasTransparentSky = false
            if (!mWallpaperImageStore.photoBackgroundEnabled) return null
            if (mSizes[0] <= 0 || mSizes[1] <= 0) return null
            val source = mWallpaperRepository.loadCachedBitmap() ?: return null
            return try {
                mPhotoHasTransparentSky = source.hasAlpha()
                val cropped = centerCrop(source, mSizes[0], mSizes[1])
                if (cropped !== source) source.recycle()
                BitmapDrawable(resources, cropped)
            } catch (e: Throwable) {
                null
            }
        }

        /** Scales [source] to fill [targetWidth] x [targetHeight], cropping the overflow. */
        private fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val srcRatio = source.width.toFloat() / source.height
            val dstRatio = targetWidth.toFloat() / targetHeight
            val src: Rect = if (srcRatio > dstRatio) {
                // Source is relatively wider: crop the left/right edges.
                val w = (source.height * dstRatio).toInt()
                val x = (source.width - w) / 2
                Rect(x, 0, x + w, source.height)
            } else {
                // Source is relatively taller: crop the top/bottom edges.
                val h = (source.width / dstRatio).toInt()
                val y = (source.height - h) / 2
                Rect(0, y, source.width, y + h)
            }
            canvas.drawBitmap(source, src, Rect(0, 0, targetWidth, targetHeight), Paint(Paint.FILTER_BITMAP_FLAG))
            return result
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
            mHolder = surfaceHolder.apply {
                addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {}
                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        if (holder.surface.isValid) {
                            mSizes[0] = width
                            mSizes[1] = height
                            mAdaptiveSize[0] = mSizes[0]
                            mAdaptiveSize[1] = mSizes[1]
                            mBackground?.setBounds(0, 0, mSizes[0], mSizes[1])
                            mForeground?.setBounds(0, 0, mSizes[0], mSizes[1])
                            mAnimate = LiveWallpaperConfigManager(this@MaterialLiveWallpaperService).animationsEnabled
                            setWeatherImplementor()
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {}
                })
                setFormat(PixelFormat.RGBA_8888)
            }
            sensorManager?.let {
                mOpenGravitySensor = true
                mGravitySensor = it.getDefaultSensor(Sensor.TYPE_GRAVITY)
            }
            mVisible = false
            setWeather(WeatherView.WEATHER_KIND_NULL, true)
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
            mRotation2D = 0f
            mRotation3D = 0f
            if (mOrientationListener.canDetectOrientation()) {
                mOrientationListener.enable()
            }

            val location: Location? = if (configManager.weatherKind == "auto" || configManager.dayNightType == "auto") {
                // TODO: Isn't there a more efficient way than reloading the location from database
                // everytime the visibility changes??
                // TODO
                runBlocking {
                    locationRepository.getFirstLocation(withParameters = false)
                        .let {
                            it?.copy(
                                weather = weatherRepository.getWeatherByLocationId(
                                    it.formattedId,
                                    withDaily = configManager.dayNightType == "auto",
                                    withHourly = false,
                                    withMinutely = false,
                                    withAlerts = false,
                                    withNormals = false
                                )
                            )
                        }
                }
            } else {
                null
            }
            val weatherKind = when (configManager.weatherKind) {
                "auto" -> location?.weather?.current?.weatherCode
                else -> WeatherCode.getInstance(configManager.weatherKind)
            }
            val daytime = when (configManager.dayNightType) {
                "day" -> true
                "night" -> false
                else -> location?.isDaylight ?: true
            }
            setWeather(
                WeatherViewController.getWeatherKind(weatherKind),
                daytime
            )

            setWeatherImplementor()
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

            setWeatherBackgroundDrawable()
            maybeRefreshPhotoBackground(location)
            maybeRefreshRainTrend(location)
            if (mAnimate) {
                val screenRefreshRate = ContextCompat.getDisplayOrDefault(this@MaterialLiveWallpaperService)
                    .refreshRate.let {
                        if (it > 60f) 60f else it
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

        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors? {
            return if (mBackground != null) {
                WallpaperColors.fromDrawable(mBackground)
            } else {
                null
            }
        }

        /**
         * Downloads/caches the location photo for [location] in the background (off the render
         * thread). On success it rebuilds the background drawable and triggers a single redraw.
         * No-op when the photo background is disabled or the location has no usable coordinates.
         */
        private fun maybeRefreshPhotoBackground(location: Location?) {
            if (!mWallpaperImageStore.photoBackgroundEnabled) return
            if (location == null || !location.isUsable) return
            mPhotoScope.launch {
                val place = PlaceQuery(
                    city = location.city.ifBlank { null },
                    municipality = location.admin2,
                    state = location.admin1,
                    country = location.country.ifBlank { null },
                )
                val file = mWallpaperRepository.refreshFor(
                    location.latitude,
                    location.longitude,
                    place
                )
                if (file != null && mVisible) {
                    mHandler?.post {
                        setWeatherBackgroundDrawable()
                        mHandler?.post(mDrawableRunnable)
                    }
                }
            }
        }

        /**
         * Fetches the Buienradar precipitation nowcast for the location (off the render thread).
         * Stores intensities (mm/h) for [drawRainTrend]; empty when no location / on error.
         */
        private fun maybeRefreshRainTrend(location: Location?) {
            if (location == null || !location.isUsable) {
                mRainIntensities = FloatArray(0)
                return
            }
            mPhotoScope.launch {
                mRainIntensities = try {
                    BuienradarNowcastSource()
                        .getRainTrend(location.latitude, location.longitude)
                        .map { it.intensityMmH.toFloat() }
                        .toFloatArray()
                } catch (e: Throwable) {
                    FloatArray(0)
                }
            }
        }

        /** Draws a subtle precipitation-nowcast strip at the bottom, only when rain is expected. */
        private fun drawRainTrend(canvas: android.graphics.Canvas) {
            try {
                val data = mRainIntensities
                if (data.size < 2) return
                val maxV = data.maxOrNull() ?: return
                if (maxV <= 0f) return
                val w = mSizes[0].toFloat()
                val h = mSizes[1].toFloat()
                if (w <= 0f || h <= 0f) return

                val stripH = (h * 0.08f).coerceIn(60f, 180f)
                val bottomInset = h * 0.06f
                val baseline = h - bottomInset
                val top = baseline - stripH
                val left = w * 0.08f
                val right = w * 0.92f
                val chartW = right - left
                val axisMax = maxOf(0.5f, maxV)
                val n = data.size
                val stepX = chartW / (n - 1)

                mRainPaint.style = Paint.Style.FILL
                mRainPaint.color = 0x55000000
                canvas.drawRoundRect(left - 24f, top - 24f, right + 24f, baseline + 24f, 28f, 28f, mRainPaint)

                val path = Path()
                path.moveTo(left, baseline)
                for (i in 0 until n) {
                    val x = left + stepX * i
                    val y = baseline - (data[i] / axisMax * stripH).coerceIn(0f, stripH)
                    path.lineTo(x, y)
                }
                path.lineTo(right, baseline)
                path.close()
                mRainPaint.color = 0xAA4FC3F7.toInt()
                canvas.drawPath(path, mRainPaint)
            } catch (e: Throwable) {
                // Never crash the wallpaper because of the overlay.
            }
        }

        override fun onDestroy() {
            onVisibilityChanged(false)
            mPhotoScope.cancel()
            mHandlerThread?.quit()
        }
    }
}
