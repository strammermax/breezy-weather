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

class CloudFieldTest {
    private fun totalAlpha(params: CloudFieldParams) = params.layers.sumOf { it.alpha.toDouble() }.toFloat()

    private fun totalSpeed(params: CloudFieldParams) = params.layers.sumOf { it.speedFactor.toDouble() }.toFloat()

    private fun visibleLayers(params: CloudFieldParams) = params.layers.count { it.alpha > 0f }

    @Test
    fun `Clear has no or fully transparent cloud layers`() {
        val params = CloudFieldFactory.cloudFieldParams(
            family = WallpaperWeatherFamily.CLEAR,
            cloudDensity = 0f,
            cloudDarkness = 0f,
            windFactor = 1f,
            windDirectionDegrees = null,
        )
        params.layers.forEach { it.alpha shouldBe 0f }
    }

    @Test
    fun `Partly cloudy has less coverage than Cloudy`() {
        val partlyCloudy = CloudFieldFactory.cloudFieldParams(
            family = WallpaperWeatherFamily.PARTLY_CLOUDY,
            cloudDensity = 0.35f,
            cloudDarkness = 0.05f,
            windFactor = 1f,
            windDirectionDegrees = null,
        )
        val cloudy = CloudFieldFactory.cloudFieldParams(
            family = WallpaperWeatherFamily.CLOUDY,
            cloudDensity = 0.85f,
            cloudDarkness = 0.25f,
            windFactor = 1f,
            windDirectionDegrees = null,
        )
        totalAlpha(partlyCloudy) shouldBeLessThan totalAlpha(cloudy)
    }

    @Test
    fun `Cloudy Rain and Thunderstorm have five visible layers`() {
        val cloudy = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.CLOUDY, 0.85f, 0.25f, 1f, null,
        )
        val rain = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.RAIN, 0.95f, 0.55f, 1f, null,
        )
        val thunderstorm = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.THUNDERSTORM, 1f, 0.85f, 3f, null,
        )
        visibleLayers(cloudy) shouldBe 5
        visibleLayers(rain) shouldBe 5
        visibleLayers(thunderstorm) shouldBe 5
    }

    @Test
    fun `Rain Thunder and Thunderstorm have an opaque ceiling layer`() {
        listOf(
            WallpaperWeatherFamily.RAIN to 0.95f,
            WallpaperWeatherFamily.THUNDER to 0.95f,
            WallpaperWeatherFamily.THUNDERSTORM to 1f,
        ).forEach { (family, density) ->
            val params = CloudFieldFactory.cloudFieldParams(
                family, density, 0.7f, 1f, null,
            )
            params.layers.maxOf { it.alpha } shouldBe 1f
        }
    }

    @Test
    fun `Thunderstorm is darker than Cloudy`() {
        val cloudy = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.CLOUDY, 0.85f, 0.25f, 1f, null,
        )
        val thunderstorm = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.THUNDERSTORM, 1f, 0.85f, 3f, null,
        )
        val cloudyDarkness = cloudy.layers.sumOf { it.darkness.toDouble() }.toFloat()
        val thunderstormDarkness = thunderstorm.layers.sumOf { it.darkness.toDouble() }.toFloat()
        thunderstormDarkness shouldBeGreaterThan cloudyDarkness
    }

    @Test
    fun `Thunderstorm has higher total speed than Partly cloudy`() {
        val partlyCloudy = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.PARTLY_CLOUDY, 0.35f, 0.05f, 1f, null,
        )
        val thunderstorm = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.THUNDERSTORM, 1f, 0.85f, 3f, null,
        )
        totalSpeed(thunderstorm) shouldBeGreaterThan totalSpeed(partlyCloudy)
    }

    @Test
    fun `wind increases speed factor for the same coverage`() {
        val calm = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.CLOUDY, 0.85f, 0.25f, 1f, null,
        )
        val windy = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.CLOUDY, 0.85f, 0.25f, 4.2f, null,
        )
        totalSpeed(windy) shouldBeGreaterThan totalSpeed(calm)
    }

    @Test
    fun `higher cloudDensity increases total coverage monotonically`() {
        val low = CloudFieldFactory.cloudFieldParams(WallpaperWeatherFamily.CLOUDY, 0.2f, 0.25f, 1f, null)
        val mid = CloudFieldFactory.cloudFieldParams(WallpaperWeatherFamily.CLOUDY, 0.5f, 0.25f, 1f, null)
        val high = CloudFieldFactory.cloudFieldParams(WallpaperWeatherFamily.CLOUDY, 0.9f, 0.25f, 1f, null)
        totalAlpha(low) shouldBeLessThan totalAlpha(mid)
        totalAlpha(mid) shouldBeLessThan totalAlpha(high)
    }

    @Test
    fun `higher cloudDarkness increases darkness monotonically`() {
        val low = CloudFieldFactory.cloudFieldParams(WallpaperWeatherFamily.CLOUDY, 0.85f, 0.1f, 1f, null)
        val high = CloudFieldFactory.cloudFieldParams(WallpaperWeatherFamily.CLOUDY, 0.85f, 0.9f, 1f, null)
        val lowDarkness = low.layers.sumOf { it.darkness.toDouble() }.toFloat()
        val highDarkness = high.layers.sumOf { it.darkness.toDouble() }.toFloat()
        lowDarkness shouldBeLessThan highDarkness
    }

    @Test
    fun `all layer alpha and darkness values stay within 0f to 1f`() {
        val params = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.THUNDERSTORM, 1f, 1f, 10f, 720f,
        )
        params.layers.forEach { layer ->
            layer.alpha shouldBeGreaterThanOrEqual 0f
            layer.alpha shouldBeLessThanOrEqual 1f
            layer.darkness shouldBeGreaterThanOrEqual 0f
            layer.darkness shouldBeLessThanOrEqual 1f
        }
    }

    @Test
    fun `back layer is slower than front layer`() {
        val params = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.CLOUDY, 0.85f, 0.25f, 2f, null,
        )
        val back = params.layers.first()
        val front = params.layers.last()
        back.speedFactor shouldBeLessThan front.speedFactor
    }

    @Test
    fun `five layers increase scale speed depth and vertical placement from back to front`() {
        val layers = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.CLOUDY, 0.85f, 0.25f, 1f, null,
        ).layers

        layers.size shouldBe 5
        layers.map { it.depth } shouldBe listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        layers.zipWithNext().forEach { (back, front) ->
            back.scale shouldBeLessThan front.scale
            back.speedFactor shouldBeLessThan front.speedFactor
            back.verticalOffset shouldBeLessThan front.verticalOffset
        }
    }

    @Test
    fun `missing wind direction yields a valid default direction and no NaN`() {
        val params = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.CLOUDY, 0.85f, 0.25f, 1f, null,
        )
        params.directionDegrees.isNaN() shouldBe false
        params.directionDegrees shouldBeGreaterThanOrEqual 0f
        params.directionDegrees shouldBeLessThan 360f
    }

    @Test
    fun `wind direction is normalized to 0 until 360`() {
        val params = CloudFieldFactory.cloudFieldParams(
            WallpaperWeatherFamily.CLOUDY, 0.85f, 0.25f, 1f, 725f,
        )
        params.directionDegrees shouldBeGreaterThanOrEqual 0f
        params.directionDegrees shouldBeLessThan 360f
        params.directionDegrees shouldBe 5f
    }

    @Test
    fun `identical inputs yield equal CloudFieldParams`() {
        val a = CloudFieldFactory.cloudFieldParams(WallpaperWeatherFamily.RAIN, 0.95f, 0.55f, 1.4f, 220f)
        val b = CloudFieldFactory.cloudFieldParams(WallpaperWeatherFamily.RAIN, 0.95f, 0.55f, 1.4f, 220f)
        a shouldBe b
    }

    @Test
    fun `NaN or infinite density darkness or wind factor yield no invalid parameters`() {
        val params = CloudFieldFactory.cloudFieldParams(
            family = WallpaperWeatherFamily.CLOUDY,
            cloudDensity = Float.NaN,
            cloudDarkness = Float.POSITIVE_INFINITY,
            windFactor = Float.NaN,
            windDirectionDegrees = Float.NaN,
        )
        params.layers.forEach { layer ->
            layer.alpha.isNaN() shouldBe false
            layer.darkness.isNaN() shouldBe false
            layer.speedFactor.isNaN() shouldBe false
            layer.alpha shouldBeGreaterThanOrEqual 0f
            layer.alpha shouldBeLessThanOrEqual 1f
            layer.darkness shouldBeGreaterThanOrEqual 0f
            layer.darkness shouldBeLessThanOrEqual 1f
        }
        params.directionDegrees.isNaN() shouldBe false
    }

    @Test
    fun `Clear to Cloudy transition changes coverage via alpha not a hard layer-count jump`() {
        val clear = CloudFieldFactory.cloudFieldParams(WallpaperWeatherFamily.CLEAR, 0f, 0f, 1f, null)
        val cloudy = CloudFieldFactory.cloudFieldParams(WallpaperWeatherFamily.CLOUDY, 0.85f, 0.25f, 1f, null)
        clear.layers.size shouldBe cloudy.layers.size
        totalAlpha(clear) shouldBeLessThan totalAlpha(cloudy)
    }
}
