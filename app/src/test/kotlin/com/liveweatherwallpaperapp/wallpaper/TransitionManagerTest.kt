/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class TransitionManagerTest {

    private class FakeClock(var now: Long = 0L) {
        fun read(): Long = now
    }

    @Test
    fun `progress is null before any transition is started`() {
        val clock = FakeClock()
        val manager = TransitionManager(clock = clock::read)

        manager.transitionProgress().shouldBeNull()
        manager.isActive shouldBe false
    }

    @Test
    fun `progress starts at zero at the start time`() {
        val clock = FakeClock(now = 1_000L)
        val manager = TransitionManager(clock = clock::read)

        manager.startTransition(durationMillis = 10_000L)

        manager.transitionProgress() shouldBe 0f
    }

    @Test
    fun `transition auto-completes and clears after its duration`() {
        val clock = FakeClock(now = 0L)
        val manager = TransitionManager(clock = clock::read)
        manager.startTransition(durationMillis = 10_000L)

        clock.now = 10_000L
        manager.transitionProgress().shouldBeNull()
        manager.isActive shouldBe false
    }

    @Test
    fun `progress is monotonic and eased between start and end`() {
        val clock = FakeClock(now = 0L)
        val manager = TransitionManager(clock = clock::read)
        manager.startTransition(durationMillis = 1_000L)

        clock.now = 250L
        val quarter = manager.transitionProgress().shouldNotBeNull()
        clock.now = 500L
        val half = manager.transitionProgress().shouldNotBeNull()
        clock.now = 750L
        val threeQuarter = manager.transitionProgress().shouldNotBeNull()

        quarter.shouldBeGreaterThanOrEqual(0f)
        half.shouldBeGreaterThan(quarter)
        threeQuarter.shouldBeGreaterThan(half)
        // smoothStep is symmetric around the midpoint.
        half shouldBe 0.5f
    }

    @Test
    fun `duration zero applies the target immediately without an active transition`() {
        val clock = FakeClock(now = 0L)
        val manager = TransitionManager(clock = clock::read)

        manager.startTransition(durationMillis = 0L)

        manager.isActive shouldBe false
        manager.transitionProgress().shouldBeNull()
    }

    @Test
    fun `smoothStep stays within unit range and is monotonic`() {
        var previous = smoothStep(0f)
        smoothStep(-1f) shouldBe 0f
        smoothStep(2f) shouldBe 1f
        var t = 0f
        while (t <= 1f) {
            val value = smoothStep(t)
            value.shouldBeGreaterThanOrEqual(0f)
            value.shouldBeLessThanOrEqual(1f)
            value.shouldBeGreaterThanOrEqual(previous)
            previous = value
            t += 0.05f
        }
    }

    @Test
    fun `float interpolation hits begin middle and end`() {
        lerp(10f, 20f, 0f) shouldBe 10f
        lerp(10f, 20f, 0.5f) shouldBe 15f
        lerp(10f, 20f, 1f) shouldBe 20f
    }

    @Test
    fun `color interpolation blends every channel independently`() {
        val from = 0x00112233
        val to = 0xFFAABBCC.toInt()
        val mid = lerpColor(from, to, 0.5f)

        ((mid ushr 24) and 0xFF) shouldBe 0x7F
        ((mid ushr 16) and 0xFF) shouldBe 0x5D
        ((mid ushr 8) and 0xFF) shouldBe 0x6E
        (mid and 0xFF) shouldBe 0x7F

        lerpColor(from, to, 0f) shouldBe from
        lerpColor(from, to, 1f) shouldBe to
    }

    @Test
    fun `angle interpolation takes the short route across zero`() {
        val mid = lerpAngle(350f, 10f, 0.5f)
        // Expected to pass through 0/360, not through 180.
        (abs(mid - 0f) < 0.01f || abs(mid - 360f) < 0.01f) shouldBe true

        lerpAngle(350f, 10f, 0f) shouldBe 350f
    }

    @Test
    fun `first scene state produces no transition`() {
        transitionDurationMillis(
            from = null,
            to = WallpaperWeatherFamily.RAIN,
            reason = SceneTransitionReason.WEATHER_DATA_CHANGED,
            animationsEnabled = true,
        ) shouldBe 0L
    }

    @Test
    fun `animations disabled forces duration zero`() {
        transitionDurationMillis(
            from = WallpaperWeatherFamily.CLEAR,
            to = WallpaperWeatherFamily.RAIN,
            reason = SceneTransitionReason.WEATHER_DATA_CHANGED,
            animationsEnabled = false,
        ) shouldBe 0L
    }

    @Test
    fun `rotating mode uses the short duration`() {
        transitionDurationMillis(
            from = WallpaperWeatherFamily.CLEAR,
            to = WallpaperWeatherFamily.RAIN,
            reason = SceneTransitionReason.ROTATING_TEST,
            animationsEnabled = true,
        ) shouldBe 2_000L
    }

    @Test
    fun `forced mode uses the quick confirmation duration`() {
        transitionDurationMillis(
            from = WallpaperWeatherFamily.CLEAR,
            to = WallpaperWeatherFamily.CLOUDY,
            reason = SceneTransitionReason.USER_FORCED_MODE,
            animationsEnabled = true,
        ) shouldBe 3_000L
    }

    @Test
    fun `normal weather family change uses the default duration`() {
        transitionDurationMillis(
            from = WallpaperWeatherFamily.CLEAR,
            to = WallpaperWeatherFamily.CLOUDY,
            reason = SceneTransitionReason.WEATHER_DATA_CHANGED,
            animationsEnabled = true,
        ) shouldBe 60_000L
    }

    @Test
    fun `precipitation start or stop uses the medium duration`() {
        transitionDurationMillis(
            from = WallpaperWeatherFamily.CLOUDY,
            to = WallpaperWeatherFamily.RAIN,
            reason = SceneTransitionReason.WEATHER_DATA_CHANGED,
            animationsEnabled = true,
        ) shouldBe 45_000L
    }

    @Test
    fun `thunderstorm uses the bounded faster duration`() {
        transitionDurationMillis(
            from = WallpaperWeatherFamily.RAIN,
            to = WallpaperWeatherFamily.THUNDERSTORM,
            reason = SceneTransitionReason.WEATHER_DATA_CHANGED,
            animationsEnabled = true,
        ) shouldBe 30_000L
    }

    @Test
    fun `identical target does not start a transition`() {
        transitionDurationMillis(
            from = WallpaperWeatherFamily.RAIN,
            to = WallpaperWeatherFamily.RAIN,
            reason = SceneTransitionReason.WEATHER_DATA_CHANGED,
            animationsEnabled = true,
        ) shouldBe 0L
    }
}