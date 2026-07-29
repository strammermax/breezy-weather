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

package com.liveweatherwallpaperapp.wallpaper

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Process
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import com.liveweatherwallpaperapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hardware-accelerated View that renders the live wallpaper's animated weather effects
 * (clouds, fog, precipitation, glass-rain) via [WallpaperWeatherEffectRenderer].
 *
 * Intended as an intermediate layer in [com.liveweatherwallpaperapp.ui.details.DetailsActivity]:
 * sits between the static [WallpaperSceneSnapshot] window background and the transparent
 * Compose scaffold, giving the detail screen the same animated cloud/rain effects as the
 * live wallpaper home screen.
 *
 * Hardware acceleration is required so [android.graphics.RuntimeShader] (AGSL) works.
 * Do NOT call [setLayerType] with [LAYER_TYPE_SOFTWARE] on this view.
 */
internal class WallpaperEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var renderer: WallpaperWeatherEffectRenderer? = null
    private var shouldAnimate = false
    private var lastFrameNanos = 0L

    // Debug-only fps counter to verify the 60fps cap on-device (e.g. on 90/120Hz panels),
    // compiled out of release builds. No-op cost in release since BuildConfig.DEBUG is a
    // compile-time constant the compiler can dead-code-eliminate the branch for.
    private var mTelemetryFrameCount = 0
    private var mLastTelemetryLogMillis = 0L

    companion object {
        /** Decorative background is capped at 30fps so the foreground always gets GPU time. */
        private const val MAX_FRAME_INTERVAL_MILLIS = 34L
        private const val TELEMETRY_LOG_INTERVAL_MILLIS = 5_000L

        // Temporary isolation switches for the ~20fps-on-30fps-cap MainActivity measurement --
        // flip one at a time, rebuild, and compare the fps telemetry to find which layer is
        // actually eating the frame budget. All false is the normal, shipped configuration.
        private const val DEBUG_DISABLE_FROSTED_BLUR = false
        private const val DEBUG_DISABLE_FOREGROUND_PHOTO = false
        private const val DEBUG_DISABLE_WEATHER_PASSES = false
    }

    /** Blurs only this background layer; UI and glass cards above it remain sharp. */
    fun setFrosted(frosted: Boolean, strength: Int = 2, tint: String = "system") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !DEBUG_DISABLE_FROSTED_BLUR) {
            val radius = when (strength.coerceIn(1, 3)) {
                1 -> 16f
                3 -> 42f
                else -> 28f
            }
            setRenderEffect(
                if (frosted) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null
            )
        }
        val useLightFrost = when (tint) {
            "light" -> true
            "dark" -> false
            else ->
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
                    Configuration.UI_MODE_NIGHT_YES
        }
        foreground = if (frosted) {
            (if (useLightFrost) 0x26FFFFFF else 0x38000000).toDrawable()
        } else {
            null
        }
    }

    /** Near/foreground photo content (e.g. a building), drawn between the two weather passes
     *  so clouds stay behind it — see [WallpaperSceneSnapshot.render]'s `depth` parameter. */
    private var foregroundPhoto: Bitmap? = null
    private var foregroundPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val foregroundDestination = Rect()

    /** Sets (or clears, with null) the near-photo layer and its night/greyscale tint. */
    fun setForegroundPhoto(bitmap: Bitmap?, greyscaleAmount: Float = 0f) {
        foregroundPhoto = bitmap
        foregroundPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (greyscaleAmount > 0f) {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1f - greyscaleAmount) })
            }
        }
        invalidate()
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!shouldAnimate) return
            val intervalMillis = if (lastFrameNanos > 0L) {
                ((frameTimeNanos - lastFrameNanos) / 1_000_000L).coerceIn(8L, 100L)
            } else {
                16L
            }
            lastFrameNanos = frameTimeNanos
            renderer?.update(intervalMillis, animate = true)
            invalidate()
            if (BuildConfig.DEBUG) maybeLogFrameTelemetry()
            // Unlike the home-screen wallpaper (a fixed 30fps RxJava interval), this view was
            // previously posting on every vsync -- up to 120fps on high-refresh-rate panels, for
            // a background layer sitting behind a RenderEffect blur that recomposites on every
            // invalidate(). Capped to 60fps: no change on 60Hz devices, up to 2-4x fewer
            // update()+invalidate()+blur-recomposite cycles on 90/120Hz ones.
            Choreographer.getInstance().postFrameCallbackDelayed(this, MAX_FRAME_INTERVAL_MILLIS)
        }
    }

    private fun maybeLogFrameTelemetry() {
        mTelemetryFrameCount++
        val now = System.currentTimeMillis()
        if (mLastTelemetryLogMillis == 0L) {
            mLastTelemetryLogMillis = now
            return
        }
        val elapsed = now - mLastTelemetryLogMillis
        if (elapsed < TELEMETRY_LOG_INTERVAL_MILLIS) return
        val fps = mTelemetryFrameCount * 1000.0 / elapsed
        Log.d("LWW", "WallpaperEffectView fps=%.1f frames=%d window=%dms".format(fps, mTelemetryFrameCount, elapsed))
        mTelemetryFrameCount = 0
        mLastTelemetryLogMillis = now
    }

    /**
     * Rebuilds the renderer for [weatherKind] and [daylight] (0 = night, 1 = day).
     * Call whenever the user swipes to a different day or the weather condition changes.
     */
    fun setWeather(weatherKind: Int, daylight: Float) {
        renderer = createRenderer(weatherKind, daylight)
    }

    /** Builds resource-heavy cloud and precipitation state away from the UI thread. */
    suspend fun setWeatherAsync(weatherKind: Int, daylight: Float) {
        val preparedRenderer = withContext(Dispatchers.Default) {
            val originalPriority = Process.getThreadPriority(Process.myTid())
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                createRenderer(weatherKind, daylight)
            } finally {
                Process.setThreadPriority(originalPriority)
            }
        }
        renderer = preparedRenderer
        invalidate()
    }

    private fun createRenderer(weatherKind: Int, daylight: Float): WallpaperWeatherEffectRenderer {
        val state = WallpaperSceneStateFactory.create(
            weatherKind = weatherKind,
            daylight = daylight
        )
        val liveWallpaperConfig = LiveWallpaperConfigManager(context)
        return WallpaperWeatherEffectRenderer(
            weatherKind = state.weatherKind,
            daylight = state.daylight,
            cloudSpeedFactor = state.windFactor,
            cloudField = CloudFieldFactory.cloudFieldParams(
                family = state.weatherFamily,
                cloudDensity = state.cloudDensity,
                cloudDarkness = state.cloudDarkness,
                windFactor = state.windFactor,
                windDirectionDegrees = state.windDirectionDegrees
            ),
            fogField = FogFieldFactory.fogFieldParams(
                fogIntensity = state.fogIntensity,
                hazeIntensity = state.hazeIntensity,
                windFactor = state.windFactor,
                windDirectionDegrees = state.windDirectionDegrees
            ),
            starField = StarFieldFactory.starFieldParams(locationSeed = 0L),
            glassRainIntensity = state.glassRainIntensity,
            precipitationIntensity = state.precipitationIntensity,
            precipitationTiltSlope = state.precipitationTiltSlope,
            resources = resources,
            useNewClouds = liveWallpaperConfig.newCloudsEnabled,
            newCloudsParams = CloudEngineAdapter.sceneParams(state),
            cloudEngineContext = context
        )
    }

    /** Start or stop the Choreographer-driven animation loop. */
    fun setDrawable(draw: Boolean) {
        if (shouldAnimate == draw) return
        shouldAnimate = draw
        if (draw) {
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (renderer != null) setDrawable(true)
    }

    override fun onDetachedFromWindow() {
        setDrawable(false)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val r = renderer ?: return
        if (!DEBUG_DISABLE_WEATHER_PASSES) r.drawBackgroundWeatherPass(canvas)
        if (!DEBUG_DISABLE_FOREGROUND_PHOTO) {
            foregroundPhoto?.let { canvas.drawBitmap(it, null, foregroundDestination, foregroundPaint) }
        }
        if (!DEBUG_DISABLE_WEATHER_PASSES) {
            r.drawForegroundWeatherPass(canvas)
            r.drawGlassRainDrops(canvas)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        foregroundDestination.set(0, 0, w, h)
    }
}
