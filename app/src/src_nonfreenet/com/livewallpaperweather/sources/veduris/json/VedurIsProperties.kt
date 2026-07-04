package com.livewallpaperweather.sources.veduris.json

import kotlinx.serialization.Serializable

@Serializable
data class VedurIsProperties(
    val station: VedurIsStation,
)
