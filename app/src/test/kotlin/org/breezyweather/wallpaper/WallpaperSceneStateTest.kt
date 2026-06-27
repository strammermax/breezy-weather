/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.junit.jupiter.api.Test

class WallpaperSceneStateTest {
    @Test
    fun `all supported weather kinds map to an explicit family`() {
        val mappings = listOf(
            WeatherView.WEATHER_KIND_CLEAR to WallpaperWeatherFamily.CLEAR,
            WeatherView.WEATHER_KIND_CLOUD to WallpaperWeatherFamily.PARTLY_CLOUDY,
            WeatherView.WEATHER_KIND_CLOUDY to WallpaperWeatherFamily.CLOUDY,
            WeatherView.WEATHER_KIND_RAINY to WallpaperWeatherFamily.RAIN,
            WeatherView.WEATHER_KIND_SNOW to WallpaperWeatherFamily.SNOW,
            WeatherView.WEATHER_KIND_SLEET to WallpaperWeatherFamily.SLEET,
            WeatherView.WEATHER_KIND_HAIL to WallpaperWeatherFamily.HAIL,
            WeatherView.WEATHER_KIND_FOG to WallpaperWeatherFamily.FOG,
            WeatherView.WEATHER_KIND_HAZE to WallpaperWeatherFamily.HAZE,
            WeatherView.WEATHER_KIND_THUNDER to WallpaperWeatherFamily.THUNDER,
            WeatherView.WEATHER_KIND_THUNDERSTORM to WallpaperWeatherFamily.THUNDERSTORM,
            WeatherView.WEATHER_KIND_WIND to WallpaperWeatherFamily.WIND,
        )

        mappings.map { WallpaperSceneStateFactory.weatherFamily(it.first) to it.second }
            .shouldContainExactly(mappings.map { it.second to it.second })
    }

    @Test
    fun `unknown weather kind falls back to clear`() {
        val state = WallpaperSceneStateFactory.create(weatherKind = Int.MAX_VALUE, daylight = 1f)

        state.weatherFamily shouldBe WallpaperWeatherFamily.CLEAR
        state.weatherKind shouldBe WeatherView.WEATHER_KIND_CLEAR
    }

    @Test
    fun `normalized values stay within unit range`() {
        allStates().forEach { state ->
            listOf(
                state.daylight,
                state.cloudDensity,
                state.cloudDarkness,
                state.precipitationIntensity,
                state.fogIntensity,
                state.hazeIntensity,
                state.thunderIntensity,
                state.glassRainIntensity,
                state.photoDimming,
            ).forEach {
                it.shouldBeGreaterThanOrEqual(0f)
                it.shouldBeLessThanOrEqual(1f)
            }
        }
    }

    @Test
    fun `wet and frozen precipitation always includes clouds`() {
        listOf(
            WeatherView.WEATHER_KIND_RAINY,
            WeatherView.WEATHER_KIND_SNOW,
            WeatherView.WEATHER_KIND_SLEET,
            WeatherView.WEATHER_KIND_HAIL,
        ).forEach {
            WallpaperSceneStateFactory.create(it, daylight = 1f).cloudDensity.shouldBeGreaterThan(0f)
        }
    }

    @Test
    fun `glass rain is restricted to wet glass families`() {
        val enabled = allStates().filter { it.glassRainIntensity > 0f }.map { it.weatherFamily }

        enabled shouldContainExactly listOf(
            WallpaperWeatherFamily.RAIN,
            WallpaperWeatherFamily.SLEET,
            WallpaperWeatherFamily.THUNDERSTORM,
        )
    }

    @Test
    fun `plain thunder and measured rain on dry families do not enable glass rain`() {
        WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_THUNDER,
            daylight = 1f,
        ).glassRainIntensity shouldBe 0f

        WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLEAR,
            daylight = 1f,
            precipitationMillimetersPerHour = 4f,
        ).glassRainIntensity shouldBe 0f
    }

    @Test
    fun `forecast rain amount selects light medium and heavy glass profiles`() {
        fun intensity(mmPerHour: Float) = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_RAINY,
            daylight = 1f,
            precipitationMillimetersPerHour = mmPerHour,
        ).glassRainIntensity

        intensity(1f) shouldBe 0.3f
        intensity(7f) shouldBe 0.7f
        intensity(18f) shouldBe 1f
    }

    @Test
    fun `thunderstorm combines precipitation thunder and dark clouds`() {
        val state = WallpaperSceneStateFactory.create(WeatherView.WEATHER_KIND_THUNDERSTORM, 1f)

        state.precipitationIntensity shouldBe 1f
        state.thunderIntensity shouldBe 1f
        state.cloudDensity shouldBe 1f
        state.cloudDarkness.shouldBeGreaterThan(0.5f)
    }

    @Test
    fun `measured cloud cover refines the coarse weather family`() {
        val state = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLEAR,
            daylight = 1f,
            cloudCoverPercent = 75f,
        )

        state.condition.sky shouldBe WallpaperSkyCondition.MOSTLY_CLOUDY
        state.cloudDensity shouldBe 0.70f
    }

    @Test
    fun `Fair keeps sparse cloud coverage`() {
        val fair = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLEAR,
            daylight = 1f,
            cloudCoverPercent = 20f,
        )
        val partlyCloudy = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLOUD,
            daylight = 1f,
            cloudCoverPercent = 45f,
        )

        fair.condition.sky shouldBe WallpaperSkyCondition.FAIR
        fair.cloudDensity shouldBe 0.18f
        fair.cloudDensity shouldBeLessThan partlyCloudy.cloudDensity
    }

    @Test
    fun `Light cloudy defaults to Fair when measured cloud cover is unavailable`() {
        val state = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLOUD,
            daylight = 1f,
        )

        state.weatherFamily shouldBe WallpaperWeatherFamily.PARTLY_CLOUDY
        state.condition.sky shouldBe WallpaperSkyCondition.FAIR
        state.cloudDensity shouldBe 0.18f
    }

    @Test
    fun `rain amount selects drizzle moderate rain and heavy rain`() {
        fun state(mm: Float) = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_RAINY,
            daylight = 1f,
            precipitationMillimetersPerHour = mm,
        )

        state(0.1f).condition.precipitation shouldBe WallpaperPrecipitationCondition.DRIZZLE
        state(0.1f).condition.precipitationIntensity shouldBe WallpaperEffectIntensity.LIGHT
        state(7f).condition.precipitation shouldBe WallpaperPrecipitationCondition.RAIN
        state(7f).condition.precipitationIntensity shouldBe WallpaperEffectIntensity.MODERATE
        state(18f).condition.precipitationIntensity shouldBe WallpaperEffectIntensity.HEAVY
    }

    @Test
    fun `low visibility composes dense fog with rain`() {
        val state = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_RAINY,
            daylight = 1f,
            visibilityMeters = 800f,
        )

        state.condition.precipitation shouldBe WallpaperPrecipitationCondition.RAIN
        state.condition.visibility shouldBe WallpaperVisibilityCondition.DENSE_FOG
        state.fogIntensity.shouldBeGreaterThan(0f)
        state.precipitationIntensity.shouldBeGreaterThan(0f)
    }

    @Test
    fun `precipitation keeps an overcast sky despite a low measured cloud value`() {
        val state = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_RAINY,
            daylight = 1f,
            cloudCoverPercent = 5f,
        )

        state.condition.sky shouldBe WallpaperSkyCondition.OVERCAST
        state.cloudDensity.shouldBeGreaterThanOrEqual(0.95f)
    }

    @Test
    fun `wind factor follows thresholds and gusts`() {
        WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLOUD,
            daylight = 1f,
            windSpeedMetersPerSecond = 7.9f,
        ).windFactor shouldBe 1f

        val medium = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLOUD,
            daylight = 1f,
            windSpeedMetersPerSecond = 11f,
        ).windFactor
        medium.shouldBeGreaterThan(1f)
        medium.shouldBeLessThanOrEqual(2.8f)

        WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLOUD,
            daylight = 1f,
            windGustMetersPerSecond = 14f,
        ).windFactor shouldBe 3.6f
    }

    @Test
    fun `storm and wind families enforce minimum animation speeds`() {
        WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_THUNDERSTORM,
            daylight = 1f,
        ).windFactor shouldBe 2.8f

        WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_WIND,
            daylight = 1f,
        ).windFactor shouldBe 4.2f
    }

    @Test
    fun `daylight and photo tint are complementary and safe`() {
        val night = WallpaperSceneStateFactory.create(WeatherView.WEATHER_KIND_CLEAR, -2f)
        val day = WallpaperSceneStateFactory.create(WeatherView.WEATHER_KIND_CLEAR, 2f)
        val invalid = WallpaperSceneStateFactory.create(WeatherView.WEATHER_KIND_CLEAR, Float.NaN)

        night.daylight shouldBe 0f
        night.photoDimming shouldBe 1f
        day.daylight shouldBe 1f
        day.photoDimming shouldBe 0f
        invalid.daylight shouldBe 1f
        invalid.photoDimming shouldBe 0f
    }

    @Test
    fun `wind inputs are sanitized and direction is normalized`() {
        val state = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLOUD,
            daylight = 1f,
            windSpeedMetersPerSecond = Float.NaN,
            windGustMetersPerSecond = Float.POSITIVE_INFINITY,
            windDirectionDegrees = -10f,
        )

        state.windSpeedMetersPerSecond shouldBe 0f
        state.windGustMetersPerSecond shouldBe 0f
        state.windDirectionDegrees shouldBe 350f

        WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLOUD,
            daylight = 1f,
            windDirectionDegrees = -1f,
        ).windDirectionDegrees shouldBe null
    }

    @Test
    fun `astro timestamps are preserved and identical inputs produce equal states`() {
        val first = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLOUDY,
            daylight = 0.4f,
            sunriseMillis = 100L,
            sunsetMillis = 200L,
            moonriseMillis = 300L,
            moonsetMillis = 400L,
        )
        val second = WallpaperSceneStateFactory.create(
            WeatherView.WEATHER_KIND_CLOUDY,
            daylight = 0.4f,
            sunriseMillis = 100L,
            sunsetMillis = 200L,
            moonriseMillis = 300L,
            moonsetMillis = 400L,
        )

        first shouldBe second
        first.sunriseMillis shouldBe 100L
        first.sunsetMillis shouldBe 200L
        first.moonriseMillis shouldBe 300L
        first.moonsetMillis shouldBe 400L
    }

    private fun allStates(): List<WallpaperSceneState> = listOf(
        WeatherView.WEATHER_KIND_CLEAR,
        WeatherView.WEATHER_KIND_CLOUD,
        WeatherView.WEATHER_KIND_CLOUDY,
        WeatherView.WEATHER_KIND_RAINY,
        WeatherView.WEATHER_KIND_SNOW,
        WeatherView.WEATHER_KIND_SLEET,
        WeatherView.WEATHER_KIND_HAIL,
        WeatherView.WEATHER_KIND_FOG,
        WeatherView.WEATHER_KIND_HAZE,
        WeatherView.WEATHER_KIND_THUNDER,
        WeatherView.WEATHER_KIND_THUNDERSTORM,
        WeatherView.WEATHER_KIND_WIND,
    ).map { WallpaperSceneStateFactory.create(it, daylight = 0.5f) }
}
