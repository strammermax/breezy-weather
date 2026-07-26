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
    /** "urban"/"rural"/etc., as classified by RemoveSky. Null if unknown. */
    val sceneType: String? = null,
    /** "sunny"/"cloudy"/"rain"/"snow"/"windy"/"hail", as classified by RemoveSky. Null if unknown. */
    val weather: String? = null,
    /** RemoveSky's resolved location coordinates (city/place center) -- distinct from
     * [exifLat]/[exifLon] (the photo's own EXIF GPS, often absent for curated landscape
     * photos). Used as the distance fallback when EXIF GPS is unknown. Null if unknown. */
    val resolvedLat: Double? = null,
    val resolvedLon: Double? = null,
    /** When RemoveSky processed this photo (server's `processed_at`) -- the "created_date"
     * customsortlogic.md's recency-margin rule sorts on. Distinct from [createdAt] (local
     * first-sync time) and [capturedAt] (EXIF capture date). Null if unknown (e.g. synced
     * before the server started sending this field). */
    val processedAt: String? = null,
    /** The user's own "hide this photo" choice (see the photo-manager screen), distinct from
     * [disabled] (curator-driven, from RemoveSky). Never written by [WallpaperPhotoRepository.upsertDownloaded] --
     * only [WallpaperPhotoRepository.setUserBanned] touches this, so a sync never overwrites it. */
    val userBanned: Boolean = false,
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
        /** Null when only metadata has been synced so far and no local file exists yet
         * (see `upsertDataDB` in ImagesDataSync.kt) -- `downloadMissingImages` relies on this
         * being genuinely null, not an empty string, to find records still needing a download. */
        filePath: String? = null,
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
        resolvedLat: Double? = null,
        resolvedLon: Double? = null,
        processedAt: String? = null,
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
            resolvedLat = resolvedLat,
            resolvedLon = resolvedLon,
            processedAt = processedAt,
            now = now
        )
    }

    suspend fun setDisabled(id: String, disabled: Boolean, now: Long = System.currentTimeMillis()) =
        handler.await {
            wallpaper_photosQueries.setDisabled(disabled, now, id)
        }

    /** The user's own "hide this photo" toggle (photo-manager screen) -- distinct from
     * [setDisabled] (curator-driven). See [WallpaperPhotoRecord.userBanned]. */
    suspend fun setUserBanned(id: String, userBanned: Boolean, now: Long = System.currentTimeMillis()) =
        handler.await {
            wallpaper_photosQueries.setUserBanned(userBanned, now, id)
        }

    /** Looks up a record's id by its source URL -- used by deleteRecordsDataDB, which only
     * knows removed photos by URL (RemoveSky's `/removed` response), never by local id. */
    suspend fun getIdBySourceUrl(sourceUrl: String): String? = handler.awaitOneOrNull {
        wallpaper_photosQueries.getIdBySourceUrl(sourceUrl)
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

    /** Removes every catalog row for [locationKey] -- e.g. when the user deletes that
     * location from the weather app, so its wallpaper photos don't linger orphaned. */
    suspend fun deleteForLocation(locationKey: String) = handler.await {
        wallpaper_photosQueries.deleteForLocation(locationKey)
    }

    suspend fun deleteAll() = handler.await {
        wallpaper_photosQueries.deleteAll()
    }

    suspend fun deleteById(id: String) = handler.await {
        wallpaper_photosQueries.deleteById(id)
    }

    private fun map(
        id: String,
        sourceUrl: String?,
        locationKey: String,
        locationName: String,
        filePath: String?,
        attribution: String?,
        processed: Boolean,
        @Suppress("UNUSED_PARAMETER") rating: Long, // schema still has the column (unused; see breezy-weather#12); positional SQLDelight mapper, must stay
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
        resolvedLat: Double?,
        resolvedLon: Double?,
        processedAt: String?,
        userBanned: Boolean,
    ) = WallpaperPhotoRecord(
        id = id,
        sourceUrl = sourceUrl,
        locationKey = locationKey,
        locationName = locationName,
        filePath = filePath,
        attribution = attribution,
        processed = processed,
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
        weather = weather,
        resolvedLat = resolvedLat,
        resolvedLon = resolvedLon,
        processedAt = processedAt,
        userBanned = userBanned
    )
}
