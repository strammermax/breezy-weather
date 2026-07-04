package com.livewallpaperweather.sources.openmeteo

import livewallpaperweather.domain.weather.reference.WeatherCode

/** Maps Open-Meteo's official WMO interpretation codes to the app's semantic weather types. */
internal fun getOpenMeteoWeatherCode(code: Int?): WeatherCode? = when (code) {
    0, 1 -> WeatherCode.CLEAR
    2 -> WeatherCode.PARTLY_CLOUDY
    3 -> WeatherCode.CLOUDY
    45, 48 -> WeatherCode.FOG
    51, 53, 55, // Drizzle
    56, 57, // Freezing drizzle
    61, 63, 65, // Rain
    66, 67, // Freezing rain
    80, 81, 82, // Rain showers
    -> WeatherCode.RAIN
    71, 73, 75, // Snowfall
    77, // Snow grains
    85, 86, // Snow showers
    -> WeatherCode.SNOW
    95, 96, 99 -> WeatherCode.THUNDERSTORM
    else -> null
}
