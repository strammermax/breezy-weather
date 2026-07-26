/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper.photo

import com.liveweatherwallpaperapp.wallpaper.CelestialTiming
import com.liveweatherwallpaperapp.wallpaper.WallpaperSeasonGrading
import livewallpaperweather.data.wallpaper.WallpaperPhotoRecord
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.weather.reference.WeatherCode
import java.time.Instant
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Picks the best candidate among [photos] for this location: a strict, lexicographic priority
 * order (each tier only breaks ties left by the previous one — there is no additive trade-off
 * between them), excluding [disabled][WallpaperPhotoRecord.disabled] photos and any whose
 * [sourceUrl][WallpaperPhotoRecord.sourceUrl] is in [excludedUrls] (already tried this refresh):
 *
 *  1. Season match (matches the current season > unknown season > a different, known season —
 *     no snow photos in summer just because nothing else stands out).
 *  2. Day/night match (matches now > doesn't; a photo with no day/night classification is
 *     treated as a day photo).
 *  3. GPS proximity to [latitude]/[longitude] — strictly closer wins; photos with no EXIF GPS
 *     rank last in this tier (so they fall through to the remaining ones, same as everyone
 *     else lacking GPS, rather than being treated as "close").
 *  4. Fewest views.
 *
 * [latitude]/[longitude]/[now] only drive the day-night/season/GPS tiers — they intentionally
 * don't need a fully resolved [Location] (with weather/timezone) so this stays usable from a
 * plain coordinate pair.
 */
internal fun selectWallpaperPhoto(
    photos: List<WallpaperPhotoRecord>,
    excludedUrls: Set<String>,
    latitude: Double = DEFAULT_LATITUDE,
    longitude: Double = DEFAULT_LONGITUDE,
    now: Long = System.currentTimeMillis(),
): WallpaperPhotoRecord? {
    val isNight = isCurrentlyNight(latitude, longitude, now)
    val currentSeason = WallpaperSeasonGrading.seasonFor(
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate(),
        latitude
    ).name.lowercase()

    return photos
        .asSequence()
        .filterNot { it.disabled }
        .filterNot { it.sourceUrl != null && it.sourceUrl in excludedUrls }
        .sortedWith(
            compareByDescending<WallpaperPhotoRecord> { seasonTier(it, currentSeason) }
                .thenByDescending { dayNightMatches(it, isNight) }
                .thenBy { gpsDistanceKmOrWorst(it, latitude, longitude) }
                .thenBy { it.viewCount }
                .thenBy { it.lastShownAt ?: Long.MIN_VALUE }
                .thenBy { it.createdAt }
        )
        .firstOrNull()
}

/** 2 = matches [currentSeason], 1 = unknown (never worse than a wrong season), 0 = a different, known season. */
internal fun seasonTier(photo: WallpaperPhotoRecord, currentSeason: String): Int = when (photo.season) {
    null -> 1
    currentSeason -> 2
    else -> 0
}

/** Unclassified ([WallpaperPhotoRecord.dayPeriod] null) photos count as day photos. */
internal fun dayNightMatches(photo: WallpaperPhotoRecord, isNight: Boolean): Boolean =
    (photo.dayPeriod == "night") == isNight

/**
 * Distance in km from [photo]'s location to [latitude]/[longitude], or [Double.MAX_VALUE] when
 * unknown so it always sorts last in this tier rather than competing with real distances.
 * Prefers the photo's own EXIF GPS (where the camera was); falls back to RemoveSky's resolved
 * place coordinates (city/place center) since most curated landscape photos have no EXIF GPS
 * at all.
 */
internal fun gpsDistanceKmOrWorst(photo: WallpaperPhotoRecord, latitude: Double, longitude: Double): Double {
    val photoLat = photo.exifLat ?: photo.resolvedLat ?: return Double.MAX_VALUE
    val photoLon = photo.exifLon ?: photo.resolvedLon ?: return Double.MAX_VALUE
    return haversineKm(photoLat, photoLon, latitude, longitude)
}

/**
 * Builds a showlist of at least [minSize] unique candidates among [photos] for this location,
 * in priority order, via an escalating fallback ladder -- unlike [selectWallpaperPhoto]'s
 * strict single-pass tiering, this widens the search (radius, then filters) only as far as
 * needed to reach [minSize], so a location with plenty of matching photos never has to fall
 * back to a looser match just because the ladder "ran the full course".
 *
 * [isCurrentPosition] controls whether the radius ladder runs at all: for the GPS-tracked
 * "current location", (lat, lon) genuinely moves around, so "how close to me" is meaningful.
 * For a manually-picked, fixed location (e.g. Barca), (lat, lon) is always that place's exact
 * same center point -- ranking by distance from a point that never changes would return the
 * same handful of closest photos every single time, with no variety. So a fixed location
 * skips straight to the location-wide steps (no distance cutoff at all).
 *
 * Full ladder:
 *  1. (only if [isCurrentPosition]) radius 1/2/5 km, season+weather required, then dropping
 *     weather, then season, then both -- 12 attempts total.
 *  2. Whole location (no radius), in the same season+weather relaxation order, and additionally
 *     dropping day/night matching as the very last resort -- run whenever step 1 didn't reach
 *     [minSize] (always, for a fixed location; only as a fallback for the current location).
 *
 * Within each step, matches are ordered newest first, least-viewed first -- matching the
 * priority [selectWallpaperPhoto] uses standalone.
 *
 * [currentWeather] should already be RemoveSky's vocabulary (see [weatherCodeToRemoveSkyWeather])
 * -- pass null to skip weather matching entirely (e.g. weather data not available yet).
 */
internal fun buildShowlist(
    photos: List<WallpaperPhotoRecord>,
    excludedUrls: Set<String>,
    latitude: Double,
    longitude: Double,
    currentWeather: String?,
    isCurrentPosition: Boolean = true,
    now: Long = System.currentTimeMillis(),
    minSize: Int = 6,
): List<WallpaperPhotoRecord> {
    val isNight = isCurrentlyNight(latitude, longitude, now)
    val currentSeason = WallpaperSeasonGrading.seasonFor(
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate(),
        latitude
    ).name.lowercase()

    val notDisabledOrExcluded = photos
        .asSequence()
        .filterNot { it.disabled }
        .filterNot { it.sourceUrl != null && it.sourceUrl in excludedUrls }
        .toList()
    val dayNightMatching = notDisabledOrExcluded.filter { dayNightMatches(it, isNight) }

    val result = mutableListOf<WallpaperPhotoRecord>()
    val usedIds = mutableSetOf<String>()

    fun sortedMatches(pool: List<WallpaperPhotoRecord>, requireSeason: Boolean, requireWeather: Boolean) =
        pool.asSequence()
            .filter { it.id !in usedIds }
            .filter { !requireSeason || seasonTier(it, currentSeason) == 2 }
            .filter { !requireWeather || currentWeather == null || it.weather == currentWeather }
            .sortedWith(
                compareByDescending<WallpaperPhotoRecord> { it.createdAt }
                    .thenBy { it.viewCount }
            )
            .toList()

    fun append(matches: List<WallpaperPhotoRecord>) {
        matches.forEach { usedIds += it.id }
        result += matches
    }

    // Step 1: radius ladder, current-location only.
    if (isCurrentPosition) {
        val radii = listOf(1.0, 2.0, 5.0)
        val filterStages = listOf(true to true, false to true, true to false, false to false)
        for ((requireSeason, requireWeather) in filterStages) {
            for (radiusKm in radii) {
                if (result.size >= minSize) return result
                append(
                    sortedMatches(
                        dayNightMatching.filter { gpsDistanceKmOrWorst(it, latitude, longitude) <= radiusKm },
                        requireSeason,
                        requireWeather
                    )
                )
            }
        }
    }

    // Step 2: whole location, no distance cutoff -- always runs for a fixed location; only a
    // fallback for the current location if the radius ladder above didn't reach minSize.
    for ((requireSeason, requireWeather) in listOf(true to true, true to false, false to true, false to false)) {
        if (result.size >= minSize) return result
        append(sortedMatches(dayNightMatching, requireSeason, requireWeather))
    }

    // Last resort: drop day/night matching too, keeping only "not disabled/excluded".
    if (result.size < minSize) {
        append(sortedMatches(notDisabledOrExcluded, requireSeason = false, requireWeather = false))
    }
    return result
}

/** Maps the app's live weather condition to RemoveSky's photo `weather` vocabulary (sunny,
 * cloudy, rain, snow, windy, hail) for [buildShowlist]'s weather tier. Null for codes with no
 * clean match (fog/haze/sleet/thunder...) -- better to skip the weather filter than guess wrong. */
internal fun weatherCodeToRemoveSkyWeather(code: WeatherCode?): String? =
    when (code) {
        WeatherCode.CLEAR -> "sunny"
        WeatherCode.PARTLY_CLOUDY, WeatherCode.CLOUDY -> "cloudy"
        WeatherCode.RAIN -> "rain"
        WeatherCode.SNOW -> "snow"
        WeatherCode.WIND -> "windy"
        WeatherCode.HAIL -> "hail"
        else -> null
    }

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
}

internal fun isCurrentlyNight(latitude: Double, longitude: Double, now: Long): Boolean {
    val (sunrise, sunset) = CelestialTiming.approximateSunInterval(
        Location(latitude = latitude, longitude = longitude),
        now
    ) ?: return false
    return now < sunrise || now > sunset
}

private const val DEFAULT_LATITUDE = 52.0
private const val DEFAULT_LONGITUDE = 5.0
private const val EARTH_RADIUS_KM = 6371.0
