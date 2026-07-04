package com.liveweatherwallpaperapp.sources.veduris.json

import kotlinx.serialization.Serializable

@Serializable
data class VedurIsFeature(
    val geometry: VedurIsGeometry,
    val properties: VedurIsProperties,
)
