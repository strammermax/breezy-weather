/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FrameTelemetryTest {
    @Test
    fun `average frame time is correct for known input`() {
        val telemetry = FrameTelemetry(windowSize = FrameTelemetry.MIN_WINDOW_SIZE)
        repeat(10) { telemetry.recordFrame(10f) }
        repeat(10) { telemetry.recordFrame(20f) }

        val snapshot = telemetry.snapshot(WallpaperQualityProfile.BALANCED)
        snapshot.averageFrameTimeMillis shouldBe 15f
        snapshot.totalFrames shouldBe 20
    }

    @Test
    fun `p95 frame time is correct for known input`() {
        val telemetry = FrameTelemetry(windowSize = 120)
        repeat(114) { telemetry.recordFrame(10f) }
        repeat(6) { telemetry.recordFrame(100f) }

        val snapshot = telemetry.snapshot(WallpaperQualityProfile.BALANCED)
        // 95th percentile index of 120 sorted samples is index 113, the last 10f value.
        snapshot.p95FrameTimeMillis shouldBe 10f
    }

    @Test
    fun `ring buffer overwrites old values after windowSize frames`() {
        val telemetry = FrameTelemetry(windowSize = FrameTelemetry.MIN_WINDOW_SIZE)
        repeat(FrameTelemetry.MIN_WINDOW_SIZE) { telemetry.recordFrame(100f) }
        repeat(FrameTelemetry.MIN_WINDOW_SIZE) { telemetry.recordFrame(10f) }

        val snapshot = telemetry.snapshot(WallpaperQualityProfile.BALANCED)
        snapshot.averageFrameTimeMillis shouldBe 10f
        snapshot.totalFrames shouldBe FrameTelemetry.MIN_WINDOW_SIZE * 2
    }

    @Test
    fun `dropped frames are counted via slow frames and explicit drops`() {
        val telemetry = FrameTelemetry(windowSize = FrameTelemetry.MIN_WINDOW_SIZE)
        telemetry.recordFrame(10f)
        telemetry.recordFrame(FrameTelemetry.DROPPED_FRAME_THRESHOLD_MILLIS + 1f)
        telemetry.recordDroppedFrame()

        val snapshot = telemetry.snapshot(WallpaperQualityProfile.BALANCED)
        snapshot.droppedFrames shouldBe 2
        snapshot.totalFrames shouldBe 3
    }

    @Test
    fun `reset clears all counters`() {
        val telemetry = FrameTelemetry(windowSize = FrameTelemetry.MIN_WINDOW_SIZE)
        telemetry.recordFrame(10f)
        telemetry.recordDroppedFrame()
        telemetry.recordDegradation()
        telemetry.recordRecovery()

        telemetry.reset()

        val snapshot = telemetry.snapshot(WallpaperQualityProfile.HIGH)
        snapshot.averageFrameTimeMillis shouldBe 0f
        snapshot.p95FrameTimeMillis shouldBe 0f
        snapshot.droppedFrames shouldBe 0
        snapshot.totalFrames shouldBe 0
        snapshot.degradationEvents shouldBe 0
        snapshot.recoveryEvents shouldBe 0
    }

    @Test
    fun `snapshot reports the active profile and degradation events`() {
        val telemetry = FrameTelemetry(windowSize = FrameTelemetry.MIN_WINDOW_SIZE)
        telemetry.recordFrame(10f)
        telemetry.recordDegradation()
        telemetry.recordDegradation()
        telemetry.recordRecovery()

        val snapshot = telemetry.snapshot(WallpaperQualityProfile.HIGH)
        snapshot.activeProfile shouldBe WallpaperQualityProfile.HIGH
        snapshot.degradationEvents shouldBe 2
        snapshot.recoveryEvents shouldBe 1
    }

    @Test
    fun `windowSize below the minimum is rejected`() {
        try {
            FrameTelemetry(windowSize = FrameTelemetry.MIN_WINDOW_SIZE - 1)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}

class WallpaperLifecycleTelemetryTest {
    @Test
    fun `lifecycle events are recorded in order with the injected clock`() {
        var now = 1_000L
        val telemetry = WallpaperLifecycleTelemetry(clockMillis = { now })

        telemetry.recordVisibilityChanged(true)
        now = 2_000L
        telemetry.recordVisibilityChanged(false)
        now = 3_000L
        telemetry.recordVisibilityChanged(true)

        val events = telemetry.events()
        events.size shouldBe 3
        events[0] shouldBe WallpaperLifecycleTelemetry.Event(true, 1_000L)
        events[1] shouldBe WallpaperLifecycleTelemetry.Event(false, 2_000L)
        events[2] shouldBe WallpaperLifecycleTelemetry.Event(true, 3_000L)
    }

    @Test
    fun `only the most recent maxEvents are kept`() {
        val telemetry = WallpaperLifecycleTelemetry(maxEvents = 2, clockMillis = { 0L })

        telemetry.recordVisibilityChanged(true)
        telemetry.recordVisibilityChanged(false)
        telemetry.recordVisibilityChanged(true)

        val events = telemetry.events()
        events.size shouldBe 2
        events[0].visible shouldBe false
        events[1].visible shouldBe true
    }

    @Test
    fun `reset clears recorded events`() {
        val telemetry = WallpaperLifecycleTelemetry(clockMillis = { 0L })
        telemetry.recordVisibilityChanged(true)

        telemetry.reset()

        telemetry.events().size shouldBe 0
    }
}
