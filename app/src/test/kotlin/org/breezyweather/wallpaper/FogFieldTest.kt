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
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FogFieldTest {
    @Test
    fun `band count matches the quality profile`() {
        val params = FogFieldFactory.fogFieldParams(
            fogIntensity = 0.85f,
            hazeIntensity = 0f,
            windFactor = 1f,
            windDirectionDegrees = null,
        )
        params.bands.size shouldBe 4
    }

    @Test
    fun `lower bands have higher alpha than higher bands`() {
        val params = FogFieldFactory.fogFieldParams(
            fogIntensity = 0.85f,
            hazeIntensity = 0f,
            windFactor = 1f,
            windDirectionDegrees = null,
        )
        val horizonBand = params.bands.first()
        val topBand = params.bands.last()
        horizonBand.baseAlpha shouldBeGreaterThan topBand.baseAlpha
        horizonBand.verticalCenter shouldBeGreaterThan topBand.verticalCenter
    }

    @Test
    fun `fog uses cooler color than haze`() {
        val fog = FogFieldFactory.fogColor(isHaze = false, daylight = 1f)
        val haze = FogFieldFactory.fogColor(isHaze = true, daylight = 1f)
        // Cooler = relatively less red, more blue.
        fog[0] shouldBeLessThan haze[0]
        fog[2] shouldBeGreaterThan haze[2]
    }

    @Test
    fun `heavy fog foreground uses neutral grey values`() {
        val color = FogFieldFactory.fogColor(
            isHaze = false,
            daylight = 1f,
            neutralAmount = 1f,
        )

        color[0] shouldBe color[1]
        color[1] shouldBe color[2]
    }

    @Test
    fun `haze uses lower maximum alpha than fog`() {
        val fog = FogFieldFactory.fogFieldParams(
            fogIntensity = 1f,
            hazeIntensity = 0f,
            windFactor = 1f,
            windDirectionDegrees = null,
        )
        val haze = FogFieldFactory.fogFieldParams(
            fogIntensity = 0f,
            hazeIntensity = 1f,
            windFactor = 1f,
            windDirectionDegrees = null,
        )
        val fogMax = fog.bands.maxOf { it.baseAlpha }
        val hazeMax = haze.bands.maxOf { it.baseAlpha }
        hazeMax shouldBeLessThan fogMax
    }

    @Test
    fun `normal and heavy fog enable progressively denser foreground gradients`() {
        val light = FogFieldFactory.fogFieldParams(0.46f, 0f, 1f, null)
        val normal = FogFieldFactory.fogFieldParams(0.69f, 0f, 1f, null)
        val heavy = FogFieldFactory.fogFieldParams(1f, 0f, 1f, null)
        val haze = FogFieldFactory.fogFieldParams(0f, 1f, 1f, null)

        light.foregroundGradientStrength shouldBe 0f
        normal.foregroundGradientStrength shouldBe 0.72f
        heavy.foregroundGradientStrength shouldBe 1f
        haze.foregroundGradientStrength shouldBe 0.60f
    }

    @Test
    fun `wind speed scales drift speed`() {
        val calm = FogFieldFactory.fogFieldParams(0.85f, 0f, 1f, null)
        val windy = FogFieldFactory.fogFieldParams(0.85f, 0f, 4.2f, null)
        val calmSpeed = calm.bands.sumOf { it.speedFactor.toDouble() }.toFloat()
        val windySpeed = windy.bands.sumOf { it.speedFactor.toDouble() }.toFloat()
        windySpeed shouldBeGreaterThan calmSpeed
    }

    @Test
    fun `wind direction is normalized to 0 until 360`() {
        val params = FogFieldFactory.fogFieldParams(0.85f, 0f, 1f, 725f)
        params.directionDegrees shouldBeGreaterThanOrEqual 0f
        params.directionDegrees shouldBeLessThan 360f
        params.directionDegrees shouldBe 5f
    }

    @Test
    fun `missing wind direction yields a valid default direction and no NaN`() {
        val params = FogFieldFactory.fogFieldParams(0.85f, 0f, 1f, null)
        params.directionDegrees.isNaN() shouldBe false
        params.directionDegrees shouldBeGreaterThanOrEqual 0f
        params.directionDegrees shouldBeLessThan 360f
    }

    @Test
    fun `intensity 0 yields no visible bands`() {
        val params = FogFieldFactory.fogFieldParams(
            fogIntensity = 0f,
            hazeIntensity = 0f,
            windFactor = 1f,
            windDirectionDegrees = null,
        )
        params.bands.forEach { it.baseAlpha shouldBe 0f }
    }

    @Test
    fun `combined alpha stays under the readability cap`() {
        val fog = FogFieldFactory.fogFieldParams(1f, 0f, 1f, null)
        val haze = FogFieldFactory.fogFieldParams(0f, 1f, 1f, null)
        fog.bands.forEach { it.baseAlpha shouldBeLessThanOrEqual 0.68f }
        haze.bands.forEach { it.baseAlpha shouldBeLessThanOrEqual 0.56f }
    }

    @Test
    fun `identical inputs yield equal FogFieldParams`() {
        val a = FogFieldFactory.fogFieldParams(0.85f, 0f, 1.4f, 220f)
        val b = FogFieldFactory.fogFieldParams(0.85f, 0f, 1.4f, 220f)
        a shouldBe b
    }

    @Test
    fun `NaN or infinite intensity or wind factor yield no invalid parameters`() {
        val params = FogFieldFactory.fogFieldParams(
            fogIntensity = Float.NaN,
            hazeIntensity = Float.POSITIVE_INFINITY,
            windFactor = Float.NaN,
            windDirectionDegrees = Float.NaN,
        )
        params.bands.forEach { band ->
            band.baseAlpha.isNaN() shouldBe false
            band.speedFactor.isNaN() shouldBe false
            band.baseAlpha shouldBeGreaterThanOrEqual 0f
            band.baseAlpha shouldBeLessThanOrEqual 1f
        }
        params.directionDegrees.isNaN() shouldBe false
    }
}
