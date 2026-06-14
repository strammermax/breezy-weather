/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import org.breezyweather.ui.theme.weatherView.WeatherView
import kotlin.math.max

enum class WallpaperWeatherFamily {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    RAIN,
    SNOW,
    SLEET,
    HAIL,
    FOG,
    HAZE,
    THUNDER,
    THUNDERSTORM,
    WIND,
}

/** Immutable, render-ready snapshot built exclusively from locally available data. */
data class WallpaperSceneState(
    val weatherKind: Int,
    val weatherFamily: WallpaperWeatherFamily,
    val daylight: Float,
    val windSpeedMetersPerSecond: Float,
    val windGustMetersPerSecond: Float,
    val windDirectionDegrees: Float?,
    val windFactor: Float,
    val cloudDensity: Float,
    val cloudDarkness: Float,
    val precipitationIntensity: Float,
    val fogIntensity: Float,
    val hazeIntensity: Float,
    val thunderIntensity: Float,
    val glassRainIntensity: Float,
    val photoNightTint: Float,
    val sunriseMillis: Long?,
    val sunsetMillis: Long?,
    val moonriseMillis: Long?,
    val moonsetMillis: Long?,
) {
    val daytime: Boolean
        get() = daylight >= 0.5f
}

object WallpaperSceneStateFactory {
    fun create(
        weatherKind: Int,
        daylight: Float,
        windSpeedMetersPerSecond: Float = 0f,
        windGustMetersPerSecond: Float = 0f,
        windDirectionDegrees: Float? = null,
        sunriseMillis: Long? = null,
        sunsetMillis: Long? = null,
        moonriseMillis: Long? = null,
        moonsetMillis: Long? = null,
    ): WallpaperSceneState {
        val family = weatherFamily(weatherKind)
        val safeDaylight = normalizedUnit(daylight, fallback = 1f)
        val safeWindSpeed = normalizedNonNegative(windSpeedMetersPerSecond)
        val safeWindGust = normalizedNonNegative(windGustMetersPerSecond)
        val profile = effectProfile(family)

        return WallpaperSceneState(
            weatherKind = weatherKindFor(family),
            weatherFamily = family,
            daylight = safeDaylight,
            windSpeedMetersPerSecond = safeWindSpeed,
            windGustMetersPerSecond = safeWindGust,
            windDirectionDegrees = normalizeDegrees(windDirectionDegrees),
            windFactor = windFactor(family, safeWindSpeed, safeWindGust),
            cloudDensity = profile.cloudDensity,
            cloudDarkness = profile.cloudDarkness,
            precipitationIntensity = profile.precipitationIntensity,
            fogIntensity = profile.fogIntensity,
            hazeIntensity = profile.hazeIntensity,
            thunderIntensity = profile.thunderIntensity,
            glassRainIntensity = profile.glassRainIntensity,
            photoNightTint = 1f - safeDaylight,
            sunriseMillis = sunriseMillis,
            sunsetMillis = sunsetMillis,
            moonriseMillis = moonriseMillis,
            moonsetMillis = moonsetMillis,
        )
    }

    fun weatherFamily(weatherKind: Int): WallpaperWeatherFamily = when (weatherKind) {
        WeatherView.WEATHER_KIND_CLEAR -> WallpaperWeatherFamily.CLEAR
        WeatherView.WEATHER_KIND_CLOUD -> WallpaperWeatherFamily.PARTLY_CLOUDY
        WeatherView.WEATHER_KIND_CLOUDY -> WallpaperWeatherFamily.CLOUDY
        WeatherView.WEATHER_KIND_RAINY -> WallpaperWeatherFamily.RAIN
        WeatherView.WEATHER_KIND_SNOW -> WallpaperWeatherFamily.SNOW
        WeatherView.WEATHER_KIND_SLEET -> WallpaperWeatherFamily.SLEET
        WeatherView.WEATHER_KIND_HAIL -> WallpaperWeatherFamily.HAIL
        WeatherView.WEATHER_KIND_FOG -> WallpaperWeatherFamily.FOG
        WeatherView.WEATHER_KIND_HAZE -> WallpaperWeatherFamily.HAZE
        WeatherView.WEATHER_KIND_THUNDER -> WallpaperWeatherFamily.THUNDER
        WeatherView.WEATHER_KIND_THUNDERSTORM -> WallpaperWeatherFamily.THUNDERSTORM
        WeatherView.WEATHER_KIND_WIND -> WallpaperWeatherFamily.WIND
        else -> WallpaperWeatherFamily.CLEAR
    }

    private fun weatherKindFor(family: WallpaperWeatherFamily): Int = when (family) {
        WallpaperWeatherFamily.CLEAR -> WeatherView.WEATHER_KIND_CLEAR
        WallpaperWeatherFamily.PARTLY_CLOUDY -> WeatherView.WEATHER_KIND_CLOUD
        WallpaperWeatherFamily.CLOUDY -> WeatherView.WEATHER_KIND_CLOUDY
        WallpaperWeatherFamily.RAIN -> WeatherView.WEATHER_KIND_RAINY
        WallpaperWeatherFamily.SNOW -> WeatherView.WEATHER_KIND_SNOW
        WallpaperWeatherFamily.SLEET -> WeatherView.WEATHER_KIND_SLEET
        WallpaperWeatherFamily.HAIL -> WeatherView.WEATHER_KIND_HAIL
        WallpaperWeatherFamily.FOG -> WeatherView.WEATHER_KIND_FOG
        WallpaperWeatherFamily.HAZE -> WeatherView.WEATHER_KIND_HAZE
        WallpaperWeatherFamily.THUNDER -> WeatherView.WEATHER_KIND_THUNDER
        WallpaperWeatherFamily.THUNDERSTORM -> WeatherView.WEATHER_KIND_THUNDERSTORM
        WallpaperWeatherFamily.WIND -> WeatherView.WEATHER_KIND_WIND
    }

    private fun windFactor(family: WallpaperWeatherFamily, speed: Float, gust: Float): Float {
        val wind = max(speed, gust)
        val measured = when {
            wind < 8f -> 1f
            wind < 14f -> 1f + (wind - 8f) / 6f * 1.8f
            else -> 3.6f
        }
        return when (family) {
            WallpaperWeatherFamily.THUNDER,
            WallpaperWeatherFamily.THUNDERSTORM,
            -> max(measured, 2.8f)
            WallpaperWeatherFamily.WIND -> max(measured, 4.2f)
            else -> measured
        }
    }

    private fun effectProfile(family: WallpaperWeatherFamily): EffectProfile = when (family) {
        WallpaperWeatherFamily.CLEAR -> EffectProfile()
        WallpaperWeatherFamily.PARTLY_CLOUDY -> EffectProfile(0.35f, 0.05f)
        WallpaperWeatherFamily.CLOUDY -> EffectProfile(0.85f, 0.25f)
        WallpaperWeatherFamily.RAIN -> EffectProfile(0.95f, 0.55f, 0.75f, glassRainIntensity = 0.70f)
        WallpaperWeatherFamily.SNOW -> EffectProfile(0.90f, 0.35f, 0.75f)
        WallpaperWeatherFamily.SLEET -> EffectProfile(0.95f, 0.50f, 0.80f, glassRainIntensity = 0.55f)
        WallpaperWeatherFamily.HAIL -> EffectProfile(1f, 0.60f, 0.90f)
        WallpaperWeatherFamily.FOG -> EffectProfile(0.65f, 0.20f, fogIntensity = 0.85f)
        WallpaperWeatherFamily.HAZE -> EffectProfile(0.35f, 0.10f, hazeIntensity = 0.65f)
        WallpaperWeatherFamily.THUNDER -> EffectProfile(0.95f, 0.70f, thunderIntensity = 0.55f)
        WallpaperWeatherFamily.THUNDERSTORM -> EffectProfile(
            1f,
            0.85f,
            1f,
            thunderIntensity = 1f,
            glassRainIntensity = 0.90f,
        )
        WallpaperWeatherFamily.WIND -> EffectProfile(0.55f, 0.15f)
    }

    private fun normalizedUnit(value: Float, fallback: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else fallback

    private fun normalizedNonNegative(value: Float): Float =
        if (value.isFinite()) value.coerceAtLeast(0f) else 0f

    private fun normalizeDegrees(value: Float?): Float? {
        if (value == null || !value.isFinite() || value == -1f) return null
        return ((value % 360f) + 360f) % 360f
    }

    private data class EffectProfile(
        val cloudDensity: Float = 0f,
        val cloudDarkness: Float = 0f,
        val precipitationIntensity: Float = 0f,
        val fogIntensity: Float = 0f,
        val hazeIntensity: Float = 0f,
        val thunderIntensity: Float = 0f,
        val glassRainIntensity: Float = 0f,
    )
}
