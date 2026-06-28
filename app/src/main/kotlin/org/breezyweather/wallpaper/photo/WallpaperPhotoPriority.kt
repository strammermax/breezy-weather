/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper.photo

import breezyweather.data.wallpaper.WallpaperPhotoRecord
import breezyweather.domain.location.model.Location
import org.breezyweather.wallpaper.CelestialTiming
import org.breezyweather.wallpaper.WallpaperSeasonGrading
import java.time.Instant
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Picks the best candidate among [photos] for this location, ranked by [wallpaperPhotoScore]
 * and excluding [disabled][WallpaperPhotoRecord.disabled] photos and any whose
 * [sourceUrl][WallpaperPhotoRecord.sourceUrl] is in [excludedUrls] (already tried this refresh).
 *
 * [latitude]/[longitude]/[now] only drive the day-night/season match terms of the score — they
 * intentionally don't need a fully resolved [Location] (with weather/timezone) so this stays
 * usable from a plain coordinate pair.
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
            compareByDescending<WallpaperPhotoRecord> {
                wallpaperPhotoScore(it, isNight, currentSeason, latitude, longitude)
            }
                .thenBy { it.viewCount }
                .thenBy { it.lastShownAt ?: Long.MIN_VALUE }
                .thenBy { it.createdAt }
        )
        .firstOrNull()
}

/**
 * Additive priority score for [photo] — higher wins. Each term is independent so a photo can
 * lose on one axis (e.g. wrong season) without being excluded outright, except thumbs-down:
 * [THUMBS_DOWN_PENALTY] is large enough that a disliked photo only wins when every other
 * eligible candidate is also disliked (a deliberate "last resort", not a soft preference).
 */
internal fun wallpaperPhotoScore(
    photo: WallpaperPhotoRecord,
    isNight: Boolean,
    currentSeason: String,
    latitude: Double = DEFAULT_LATITUDE,
    longitude: Double = DEFAULT_LONGITUDE,
): Int {
    val viewPenalty = -photo.viewCount.coerceAtMost(MAX_VIEW_PENALTY)

    // Most photos have no day/night classification yet; default to "day" rather than treating
    // it as neutral, per spec.
    val photoIsNight = photo.dayPeriod == "night"
    val dayNightScore = if (photoIsNight == isNight) DAY_NIGHT_MATCH_BONUS else 0

    // Season is frequently unknown (null) — that must stay neutral. A *known* season is either
    // a strong positive (matches now) or a strong negative (e.g. no snow photos in summer);
    // unknown is never worse than a wrong season.
    val seasonScore = when (photo.season) {
        null -> 0
        currentSeason -> SEASON_MATCH_BONUS
        else -> -SEASON_MISMATCH_PENALTY
    }

    val ratingScore = when (photo.rating) {
        1 -> THUMBS_UP_BONUS
        -1 -> -THUMBS_DOWN_PENALTY
        else -> 0
    }

    // The photo's own EXIF GPS (only present for a minority of photos — most are null and stay
    // neutral, same as season). Distinct from resolved_lat/resolved_lon, which RemoveSky may
    // fall back to a reverse-geocode/IP guess rather than the photo's actual GPS.
    val gpsScore = gpsProximityScore(photo.exifLat, photo.exifLon, latitude, longitude)

    return viewPenalty + dayNightScore + seasonScore + ratingScore + gpsScore
}

private fun gpsProximityScore(
    photoLatitude: Double?,
    photoLongitude: Double?,
    currentLatitude: Double,
    currentLongitude: Double,
): Int {
    if (photoLatitude == null || photoLongitude == null) return 0
    val distanceKm = haversineKm(photoLatitude, photoLongitude, currentLatitude, currentLongitude)
    return when {
        distanceKm <= GPS_CLOSE_RADIUS_KM -> GPS_CLOSE_BONUS
        distanceKm <= GPS_NEAR_RADIUS_KM -> GPS_NEAR_BONUS
        else -> 0
    }
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
private const val MAX_VIEW_PENALTY = 25
private const val DAY_NIGHT_MATCH_BONUS = 20
private const val SEASON_MATCH_BONUS = 15
private const val SEASON_MISMATCH_PENALTY = 15
private const val THUMBS_UP_BONUS = 5
private const val THUMBS_DOWN_PENALTY = 1000
private const val GPS_CLOSE_RADIUS_KM = 5.0
private const val GPS_NEAR_RADIUS_KM = 10.0
private const val GPS_CLOSE_BONUS = 10
private const val GPS_NEAR_BONUS = 5
private const val EARTH_RADIUS_KM = 6371.0
