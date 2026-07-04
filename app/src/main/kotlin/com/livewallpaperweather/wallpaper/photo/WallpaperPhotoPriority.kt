/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.wallpaper.photo

import livewallpaperweather.data.wallpaper.WallpaperPhotoRecord
import livewallpaperweather.domain.location.model.Location
import com.livewallpaperweather.wallpaper.CelestialTiming
import com.livewallpaperweather.wallpaper.WallpaperSeasonGrading
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
 *  1. Not thumbs-down — a disliked photo only wins when every eligible candidate is disliked
 *     (a deliberate "last resort", not a soft preference).
 *  2. Season match (matches the current season > unknown season > a different, known season —
 *     no snow photos in summer just because nothing else stands out).
 *  3. Day/night match (matches now > doesn't; a photo with no day/night classification is
 *     treated as a day photo).
 *  4. GPS proximity to [latitude]/[longitude] — strictly closer wins; photos with no EXIF GPS
 *     rank last in this tier (so they fall through to the remaining ones, same as everyone
 *     else lacking GPS, rather than being treated as "close").
 *  5. Thumbs up.
 *  6. Fewest views.
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
        latitude,
    ).name.lowercase()

    return photos
        .asSequence()
        .filterNot { it.disabled }
        .filterNot { it.sourceUrl != null && it.sourceUrl in excludedUrls }
        .sortedWith(
            compareByDescending<WallpaperPhotoRecord> { it.rating != -1 }
                .thenByDescending { seasonTier(it, currentSeason) }
                .thenByDescending { dayNightMatches(it, isNight) }
                .thenBy { gpsDistanceKmOrWorst(it, latitude, longitude) }
                .thenByDescending { it.rating == 1 }
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
 * Distance in km from [photo]'s own EXIF GPS to [latitude]/[longitude], or [Double.MAX_VALUE]
 * when unknown so it always sorts last in this tier rather than competing with real distances.
 */
internal fun gpsDistanceKmOrWorst(photo: WallpaperPhotoRecord, latitude: Double, longitude: Double): Double {
    val photoLat = photo.exifLat ?: return Double.MAX_VALUE
    val photoLon = photo.exifLon ?: return Double.MAX_VALUE
    return haversineKm(photoLat, photoLon, latitude, longitude)
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun isCurrentlyNight(latitude: Double, longitude: Double, now: Long): Boolean {
    val (sunrise, sunset) = CelestialTiming.approximateSunInterval(
        Location(latitude = latitude, longitude = longitude),
        now,
    ) ?: return false
    return now < sunrise || now > sunset
}

private const val DEFAULT_LATITUDE = 52.0
private const val DEFAULT_LONGITUDE = 5.0
private const val EARTH_RADIUS_KM = 6371.0
