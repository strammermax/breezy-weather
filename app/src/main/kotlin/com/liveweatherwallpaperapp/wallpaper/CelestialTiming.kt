/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import livewallpaperweather.domain.location.model.Location
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sun/moon interval resolution shared between [MaterialLiveWallpaperService] and the
 * [WallpaperSceneSnapshot] used by MainActivity, so both render the same sky gradient
 * for a given location and time.
 */
internal object CelestialTiming {

    private val dayMillis = 24L * 60L * 60L * 1000L

    // ---- Shared arc-rendering constants & helpers ----------------------------------------

    /** Fraction of screen height at which the celestial arc crosses the horizon. */
    const val HORIZON_Y_FRACTION = 0.48f

    /** Fraction of screen height at the apex of the arc (topmost point). */
    const val PEAK_Y_FRACTION = 0.12f

    /** Fraction of shortest screen dimension for the moon disc diameter. */
    const val CELESTIAL_SIZE_FRACTION = 0.14f

    /** Cross-fade duration around sunrise/sunset (25 minutes each side). */
    const val CROSSFADE_MILLIS = 25L * 60L * 1000L

    /**
     * Normalised progress of a celestial body along its arc (0 = rise, 1 = set).
     * Returns 0.5 if either bound is null (body placed at zenith).
     */
    fun celestialProgress(now: Long, riseMillis: Long?, setMillis: Long?): Float {
        val start = riseMillis ?: return 0.5f
        val end = setMillis ?: return 0.5f
        if (end <= start) return 0.5f
        return ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    /** Horizontal screen coordinate for a celestial body at the given arc [progress]. */
    fun celestialX(width: Int, progress: Float): Float = width * (0.12f + 0.76f * progress)

    /** Vertical screen coordinate for a celestial body at the given arc [progress]. */
    fun celestialY(height: Int, progress: Float): Float {
        val horizonY = height * HORIZON_Y_FRACTION
        val peakY = height * PEAK_Y_FRACTION
        return horizonY - sin(PI * progress).toFloat() * (horizonY - peakY)
    }

    /**
     * Alpha [0..1] for the sun, cross-fading over [CROSSFADE_MILLIS] around sunrise/sunset.
     * Falls back to [daytime] if exact times are unavailable.
     */
    fun sunVisibility(
        now: Long,
        sunriseMillis: Long?,
        sunsetMillis: Long?,
        daytime: Boolean,
    ): Float {
        val sunrise = sunriseMillis ?: return if (daytime) 1f else 0f
        val sunset = sunsetMillis ?: return if (daytime) 1f else 0f
        return when {
            now < sunrise - CROSSFADE_MILLIS -> 0f
            now < sunrise + CROSSFADE_MILLIS -> SkyColors.fraction(
                now,
                sunrise - CROSSFADE_MILLIS,
                sunrise + CROSSFADE_MILLIS
            )
            now < sunset - CROSSFADE_MILLIS -> 1f
            now < sunset + CROSSFADE_MILLIS ->
                1f -
                    SkyColors.fraction(now, sunset - CROSSFADE_MILLIS, sunset + CROSSFADE_MILLIS)
            else -> 0f
        }
    }

    /**
     * Alpha [0..1] for the moon, cross-fading over [CROSSFADE_MILLIS] around moonrise/moonset.
     * Returns 0 if either time is unavailable.
     */
    fun moonVisibility(now: Long, moonriseMillis: Long?, moonsetMillis: Long?): Float {
        val moonrise = moonriseMillis ?: return 0f
        val moonset = moonsetMillis ?: return 0f
        if (moonset <= moonrise) return 0f
        return when {
            now < moonrise - CROSSFADE_MILLIS -> 0f
            now < moonrise + CROSSFADE_MILLIS -> SkyColors.fraction(
                now,
                moonrise - CROSSFADE_MILLIS,
                moonrise + CROSSFADE_MILLIS
            )
            now < moonset - CROSSFADE_MILLIS -> 1f
            now < moonset + CROSSFADE_MILLIS ->
                1f -
                    SkyColors.fraction(now, moonset - CROSSFADE_MILLIS, moonset + CROSSFADE_MILLIS)
            else -> 0f
        }
    }

    fun sunIntervals(location: Location?, now: Long): List<Pair<Long, Long>> =
        location?.weather?.dailyForecast.orEmpty().mapNotNull { day ->
            astroInterval(day.sun?.riseDate?.time, day.sun?.setDate?.time, now)
        }

    fun moonIntervals(location: Location?, now: Long): List<Pair<Long, Long>> =
        location?.weather?.dailyForecast.orEmpty().mapNotNull { day ->
            astroInterval(day.moon?.riseDate?.time, day.moon?.setDate?.time, now)
        }

    fun closestAstroInterval(intervals: List<Pair<Long, Long>>, now: Long): Pair<Long, Long>? =
        intervals.firstOrNull { now in it.first..it.second }
            ?: intervals.minByOrNull { interval -> min(abs(now - interval.first), abs(now - interval.second)) }

    private fun astroInterval(rise: Long?, set: Long?, now: Long): Pair<Long, Long>? {
        if (rise == null || set == null) return null
        var start = rise
        var end = set
        if (end <= start) end += dayMillis
        if (now < start && now + dayMillis <= end) {
            start -= dayMillis
            end -= dayMillis
        }
        return start to end
    }

    fun approximateSunInterval(location: Location, now: Long): Pair<Long, Long>? {
        if (!location.isCurrentPosition && location.latitude == 0.0 && location.longitude == 0.0) return null
        val timeZone = TimeZone.getDefault()
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = now }
        val dayOfYear = calendar[Calendar.DAY_OF_YEAR]
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1)
        val equationOfTime = 229.18 * (
            0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2.0 * gamma) - 0.040849 * sin(2.0 * gamma)
            )
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2.0 * gamma) + 0.000907 * sin(2.0 * gamma) -
            0.002697 * cos(3.0 * gamma) + 0.00148 * sin(3.0 * gamma)
        val latitudeRadians = Math.toRadians(location.latitude.coerceIn(-89.0, 89.0))
        val zenith = Math.toRadians(90.833)
        val hourAngleCos = (
            cos(zenith) / (cos(latitudeRadians) * cos(declination)) -
                tan(latitudeRadians) * tan(declination)
            ).coerceIn(-1.0, 1.0)
        val hourAngleDegrees = Math.toDegrees(acos(hourAngleCos))
        val offsetMinutes = timeZone.getOffset(now) / 60_000.0
        val solarNoonMinutes = 720.0 - 4.0 * location.longitude - equationOfTime + offsetMinutes
        val sunriseMinutes = solarNoonMinutes - hourAngleDegrees * 4.0
        val sunsetMinutes = solarNoonMinutes + hourAngleDegrees * 4.0
        val midnight = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (midnight + (sunriseMinutes * 60_000.0).toLong()) to
            (midnight + (sunsetMinutes * 60_000.0).toLong())
    }
}
