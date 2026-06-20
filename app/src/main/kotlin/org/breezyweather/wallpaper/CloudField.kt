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
 * Always returns the same number of layers (back to front) so transitions between families
 * never appear as a hard layer-count pop: families with fewer "visible" layers simply set the
 * remaining layers' alpha to 0.
 */
object CloudFieldFactory {
    private const val LAYER_COUNT = 5
    private const val DEFAULT_DIRECTION_DEGREES = 70f

    // Back-to-front parallax: distant layers are smaller, higher, lighter and slower.
    private val BASE_SCALE = floatArrayOf(0.55f, 0.70f, 0.88f, 1.08f, 1.30f)
    private val BASE_SPEED = floatArrayOf(0.35f, 0.50f, 0.65f, 0.82f, 1.00f)
    private val ALPHA_FRACTION = floatArrayOf(0.38f, 0.50f, 0.64f, 0.78f, 0.92f)
    private val DARKNESS_OFFSET = floatArrayOf(-0.08f, -0.04f, 0f, 0.04f, 0.08f)
    private val VERTICAL_OFFSET = floatArrayOf(-0.06f, -0.03f, 0f, 0.03f, 0.06f)

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
        val speedMultiplier = 0.3f + wind
        val coverageBoost = when (family) {
            WallpaperWeatherFamily.RAIN,
            WallpaperWeatherFamily.THUNDER,
            WallpaperWeatherFamily.THUNDERSTORM,
            -> 1.15f
            else -> 1f
        }

        val layers = (0 until LAYER_COUNT).map { i ->
            val alpha = if (isLayerVisible(density, i)) {
                (density * ALPHA_FRACTION[i] * coverageBoost).coerceIn(0f, 1f)
            } else {
                0f
            }
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

    private fun isLayerVisible(density: Float, index: Int): Boolean = when {
        density <= 0f -> false
        density < 0.65f -> index == 0 || index == 2 || index == 4
        else -> true
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
