/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import breezyweather.domain.weather.model.Precipitation
import org.breezyweather.ui.theme.weatherView.WeatherView
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

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
    /**
     * Signed horizontal shear (dx/dy) applied to falling rain/snow/hail, derived from wind
     * speed (Beaufort scale) and direction. 0 = falls straight down, magnitude grows towards
     * 1 as wind strength approaches storm force, sign follows the wind direction.
     */
    val precipitationTiltSlope: Float,
    val photoNightTint: Float,
    val sunriseMillis: Long?,
    val sunsetMillis: Long?,
    val moonriseMillis: Long?,
    val moonsetMillis: Long?,
    /**
     * When the weather data behind this snapshot was last refreshed, or null if unknown
     * (ACT-011 snapshot-consistency: sun/moon, location and weather all derive from this
     * same timestamp).
     */
    val weatherRefreshedAtMillis: Long? = null,
    /**
     * Latitude of the active location, or null if unknown (ACT-012: used to determine the
     * hemisphere for the seasonal grading experiment; never logged exactly).
     */
    val latitude: Double? = null,
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
        /**
         * Forecasted precipitation for the current hour, in mm. Drives how light/heavy
         * rain, snow, sleet, hail and thunderstorms render, on top of each family's base
         * profile. Null (no data) leaves the base profile untouched.
         */
        precipitationMillimetersPerHour: Float? = null,
        sunriseMillis: Long? = null,
        sunsetMillis: Long? = null,
        moonriseMillis: Long? = null,
        moonsetMillis: Long? = null,
        weatherRefreshedAtMillis: Long? = null,
        latitude: Double? = null,
    ): WallpaperSceneState {
        val family = weatherFamily(weatherKind)
        val normalizedKind = weatherKindFor(family)
        val safeDaylight = normalizedUnit(daylight, fallback = 1f)
        val safeWindSpeed = normalizedNonNegative(windSpeedMetersPerSecond)
        val safeWindGust = normalizedNonNegative(windGustMetersPerSecond)
        val profile = effectProfile(family)

        // ACT-XXX: scale the precipitation-driven parts of the profile by how light or
        // heavy the current hour's forecast actually is, so a drizzle and a downpour of
        // the same WeatherCode no longer render identically.
        val precipFactor = precipitationIntensityFactor(precipitationMillimetersPerHour)
        val isPrecipitating = profile.precipitationIntensity > 0f
        val adjustedPrecipitationIntensity = if (isPrecipitating) {
            (profile.precipitationIntensity * precipFactor).coerceIn(0.1f, 1f)
        } else {
            profile.precipitationIntensity
        }
        val adjustedGlassRainIntensity = if (isPrecipitating && profile.glassRainIntensity > 0f) {
            (profile.glassRainIntensity * precipFactor).coerceIn(0.1f, 1f)
        } else {
            profile.glassRainIntensity
        }
        val cloudFactor = if (isPrecipitating) lerp(1f, precipFactor, 0.5f) else 1f

        return WallpaperSceneState(
            weatherKind = normalizedKind,
            weatherFamily = family,
            daylight = safeDaylight,
            windSpeedMetersPerSecond = safeWindSpeed,
            windGustMetersPerSecond = safeWindGust,
            windDirectionDegrees = normalizeDegrees(windDirectionDegrees),
            windFactor = windFactor(family, safeWindSpeed, safeWindGust),
            cloudDensity = (profile.cloudDensity * cloudFactor).coerceIn(0f, 1f),
            cloudDarkness = (profile.cloudDarkness * cloudFactor).coerceIn(0f, 1f),
            precipitationIntensity = adjustedPrecipitationIntensity,
            fogIntensity = profile.fogIntensity,
            hazeIntensity = profile.hazeIntensity,
            thunderIntensity = profile.thunderIntensity,
            glassRainIntensity = adjustedGlassRainIntensity,
            precipitationTiltSlope = precipitationTiltSlope(safeWindSpeed, safeWindGust, windDirectionDegrees),
            photoNightTint = 1f - safeDaylight,
            sunriseMillis = sunriseMillis,
            sunsetMillis = sunsetMillis,
            moonriseMillis = moonriseMillis,
            moonsetMillis = moonsetMillis,
            weatherRefreshedAtMillis = weatherRefreshedAtMillis,
            latitude = latitude,
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

    private fun windFactor(
        family: WallpaperWeatherFamily,
        windSpeedMetersPerSecond: Float,
        windGustMetersPerSecond: Float,
    ): Float {
        val wind = max(windSpeedMetersPerSecond, windGustMetersPerSecond)
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
        WallpaperWeatherFamily.PARTLY_CLOUDY -> EffectProfile(cloudDensity = 0.35f, cloudDarkness = 0.05f)
        WallpaperWeatherFamily.CLOUDY -> EffectProfile(cloudDensity = 0.85f, cloudDarkness = 0.25f)
        WallpaperWeatherFamily.RAIN -> EffectProfile(
            cloudDensity = 0.95f,
            cloudDarkness = 0.55f,
            precipitationIntensity = 0.75f,
            glassRainIntensity = 0.70f,
        )
        WallpaperWeatherFamily.SNOW -> EffectProfile(
            cloudDensity = 0.90f,
            cloudDarkness = 0.35f,
            precipitationIntensity = 0.75f,
        )
        WallpaperWeatherFamily.SLEET -> EffectProfile(
            cloudDensity = 0.95f,
            cloudDarkness = 0.50f,
            precipitationIntensity = 0.80f,
            glassRainIntensity = 0.55f,
        )
        WallpaperWeatherFamily.HAIL -> EffectProfile(
            cloudDensity = 1f,
            cloudDarkness = 0.60f,
            precipitationIntensity = 0.90f,
        )
        WallpaperWeatherFamily.FOG -> EffectProfile(
            cloudDensity = 0.65f,
            cloudDarkness = 0.20f,
            fogIntensity = 0.85f,
        )
        WallpaperWeatherFamily.HAZE -> EffectProfile(
            cloudDensity = 0.35f,
            cloudDarkness = 0.10f,
            hazeIntensity = 0.65f,
        )
        WallpaperWeatherFamily.THUNDER -> EffectProfile(
            cloudDensity = 0.95f,
            cloudDarkness = 0.70f,
            thunderIntensity = 0.55f,
        )
        WallpaperWeatherFamily.THUNDERSTORM -> EffectProfile(
            cloudDensity = 1f,
            cloudDarkness = 0.85f,
            precipitationIntensity = 1f,
            thunderIntensity = 1f,
            glassRainIntensity = 0.90f,
        )
        WallpaperWeatherFamily.WIND -> EffectProfile(cloudDensity = 0.55f, cloudDarkness = 0.15f)
    }

    /**
     * Maps an hourly precipitation amount (mm) to a relative intensity factor around 1.0,
     * using the same light/medium/heavy/rainstorm thresholds as the rest of the app
     * ([Precipitation]'s `PRECIPITATION_HOURLY_*` constants). Missing data (null or <= 0)
     * returns 1.0 so the family's base profile renders unchanged.
     */
    private fun precipitationIntensityFactor(precipitationMillimetersPerHour: Float?): Float {
        if (precipitationMillimetersPerHour == null || !precipitationMillimetersPerHour.isFinite() ||
            precipitationMillimetersPerHour <= 0f
        ) {
            return 1f
        }
        val light = Precipitation.PRECIPITATION_HOURLY_LIGHT.toFloat()
        val medium = Precipitation.PRECIPITATION_HOURLY_MEDIUM.toFloat()
        val heavy = Precipitation.PRECIPITATION_HOURLY_HEAVY.toFloat()
        val rainstorm = Precipitation.PRECIPITATION_HOURLY_RAINSTORM.toFloat()
        val mm = precipitationMillimetersPerHour
        return when {
            mm < light -> lerp(0.55f, 0.80f, mm / light)
            mm < medium -> lerp(0.80f, 1.0f, (mm - light) / (medium - light))
            mm < heavy -> lerp(1.0f, 1.15f, (mm - medium) / (heavy - medium))
            mm < rainstorm -> lerp(1.15f, 1.3f, (mm - heavy) / (rainstorm - heavy))
            else -> 1.3f
        }
    }

    /**
     * Converts wind speed/gust to a signed shear slope for falling precipitation.
     *
     * Wind speed is converted to the Beaufort scale ("windkracht") via `B = (v/0.836)^(2/3)`,
     * then mapped to a tilt fraction matching: 0 Bft -> 0% (straight down), 8 Bft -> 45%,
     * 12 Bft -> 90% (horizontal). The fraction is converted to an angle (`fraction * 90deg`)
     * and then to a shear slope (`tan(angle)`), signed by wind direction.
     */
    private fun precipitationTiltSlope(
        windSpeedMetersPerSecond: Float,
        windGustMetersPerSecond: Float,
        windDirectionDegrees: Float?,
    ): Float {
        val wind = max(windSpeedMetersPerSecond, windGustMetersPerSecond)
        val beaufort = (wind / 0.836f).toDouble().pow(2.0 / 3.0).toFloat().coerceIn(0f, 12f)
        val tiltFraction = if (beaufort <= 8f) {
            beaufort / 8f * 0.45f
        } else {
            0.45f + (beaufort - 8f) / 4f * 0.45f
        }.coerceIn(0f, 0.90f)
        val angleRadians = Math.toRadians((tiltFraction * 90.0).toDouble())
        val magnitude = tan(angleRadians).toFloat()
        val direction = normalizeDegrees(windDirectionDegrees)
        val sign = if (direction == null || cos(Math.toRadians(direction.toDouble())) >= 0.0) 1f else -1f
        return magnitude * sign
    }

    private fun lerp(from: Float, to: Float, t: Float): Float =
        from + (to - from) * t.coerceIn(0f, 1f)

    private fun normalizedUnit(value: Float, fallback: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else fallback

    private fun normalizedNonNegative(value: Float): Float =
        if (value.isFinite()) value.coerceAtLeast(0f) else 0f

    private fun normalizeDegrees(value: Float?): Float? {
        if (value == null || !value.isFinite()) return null
        if (value == -1f) return null
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
