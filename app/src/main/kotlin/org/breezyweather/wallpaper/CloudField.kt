/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

/**
 * Render parameters for a single procedural cloud mass layer.
 *
 * @property depth 0f = far/back, 1f = near/front.
 * @property scale relative cell size of the cloud shapes.
 * @property speedFactor relative horizontal speed.
 * @property alpha coverage/opacity of this layer (0f..1f).
 * @property darkness 0f = light, 1f = dark (0f..1f).
 * @property verticalOffset vertical placement offset for this layer.
 */
data class CloudLayer(
    val depth: Float,
    val scale: Float,
    val speedFactor: Float,
    val alpha: Float,
    val darkness: Float,
    val verticalOffset: Float,
)

/** A full cloud field: a fixed set of layers plus the shared wind direction. */
data class CloudFieldParams(
    val layers: List<CloudLayer>,
    val directionDegrees: Float,
)

/**
 * Derives [CloudFieldParams] from the [WallpaperSceneState] cloud fields as a pure function.
 *
 * Always returns the same number of layers (back/mid/front) so transitions between families
 * never appear as a hard layer-count pop: families with fewer "visible" layers simply set the
 * remaining layers' alpha to 0.
 */
object CloudFieldFactory {
    private const val LAYER_COUNT = 3
    private const val DEFAULT_DIRECTION_DEGREES = 70f

    // Per-layer base shape: back (large, slow, light), mid, front (small, fast, slightly darker).
    private val BASE_SCALE = floatArrayOf(1.6f, 1.0f, 0.65f)
    private val BASE_SPEED = floatArrayOf(0.35f, 0.65f, 1.05f)
    private val ALPHA_FRACTION = floatArrayOf(0.90f, 0.75f, 0.55f)
    private val DARKNESS_OFFSET = floatArrayOf(-0.05f, 0f, 0.05f)
    private val VERTICAL_OFFSET = floatArrayOf(-0.05f, 0f, 0.04f)

    fun cloudFieldParams(
        family: WallpaperWeatherFamily,
        cloudDensity: Float,
        cloudDarkness: Float,
        windFactor: Float,
        windDirectionDegrees: Float?,
    ): CloudFieldParams {
        val density = sanitizeUnit(cloudDensity)
        val darkness = sanitizeUnit(cloudDarkness)
        val wind = sanitizeNonNegative(windFactor)
        val visibleLayers = visibleLayerCount(family)
        val speedMultiplier = 0.3f + wind

        val layers = (0 until LAYER_COUNT).map { i ->
            val alpha = if (i < visibleLayers) (density * ALPHA_FRACTION[i]).coerceIn(0f, 1f) else 0f
            CloudLayer(
                depth = i.toFloat() / (LAYER_COUNT - 1),
                scale = BASE_SCALE[i],
                speedFactor = BASE_SPEED[i] * speedMultiplier,
                alpha = alpha,
                darkness = (darkness + DARKNESS_OFFSET[i]).coerceIn(0f, 1f),
                verticalOffset = VERTICAL_OFFSET[i],
            )
        }

        return CloudFieldParams(
            layers = layers,
            directionDegrees = normalizeDegrees(windDirectionDegrees),
        )
    }

    private fun visibleLayerCount(family: WallpaperWeatherFamily): Int = when (family) {
        WallpaperWeatherFamily.CLEAR -> 0
        WallpaperWeatherFamily.PARTLY_CLOUDY,
        WallpaperWeatherFamily.FOG,
        WallpaperWeatherFamily.HAZE,
        -> 2
        else -> 3
    }

    private fun sanitizeUnit(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    private fun sanitizeNonNegative(value: Float): Float =
        if (value.isFinite()) value.coerceAtLeast(0f) else 0f

    private fun normalizeDegrees(value: Float?): Float {
        if (value == null || !value.isFinite()) return DEFAULT_DIRECTION_DEGREES
        return ((value % 360f) + 360f) % 360f
    }
}
