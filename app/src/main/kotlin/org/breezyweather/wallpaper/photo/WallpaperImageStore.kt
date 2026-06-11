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
import org.breezyweather.domain.settings.ConfigStore
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

    /**
     * Which provider supplies the background image: [SOURCE_MAPBOX] (satellite of the exact
     * coordinates) or [SOURCE_UNSPLASH] (a photo searched by place name).
     */
    var backgroundSource: String
        get() = config.getString(KEY_BG_SOURCE, null) ?: SOURCE_UNSPLASH
        set(value) {
            config.edit().putString(KEY_BG_SOURCE, value).apply()
        }

    /**
     * Unsplash API access key (Client-ID). Empty by default — see README for how to obtain
     * a free key. Can also be supplied at build time via BuildConfig as a fallback.
     */
    var unsplashAccessKey: String
        get() = config.getString(KEY_UNSPLASH_KEY, null).orEmpty()
        set(value) {
            config.edit().putString(KEY_UNSPLASH_KEY, value).apply()
        }

    /** Mapbox access token (pk.…) for the satellite Static Images API. */
    var mapboxAccessToken: String
        get() = config.getString(KEY_MAPBOX_TOKEN, null).orEmpty()
        set(value) {
            config.edit().putString(KEY_MAPBOX_TOKEN, value).apply()
        }

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

    /**
     * Returns the source URL previously cached for the per-place [fileName], or null. Lets the
     * repository skip re-downloading when the same place resolves to the same image again.
     */
    fun cachedUrlFor(fileName: String): String? = cacheUrlMap().optString(fileName).ifBlank { null }

    /** Records that [fileName] was downloaded from [url] (per-place cache bookkeeping). */
    fun setCachedUrl(fileName: String, url: String) {
        val map = cacheUrlMap()
        map.put(fileName, url)
        config.edit().putString(KEY_CACHE_URLS, map.toString()).apply()
    }

    private fun cacheUrlMap(): JSONObject = try {
        config.getString(KEY_CACHE_URLS, null)?.let { JSONObject(it) } ?: JSONObject()
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

    companion object {
        private const val SP_NAME = "live_wallpaper_photo"
        private const val KEY_ENABLED = "photo_background_enabled"
        private const val KEY_BG_SOURCE = "background_source"
        private const val KEY_UNSPLASH_KEY = "unsplash_access_key"
        private const val KEY_MAPBOX_TOKEN = "mapbox_access_token"
        private const val KEY_CACHED_PATH = "cached_photo_path"
        private const val KEY_CACHED_URL = "cached_photo_url"
        private const val KEY_CACHED_ATTRIBUTION = "cached_photo_attribution"
        private const val KEY_CACHE_URLS = "cache_urls"
        private const val KEY_LOCATION_DATA = "location_data"

        const val SOURCE_MAPBOX = "mapbox"
        const val SOURCE_UNSPLASH = "unsplash"
        const val SOURCE_WIKIMEDIA = "wikimedia"

        /** File name used for the cached background bitmap inside the app files dir. */
        const val CACHE_FILE_NAME = "wallpaper_location_photo.jpg"
    }
}
