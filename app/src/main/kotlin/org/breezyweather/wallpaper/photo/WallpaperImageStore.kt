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

package org.breezyweather.wallpaper.photo

import android.content.Context
import org.breezyweather.BuildConfig
import org.breezyweather.domain.settings.ConfigStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the configuration and cache bookkeeping for the photo-background feature of
 * the live wallpaper. Thin wrapper over [ConfigStore] (SharedPreferences) so it can be
 * read from both the UI process and the wallpaper-rendering thread.
 */
class WallpaperImageStore(context: Context) {

    private val config = ConfigStore(context, SP_NAME)

    /** Master switch: when off, the wallpaper uses the original gradient backgrounds. */
    var photoBackgroundEnabled: Boolean
        get() = config.getBoolean(KEY_ENABLED, false)
        set(value) {
            config.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** Maximum disk space used by downloaded wallpaper photos. */
    var photoCacheLimitMb: Int
        get() = config.getInt(KEY_CACHE_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB)
            .coerceIn(MIN_CACHE_LIMIT_MB, MAX_CACHE_LIMIT_MB)
        set(value) {
            config.edit().putInt(
                KEY_CACHE_LIMIT_MB,
                value.coerceIn(MIN_CACHE_LIMIT_MB, MAX_CACHE_LIMIT_MB)
            ).apply()
        }

    /** Maximum number of downloaded photos retained for a single location. */
    var maxCachedPhotosPerLocation: Int
        get() = config.getInt(KEY_MAX_PHOTOS_PER_LOCATION, DEFAULT_MAX_PHOTOS_PER_LOCATION)
            .coerceIn(MIN_PHOTOS_PER_LOCATION, MAX_PHOTOS_PER_LOCATION)
        set(value) {
            config.edit().putInt(
                KEY_MAX_PHOTOS_PER_LOCATION,
                value.coerceIn(MIN_PHOTOS_PER_LOCATION, MAX_PHOTOS_PER_LOCATION)
            ).apply()
        }

    /**
     * Base URL from `local.properties`, with the public service as fallback.
     */
    val removeSkyBaseUrl: String
        get() = BuildConfig.REMOVESKY_URL.ifBlank { RemoveSkyProvider.DEFAULT_BASE_URL }

    /** Optional RemoveSky API key (sent as the `x-api-key` header); empty when the API is open. */
    val removeSkyApiKey: String get() = BuildConfig.REMOVESKY_API_KEY

    /**
     * Cloudflare Access service-token Client ID. When set (with [cfAccessClientSecret]), the app
     * sends `CF-Access-Client-Id` / `CF-Access-Client-Secret` so requests pass a Zero-Trust /
     * Access gate in front of RemoveSky. Values are supplied through `local.properties`.
     */
    val cfAccessClientId: String get() = BuildConfig.CF_ACCESS_CLIENT_ID

    /** Cloudflare Access service-token Client Secret (paired with [cfAccessClientId]). */
    val cfAccessClientSecret: String get() = BuildConfig.CF_ACCESS_CLIENT_SECRET

    /** Absolute path of the currently cached background photo, or null if none. */
    var cachedPhotoPath: String?
        get() = config.getString(KEY_CACHED_PATH, null)
        set(value) {
            config.edit().putString(KEY_CACHED_PATH, value).apply()
        }

    /** The image URL that produced [cachedPhotoPath]; used to avoid re-downloading. */
    var cachedPhotoUrl: String?
        get() = config.getString(KEY_CACHED_URL, null)
        set(value) {
            config.edit().putString(KEY_CACHED_URL, value).apply()
        }

    /** Attribution/credit string for the currently active photo (provider/author/license). */
    var cachedPhotoAttribution: String?
        get() = config.getString(KEY_CACHED_ATTRIBUTION, null)
        set(value) {
            config.edit().putString(KEY_CACHED_ATTRIBUTION, value).apply()
        }

    /** Atomically switches the active wallpaper photo, avoiding partially updated cache state. */
    fun activatePhoto(path: String, url: String, attribution: String?) {
        config.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_CACHED_PATH, path)
            .putString(KEY_CACHED_URL, url)
            .putString(KEY_CACHED_ATTRIBUTION, attribution)
            .apply()
    }

    /** Most recently shown URLs for [placeKey], newest first. */
    fun recentUrlsFor(placeKey: String): List<String> = try {
        val values = recentUrlMap().optJSONArray(placeKey) ?: return emptyList()
        buildList {
            for (index in 0 until values.length()) {
                values.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    } catch (e: Throwable) {
        emptyList()
    }

    /** Adds [url] to the per-location history while retaining the last four unique entries. */
    fun recordRecentUrl(placeKey: String, url: String) {
        val urls = listOf(url) + recentUrlsFor(placeKey).filterNot { it == url }
        val map = recentUrlMap()
        map.put(placeKey, JSONArray(urls.take(RECENT_URL_COUNT)))
        config.edit().putString(KEY_RECENT_URLS, map.toString()).apply()
    }

    private fun recentUrlMap(): JSONObject = try {
        config.getString(KEY_RECENT_URLS, null)?.let(::JSONObject) ?: JSONObject()
    } catch (e: Throwable) {
        JSONObject()
    }

    /** Manually curated area-to-image mappings. */
    var locationData: List<LocationData>
        get() = LocationData.listFromJson(config.getString(KEY_LOCATION_DATA, null))
        set(value) {
            config.edit().putString(KEY_LOCATION_DATA, LocationData.listToJson(value)).apply()
        }

    fun addLocationData(item: LocationData) {
        locationData = locationData + item
    }

    /**
     * Timestamp (epoch millis) of the last automatic photo refresh for [locationId], or 0 if the
     * photo for this location was never (auto-)refreshed (ACT-010 freshness signal for ACT-011).
     */
    fun photoRefreshedAtFor(locationId: String): Long = photoRefreshedAtMap().optLong(locationId, 0L)

    /** Records that [locationId]'s photo was (re)checked/refreshed at [timestampMillis]. */
    fun setPhotoRefreshedAt(locationId: String, timestampMillis: Long) {
        val map = photoRefreshedAtMap()
        map.put(locationId, timestampMillis)
        config.edit().putString(KEY_PHOTO_REFRESHED_AT, map.toString()).apply()
    }

    private fun photoRefreshedAtMap(): JSONObject = try {
        config.getString(KEY_PHOTO_REFRESHED_AT, null)?.let(::JSONObject) ?: JSONObject()
    } catch (e: Throwable) {
        JSONObject()
    }

    companion object {
        private const val SP_NAME = "live_wallpaper_photo"
        private const val KEY_ENABLED = "photo_background_enabled"
        private const val KEY_CACHE_LIMIT_MB = "photo_cache_limit_mb"
        private const val KEY_MAX_PHOTOS_PER_LOCATION = "max_photos_per_location"
        private const val KEY_CACHED_PATH = "cached_photo_path"
        private const val KEY_CACHED_URL = "cached_photo_url"
        private const val KEY_CACHED_ATTRIBUTION = "cached_photo_attribution"
        private const val KEY_RECENT_URLS = "recent_urls"
        private const val KEY_LOCATION_DATA = "location_data"
        private const val KEY_PHOTO_REFRESHED_AT = "photo_refreshed_at"

        /** File name used for the cached background bitmap inside the app files dir. */
        const val CACHE_FILE_NAME = "wallpaper_location_photo.jpg"
        const val DEFAULT_CACHE_LIMIT_MB = 100
        const val MIN_CACHE_LIMIT_MB = 25
        const val MAX_CACHE_LIMIT_MB = 500
        const val RECENT_URL_COUNT = 4
        const val DEFAULT_MAX_PHOTOS_PER_LOCATION = 12
        const val MIN_PHOTOS_PER_LOCATION = 4
        const val MAX_PHOTOS_PER_LOCATION = 50
    }
}
