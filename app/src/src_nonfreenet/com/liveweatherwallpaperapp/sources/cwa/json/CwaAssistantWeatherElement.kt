package com.liveweatherwallpaperapp.sources.cwa.json

import kotlinx.serialization.Serializable

@Serializable
data class CwaAssistantWeatherElement(
    val ElementValue: CwaAssistantElementValue? = null,
)
