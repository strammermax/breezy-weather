/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

/**
 * Render parameters for the `:cloud-engine` module (the wolkentypes prototype), derived from
 * [WallpaperSceneState] as a pure function. [weatherId] is one of the keys understood by
 * `com.wolkentypes.app.clouds.cloudProfileFor` (clear, mostly_clear, partly_cloudy,
 * mostly_cloudy, cloudy, overcast, rain, drizzle, fog, thunderstorm, snow, snow_showers, windy —
 * "showers" exists as a profile but has no reachable trigger here, see the RAIN branch below).
 * This adapter is intentionally one-directional: [WallpaperSceneState] stays the single source
 * of truth for weather data, cloud-engine only receives a derived, render-ready snapshot.
 */
data class CloudEngineSceneParams(
    val weatherId: String,
    val windSpeedMultiplier: Float,
    val densityMultiplier: Float,
)

object CloudEngineAdapter {
    fun sceneParams(state: WallpaperSceneState): CloudEngineSceneParams = CloudEngineSceneParams(
        weatherId = weatherId(state),
        windSpeedMultiplier = state.windFactor,
        densityMultiplier = state.cloudDensity.coerceIn(0f, 1f),
    )

    private fun weatherId(state: WallpaperSceneState): String {
        val condition = state.condition
        return when (state.weatherFamily) {
            WallpaperWeatherFamily.CLEAR -> "clear"
            WallpaperWeatherFamily.HAZE -> "mostly_clear"
            WallpaperWeatherFamily.WIND -> "windy"
            WallpaperWeatherFamily.FOG -> "fog"
            WallpaperWeatherFamily.THUNDER, WallpaperWeatherFamily.THUNDERSTORM -> "thunderstorm"
            WallpaperWeatherFamily.PARTLY_CLOUDY -> when (condition.sky) {
                WallpaperSkyCondition.CLEAR, WallpaperSkyCondition.FAIR -> "mostly_clear"
                WallpaperSkyCondition.PARTLY_CLOUDY -> "partly_cloudy"
                WallpaperSkyCondition.MOSTLY_CLOUDY, WallpaperSkyCondition.OVERCAST -> "mostly_cloudy"
            }
            WallpaperWeatherFamily.CLOUDY -> when (condition.sky) {
                WallpaperSkyCondition.OVERCAST -> "overcast"
                else -> "cloudy"
            }
            WallpaperWeatherFamily.SNOW -> when (condition.precipitationIntensity) {
                WallpaperEffectIntensity.NONE, WallpaperEffectIntensity.LIGHT -> "snow"
                WallpaperEffectIntensity.MODERATE, WallpaperEffectIntensity.HEAVY -> "snow_showers"
            }
            // Sleet and hail have no dedicated cloud-engine profile; the same solid,
            // dark front used for rain is the closest visual match.
            WallpaperWeatherFamily.SLEET, WallpaperWeatherFamily.HAIL -> "rain"
            // A separate "showers" (scattered, open-sky) look would need signal
            // WallpaperSceneState doesn't carry (e.g. shower vs. steady-front probability),
            // so every non-drizzle rain maps to the solid rain front.
            WallpaperWeatherFamily.RAIN -> when (condition.precipitation) {
                WallpaperPrecipitationCondition.DRIZZLE -> "drizzle"
                else -> "rain"
            }
        }
    }
}
