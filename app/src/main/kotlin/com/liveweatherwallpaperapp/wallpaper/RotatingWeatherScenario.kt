/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import androidx.annotation.StringRes
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.ui.theme.weatherView.WeatherView

internal data class RotatingWeatherScenario(
    @StringRes val labelRes: Int,
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
        RotatingWeatherScenario(R.string.weather_kind_clear, WeatherView.WEATHER_KIND_CLEAR, cloudCoverPercent = 0f),
        RotatingWeatherScenario(R.string.weather_kind_fair, WeatherView.WEATHER_KIND_CLEAR, cloudCoverPercent = 20f),
        RotatingWeatherScenario(
            R.string.weather_kind_partly_cloudy,
            WeatherView.WEATHER_KIND_CLOUD,
            cloudCoverPercent = 45f
        ),
        // Reference look (camiel, 2026-06-20): rich, deep-blue sky with voluminous,
        // clustered cumulus that have real depth/colour contrast — bigger and denser
        // than "Partly cloudy" so the masses read individually instead of scattered.
        RotatingWeatherScenario(
            R.string.weather_kind_hollandse_lucht,
            WeatherView.WEATHER_KIND_CLOUD,
            cloudCoverPercent = 70f,
            richSky = true
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_mostly_cloudy,
            WeatherView.WEATHER_KIND_CLOUDY,
            cloudCoverPercent = 75f
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_cloudy,
            WeatherView.WEATHER_KIND_CLOUDY,
            cloudCoverPercent = 100f
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_rain_light,
            WeatherView.WEATHER_KIND_RAINY,
            precipitationMillimetersPerHour = 1f
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_rain,
            WeatherView.WEATHER_KIND_RAINY,
            precipitationMillimetersPerHour = 8f
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_rain_heavy,
            WeatherView.WEATHER_KIND_RAINY,
            precipitationMillimetersPerHour = 18f
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_snow,
            WeatherView.WEATHER_KIND_SNOW,
            precipitationMillimetersPerHour = 8f
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_sleet,
            WeatherView.WEATHER_KIND_SLEET,
            precipitationMillimetersPerHour = 3f
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_hail,
            WeatherView.WEATHER_KIND_HAIL,
            precipitationMillimetersPerHour = 18f
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_fog_light,
            WeatherView.WEATHER_KIND_HAZE,
            visibilityMeters = 8_000f
        ),
        RotatingWeatherScenario(R.string.weather_kind_fog, WeatherView.WEATHER_KIND_FOG, visibilityMeters = 3_000f),
        RotatingWeatherScenario(R.string.weather_kind_fog_heavy, WeatherView.WEATHER_KIND_FOG, visibilityMeters = 800f),
        RotatingWeatherScenario(R.string.weather_kind_thunder, WeatherView.WEATHER_KIND_THUNDER),
        RotatingWeatherScenario(
            R.string.weather_kind_thunderstorm,
            WeatherView.WEATHER_KIND_THUNDERSTORM,
            precipitationMillimetersPerHour = 18f
        ),
        RotatingWeatherScenario(R.string.weather_kind_wind, WeatherView.WEATHER_KIND_WIND),
        RotatingWeatherScenario(
            R.string.weather_kind_rain_dense_fog,
            WeatherView.WEATHER_KIND_RAINY,
            precipitationMillimetersPerHour = 8f,
            visibilityMeters = 800f
        ),
        // Extra intensity variants, not separate rows in the sheet but kept for coverage.
        RotatingWeatherScenario(
            R.string.weather_kind_snow_light,
            WeatherView.WEATHER_KIND_SNOW,
            precipitationMillimetersPerHour = 1f
        ),
        RotatingWeatherScenario(
            R.string.weather_kind_snow_heavy,
            WeatherView.WEATHER_KIND_SNOW,
            precipitationMillimetersPerHour = 18f
        )
    )
}
