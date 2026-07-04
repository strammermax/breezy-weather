/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.background.weather

import livewallpaperweather.domain.weather.model.Weather
import livewallpaperweather.domain.weather.reference.WeatherCode

private val PRECIPITATING_CODES = setOf(
    WeatherCode.RAIN,
    WeatherCode.SNOW,
    WeatherCode.SLEET,
    WeatherCode.HAIL,
    WeatherCode.THUNDERSTORM,
)

/**
 * Whether [weather] justifies a short-interval follow-up refresh rather than waiting for the
 * user's configured polling rate: an active alert, or precipitation expected to start or stop
 * within the next two hours (a plain [WeatherCode.THUNDER] carries no precipitation, see the
 * live wallpaper's rain-mode handling, so it's deliberately excluded from [PRECIPITATING_CODES]).
 */
internal fun needsAdaptiveRefresh(weather: Weather?): Boolean {
    if (weather == null) return false
    if (weather.currentAlertList.isNotEmpty()) return true

    val currentCode = weather.current?.weatherCode ?: return false
    val upcoming = weather.nextHourlyForecast.take(2)
    if (upcoming.isEmpty()) return false
    val currentlyPrecipitating = currentCode in PRECIPITATING_CODES
    return upcoming.any { (it.weatherCode in PRECIPITATING_CODES) != currentlyPrecipitating }
}
