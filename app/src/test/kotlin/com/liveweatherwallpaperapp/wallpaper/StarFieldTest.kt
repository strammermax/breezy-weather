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

class StarFieldTest {
    @Test
    fun `star count matches the quality profile`() {
        val params = StarFieldFactory.starFieldParams(locationSeed = 0L)
        params.stars.size shouldBe 90
    }

    @Test
    fun `star fields stay within bounds`() {
        val params = StarFieldFactory.starFieldParams(locationSeed = 42L)
        params.stars.forEach { star ->
            star.x shouldBeGreaterThanOrEqual 0f
            star.x shouldBeLessThanOrEqual 1f
            star.y shouldBeGreaterThanOrEqual 0f
            star.y shouldBeLessThanOrEqual 0.62f
            star.brightness shouldBeGreaterThanOrEqual 0f
            star.brightness shouldBeLessThanOrEqual 1f
        }
    }

    @Test
    fun `identical location seeds yield equal star fields`() {
        val a = StarFieldFactory.starFieldParams(locationSeed = 1234L)
        val b = StarFieldFactory.starFieldParams(locationSeed = 1234L)
        a shouldBe b
    }

    @Test
    fun `different location seeds yield different star fields`() {
        val a = StarFieldFactory.starFieldParams(locationSeed = 1L)
        val b = StarFieldFactory.starFieldParams(locationSeed = 2L)
        a.stars shouldBe a.stars
        (a.stars == b.stars) shouldBe false
    }

    @Test
    fun `stars are fully visible at night and hidden during the day`() {
        StarFieldFactory.starVisibility(0f) shouldBe 1f
        StarFieldFactory.starVisibility(1f) shouldBe 0f
    }

    @Test
    fun `star visibility fades out as daylight increases`() {
        val night = StarFieldFactory.starVisibility(0.1f)
        val dusk = StarFieldFactory.starVisibility(0.2f)
        night shouldBeGreaterThan dusk
    }

    @Test
    fun `NaN or infinite daylight yields no invalid visibility`() {
        StarFieldFactory.starVisibility(Float.NaN).isNaN() shouldBe false
        StarFieldFactory.starVisibility(Float.POSITIVE_INFINITY) shouldBe 1f
    }

    @Test
    fun `location seed is finite for non-finite coordinates`() {
        StarFieldFactory.locationSeed(Double.NaN, Double.NaN) shouldBe 0L
        StarFieldFactory.locationSeed(Double.POSITIVE_INFINITY, 4.0) shouldBe 0L
    }
}
