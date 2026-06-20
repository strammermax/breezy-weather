/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import org.breezyweather.ui.theme.weatherView.WeatherView

internal data class RotatingWeatherScenario(
    val label: String,
    val weatherKind: Int,
    val precipitationMillimetersPerHour: Float? = null,
    val cloudCoverPercent: Float? = null,
    val visibilityMeters: Float? = null,
)

internal object RotatingWeatherScenarios {
    val ALL = listOf(
        RotatingWeatherScenario("Clear", WeatherView.WEATHER_KIND_CLEAR, cloudCoverPercent = 0f),
        RotatingWeatherScenario("Fair", WeatherView.WEATHER_KIND_CLEAR, cloudCoverPercent = 20f),
        RotatingWeatherScenario("Partly cloudy", WeatherView.WEATHER_KIND_CLOUD, cloudCoverPercent = 45f),
        RotatingWeatherScenario("Mostly cloudy", WeatherView.WEATHER_KIND_CLOUDY, cloudCoverPercent = 75f),
        RotatingWeatherScenario("Overcast", WeatherView.WEATHER_KIND_CLOUDY, cloudCoverPercent = 100f),
        RotatingWeatherScenario("Drizzle", WeatherView.WEATHER_KIND_RAINY, precipitationMillimetersPerHour = 1f),
        RotatingWeatherScenario("Rain", WeatherView.WEATHER_KIND_RAINY, precipitationMillimetersPerHour = 8f),
        RotatingWeatherScenario("Heavy rain", WeatherView.WEATHER_KIND_RAINY, precipitationMillimetersPerHour = 18f),
        RotatingWeatherScenario("Light snow", WeatherView.WEATHER_KIND_SNOW, precipitationMillimetersPerHour = 1f),
        RotatingWeatherScenario("Snow", WeatherView.WEATHER_KIND_SNOW, precipitationMillimetersPerHour = 8f),
        RotatingWeatherScenario("Heavy snow", WeatherView.WEATHER_KIND_SNOW, precipitationMillimetersPerHour = 18f),
        RotatingWeatherScenario("Sleet", WeatherView.WEATHER_KIND_SLEET, precipitationMillimetersPerHour = 8f),
        RotatingWeatherScenario("Hail", WeatherView.WEATHER_KIND_HAIL, precipitationMillimetersPerHour = 18f),
        RotatingWeatherScenario("Haze", WeatherView.WEATHER_KIND_HAZE, visibilityMeters = 8_000f),
        RotatingWeatherScenario("Fog", WeatherView.WEATHER_KIND_FOG, visibilityMeters = 3_000f),
        RotatingWeatherScenario("Dense fog", WeatherView.WEATHER_KIND_FOG, visibilityMeters = 800f),
        RotatingWeatherScenario("Thunder", WeatherView.WEATHER_KIND_THUNDER),
        RotatingWeatherScenario(
            "Thunderstorm",
            WeatherView.WEATHER_KIND_THUNDERSTORM,
            precipitationMillimetersPerHour = 18f,
        ),
        RotatingWeatherScenario("Wind", WeatherView.WEATHER_KIND_WIND),
        RotatingWeatherScenario(
            "Rain + dense fog",
            WeatherView.WEATHER_KIND_RAINY,
            precipitationMillimetersPerHour = 8f,
            visibilityMeters = 800f,
        ),
    )
}
