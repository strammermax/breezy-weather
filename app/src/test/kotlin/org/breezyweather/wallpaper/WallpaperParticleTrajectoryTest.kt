/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class WallpaperParticleTrajectoryTest {
    @Test
    fun `hail falls faster than snow at the same depth`() {
        for (depth in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val snow = WallpaperParticleTrajectory.fallSpeed(WallpaperParticleKind.SNOW, depth)
            val hail = WallpaperParticleTrajectory.fallSpeed(WallpaperParticleKind.HAIL, depth)
            hail shouldBeGreaterThan snow
        }
    }

    @Test
    fun `hail is more compact than snow at the same depth`() {
        for (depth in listOf(0f, 0.5f, 1f)) {
            val snow = WallpaperParticleTrajectory.radius(WallpaperParticleKind.SNOW, depth)
            val hail = WallpaperParticleTrajectory.radius(WallpaperParticleKind.HAIL, depth)
            hail shouldBeLessThan snow
        }
    }

    @Test
    fun `fall speed increases with depth for both kinds`() {
        for (kind in WallpaperParticleKind.entries) {
            WallpaperParticleTrajectory.fallSpeed(kind, 0f) shouldBeLessThan
                WallpaperParticleTrajectory.fallSpeed(kind, 1f)
        }
    }

    @Test
    fun `wind component stays within plus and minus wind factor`() {
        val windFactor = 2.5f
        for (direction in 0..359 step 7) {
            val component = WallpaperParticleTrajectory.horizontalWindComponent(direction.toFloat(), windFactor)
            abs(component) shouldBeLessThanOrEqual windFactor + 1e-4f
        }
    }

    @Test
    fun `wind speed scales the horizontal component`() {
        val calm = WallpaperParticleTrajectory.horizontalWindComponent(0f, 1f)
        val windy = WallpaperParticleTrajectory.horizontalWindComponent(0f, 4f)
        abs(windy) shouldBeGreaterThan abs(calm)
    }

    @Test
    fun `opposite wind directions flip the drift sign`() {
        val east = WallpaperParticleTrajectory.horizontalWindComponent(0f, 1f)
        val west = WallpaperParticleTrajectory.horizontalWindComponent(180f, 1f)
        (east * west) shouldBeLessThan 0f
    }

    @Test
    fun `drift speed direction follows the wind component`() {
        val positive = WallpaperParticleTrajectory.driftSpeed(WallpaperParticleKind.SNOW, 0.5f, 1.2f)
        val negative = WallpaperParticleTrajectory.driftSpeed(WallpaperParticleKind.SNOW, 0.5f, -1.2f)
        positive shouldBeGreaterThan 0f
        negative shouldBeLessThan 0f
    }

    @Test
    fun `zero wind component yields no drift`() {
        WallpaperParticleTrajectory.driftSpeed(WallpaperParticleKind.SNOW, 0.7f, 0f) shouldBe 0f
        WallpaperParticleTrajectory.driftSpeed(WallpaperParticleKind.HAIL, 0.7f, 0f) shouldBe 0f
    }

    @Test
    fun `hail does not sway but snow does`() {
        WallpaperParticleTrajectory.swayAmplitude(WallpaperParticleKind.HAIL, 0.5f) shouldBe 0f
        WallpaperParticleTrajectory.swayAmplitude(WallpaperParticleKind.SNOW, 0.5f) shouldBeGreaterThan 0f
    }

    @Test
    fun `non finite wind direction yields a neutral component and no NaN`() {
        val nan = WallpaperParticleTrajectory.horizontalWindComponent(Float.NaN, 2f)
        nan.isNaN() shouldBe false
        nan shouldBe 0f
    }

    @Test
    fun `non finite wind factor does not produce NaN`() {
        val component = WallpaperParticleTrajectory.horizontalWindComponent(45f, Float.NaN)
        component.isNaN() shouldBe false
        component shouldBe 0f
    }

    @Test
    fun `effective layer count stays within the reserved buffer bounds`() {
        for (layers in listOf(0f, 8f, 10f, 15f, 20f, 30f, Float.NaN)) {
            val count = WallpaperParticleTrajectory.activeParticleCount(
                WallpaperParticleKind.SNOW,
                layers,
                minActive = 140,
                maxActive = 240,
            )
            count shouldBeGreaterThanOrEqual 140
            count shouldBeLessThanOrEqual 240
        }
    }

    @Test
    fun `higher quality draws more active particles`() {
        val low = WallpaperParticleTrajectory.activeParticleCount(
            WallpaperParticleKind.SNOW, 10f, 140, 240,
        )
        val high = WallpaperParticleTrajectory.activeParticleCount(
            WallpaperParticleKind.SNOW, 20f, 140, 240,
        )
        high shouldBeGreaterThan low
    }

    @Test
    fun `snow is denser than hail at the same quality`() {
        val snow = WallpaperParticleTrajectory.activeParticleCount(
            WallpaperParticleKind.SNOW, 20f, 100, 240,
        )
        val hail = WallpaperParticleTrajectory.activeParticleCount(
            WallpaperParticleKind.HAIL, 20f, 100, 240,
        )
        snow shouldBeGreaterThan hail
    }

    @Test
    fun `depth bands span the full 0 to 1 range and sit within 10 to 20 layers`() {
        WallpaperParticleTrajectory.depthForBand(0) shouldBe 0f
        WallpaperParticleTrajectory.depthForBand(WallpaperParticleTrajectory.DEPTH_BANDS - 1) shouldBe 1f
        WallpaperParticleTrajectory.DEPTH_BANDS shouldBeGreaterThanOrEqual 10
        WallpaperParticleTrajectory.DEPTH_BANDS shouldBeLessThanOrEqual 20
    }

    @Test
    fun `depth band index is clamped to valid range`() {
        WallpaperParticleTrajectory.depthForBand(-5) shouldBe 0f
        WallpaperParticleTrajectory.depthForBand(9999) shouldBe 1f
    }

    @Test
    fun `alpha increases with depth and stays within 0 to 1`() {
        for (kind in WallpaperParticleKind.entries) {
            val near = WallpaperParticleTrajectory.alpha(kind, 1f)
            val far = WallpaperParticleTrajectory.alpha(kind, 0f)
            near shouldBeGreaterThan far
            far shouldBeGreaterThanOrEqual 0f
            near shouldBeLessThanOrEqual 1f
        }
    }
}