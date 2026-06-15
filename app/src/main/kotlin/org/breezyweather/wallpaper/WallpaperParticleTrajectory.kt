/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import kotlin.math.cos
import kotlin.math.roundToInt

/** The two precipitation kinds that use the pre-allocated particle pool (ACT-004). */
enum class WallpaperParticleKind {
    SNOW,
    HAIL,
}

/**
 * Pure, allocation-free trajectory math for the snow/hail particle pool (ACT-004).
 *
 * Kept free of any Android dependency so it can be unit tested on the JVM. The pool itself
 * ([WallpaperParticlePool]) owns the pre-reserved buffers and the small sprite atlas and only
 * calls into these pure functions; it never allocates per frame.
 *
 * Design rules from the backlog:
 * - 10 to 20 effective depth layers (or equivalent depth);
 * - wind direction and speed influence the horizontal trajectory;
 * - hail falls faster and more compact (smaller, tighter) than snow.
 */
object WallpaperParticleTrajectory {
    /** Number of distinct depth bands. Sits inside the documented 10..20 effective-layer range. */
    const val DEPTH_BANDS = 18

    const val MIN_EFFECTIVE_LAYERS = 10f
    const val MAX_EFFECTIVE_LAYERS = 20f

    /**
     * Horizontal wind component in the range [-windFactor, windFactor].
     *
     * Uses the cosine of the (normalized) wind direction so the drift sign matches the cloud
     * layers' horizontal direction in the shader. A missing or non-finite direction is treated as
     * a calm, neutral 0 contribution rather than producing NaN.
     */
    fun horizontalWindComponent(windDirectionDegrees: Float, windFactor: Float): Float {
        if (!windDirectionDegrees.isFinite()) return 0f
        val direction = ((windDirectionDegrees % 360f) + 360f) % 360f
        val wind = if (windFactor.isFinite()) windFactor.coerceAtLeast(0f) else 0f
        return cos(Math.toRadians(direction.toDouble())).toFloat() * wind
    }

    /** Vertical fall speed in pixels per second. Hail falls clearly faster than snow. */
    fun fallSpeed(kind: WallpaperParticleKind, depth: Float): Float {
        val d = depth.coerceIn(0f, 1f)
        return when (kind) {
            WallpaperParticleKind.SNOW -> 26f + d * 120f
            WallpaperParticleKind.HAIL -> 340f + d * 560f
        }
    }

    /** Particle radius in pixels. Hail is more compact (smaller) than snow at the same depth. */
    fun radius(kind: WallpaperParticleKind, depth: Float): Float {
        val d = depth.coerceIn(0f, 1f)
        return when (kind) {
            WallpaperParticleKind.SNOW -> 1.6f + d * 3.4f
            WallpaperParticleKind.HAIL -> 1.4f + d * 2.0f
        }
    }

    /** Base opacity of a particle. Nearer (deeper) particles are more opaque. */
    fun alpha(kind: WallpaperParticleKind, depth: Float): Float {
        val d = depth.coerceIn(0f, 1f)
        return when (kind) {
            WallpaperParticleKind.SNOW -> (0.18f + d * 0.72f).coerceIn(0f, 1f)
            WallpaperParticleKind.HAIL -> (0.42f + d * 0.50f).coerceIn(0f, 1f)
        }
    }

    /** Horizontal drift speed in pixels per second derived from the wind component. */
    fun driftSpeed(kind: WallpaperParticleKind, depth: Float, windComponent: Float): Float {
        val d = depth.coerceIn(0f, 1f)
        val component = if (windComponent.isFinite()) windComponent else 0f
        return when (kind) {
            WallpaperParticleKind.SNOW -> component * (10f + d * 26f)
            WallpaperParticleKind.HAIL -> component * (16f + d * 30f)
        }
    }

    /** Lateral sway amplitude. Snow wobbles; hail falls compact and straight (no sway). */
    fun swayAmplitude(kind: WallpaperParticleKind, depth: Float): Float {
        val d = depth.coerceIn(0f, 1f)
        return when (kind) {
            WallpaperParticleKind.SNOW -> 6f + d * 14f
            WallpaperParticleKind.HAIL -> 0f
        }
    }

    /**
     * Maps the adaptive quality value (effective precipitation layers, ~10..20) to the number of
     * active particles drawn from the pool. Higher quality draws more of the reserved buffer.
     */
    fun activeParticleCount(
        kind: WallpaperParticleKind,
        effectiveLayers: Float,
        minActive: Int,
        maxActive: Int,
    ): Int {
        if (maxActive <= minActive) return maxActive.coerceAtLeast(0)
        val layers = if (effectiveLayers.isFinite()) effectiveLayers else MIN_EFFECTIVE_LAYERS
        val clamped = layers.coerceIn(MIN_EFFECTIVE_LAYERS, MAX_EFFECTIVE_LAYERS)
        val fraction = (clamped - MIN_EFFECTIVE_LAYERS) / (MAX_EFFECTIVE_LAYERS - MIN_EFFECTIVE_LAYERS)
        // Snow is denser than hail at the same quality level.
        val biased = when (kind) {
            WallpaperParticleKind.SNOW -> fraction
            WallpaperParticleKind.HAIL -> fraction * 0.92f
        }
        return (minActive + (maxActive - minActive) * biased).roundToInt().coerceIn(minActive, maxActive)
    }

    /** Converts a depth band index into a normalized 0f (far) .. 1f (near) depth value. */
    fun depthForBand(band: Int): Float {
        val safeBand = band.coerceIn(0, DEPTH_BANDS - 1)
        return safeBand.toFloat() / (DEPTH_BANDS - 1)
    }
}