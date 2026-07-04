package com.livewallpaperweather.sources.veduris.json

import kotlinx.serialization.Serializable

@Serializable
data class VedurIsStationResult(
    val forecasts: Map<String, VedurIsForecast>? = null,
)
