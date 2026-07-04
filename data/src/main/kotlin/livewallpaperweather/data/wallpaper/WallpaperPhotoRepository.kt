/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package livewallpaperweather.data.wallpaper

import livewallpaperweather.data.DatabaseHandler

data class WallpaperPhotoRecord(
    val id: String,
    val sourceUrl: String?,
    val locationKey: String,
    val locationName: String,
    val filePath: String?,
    val attribution: String?,
    val processed: Boolean,
    val rating: Int,
    val disabled: Boolean,
    val viewCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastShownAt: Long?,
    /** "day" or "night", as classified by RemoveSky. Null if unknown. */
    val dayPeriod: String? = null,
    /** Country the photo was geotagged/located in, as resolved by RemoveSky. Null if unknown. */
    val country: String? = null,
    /** "winter"/"spring"/"summer"/"autumn", as classified by RemoveSky. Null if unknown. */
    val season: String? = null,
    /** The photo's own EXIF GPS coordinates, distinct from the matched place. Null if unknown. */
    val exifLat: Double? = null,
    val exifLon: Double? = null,
)

class WallpaperPhotoRepository(
    private val handler: DatabaseHandler,
) {
    suspend fun getAll(): List<WallpaperPhotoRecord> = handler.awaitList {
        wallpaper_photosQueries.getAll(::map)
    }

    suspend fun getForLocation(locationKey: String): List<WallpaperPhotoRecord> = handler.awaitList {
        wallpaper_photosQueries.getForLocation(locationKey, ::map)
    }

    suspend fun getById(id: String): WallpaperPhotoRecord? = handler.awaitOneOrNull {
        wallpaper_photosQueries.getById(id, ::map)
    }

    suspend fun getDisabledSourceUrls(): Set<String> = handler.awaitList {
        wallpaper_photosQueries.getDisabledSourceUrls()
    }.filterNotNull().toSet()

    suspend fun upsertDownloaded(
        id: String,
        sourceUrl: String?,
        locationKey: String,
        locationName: String,
        filePath: String,
        attribution: String?,
        processed: Boolean,
        dayPeriod: String? = null,
        country: String? = null,
        season: String? = null,
        exifLat: Double? = null,
        exifLon: Double? = null,
        now: Long = System.currentTimeMillis(),
    ) = handler.await {
        wallpaper_photosQueries.upsertDownloaded(
            id = id,
            sourceUrl = sourceUrl,
            locationKey = locationKey,
            locationName = locationName,
            filePath = filePath,
            attribution = attribution,
            processed = processed,
            dayPeriod = dayPeriod,
            country = country,
            season = season,
            exifLat = exifLat,
            exifLon = exifLon,
            now = now,
        )
    }

    suspend fun setRating(id: String, rating: Int, now: Long = System.currentTimeMillis()) = handler.await {
        wallpaper_photosQueries.setRating(rating.coerceIn(-1, 1).toLong(), now, id)
    }

    suspend fun setDisabled(id: String, disabled: Boolean, now: Long = System.currentTimeMillis()) =
        handler.await {
            wallpaper_photosQueries.setDisabled(disabled, now, id)
        }

    suspend fun markShown(id: String, now: Long = System.currentTimeMillis()) = handler.await {
        wallpaper_photosQueries.markShown(now, id)
    }

    suspend fun clearFilePath(id: String, now: Long = System.currentTimeMillis()) = handler.await {
        wallpaper_photosQueries.clearFilePath(now, id)
    }

    suspend fun clearAllFilePaths(now: Long = System.currentTimeMillis()) = handler.await {
        wallpaper_photosQueries.clearAllFilePaths(now)
    }

    private fun map(
        id: String,
        sourceUrl: String?,
        locationKey: String,
        locationName: String,
        filePath: String?,
        attribution: String?,
        processed: Boolean,
        rating: Long,
        disabled: Boolean,
        viewCount: Long,
        createdAt: Long,
        updatedAt: Long,
        lastShownAt: Long?,
        dayPeriod: String?,
        country: String?,
        season: String?,
        exifLat: Double?,
        exifLon: Double?,
    ) = WallpaperPhotoRecord(
        id = id,
        sourceUrl = sourceUrl,
        locationKey = locationKey,
        locationName = locationName,
        filePath = filePath,
        attribution = attribution,
        processed = processed,
        rating = rating.toInt(),
        disabled = disabled,
        viewCount = viewCount.toInt(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastShownAt = lastShownAt,
        dayPeriod = dayPeriod,
        country = country,
        season = season,
        exifLat = exifLat,
        exifLon = exifLon,
    )
}
