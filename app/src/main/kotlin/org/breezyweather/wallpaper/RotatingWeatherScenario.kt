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
    /** "Holl. wolken": deeper-blue sky, richer/sharper cumulus, modelled on a reference photo. */
    val richSky: Boolean = false,
)

internal object RotatingWeatherScenarios {
    /**
     * Mirrors the "Weertypen" tab of docs/weereffecten.xlsx 1:1 (same 17 rows, same order,
     * same Dutch labels), so the rotation can be compared row-by-row against that sheet.
     * The three extra intensity variants below (granularity the sheet doesn't split out)
     * are appended after row 17 rather than dropped.
     */
    val ALL = listOf(
        RotatingWeatherScenario("Onbewolkt", WeatherView.WEATHER_KIND_CLEAR, cloudCoverPercent = 0f),
        RotatingWeatherScenario("Fair", WeatherView.WEATHER_KIND_CLEAR, cloudCoverPercent = 20f),
        RotatingWeatherScenario("Partly cloudy", WeatherView.WEATHER_KIND_CLOUD, cloudCoverPercent = 45f),
        // Reference look (camiel, 2026-06-20): rich, deep-blue sky with voluminous,
        // clustered cumulus that have real depth/colour contrast — bigger and denser
        // than "Partly cloudy" so the masses read individually instead of scattered.
        RotatingWeatherScenario(
            "Holl. wolken",
            WeatherView.WEATHER_KIND_CLOUD,
            cloudCoverPercent = 70f,
            richSky = true,
        ),
        RotatingWeatherScenario("Mostly cloudy", WeatherView.WEATHER_KIND_CLOUDY, cloudCoverPercent = 75f),
        RotatingWeatherScenario("Overcast", WeatherView.WEATHER_KIND_CLOUDY, cloudCoverPercent = 100f),
        RotatingWeatherScenario("Lichte regen", WeatherView.WEATHER_KIND_RAINY, precipitationMillimetersPerHour = 1f),
        RotatingWeatherScenario("Regen", WeatherView.WEATHER_KIND_RAINY, precipitationMillimetersPerHour = 8f),
        RotatingWeatherScenario("Zware regen", WeatherView.WEATHER_KIND_RAINY, precipitationMillimetersPerHour = 18f),
        RotatingWeatherScenario("Sneeuw", WeatherView.WEATHER_KIND_SNOW, precipitationMillimetersPerHour = 8f),
        RotatingWeatherScenario("Natte sneeuw", WeatherView.WEATHER_KIND_SLEET, precipitationMillimetersPerHour = 3f),
        RotatingWeatherScenario("Hagel", WeatherView.WEATHER_KIND_HAIL, precipitationMillimetersPerHour = 18f),
        RotatingWeatherScenario("Lichte mist", WeatherView.WEATHER_KIND_HAZE, visibilityMeters = 8_000f),
        RotatingWeatherScenario("Mist", WeatherView.WEATHER_KIND_FOG, visibilityMeters = 3_000f),
        RotatingWeatherScenario("Zware mist", WeatherView.WEATHER_KIND_FOG, visibilityMeters = 800f),
        RotatingWeatherScenario("Onweer", WeatherView.WEATHER_KIND_THUNDER),
        RotatingWeatherScenario(
            "Onweersbui",
            WeatherView.WEATHER_KIND_THUNDERSTORM,
            precipitationMillimetersPerHour = 18f,
        ),
        RotatingWeatherScenario("Wind", WeatherView.WEATHER_KIND_WIND),
        RotatingWeatherScenario(
            "Regen + dichte mist",
            WeatherView.WEATHER_KIND_RAINY,
            precipitationMillimetersPerHour = 8f,
            visibilityMeters = 800f,
        ),
        // Extra intensity variants, not separate rows in the sheet but kept for coverage.
        RotatingWeatherScenario("Lichte sneeuw", WeatherView.WEATHER_KIND_SNOW, precipitationMillimetersPerHour = 1f),
        RotatingWeatherScenario("Zware sneeuw", WeatherView.WEATHER_KIND_SNOW, precipitationMillimetersPerHour = 18f),
    )
}
