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
import org.shredzone.commons.suncalc.MoonIllumination
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan
import java.util.Date

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

enum class WallpaperSkyCondition {
    CLEAR,
    FAIR,
    PARTLY_CLOUDY,
    MOSTLY_CLOUDY,
    OVERCAST,
}

enum class WallpaperPrecipitationCondition {
    NONE,
    DRIZZLE,
    RAIN,
    SLEET,
    SNOW,
    HAIL,
}

enum class WallpaperEffectIntensity {
    NONE,
    LIGHT,
    MODERATE,
    HEAVY,
}

enum class WallpaperVisibilityCondition {
    CLEAR,
    HAZE,
    FOG,
    DENSE_FOG,
}

/** Provider-independent weather axes which can be combined instead of requiring a renderer per phrase. */
data class WallpaperEffectCondition(
    val sky: WallpaperSkyCondition,
    val precipitation: WallpaperPrecipitationCondition,
    val precipitationIntensity: WallpaperEffectIntensity,
    val visibility: WallpaperVisibilityCondition,
    val thunderIntensity: Float,
    val windy: Boolean,
)

/** Immutable, render-ready snapshot built exclusively from locally available data. */
data class WallpaperSceneState(
    val weatherKind: Int,
    val weatherFamily: WallpaperWeatherFamily,
    val condition: WallpaperEffectCondition,
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
    /**
     * How much to darken the bottom photo, 0f (no change) .. 1f (darkest). Combines how
     * far into the night it is with how dense/dark the current cloud cover is, so a
     * sunny-day photo reads as overcast/rainy under heavy clouds and dark at night.
     */
    val photoDimming: Float,
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
    /**
     * Moon phase angle for the current date: 0 = new moon, 90 = first quarter,
     * 180 = full moon, 270 = last quarter. Matches [breezyweather.domain.weather.model.MoonPhase.angle].
     */
    val moonPhaseAngle: Float = 180f,
    /**
     * "Holl. wolken" preset: a deeper-blue sky and richer/sharper-edged cumulus than the
     * regular Partly cloudy/Mostly cloudy look, modelled on a reference photo (see
     * RotatingWeatherScenario.richSky). Purely cosmetic — doesn't change density/coverage.
     */
    val richSky: Boolean = false,
) {
    val daytime: Boolean
        get() = daylight >= 0.5f

    /** Fog desaturates the source photo before the foreground fog veil is drawn. */
    val photoGreyscaleAmount: Float
        get() = when {
            weatherFamily == WallpaperWeatherFamily.HAZE -> 0.22f
            weatherFamily != WallpaperWeatherFamily.FOG -> 0f
            fogIntensity >= 0.95f -> 1f
            fogIntensity >= 0.60f -> 0.85f
            else -> 0f
        }

    val usesGreyscalePhoto: Boolean
        get() = photoGreyscaleAmount > 0f
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
        /** Actual current cloud cover in percent. Null falls back to the weather-code profile. */
        cloudCoverPercent: Float? = null,
        /** Actual current visibility in metres. Null falls back to the weather-code profile. */
        visibilityMeters: Float? = null,
        sunriseMillis: Long? = null,
        sunsetMillis: Long? = null,
        moonriseMillis: Long? = null,
        moonsetMillis: Long? = null,
        weatherRefreshedAtMillis: Long? = null,
        latitude: Double? = null,
        richSky: Boolean = false,
    ): WallpaperSceneState {
        val family = weatherFamily(weatherKind)
        val normalizedKind = weatherKindFor(family)
        val safeDaylight = normalizedUnit(daylight, fallback = 1f)
        val safeWindSpeed = normalizedNonNegative(windSpeedMetersPerSecond)
        val safeWindGust = normalizedNonNegative(windGustMetersPerSecond)
        val condition = effectCondition(
            family = family,
            precipitationMillimetersPerHour = precipitationMillimetersPerHour,
            cloudCoverPercent = cloudCoverPercent,
            visibilityMeters = visibilityMeters,
            windSpeedMetersPerSecond = safeWindSpeed,
            windGustMetersPerSecond = safeWindGust,
        )
        val profile = effectProfile(condition)

        // Fog weather codes cover a broad visibility range. Preserve the condition
        // category, but let measured/forced visibility make light, normal and dense
        // fog visibly distinct in the preview and in automatic weather mode.
        val adjustedFogIntensity = if (family == WallpaperWeatherFamily.FOG) {
            fogIntensityForVisibility(visibilityMeters, profile.fogIntensity)
        } else {
            profile.fogIntensity
        }

        // ACT-XXX: scale the precipitation-driven parts of the profile by how light or
        // heavy the current hour's forecast actually is, so a drizzle and a downpour of
        // the same WeatherCode no longer render identically.
        val precipFactor = precipitationIntensityFactor(precipitationMillimetersPerHour)
        // Check both the profile AND actual precipitation data to determine if it's precipitating
        val isPrecipitating = profile.precipitationIntensity > 0f ||
            (precipitationMillimetersPerHour != null && precipitationMillimetersPerHour > 0f)
        val adjustedPrecipitationIntensity = if (isPrecipitating) {
            (profile.precipitationIntensity * precipFactor).coerceIn(0.1f, 1f)
        } else {
            profile.precipitationIntensity
        }
        // Rain-on-glass belongs only to explicitly wet-glass families. Measured
        // precipitation must not leak the effect into Clear, Cloudy, Thunder, etc.
        val supportsGlassRain = when (family) {
            WallpaperWeatherFamily.RAIN,
            WallpaperWeatherFamily.SLEET,
            WallpaperWeatherFamily.THUNDERSTORM,
            -> profile.glassRainIntensity > 0f ||
                (precipitationMillimetersPerHour?.let { it.isFinite() && it > 0f } == true)
            else -> false
        }
        val adjustedGlassRainIntensity = if (isPrecipitating && supportsGlassRain) {
            glassRainAmount(precipitationMillimetersPerHour, profile.glassRainIntensity)
        } else {
            0f
        }
        // Real drizzle still falls from a fully overcast nimbostratus deck — only the
        // streak count/intensity should scale with mm/h (see adjustedPrecipitationIntensity
        // above), not how dark/dense the sky itself looks. A light lerp here previously
        // washed out the sky for Motregen specifically (sheet: "Donkergrijs", same as Regen).
        val cloudFactor = if (isPrecipitating) lerp(1f, precipFactor, 0.15f) else 1f
        val adjustedCloudDensity = (profile.cloudDensity * cloudFactor).coerceIn(0f, 1f)
        val adjustedCloudDarkness = (profile.cloudDarkness * cloudFactor).coerceIn(0f, 1f)

        // Source photos are typically shot in bright daylight: darken them towards night
        // colors as the sky darkens at night AND as cloud cover/darkness increases, so
        // overcast/rainy/stormy scenes don't look like a sunny photo with rain on top.
        val nightDimming = 1f - safeDaylight
        val cloudDimming = (adjustedCloudDensity * adjustedCloudDarkness * 1.3f).coerceIn(0f, 1f)
        val photoDimming = (1f - (1f - nightDimming) * (1f - cloudDimming)).coerceIn(0f, 1f)

        val moonPhaseAngle = (MoonIllumination.compute().on(Date()).execute().phase + 180.0)
            .roundToInt().toFloat().let { ((it % 360f) + 360f) % 360f }

        return WallpaperSceneState(
            weatherKind = normalizedKind,
            weatherFamily = family,
            condition = condition,
            daylight = safeDaylight,
            windSpeedMetersPerSecond = safeWindSpeed,
            windGustMetersPerSecond = safeWindGust,
            windDirectionDegrees = normalizeDegrees(windDirectionDegrees),
            windFactor = windFactor(family, safeWindSpeed, safeWindGust),
            cloudDensity = adjustedCloudDensity,
            cloudDarkness = adjustedCloudDarkness,
            precipitationIntensity = adjustedPrecipitationIntensity,
            fogIntensity = adjustedFogIntensity,
            hazeIntensity = profile.hazeIntensity,
            thunderIntensity = profile.thunderIntensity,
            glassRainIntensity = adjustedGlassRainIntensity,
            precipitationTiltSlope = precipitationTiltSlope(safeWindSpeed, safeWindGust, windDirectionDegrees),
            photoDimming = photoDimming,
            sunriseMillis = sunriseMillis,
            sunsetMillis = sunsetMillis,
            moonriseMillis = moonriseMillis,
            moonsetMillis = moonsetMillis,
            weatherRefreshedAtMillis = weatherRefreshedAtMillis,
            latitude = latitude,
            moonPhaseAngle = moonPhaseAngle,
            richSky = richSky,
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

    private fun effectCondition(
        family: WallpaperWeatherFamily,
        precipitationMillimetersPerHour: Float?,
        cloudCoverPercent: Float?,
        visibilityMeters: Float?,
        windSpeedMetersPerSecond: Float,
        windGustMetersPerSecond: Float,
    ): WallpaperEffectCondition {
        val familySky = when (family) {
            WallpaperWeatherFamily.CLEAR -> WallpaperSkyCondition.CLEAR
            // The app label is "Licht bewolkt": without a measured cloud percentage,
            // render it as Fair (mostly blue with a few loose cumulus clouds). Providers
            // that do supply a higher percentage can still promote it below.
            WallpaperWeatherFamily.PARTLY_CLOUDY -> WallpaperSkyCondition.FAIR
            WallpaperWeatherFamily.HAZE -> WallpaperSkyCondition.FAIR
            WallpaperWeatherFamily.WIND -> WallpaperSkyCondition.PARTLY_CLOUDY
            WallpaperWeatherFamily.CLOUDY,
            WallpaperWeatherFamily.FOG,
            -> WallpaperSkyCondition.MOSTLY_CLOUDY
            else -> WallpaperSkyCondition.OVERCAST
        }
        val measuredSky = cloudCoverPercent
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0f, 100f)
            ?.let {
                when {
                    it <= 10f -> WallpaperSkyCondition.CLEAR
                    it <= 30f -> WallpaperSkyCondition.FAIR
                    it <= 60f -> WallpaperSkyCondition.PARTLY_CLOUDY
                    it <= 85f -> WallpaperSkyCondition.MOSTLY_CLOUDY
                    else -> WallpaperSkyCondition.OVERCAST
                }
            }
        val sky = maxOf(familySky, measuredSky ?: familySky)

        val precipitation = when (family) {
            WallpaperWeatherFamily.RAIN,
            WallpaperWeatherFamily.THUNDERSTORM,
            -> if (isDrizzle(precipitationMillimetersPerHour)) {
                WallpaperPrecipitationCondition.DRIZZLE
            } else {
                WallpaperPrecipitationCondition.RAIN
            }
            WallpaperWeatherFamily.SLEET -> WallpaperPrecipitationCondition.SLEET
            WallpaperWeatherFamily.SNOW -> WallpaperPrecipitationCondition.SNOW
            WallpaperWeatherFamily.HAIL -> WallpaperPrecipitationCondition.HAIL
            else -> WallpaperPrecipitationCondition.NONE
        }
        val precipitationIntensity = precipitationIntensity(
            precipitation,
            precipitationMillimetersPerHour,
            family == WallpaperWeatherFamily.THUNDERSTORM,
        )
        val visibility = visibilityCondition(family, visibilityMeters)
        val thunder = when (family) {
            WallpaperWeatherFamily.THUNDER -> 0.55f
            WallpaperWeatherFamily.THUNDERSTORM -> 1f
            else -> 0f
        }
        return WallpaperEffectCondition(
            sky = sky,
            precipitation = precipitation,
            precipitationIntensity = precipitationIntensity,
            visibility = visibility,
            thunderIntensity = thunder,
            windy = family == WallpaperWeatherFamily.WIND ||
                max(windSpeedMetersPerSecond, windGustMetersPerSecond) >= 8f,
        )
    }

    private fun effectProfile(condition: WallpaperEffectCondition): EffectProfile {
        val skyCloudDensity = when (condition.sky) {
            WallpaperSkyCondition.CLEAR -> 0f
            // Fair remains an almost-clear sky. Prominence comes from the renderer's
            // brighter, more opaque cumulus treatment, not from adding more coverage.
            WallpaperSkyCondition.FAIR -> 0.18f
            WallpaperSkyCondition.PARTLY_CLOUDY -> 0.42f
            WallpaperSkyCondition.MOSTLY_CLOUDY -> 0.70f
            WallpaperSkyCondition.OVERCAST -> 0.95f
        }
        val cloudDensity = max(skyCloudDensity, when {
            condition.thunderIntensity >= 1f -> 1f
            condition.precipitation == WallpaperPrecipitationCondition.HAIL -> 1f
            condition.precipitation != WallpaperPrecipitationCondition.NONE -> 0.95f
            else -> 0f
        })
        var cloudDarkness = when (condition.sky) {
            WallpaperSkyCondition.CLEAR -> 0f
            // Even sparse Fair clouds need enough tonal range for a light-grey base;
            // lower values flattened the rear layers to white and removed their depth.
            WallpaperSkyCondition.FAIR -> 0.10f
            WallpaperSkyCondition.PARTLY_CLOUDY -> 0.14f
            WallpaperSkyCondition.MOSTLY_CLOUDY -> 0.24f
            WallpaperSkyCondition.OVERCAST -> 0.34f
        }
        cloudDarkness = max(cloudDarkness, when (condition.precipitation) {
            WallpaperPrecipitationCondition.DRIZZLE -> 0.38f
            WallpaperPrecipitationCondition.RAIN -> 0.55f
            WallpaperPrecipitationCondition.SLEET -> 0.50f
            WallpaperPrecipitationCondition.SNOW -> 0.45f
            WallpaperPrecipitationCondition.HAIL -> 0.70f
            WallpaperPrecipitationCondition.NONE -> 0f
        })
        cloudDarkness = max(cloudDarkness, condition.thunderIntensity * 0.85f)

        val precipitationStrength = when (condition.precipitationIntensity) {
            WallpaperEffectIntensity.NONE -> 0f
            WallpaperEffectIntensity.LIGHT -> 0.40f
            WallpaperEffectIntensity.MODERATE -> 0.75f
            WallpaperEffectIntensity.HEAVY -> 1f
        }
        val fogIntensity = when (condition.visibility) {
            WallpaperVisibilityCondition.FOG -> 0.80f
            WallpaperVisibilityCondition.DENSE_FOG -> 1f
            else -> 0f
        }
        val hazeIntensity = if (condition.visibility == WallpaperVisibilityCondition.HAZE) 0.62f else 0f
        val supportsWetGlass = condition.precipitation == WallpaperPrecipitationCondition.DRIZZLE ||
            condition.precipitation == WallpaperPrecipitationCondition.RAIN ||
            condition.precipitation == WallpaperPrecipitationCondition.SLEET

        return EffectProfile(
            cloudDensity = cloudDensity,
            cloudDarkness = cloudDarkness.coerceIn(0f, 1f),
            precipitationIntensity = precipitationStrength,
            fogIntensity = fogIntensity,
            hazeIntensity = hazeIntensity,
            thunderIntensity = condition.thunderIntensity,
            glassRainIntensity = if (supportsWetGlass) precipitationStrength else 0f,
        )
    }

    private fun isDrizzle(precipitationMillimetersPerHour: Float?): Boolean {
        val mm = precipitationMillimetersPerHour ?: return false
        return mm.isFinite() && mm > 0f && mm < Precipitation.PRECIPITATION_HOURLY_LIGHT.toFloat()
    }

    private fun precipitationIntensity(
        precipitation: WallpaperPrecipitationCondition,
        precipitationMillimetersPerHour: Float?,
        forceHeavy: Boolean,
    ): WallpaperEffectIntensity {
        if (precipitation == WallpaperPrecipitationCondition.NONE) return WallpaperEffectIntensity.NONE
        if (forceHeavy) return WallpaperEffectIntensity.HEAVY
        val mm = precipitationMillimetersPerHour
        if (mm == null || !mm.isFinite() || mm <= 0f) return WallpaperEffectIntensity.MODERATE
        return when {
            mm < Precipitation.PRECIPITATION_HOURLY_LIGHT.toFloat() -> WallpaperEffectIntensity.LIGHT
            mm < Precipitation.PRECIPITATION_HOURLY_HEAVY.toFloat() -> WallpaperEffectIntensity.MODERATE
            else -> WallpaperEffectIntensity.HEAVY
        }
    }

    private fun visibilityCondition(
        family: WallpaperWeatherFamily,
        visibilityMeters: Float?,
    ): WallpaperVisibilityCondition {
        val measured = visibilityMeters?.takeIf { it.isFinite() && it >= 0f }?.let {
            when {
                it < 1_000f -> WallpaperVisibilityCondition.DENSE_FOG
                it < 5_000f -> WallpaperVisibilityCondition.FOG
                it < 10_000f -> WallpaperVisibilityCondition.HAZE
                else -> WallpaperVisibilityCondition.CLEAR
            }
        }
        val familyVisibility = when (family) {
            WallpaperWeatherFamily.FOG -> WallpaperVisibilityCondition.FOG
            WallpaperWeatherFamily.HAZE -> WallpaperVisibilityCondition.HAZE
            else -> WallpaperVisibilityCondition.CLEAR
        }
        return maxOf(familyVisibility, measured ?: familyVisibility)
    }

    private fun fogIntensityForVisibility(visibilityMeters: Float?, fallback: Float): Float {
        val visibility = visibilityMeters?.takeIf { it.isFinite() && it >= 0f } ?: return fallback
        return when {
            visibility < 1_000f -> 1f
            visibility < 5_000f -> lerp(0.80f, 0.58f, (visibility - 1_000f) / 4_000f)
            visibility < 10_000f -> lerp(0.58f, 0.38f, (visibility - 5_000f) / 5_000f)
            else -> 0.32f
        }
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

    private fun glassRainAmount(precipitationMillimetersPerHour: Float?, fallback: Float): Float {
        val mm = precipitationMillimetersPerHour
        if (mm == null || !mm.isFinite() || mm <= 0f) return fallback.coerceIn(0f, 1f)

        return when {
            mm < Precipitation.PRECIPITATION_HOURLY_LIGHT.toFloat() -> 0.3f
            mm < Precipitation.PRECIPITATION_HOURLY_HEAVY.toFloat() -> 0.7f
            else -> 1f
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
