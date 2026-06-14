/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

/**
 * Rain-on-glass quality profile (ACT-006).
 *
 * @property maxDrops Canvas-fallback drop count for this profile.
 * @property trailLength relative length of the trail behind sliding drops (0f = no trail).
 * @property highlightStrength relative strength of the drop highlight.
 * @property refractionStrength relative strength of the drop shadow/refraction impression.
 */
data class GlassRainProfile(
    val maxDrops: Int,
    val trailLength: Float,
    val highlightStrength: Float,
    val refractionStrength: Float,
)

/**
 * Derives [GlassRainProfile]s and the static/sliding drop split as pure functions, following
 * the same pattern as [CloudFieldFactory] / [FogFieldFactory].
 */
object GlassRainFieldFactory {
    val LOW = GlassRainProfile(maxDrops = 14, trailLength = 0f, highlightStrength = 0.65f, refractionStrength = 0f)
    val BALANCED = GlassRainProfile(maxDrops = 24, trailLength = 0.6f, highlightStrength = 0.95f, refractionStrength = 0.55f)
    val HIGH = GlassRainProfile(maxDrops = 36, trailLength = 1f, highlightStrength = 1.2f, refractionStrength = 0.85f)

    /**
     * Maps the existing adaptive precipitation-quality level (the same
     * [10f..20f] range driving snow/hail layer counts, already hysteresis-stabilized
     * by [WallpaperWeatherEffectRenderer.updateAdaptivePrecipitationQuality]) to a
     * rain-on-glass profile.
     */
    fun profileFor(precipitationLayers: Float): GlassRainProfile = when {
        !precipitationLayers.isFinite() -> BALANCED
        precipitationLayers < 13f -> LOW
        precipitationLayers < 17f -> BALANCED
        else -> HIGH
    }

    /** Fraction (0f..1f) of drops that stay static rather than sliding, based on glass-rain intensity. */
    fun staticRatio(glassRainIntensity: Float): Float {
        val intensity = sanitizeUnit(glassRainIntensity)
        return (0.70f - intensity * 0.30f).coerceIn(0.40f, 0.70f)
    }

    private fun sanitizeUnit(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}
