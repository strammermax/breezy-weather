package com.liveweatherwallpaperapp.sources.veduris.json

import kotlinx.serialization.Serializable

@Serializable
data class VedurIsGeometry(
    val coordinates: List<Double>,
)
