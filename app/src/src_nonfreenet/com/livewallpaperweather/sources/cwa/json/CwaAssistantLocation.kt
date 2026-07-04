package com.livewallpaperweather.sources.cwa.json

import kotlinx.serialization.Serializable

@Serializable
data class CwaAssistantLocation(
    val WeatherElement: CwaAssistantWeatherElement? = null,
)
