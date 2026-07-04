/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

/**
 * Central wallpaper rendering quality profiles (ACT-007). Ordered from lowest to highest
 * visual richness so that [ordinal] can be used directly for degradation/recovery steps.
 */
enum class WallpaperQualityProfile {
    BATTERY_SAVER,
    BALANCED,
    HIGH,
}

/**
 * Effect budgets driven centrally by [WallpaperQualityProfile]. Max FPS stays 30 in every
 * profile (enforced by [WallpaperWeatherEffectRenderer], not part of this budget).
 *
 * @property maxSnowParticles upper bound for the snow particle pool (ACT-004).
 * @property maxHailParticles upper bound for the hail particle pool (ACT-004).
 * @property cloudLayers number of cloud layers rendered (ACT-003), out of the 5 defined.
 * @property fogBands number of fog/haze bands rendered (ACT-005), out of the 4 defined.
 * @property maxGlassDrops upper bound for the rain-on-glass drop count (ACT-006).
 * @property blurStrength relative softness impression for fog/haze bands (ACT-005).
 * @property effectUpdateHz update rate for particle/drop simulation; rendering itself is
 * still capped at 30 FPS.
 */
data class QualityBudget(
    val maxSnowParticles: Int,
    val maxHailParticles: Int,
    val cloudLayers: Int,
    val fogBands: Int,
    val maxGlassDrops: Int,
    val blurStrength: Float,
    val effectUpdateHz: Int,
)

/**
 * Provides the [QualityBudget] for each [WallpaperQualityProfile] and resolves the
 * effective profile from a chosen profile plus a current degradation level, as pure
 * functions. See [QualityDegradationTracker] for the stateful frame-time-driven
 * degradation/recovery logic.
 */
object WallpaperQualityProfileFactory {
    private val BUDGETS = mapOf(
        WallpaperQualityProfile.BATTERY_SAVER to QualityBudget(
            maxSnowParticles = 60,
            maxHailParticles = 35,
            cloudLayers = 2,
            fogBands = 2,
            maxGlassDrops = 14,
            blurStrength = 0.4f,
            effectUpdateHz = 20,
        ),
        WallpaperQualityProfile.BALANCED to QualityBudget(
            maxSnowParticles = 100,
            maxHailParticles = 55,
            cloudLayers = 4,
            fogBands = 3,
            maxGlassDrops = 24,
            blurStrength = 0.7f,
            effectUpdateHz = 30,
        ),
        WallpaperQualityProfile.HIGH to QualityBudget(
            maxSnowParticles = 140,
            maxHailParticles = 75,
            cloudLayers = 5,
            fogBands = 4,
            maxGlassDrops = 36,
            blurStrength = 1.0f,
            effectUpdateHz = 30,
        ),
    )

    fun budgetFor(profile: WallpaperQualityProfile): QualityBudget = BUDGETS.getValue(profile)

    /** Falls back to [WallpaperQualityProfile.BALANCED] for an unknown or missing name. */
    fun fromName(name: String?): WallpaperQualityProfile =
        WallpaperQualityProfile.entries.find { it.name == name } ?: WallpaperQualityProfile.BALANCED

    /**
     * Resolves the effective profile from the user-chosen profile and a temporary
     * degradation level (0 = no degradation). The result never exceeds [chosen] and never
     * drops below [WallpaperQualityProfile.BATTERY_SAVER].
     */
    fun resolveEffectiveProfile(chosen: WallpaperQualityProfile, degradationLevel: Int): WallpaperQualityProfile {
        val ordinal = (chosen.ordinal - degradationLevel).coerceIn(0, WallpaperQualityProfile.entries.lastIndex)
        return WallpaperQualityProfile.entries[ordinal]
    }
}

/**
 * Tracks recent frame times to drive temporary degradation/recovery with hysteresis
 * (ACT-007 section 8/9), without permanently overwriting the user-chosen profile.
 *
 * - Degrades one level when 10 or more of the last 30 frames exceed [SLOW_FRAME_THRESHOLD_MILLIS].
 * - Recovers one level when the rolling average over [RECOVERY_WINDOW_MILLIS] stays below
 *   [RECOVERY_AVERAGE_THRESHOLD_MILLIS].
 *
 * Frame times must be supplied by the caller (no wall-clock access here), keeping this
 * pure-enough for unit testing.
 */
class QualityDegradationTracker(private val chosen: WallpaperQualityProfile) {
    private val window = FloatArray(SLOW_FRAME_WINDOW_SIZE)
    private var windowIndex = 0
    private var windowFilled = 0
    private var recoveryAccumulatedMillis = 0f
    private var recoveryAverageMillis = 0f

    var degradationLevel: Int = 0
        private set

    val effectiveProfile: WallpaperQualityProfile
        get() = WallpaperQualityProfileFactory.resolveEffectiveProfile(chosen, degradationLevel)

    /** Records one frame's render time and returns the (possibly updated) effective profile. */
    fun recordFrame(frameMillis: Float): WallpaperQualityProfile {
        window[windowIndex] = frameMillis
        windowIndex = (windowIndex + 1) % window.size
        if (windowFilled < window.size) windowFilled++

        if (windowFilled == window.size) {
            var slowFrames = 0
            for (sample in window) {
                if (sample > SLOW_FRAME_THRESHOLD_MILLIS) slowFrames++
            }
            if (slowFrames >= SLOW_FRAME_COUNT_THRESHOLD && degradationLevel < chosen.ordinal) {
                degradationLevel++
                recoveryAccumulatedMillis = 0f
                recoveryAverageMillis = 0f
                // Start a fresh window so a transitional frame right after this degrade step
                // can't immediately trigger another one (max 1 level per step).
                windowIndex = 0
                windowFilled = 0
            }
        }

        if (degradationLevel > 0) {
            recoveryAverageMillis += (frameMillis - recoveryAverageMillis) * RECOVERY_AVERAGE_SMOOTHING
            recoveryAccumulatedMillis += frameMillis
            if (recoveryAccumulatedMillis >= RECOVERY_WINDOW_MILLIS) {
                if (recoveryAverageMillis < RECOVERY_AVERAGE_THRESHOLD_MILLIS) {
                    degradationLevel--
                }
                recoveryAccumulatedMillis = 0f
                recoveryAverageMillis = 0f
            }
        }
        return effectiveProfile
    }

    /** Resets temporary degradation, e.g. when the wallpaper becomes visible again. */
    fun reset() {
        degradationLevel = 0
        windowIndex = 0
        windowFilled = 0
        recoveryAccumulatedMillis = 0f
        recoveryAverageMillis = 0f
    }

    companion object {
        const val SLOW_FRAME_WINDOW_SIZE = 30
        const val SLOW_FRAME_THRESHOLD_MILLIS = 40f
        const val SLOW_FRAME_COUNT_THRESHOLD = 10
        const val RECOVERY_WINDOW_MILLIS = 10_000f
        const val RECOVERY_AVERAGE_THRESHOLD_MILLIS = 35f
        private const val RECOVERY_AVERAGE_SMOOTHING = 0.05f
    }
}
