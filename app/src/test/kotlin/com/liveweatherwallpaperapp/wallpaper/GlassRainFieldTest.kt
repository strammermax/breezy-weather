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
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GlassRainFieldTest {
    @Test
    fun `profiles increase drop count, trail length and highlight strength from low to high`() {
        (GlassRainFieldFactory.LOW.maxDrops <= GlassRainFieldFactory.BALANCED.maxDrops) shouldBe true
        (GlassRainFieldFactory.BALANCED.maxDrops <= GlassRainFieldFactory.HIGH.maxDrops) shouldBe true

        GlassRainFieldFactory.LOW.trailLength shouldBeLessThanOrEqual GlassRainFieldFactory.BALANCED.trailLength
        GlassRainFieldFactory.BALANCED.trailLength shouldBeLessThanOrEqual GlassRainFieldFactory.HIGH.trailLength

        GlassRainFieldFactory.LOW.highlightStrength shouldBeLessThanOrEqual
            GlassRainFieldFactory.BALANCED.highlightStrength
        GlassRainFieldFactory.BALANCED.highlightStrength shouldBeLessThanOrEqual
            GlassRainFieldFactory.HIGH.highlightStrength

        GlassRainFieldFactory.LOW.refractionStrength shouldBeLessThanOrEqual
            GlassRainFieldFactory.BALANCED.refractionStrength
        GlassRainFieldFactory.BALANCED.refractionStrength shouldBeLessThanOrEqual
            GlassRainFieldFactory.HIGH.refractionStrength
    }

    @Test
    fun `profileFor maps the adaptive precipitation quality range to low, balanced and high`() {
        GlassRainFieldFactory.profileFor(10f) shouldBe GlassRainFieldFactory.LOW
        GlassRainFieldFactory.profileFor(12.9f) shouldBe GlassRainFieldFactory.LOW
        GlassRainFieldFactory.profileFor(13f) shouldBe GlassRainFieldFactory.BALANCED
        GlassRainFieldFactory.profileFor(16.9f) shouldBe GlassRainFieldFactory.BALANCED
        GlassRainFieldFactory.profileFor(17f) shouldBe GlassRainFieldFactory.HIGH
        GlassRainFieldFactory.profileFor(20f) shouldBe GlassRainFieldFactory.HIGH
    }

    @Test
    fun `profileFor falls back to balanced for non-finite input`() {
        GlassRainFieldFactory.profileFor(Float.NaN) shouldBe GlassRainFieldFactory.BALANCED
        GlassRainFieldFactory.profileFor(Float.POSITIVE_INFINITY) shouldBe GlassRainFieldFactory.BALANCED
    }

    @Test
    fun `static ratio decreases as glass rain intensity increases`() {
        val light = GlassRainFieldFactory.staticRatio(0.55f) // sleet
        val heavy = GlassRainFieldFactory.staticRatio(0.90f) // thunderstorm
        light shouldBeGreaterThan heavy
        light shouldBeLessThanOrEqual 0.70f
        heavy shouldBeGreaterThanOrEqual 0.40f
    }

    @Test
    fun `static ratio stays within bounds for non-finite or out-of-range intensity`() {
        GlassRainFieldFactory.staticRatio(Float.NaN) shouldBe 0.70f
        GlassRainFieldFactory.staticRatio(-5f) shouldBe 0.70f
        GlassRainFieldFactory.staticRatio(5f) shouldBe 0.40f
    }
}
