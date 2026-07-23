/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package com.liveweatherwallpaperapp.wallpaper.photo

import livewallpaperweather.data.wallpaper.WallpaperPhotoRepository
import org.json.JSONObject
import java.security.MessageDigest

/**
 * `upsertDataDB`/`deleteRecordsDataDB` from ACT-021: thin wrappers around the existing
 * [WallpaperPhotoRepository] (SQLDelight, `wallpaper_photos`) rather than a separate database
 * -- see docs/ACT-021, section 3, for why a second local DB was rejected in favor of reusing
 * this one, which [WallpaperPhotoPriority] already reads.
 */

/**
 * Upserts every entry in [json]'s `results` array (the shape returned by
 * `getImagesDataByCity`/`getImagesDataByGPS`/`updateImagesDataBy*`'s `upserted` half) into
 * [repo]. A no-op when [json] is null, has no `results` (e.g. a `{changed:false}` response),
 * or an entry has a blank `processed_url` (nothing usable to key the row on).
 */
suspend fun upsertDataDB(repo: WallpaperPhotoRepository, locationKey: String, json: JSONObject?) {
    val results = json?.optJSONArray("results") ?: return
    for (i in 0 until results.length()) {
        val item = results.getJSONObject(i)
        val url = item.optString("processed_url").ifBlank { null } ?: continue
        repo.upsertDownloaded(
            id = photoId(url),
            sourceUrl = url,
            locationKey = locationKey,
            locationName = item.optStringOrNull("location") ?: locationKey,
            filePath = null,
            attribution = item.optStringOrNull("owner_name") ?: item.optStringOrNull("title"),
            processed = true,
            dayPeriod = item.optStringOrNull("day_period"),
            country = item.optStringOrNull("country"),
            season = item.optStringOrNull("season"),
            exifLat = item.optDoubleOrNull("exif_lat"),
            exifLon = item.optDoubleOrNull("exif_lon"),
            source = item.optStringOrNull("source"),
            providerName = item.optStringOrNull("provider"),
            title = item.optStringOrNull("title"),
            pageUrl = item.optStringOrNull("page_url"),
            ownerName = item.optStringOrNull("owner_name"),
            license = item.optStringOrNull("license"),
            processedLocation = item.optStringOrNull("processed_location"),
            status = item.optStringOrNull("status") ?: "enabled",
            sourceLocation = item.optStringOrNull("location"),
            capturedAt = item.optStringOrNull("captured_at"),
            description = item.optStringOrNull("description"),
            resolvedCity = item.optStringOrNull("resolved_city"),
            isCity = item.optBooleanOrNull("is_city"),
            sceneType = item.optStringOrNull("scene_type"),
            weather = item.optStringOrNull("weather"),
            resolvedLat = item.optDoubleOrNull("resolved_lat"),
            resolvedLon = item.optDoubleOrNull("resolved_lon"),
            processedAt = item.optStringOrNull("processed_at")
        )
    }
}

/**
 * Disables every record in [repo] whose url is in [json]'s `removed_urls` array (the shape
 * returned by `/removed`, i.e. `updateImagesDataBy*`'s `removed` half) -- images the server
 * now reports as disabled or soft-deleted. Always a soft [WallpaperPhotoRepository.setDisabled],
 * never a hard delete (see docs/ACT-021, section 7.4.3): keeps `view_count`/`created_at`
 * history in case a curator re-enables the photo later. A no-op when [json] is null or has
 * no `removed_urls`, or when a url has no matching local row.
 */
suspend fun deleteRecordsDataDB(repo: WallpaperPhotoRepository, json: JSONObject?) {
    val urls = json?.optJSONArray("removed_urls") ?: return
    for (i in 0 until urls.length()) {
        val url = urls.optString(i, null) ?: continue
        val id = repo.getIdBySourceUrl(url) ?: continue
        repo.setDisabled(id, true)
    }
}

/**
 * One-time migration (see docs/ACT-021, section 10.5) of [store]'s pre-DB file cache
 * (`placeKey -> urls`, plus the single active [store]'s cached photo) into [repo] as
 * placeholder rows -- that old cache never recorded location text, GPS, day_period, or any
 * of `wallpaper_photos`'s other metadata columns, so this can't reconstruct full rows. The
 * next real getImagesDataBy*/updateImagesDataBy* sync for that place naturally overwrites
 * these placeholders with full data (`upsertDataDB` uses `INSERT ... ON CONFLICT(id) DO
 * UPDATE`).
 *
 * Returns true (migration "succeeded") when every discovered url was written without
 * throwing -- the caller should then mark [WallpaperImageStore.imageDbMigrationDone] so this
 * never re-runs. Returns false (leaving the file cache untouched) on any failure, so the next
 * app start tries again.
 */
suspend fun migrateFileCacheToRepository(store: WallpaperImageStore, repo: WallpaperPhotoRepository): Boolean {
    val urls = buildSet {
        store.allRecentUrls().values.forEach { addAll(it) }
        store.cachedPhotoUrl?.let { add(it) }
    }
    if (urls.isEmpty()) return true
    return urls.all { url ->
        runCatching {
            repo.upsertDownloaded(
                id = photoId(url),
                sourceUrl = url,
                locationKey = MIGRATED_LOCATION_KEY,
                locationName = MIGRATED_LOCATION_KEY,
                filePath = null,
                attribution = null,
                processed = true
            )
        }.isSuccess
    }
}

/** Whether [store] still has file-cache state that might need [migrateFileCacheToRepository]. */
fun fileCacheExists(store: WallpaperImageStore): Boolean =
    store.allRecentUrls().isNotEmpty() || store.cachedPhotoUrl != null

/** Placeholder `location_key` for migrated rows -- their real location is unknown (the old
 * file cache never recorded it); the next real sync for the photo's actual location
 * overwrites this via `upsertDataDB`'s `ON CONFLICT(id)` update. */
private const val MIGRATED_LOCATION_KEY = "unmigrated"

private fun photoId(url: String): String = "url:" + MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).trim().ifBlank { null } else null

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null

private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (has(key) && !isNull(key)) optBoolean(key) else null
