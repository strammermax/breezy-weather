/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import breezyweather.domain.location.model.Location
import java.util.Calendar
import java.util.TimeZone
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
