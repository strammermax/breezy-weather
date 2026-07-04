package com.livewallpaperweather.sources.veduris.json

import kotlinx.serialization.Serializable
import com.livewallpaperweather.sources.veduris.serializers.VedurIsAnySerializer

@Serializable
data class VedurIsAlertRegionsResult(
    @Suppress("ktlint")
    val features: List<@Serializable(with = VedurIsAnySerializer::class) Any?> = listOf(),
)
