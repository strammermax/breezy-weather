/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

/**
 * Render parameters for a single horizontal fog/haze depth band.
 *
 * @property verticalCenter 0f = top of the scene, 1f = horizon/bottom.
 * @property height relative height of the band.
 * @property baseAlpha base opacity of this band (0f..1f).
 * @property speedFactor relative horizontal drift speed.
 * @property blurStrength soft-edge impression (0f..1f).
 */
data class FogBand(
    val verticalCenter: Float,
    val height: Float,
    val baseAlpha: Float,
    val speedFactor: Float,
    val blurStrength: Float,
)

/** A full fog/haze field: a fixed set of depth bands plus shared drift direction. */
data class FogFieldParams(
    val bands: List<FogBand>,
    val isHaze: Boolean,
    val directionDegrees: Float,
    /**
     * Flat fog/haze tint applied across the entire scene (sky and photo foreground
     * alike), on top of the denser near-horizon [bands]. 0f = no global tint.
     */
    val globalAlpha: Float,
    /**
     * Heavy-fog foreground veil: 0 disables it, 1 fades from opaque at the top
     * to semi-transparent near the bottom, like visibility disappearing into distance.
     */
    val foregroundGradientStrength: Float,
)

/**
 * Derives [FogFieldParams] from the [WallpaperSceneState] fog/haze fields as a pure function.
 *
 * Always returns the same number of bands so transitions never appear as a hard band-count pop:
 * when both `fogIntensity` and `hazeIntensity` are 0, every band's alpha is 0.
 */
object FogFieldFactory {
    private const val BAND_COUNT = 4
    private const val DEFAULT_DIRECTION_DEGREES = 70f
    private const val FOG_MAX_ALPHA = 0.68f
    private const val HAZE_MAX_ALPHA = 0.66f

    // Flat tint covering the whole scene (sky and photo foreground), separate from
    // the denser near-horizon bands below, so fog/haze reads as a uniform atmosphere
    // rather than only a low-lying band.
    private const val FOG_GLOBAL_FRACTION = 0.58f
    private const val HAZE_GLOBAL_FRACTION = 0.45f

    // Lower bands (near the horizon) are taller, denser and slower; higher bands are
    // thinner, lighter and slightly faster.
    private val VERTICAL_CENTER = floatArrayOf(0.92f, 0.76f, 0.60f, 0.42f)
    private val HEIGHT = floatArrayOf(0.30f, 0.24f, 0.20f, 0.16f)
    private val ALPHA_FRACTION = floatArrayOf(1.00f, 0.65f, 0.40f, 0.20f)
    // Light mist stays close to the landscape: no high band that could be mistaken
    // for a cloud, and enough overlap to create visible drifting wisps over the photo.
    private val HAZE_VERTICAL_CENTER = floatArrayOf(0.94f, 0.82f, 0.70f, 0.58f)
    private val HAZE_HEIGHT = floatArrayOf(0.26f, 0.21f, 0.17f, 0.13f)
    private val HAZE_ALPHA_FRACTION = floatArrayOf(1.00f, 0.78f, 0.55f, 0.32f)
    private val SPEED_FACTOR = floatArrayOf(0.06f, 0.10f, 0.16f, 0.24f)
    private val BLUR_STRENGTH = floatArrayOf(0.75f, 0.58f, 0.42f, 0.28f)

    // Fog: cooler, neutral grey/blue. Haze: warmer, light tan/brown.
    private val FOG_DAY = floatArrayOf(0.80f, 0.87f, 0.90f)
    private val FOG_NIGHT = floatArrayOf(0.36f, 0.42f, 0.50f)
    private val HAZE_DAY = floatArrayOf(0.88f, 0.86f, 0.82f)
    private val HAZE_NIGHT = floatArrayOf(0.48f, 0.46f, 0.44f)

    fun fogFieldParams(
        fogIntensity: Float,
        hazeIntensity: Float,
        windFactor: Float,
        windDirectionDegrees: Float?,
    ): FogFieldParams {
        val fog = sanitizeUnit(fogIntensity)
        val haze = sanitizeUnit(hazeIntensity)
        val intensity = maxOf(fog, haze)
        val isHaze = haze > fog
        val maxAlpha = if (isHaze) HAZE_MAX_ALPHA else FOG_MAX_ALPHA
        val verticalCenters = if (isHaze) HAZE_VERTICAL_CENTER else VERTICAL_CENTER
        val heights = if (isHaze) HAZE_HEIGHT else HEIGHT
        val alphaFractions = if (isHaze) HAZE_ALPHA_FRACTION else ALPHA_FRACTION
        val wind = sanitizeNonNegative(windFactor)
        val speedMultiplier = 0.4f + wind * 0.6f

        val bands = (0 until BAND_COUNT).map { i ->
            FogBand(
                verticalCenter = verticalCenters[i],
                height = heights[i],
                baseAlpha = (intensity * alphaFractions[i] * maxAlpha).coerceIn(0f, 1f),
                speedFactor = SPEED_FACTOR[i] * speedMultiplier,
                blurStrength = BLUR_STRENGTH[i],
            )
        }

        val globalFraction = if (isHaze) HAZE_GLOBAL_FRACTION else FOG_GLOBAL_FRACTION
        val foregroundGradientStrength = if (isHaze) {
            // Light mist keeps the sky open, but needs a substantial low-lying veil
            // over the landscape. Its shader profile is ground-weighted, unlike fog.
            0.74f
        } else when {
            intensity >= 0.95f -> 1f
            intensity >= 0.60f -> 0.72f
            else -> 0f
        }
        return FogFieldParams(
            bands = bands,
            isHaze = isHaze,
            directionDegrees = normalizeDegrees(windDirectionDegrees),
            globalAlpha = (intensity * globalFraction * maxAlpha).coerceIn(0f, 1f),
            foregroundGradientStrength = foregroundGradientStrength,
        )
    }

    /** Returns the band color as normalized `[r, g, b]` (0f..1f), blended by `daylight`. */
    fun fogColor(isHaze: Boolean, daylight: Float, neutralAmount: Float = 0f): FloatArray {
        val d = sanitizeUnit(daylight)
        val day = if (isHaze) HAZE_DAY else FOG_DAY
        val night = if (isHaze) HAZE_NIGHT else FOG_NIGHT
        val color = FloatArray(3) { night[it] + (day[it] - night[it]) * d }
        val neutral = sanitizeUnit(neutralAmount)
        if (neutral <= 0f) return color
        val grey = color[0] * 0.2126f + color[1] * 0.7152f + color[2] * 0.0722f
        return FloatArray(3) { color[it] + (grey - color[it]) * neutral }
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
