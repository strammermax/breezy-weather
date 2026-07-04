package com.livewallpaperweather.sources.veduris.json

import kotlinx.serialization.Serializable

@Serializable
data class VedurIsFeature(
    val geometry: VedurIsGeometry,
    val properties: VedurIsProperties,
)
