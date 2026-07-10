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
    /** Provider source key, e.g. "local", "mediawiki", "flickr". Null if unknown. */
    val source: String? = null,
    /** Human-readable provider name, e.g. "lokale database". Null if unknown. */
    val providerName: String? = null,
    /** Photo title as reported by the provider. Null if unknown. */
    val title: String? = null,
    /** Page the photo was sourced from (e.g. Wikimedia file page). Null if unknown. */
    val pageUrl: String? = null,
    /** Owner/photographer credited by the provider. Null if unknown. */
    val ownerName: String? = null,
    /** License string as reported by the provider, e.g. "CC-BY-SA-4.0". Null if unknown. */
    val license: String? = null,
    /** RemoveSky's resolved place name for the match. Null if unknown. */
    val processedLocation: String? = null,
    /** "enabled"/"disabled" — whether RemoveSky currently serves this photo. Null if unknown. */
    val status: String? = null,
    /** Raw `location` field the record was matched/stored under at the source. Null if unknown. */
    val sourceLocation: String? = null,
    /** ISO timestamp the photo was taken, from EXIF. Null if unknown. */
    val capturedAt: String? = null,
    /** Free-text description RemoveSky stored for the record. Null if unknown. */
    val description: String? = null,
    /** Nearest known city RemoveSky resolved the coordinates to. Null if unknown. */
    val resolvedCity: String? = null,
    /** Whether RemoveSky classified the scene as a city/urban shot. Null if unknown. */
    val isCity: Boolean? = null,
    /** Not yet populated by RemoveSky (no scene classifier exists server-side). Null for now. */
    val sceneType: String? = null,
    /** Not yet populated by RemoveSky (no weather classifier exists server-side). Null for now. */
    val weather: String? = null,
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
        source: String? = null,
        providerName: String? = null,
        title: String? = null,
        pageUrl: String? = null,
        ownerName: String? = null,
        license: String? = null,
        processedLocation: String? = null,
        status: String? = null,
        sourceLocation: String? = null,
        capturedAt: String? = null,
        description: String? = null,
        resolvedCity: String? = null,
        isCity: Boolean? = null,
        sceneType: String? = null,
        weather: String? = null,
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
            source = source,
            providerName = providerName,
            title = title,
            pageUrl = pageUrl,
            ownerName = ownerName,
            license = license,
            processedLocation = processedLocation,
            status = status,
            sourceLocation = sourceLocation,
            capturedAt = capturedAt,
            description = description,
            resolvedCity = resolvedCity,
            isCity = isCity,
            sceneType = sceneType,
            weather = weather,
            now = now
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
        source: String?,
        providerName: String?,
        title: String?,
        pageUrl: String?,
        ownerName: String?,
        license: String?,
        processedLocation: String?,
        status: String?,
        sourceLocation: String?,
        capturedAt: String?,
        description: String?,
        resolvedCity: String?,
        isCity: Boolean?,
        sceneType: String?,
        weather: String?,
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
        source = source,
        providerName = providerName,
        title = title,
        pageUrl = pageUrl,
        ownerName = ownerName,
        license = license,
        processedLocation = processedLocation,
        status = status,
        sourceLocation = sourceLocation,
        capturedAt = capturedAt,
        description = description,
        resolvedCity = resolvedCity,
        isCity = isCity,
        sceneType = sceneType,
        weather = weather
    )
}
