package com.liveweatherwallpaperapp.sources.veduris.json

import com.liveweatherwallpaperapp.common.serializer.DateSerializer
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class VedurIsAlert(
    val identifier: String,
    val icon: String?,
    @Serializable(DateSerializer::class) val startsAt: Date?,
    @Serializable(DateSerializer::class) val endsAt: Date?,
    val headline: String?,
    val description: String?,
    val impact: String?,
)
