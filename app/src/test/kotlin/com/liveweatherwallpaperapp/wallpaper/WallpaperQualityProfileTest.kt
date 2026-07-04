/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WallpaperQualityProfileTest {
    @Test
    fun `each profile yields its expected budget`() {
        val battery = WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.BATTERY_SAVER)
        battery.maxSnowParticles shouldBe 60
        battery.maxHailParticles shouldBe 35
        battery.cloudLayers shouldBe 2
        battery.fogBands shouldBe 2
        battery.maxGlassDrops shouldBe 14
        battery.blurStrength shouldBe 0.4f
        battery.effectUpdateHz shouldBe 20

        val balanced = WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.BALANCED)
        balanced.maxSnowParticles shouldBe 100
        balanced.maxHailParticles shouldBe 55
        balanced.cloudLayers shouldBe 4
        balanced.fogBands shouldBe 3
        balanced.maxGlassDrops shouldBe 24
        balanced.blurStrength shouldBe 0.7f
        balanced.effectUpdateHz shouldBe 30

        val high = WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.HIGH)
        high.maxSnowParticles shouldBe 140
        high.maxHailParticles shouldBe 75
        high.cloudLayers shouldBe 5
        high.fogBands shouldBe 4
        high.maxGlassDrops shouldBe 36
        high.blurStrength shouldBe 1.0f
        high.effectUpdateHz shouldBe 30
    }

    @Test
    fun `budgets demonstrably differ between battery saver, balanced and high`() {
        val battery = WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.BATTERY_SAVER)
        val balanced = WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.BALANCED)
        val high = WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.HIGH)

        (battery.maxSnowParticles < balanced.maxSnowParticles) shouldBe true
        (balanced.maxSnowParticles < high.maxSnowParticles) shouldBe true
        (battery.maxGlassDrops < balanced.maxGlassDrops) shouldBe true
        (balanced.maxGlassDrops < high.maxGlassDrops) shouldBe true
        (battery.cloudLayers <= balanced.cloudLayers) shouldBe true
        (balanced.cloudLayers <= high.cloudLayers) shouldBe true
        (battery.fogBands < balanced.fogBands) shouldBe true
        (balanced.fogBands < high.fogBands) shouldBe true
    }

    @Test
    fun `an unknown or missing profile name falls back to balanced`() {
        WallpaperQualityProfileFactory.fromName(null) shouldBe WallpaperQualityProfile.BALANCED
        WallpaperQualityProfileFactory.fromName("") shouldBe WallpaperQualityProfile.BALANCED
        WallpaperQualityProfileFactory.fromName("ULTRA") shouldBe WallpaperQualityProfile.BALANCED
        WallpaperQualityProfileFactory.fromName("HIGH") shouldBe WallpaperQualityProfile.HIGH
    }

    @Test
    fun `max effective fps stays at 30 in every profile`() {
        WallpaperQualityProfile.entries.forEach { profile ->
            val budget = WallpaperQualityProfileFactory.budgetFor(profile)
            (budget.effectUpdateHz <= 30) shouldBe true
        }
    }

    @Test
    fun `resolveEffectiveProfile never exceeds the chosen profile and floors at battery saver`() {
        WallpaperQualityProfileFactory.resolveEffectiveProfile(WallpaperQualityProfile.HIGH, 0) shouldBe
            WallpaperQualityProfile.HIGH
        WallpaperQualityProfileFactory.resolveEffectiveProfile(WallpaperQualityProfile.HIGH, 1) shouldBe
            WallpaperQualityProfile.BALANCED
        WallpaperQualityProfileFactory.resolveEffectiveProfile(WallpaperQualityProfile.HIGH, 2) shouldBe
            WallpaperQualityProfile.BATTERY_SAVER
        WallpaperQualityProfileFactory.resolveEffectiveProfile(WallpaperQualityProfile.HIGH, 99) shouldBe
            WallpaperQualityProfile.BATTERY_SAVER
        WallpaperQualityProfileFactory.resolveEffectiveProfile(WallpaperQualityProfile.BATTERY_SAVER, 0) shouldBe
            WallpaperQualityProfile.BATTERY_SAVER
    }

    @Test
    fun `a sustained run of slow frames degrades by one level after the configured frame count`() {
        val tracker = QualityDegradationTracker(WallpaperQualityProfile.HIGH)
        // Fewer than the degrade threshold of slow frames: stays at HIGH.
        repeat(QualityDegradationTracker.SLOW_FRAME_COUNT_THRESHOLD - 1) {
            tracker.recordFrame(QualityDegradationTracker.SLOW_FRAME_THRESHOLD_MILLIS + 5f)
        }
        repeat(
            QualityDegradationTracker.SLOW_FRAME_WINDOW_SIZE -
                (QualityDegradationTracker.SLOW_FRAME_COUNT_THRESHOLD - 1)
        ) {
            tracker.recordFrame(10f)
        }
        tracker.effectiveProfile shouldBe WallpaperQualityProfile.HIGH

        // A full window of slow frames pushes degradation down one level.
        val tracker2 = QualityDegradationTracker(WallpaperQualityProfile.HIGH)
        repeat(QualityDegradationTracker.SLOW_FRAME_WINDOW_SIZE) {
            tracker2.recordFrame(QualityDegradationTracker.SLOW_FRAME_THRESHOLD_MILLIS + 5f)
        }
        tracker2.effectiveProfile shouldBe WallpaperQualityProfile.BALANCED
        tracker2.degradationLevel shouldBe 1
    }

    @Test
    fun `a sustained run of fast frames recovers one level after the recovery window`() {
        val tracker = QualityDegradationTracker(WallpaperQualityProfile.HIGH)
        repeat(QualityDegradationTracker.SLOW_FRAME_WINDOW_SIZE) {
            tracker.recordFrame(QualityDegradationTracker.SLOW_FRAME_THRESHOLD_MILLIS + 5f)
        }
        tracker.degradationLevel shouldBe 1

        // Feed fast frames until the 10-second recovery window is exceeded.
        val fastFrameMillis = 10f
        var accumulated = 0f
        while (accumulated < QualityDegradationTracker.RECOVERY_WINDOW_MILLIS) {
            tracker.recordFrame(fastFrameMillis)
            accumulated += fastFrameMillis
        }

        tracker.degradationLevel shouldBe 0
        tracker.effectiveProfile shouldBe WallpaperQualityProfile.HIGH
    }

    @Test
    fun `degrade and recover use different thresholds (hysteresis)`() {
        val tracker = QualityDegradationTracker(WallpaperQualityProfile.HIGH)
        repeat(QualityDegradationTracker.SLOW_FRAME_WINDOW_SIZE) {
            tracker.recordFrame(QualityDegradationTracker.SLOW_FRAME_THRESHOLD_MILLIS + 5f)
        }
        tracker.degradationLevel shouldBe 1

        // Frame times between the recovery threshold and the slow-frame threshold
        // neither degrade further nor recover, even after the recovery window elapses.
        val midFrameMillis = (
            QualityDegradationTracker.RECOVERY_AVERAGE_THRESHOLD_MILLIS +
                QualityDegradationTracker.SLOW_FRAME_THRESHOLD_MILLIS
            ) / 2f
        var accumulated = 0f
        while (accumulated < QualityDegradationTracker.RECOVERY_WINDOW_MILLIS) {
            tracker.recordFrame(midFrameMillis)
            accumulated += midFrameMillis
        }

        tracker.degradationLevel shouldBe 1
    }

    @Test
    fun `degradation does not permanently override the chosen profile`() {
        val tracker = QualityDegradationTracker(WallpaperQualityProfile.HIGH)
        repeat(QualityDegradationTracker.SLOW_FRAME_WINDOW_SIZE) {
            tracker.recordFrame(QualityDegradationTracker.SLOW_FRAME_THRESHOLD_MILLIS + 5f)
        }
        tracker.degradationLevel shouldBe 1

        // The user-chosen profile passed to the constructor is unaffected.
        WallpaperQualityProfileFactory.resolveEffectiveProfile(WallpaperQualityProfile.HIGH, 0) shouldBe
            WallpaperQualityProfile.HIGH
    }

    @Test
    fun `reset clears degradation back to the chosen profile`() {
        val tracker = QualityDegradationTracker(WallpaperQualityProfile.HIGH)
        repeat(QualityDegradationTracker.SLOW_FRAME_WINDOW_SIZE) {
            tracker.recordFrame(QualityDegradationTracker.SLOW_FRAME_THRESHOLD_MILLIS + 5f)
        }
        tracker.degradationLevel shouldBe 1

        tracker.reset()

        tracker.degradationLevel shouldBe 0
        tracker.effectiveProfile shouldBe WallpaperQualityProfile.HIGH
    }

    @Test
    fun `degradation cannot drop below battery saver even when starting there`() {
        val tracker = QualityDegradationTracker(WallpaperQualityProfile.BATTERY_SAVER)
        repeat(QualityDegradationTracker.SLOW_FRAME_WINDOW_SIZE) {
            tracker.recordFrame(QualityDegradationTracker.SLOW_FRAME_THRESHOLD_MILLIS + 5f)
        }
        tracker.degradationLevel shouldBe 0
        tracker.effectiveProfile shouldBe WallpaperQualityProfile.BATTERY_SAVER
    }
}
