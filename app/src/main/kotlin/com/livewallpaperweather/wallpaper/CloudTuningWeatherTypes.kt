/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.wallpaper

import androidx.compose.ui.graphics.Color

/**
 * Every weatherId [CloudEngineAdapter] can produce, for the debug-only cloud tuning screen
 * ([CloudTuningActivity]). [skyTop]/[skyBottom] are a simple static preview gradient -- not
 * [SkyColors], which is keyed off a full [WallpaperSceneState] rather than a bare weatherId.
 */
internal data class CloudTuningWeatherType(
    val id: String,
    val label: String,
    val skyTop: Color,
    val skyBottom: Color,
)

internal val CLOUD_TUNING_WEATHER_TYPES = listOf(
    CloudTuningWeatherType("clear", "Clear", Color(0xFF2A6FC9), Color(0xFFAEE2FF)),
    CloudTuningWeatherType("mostly_clear", "Mostly clear", Color(0xFF3F84CE), Color(0xFFC2E6FB)),
    CloudTuningWeatherType("partly_cloudy", "Partly cloudy", Color(0xFF5A8FC0), Color(0xFFCBE0EE)),
    CloudTuningWeatherType("mostly_cloudy", "Mostly cloudy", Color(0xFF5C6B7D), Color(0xFFAAB6C2)),
    CloudTuningWeatherType("cloudy", "Cloudy", Color(0xFF858B91), Color(0xFFB8BDC1)),
    CloudTuningWeatherType("overcast", "Overcast", Color(0xFFA3A8AC), Color(0xFFC3C7C9)),
    CloudTuningWeatherType("drizzle", "Drizzle", Color(0xFF6F7B85), Color(0xFF929BA1)),
    CloudTuningWeatherType("rain", "Rain", Color(0xFF263A55), Color(0xFF657A91)),
    CloudTuningWeatherType("thunderstorm", "Thunderstorm", Color(0xFF22262E), Color(0xFF4D5560)),
    CloudTuningWeatherType("snow", "Snow", Color(0xFFB7C4D6), Color(0xFFEAF1F8)),
    CloudTuningWeatherType("snow_showers", "Snow showers", Color(0xFFA9B8CC), Color(0xFFDDE7F1)),
    CloudTuningWeatherType("fog", "Fog", Color(0xFFAEB4B9), Color(0xFFD9DCDE)),
    CloudTuningWeatherType("windy", "Windy", Color(0xFF4F7FAE), Color(0xFFB9D6EA)),
)
