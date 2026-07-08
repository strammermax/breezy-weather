package com.liveweatherwallpaperapp.sources.veduris.json

import com.liveweatherwallpaperapp.sources.veduris.serializers.VedurIsAnySerializer
import kotlinx.serialization.Serializable

@Serializable
data class VedurIsAlertRegionsResult(
    @Suppress("ktlint")
    val features: List<@Serializable(with = VedurIsAnySerializer::class) Any?> = listOf(),
)
