/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import android.os.SystemClock

/**
 * Why a scene target was submitted. Drives the chosen transition duration.
 */
enum class SceneTransitionReason {
    INITIAL,
    WEATHER_DATA_CHANGED,
    AUTO_DAY_NIGHT,
    USER_FORCED_MODE,
    ROTATING_TEST,
    SURFACE_RECREATED,
}

/**
 * Pure, Android-Canvas-free controller that tracks a single active crossfade.
 *
 * Uses monotonic time ([SystemClock.elapsedRealtime] by default) so the progress is unaffected
 * by wall-clock adjustments. A clock is injectable so the logic is unit-testable without an
 * Android runtime.
 */
class TransitionManager(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private var startedAtMillis: Long = 0L
    private var durationMillis: Long = 0L
    private var active: Boolean = false

    val isActive: Boolean
        get() = active

    /**
     * Starts (or restarts) a transition. A non-positive [durationMillis] applies the target
     * immediately: no active transition is produced.
     */
    fun startTransition(durationMillis: Long) {
        if (durationMillis <= 0L) {
            cancelTransition()
            return
        }
        this.startedAtMillis = clock()
        this.durationMillis = durationMillis
        this.active = true
    }

    fun cancelTransition() {
        active = false
        durationMillis = 0L
    }

    /**
     * Eased progress in `0f..1f` while a transition is running, or `null` when there is none.
     * The transition auto-completes (and clears) once its duration has elapsed.
     */
    fun transitionProgress(): Float? {
        if (!active) return null
        val elapsed = clock() - startedAtMillis
        if (elapsed >= durationMillis || durationMillis <= 0L) {
            active = false
            return null
        }
        if (elapsed <= 0L) return 0f
        val linear = elapsed.toFloat() / durationMillis.toFloat()
        return smoothStep(linear)
    }
}

/**
 * Centrally defined transition durations. Pure function so it can be unit-tested.
 *
 * | Situation | Duration |
 * |---|---|
 * | Animations disabled / first frame / surface recreation | 0 s |
 * | Rotating test mode | 2 s |
 * | Forced Day/Night mode | 3 s |
 * | To/from thunder(storm) | 30 s |
 * | Precipitation start/stop | 45 s |
 * | Normal weather family change | 60 s |
 */
fun transitionDurationMillis(
    from: WallpaperWeatherFamily?,
    to: WallpaperWeatherFamily,
    reason: SceneTransitionReason,
    animationsEnabled: Boolean,
): Long {
    if (!animationsEnabled) return 0L
    // First-ever frame: there is nothing to fade from.
    if (from == null || reason == SceneTransitionReason.INITIAL) return 0L
    if (reason == SceneTransitionReason.SURFACE_RECREATED) return 0L
    if (reason == SceneTransitionReason.ROTATING_TEST) return 2_000L
    if (reason == SceneTransitionReason.USER_FORCED_MODE) return 3_000L
    // Auto day/night is handled by the continuous daylight factor; no discrete fade needed.
    if (reason == SceneTransitionReason.AUTO_DAY_NIGHT && from == to) return 0L
    if (from == to) return 0L
    if (involvesThunder(from) || involvesThunder(to)) return 30_000L
    if (isPrecipitation(from) != isPrecipitation(to)) return 45_000L
    return 60_000L
}

private fun involvesThunder(family: WallpaperWeatherFamily): Boolean =
    family == WallpaperWeatherFamily.THUNDER || family == WallpaperWeatherFamily.THUNDERSTORM

private fun isPrecipitation(family: WallpaperWeatherFamily): Boolean = when (family) {
    WallpaperWeatherFamily.RAIN,
    WallpaperWeatherFamily.SNOW,
    WallpaperWeatherFamily.SLEET,
    WallpaperWeatherFamily.HAIL,
    WallpaperWeatherFamily.THUNDERSTORM,
    -> true
    else -> false
}

/** Smoothstep easing. Stays within `0f..1f` and is monotonic. */
fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Linear interpolation between two floats. [progress] is expected pre-eased or raw `0f..1f`. */
fun lerp(from: Float, to: Float, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return from + (to - from) * p
}

/**
 * Per-channel ARGB interpolation. Implemented with bit math (no `android.graphics.Color`) so it
 * is unit-testable on a plain JVM and never interpolates a packed integer directly.
 */
fun lerpColor(from: Int, to: Int, progress: Float): Int {
    val p = progress.coerceIn(0f, 1f)
    val a = lerpChannel((from ushr 24) and 0xFF, (to ushr 24) and 0xFF, p)
    val r = lerpChannel((from ushr 16) and 0xFF, (to ushr 16) and 0xFF, p)
    val g = lerpChannel((from ushr 8) and 0xFF, (to ushr 8) and 0xFF, p)
    val b = lerpChannel(from and 0xFF, to and 0xFF, p)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun lerpChannel(from: Int, to: Int, progress: Float): Int =
    (from + (to - from) * progress).toInt().coerceIn(0, 255)

/**
 * Angle interpolation along the shortest arc. 350 -> 10 degrees passes through 0, not 180.
 * Result is normalized to `0f..360f`.
 */
fun lerpAngle(from: Float, to: Float, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val diff = (((to - from) % 360f) + 540f) % 360f - 180f
    return ((from + diff * p) % 360f + 360f) % 360f
}
