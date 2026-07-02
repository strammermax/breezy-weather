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

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

/**
 * Hardware-accelerated View that renders the live wallpaper's animated weather effects
 * (clouds, fog, precipitation, glass-rain) via [WallpaperWeatherEffectRenderer].
 *
 * Intended as an intermediate layer in [org.breezyweather.ui.details.DetailsActivity]:
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
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /**
     * Rebuilds the renderer for [weatherKind] and [daylight] (0 = night, 1 = day).
     * Call whenever the user swipes to a different day or the weather condition changes.
     */
    fun setWeather(weatherKind: Int, daylight: Float) {
        val state = WallpaperSceneStateFactory.create(
            weatherKind = weatherKind,
            daylight = daylight,
        )
        renderer = WallpaperWeatherEffectRenderer(
            weatherKind = state.weatherKind,
            daylight = state.daylight,
            cloudSpeedFactor = state.windFactor,
            cloudField = CloudFieldFactory.cloudFieldParams(
                family = state.weatherFamily,
                cloudDensity = state.cloudDensity,
                cloudDarkness = state.cloudDarkness,
                windFactor = state.windFactor,
                windDirectionDegrees = state.windDirectionDegrees,
            ),
            fogField = FogFieldFactory.fogFieldParams(
                fogIntensity = state.fogIntensity,
                hazeIntensity = state.hazeIntensity,
                windFactor = state.windFactor,
                windDirectionDegrees = state.windDirectionDegrees,
            ),
            starField = StarFieldFactory.starFieldParams(locationSeed = 0L),
            glassRainIntensity = state.glassRainIntensity,
            precipitationIntensity = state.precipitationIntensity,
            precipitationTiltSlope = state.precipitationTiltSlope,
            resources = resources,
            useNewClouds = LiveWallpaperConfigManager(context).newCloudsEnabled,
            newCloudsParams = CloudEngineAdapter.sceneParams(state),
            cloudEngineContext = context,
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
        r.drawBackgroundWeatherPass(canvas)
        r.drawForegroundWeatherPass(canvas)
        r.drawGlassRainDrops(canvas)
    }
}
