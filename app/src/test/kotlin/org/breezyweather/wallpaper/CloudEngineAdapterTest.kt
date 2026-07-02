/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.junit.jupiter.api.Test

class CloudEngineAdapterTest {
    private fun scene(
        weatherKind: Int,
        daylight: Float = 1f,
        windSpeedMetersPerSecond: Float = 0f,
        precipitationMillimetersPerHour: Float? = null,
        cloudCoverPercent: Float? = null,
        visibilityMeters: Float? = null,
    ) = WallpaperSceneStateFactory.create(
        weatherKind = weatherKind,
        daylight = daylight,
        windSpeedMetersPerSecond = windSpeedMetersPerSecond,
        precipitationMillimetersPerHour = precipitationMillimetersPerHour,
        cloudCoverPercent = cloudCoverPercent,
        visibilityMeters = visibilityMeters,
    )

    @Test
    fun `Clear maps to clear`() {
        CloudEngineAdapter.sceneParams(scene(WeatherView.WEATHER_KIND_CLEAR)).weatherId shouldBe "clear"
    }

    @Test
    fun `Partly cloudy without measured cover maps to mostly_clear`() {
        CloudEngineAdapter.sceneParams(scene(WeatherView.WEATHER_KIND_CLOUD)).weatherId shouldBe "mostly_clear"
    }

    @Test
    fun `Partly cloudy with high measured cover promotes to mostly_cloudy`() {
        val params = CloudEngineAdapter.sceneParams(
            scene(WeatherView.WEATHER_KIND_CLOUD, cloudCoverPercent = 80f),
        )
        params.weatherId shouldBe "mostly_cloudy"
    }

    @Test
    fun `Cloudy maps to cloudy`() {
        CloudEngineAdapter.sceneParams(scene(WeatherView.WEATHER_KIND_CLOUDY)).weatherId shouldBe "cloudy"
    }

    @Test
    fun `Cloudy with fully overcast measured cover maps to overcast`() {
        val params = CloudEngineAdapter.sceneParams(
            scene(WeatherView.WEATHER_KIND_CLOUDY, cloudCoverPercent = 95f),
        )
        params.weatherId shouldBe "overcast"
    }

    @Test
    fun `Non-drizzle rain maps to rain regardless of intensity`() {
        val moderate = CloudEngineAdapter.sceneParams(
            scene(WeatherView.WEATHER_KIND_RAINY, precipitationMillimetersPerHour = 8f),
        )
        val heavy = CloudEngineAdapter.sceneParams(
            scene(WeatherView.WEATHER_KIND_RAINY, precipitationMillimetersPerHour = 20f),
        )
        moderate.weatherId shouldBe "rain"
        heavy.weatherId shouldBe "rain"
    }

    @Test
    fun `Drizzle-range rain maps to drizzle`() {
        val params = CloudEngineAdapter.sceneParams(
            scene(WeatherView.WEATHER_KIND_RAINY, precipitationMillimetersPerHour = 0.05f),
        )
        params.weatherId shouldBe "drizzle"
    }

    @Test
    fun `Thunderstorm maps to thunderstorm`() {
        CloudEngineAdapter.sceneParams(scene(WeatherView.WEATHER_KIND_THUNDERSTORM)).weatherId shouldBe "thunderstorm"
    }

    @Test
    fun `Snow maps to snow or snow_showers depending on intensity`() {
        val light = CloudEngineAdapter.sceneParams(
            scene(WeatherView.WEATHER_KIND_SNOW, precipitationMillimetersPerHour = 0.1f),
        )
        val heavy = CloudEngineAdapter.sceneParams(
            scene(WeatherView.WEATHER_KIND_SNOW, precipitationMillimetersPerHour = 5f),
        )
        light.weatherId shouldBe "snow"
        heavy.weatherId shouldBe "snow_showers"
    }

    @Test
    fun `Fog maps to fog`() {
        CloudEngineAdapter.sceneParams(scene(WeatherView.WEATHER_KIND_FOG)).weatherId shouldBe "fog"
    }

    @Test
    fun `Wind maps to windy`() {
        CloudEngineAdapter.sceneParams(scene(WeatherView.WEATHER_KIND_WIND)).weatherId shouldBe "windy"
    }

    @Test
    fun `Sleet and hail fall back to rain`() {
        CloudEngineAdapter.sceneParams(scene(WeatherView.WEATHER_KIND_SLEET)).weatherId shouldBe "rain"
        CloudEngineAdapter.sceneParams(scene(WeatherView.WEATHER_KIND_HAIL)).weatherId shouldBe "rain"
    }

    @Test
    fun `wind speed multiplier matches the scene's wind factor`() {
        val state = scene(WeatherView.WEATHER_KIND_CLOUDY, windSpeedMetersPerSecond = 12f)
        val params = CloudEngineAdapter.sceneParams(state)
        params.windSpeedMultiplier shouldBe state.windFactor
    }

    @Test
    fun `density multiplier stays within 0f to 1f for every family`() {
        WeatherView::class.java.declaredFields
            .filter { it.name.startsWith("WEATHER_KIND_") && it.name != "WEATHER_KIND_NULL" }
            .map { it.getInt(null) }
            .forEach { kind ->
                val params = CloudEngineAdapter.sceneParams(scene(kind))
                params.densityMultiplier shouldBeGreaterThanOrEqual 0f
                params.densityMultiplier shouldBeLessThanOrEqual 1f
            }
    }
}
