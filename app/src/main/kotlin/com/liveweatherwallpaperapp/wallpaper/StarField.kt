/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import kotlin.math.PI
import kotlin.random.Random

/**
 * A single procedurally placed star.
 *
 * @property x 0f..1f horizontal position (fraction of screen width).
 * @property y 0f..1f vertical position (fraction of screen height, 0 = top).
 * @property size relative star size.
 * @property brightness base brightness (0f..1f).
 * @property twinkleSpeed relative twinkle animation speed.
 * @property twinklePhase twinkle animation phase offset, in radians.
 */
data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val brightness: Float,
    val twinkleSpeed: Float,
    val twinklePhase: Float,
)

/** A full procedural star field: a fixed set of stars plus the seed they were generated from. */
data class StarFieldParams(
    val stars: List<Star>,
    val seed: Float,
)

/**
 * Generates a procedural (not astronomically-accurate) star field and derives its
 * visibility from [WallpaperSceneState.daylight] as a pure function.
 */
object StarFieldFactory {
    private const val STAR_COUNT = 90
    private const val MAX_STAR_Y = 0.62f
    private const val VISIBILITY_THRESHOLD = 0.35f

    fun starFieldParams(locationSeed: Long = 0L): StarFieldParams {
        val seed = normalizeSeed(locationSeed)
        return StarFieldParams(stars = generateStars(seed), seed = seed)
    }

    /** Stars fade in as daylight drops below [VISIBILITY_THRESHOLD], fully visible at night. */
    fun starVisibility(daylight: Float): Float {
        val d = sanitizeUnit(daylight)
        return ((VISIBILITY_THRESHOLD - d) / VISIBILITY_THRESHOLD).coerceIn(0f, 1f)
    }

    /** Derives a stable per-location seed from latitude/longitude. */
    fun locationSeed(latitude: Double, longitude: Double): Long {
        if (!latitude.isFinite() || !longitude.isFinite()) return 0L
        val latPart = Math.round(latitude * 1000.0)
        val lonPart = Math.round(longitude * 1000.0)
        return latPart * 1_000_003L + lonPart
    }

    internal fun generateStars(seed: Float): List<Star> {
        val random = Random(seed.toRawBits().toLong())
        return (0 until STAR_COUNT).map {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat() * MAX_STAR_Y,
                size = 0.6f + random.nextFloat() * 1.6f,
                brightness = 0.35f + random.nextFloat() * 0.65f,
                twinkleSpeed = 0.5f + random.nextFloat() * 1.5f,
                twinklePhase = random.nextFloat() * (2f * PI.toFloat())
            )
        }
    }

    private fun normalizeSeed(value: Long): Float {
        val mod = ((value % 100000L) + 100000L) % 100000L
        return mod / 1000f // 0f..100f
    }

    private fun sanitizeUnit(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}
