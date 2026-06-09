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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The brains of the location-to-image matching for the live wallpaper background.
 *
 * Given the current coordinates (and, when available, the resolved place name) it decides
 * which image best represents that place, downloads it and caches it locally so the
 * [org.breezyweather.wallpaper.MaterialLiveWallpaperService] can draw it without doing any
 * network work on the render thread.
 *
 * Matching order:
 *  1. A manually-curated [LocationData] whose bounding box [LocationData.contains] the point.
 *  2. The nearest [LocationData] center within [maxMatchDistanceKm].
 *  3. An Unsplash search by place name (e.g. the city).
 *  4. null — caller keeps the default gradient background.
 */
class WallpaperRepository(
    private val context: Context,
    private val store: WallpaperImageStore = WallpaperImageStore(context),
    private val client: OkHttpClient = defaultClient(),
) {

    /** Distance under which a non-containing [LocationData] still counts as a match. */
    var maxMatchDistanceKm: Double = 50.0

    /**
     * Resolves the most appropriate image URL for the given position.
     *
     * @param placeName city / place name used as the Unsplash fallback query.
     */
    suspend fun resolveImageUrl(
        latitude: Double,
        longitude: Double,
        placeName: String?,
    ): String? {
        val manual = store.locationData

        manual.firstOrNull { it.contains(latitude, longitude) }?.let { return it.imageUrl }

        manual
            .map { it to it.distanceKmTo(latitude, longitude) }
            .filter { it.second <= maxMatchDistanceKm }
            .minByOrNull { it.second }
            ?.let { return it.first.imageUrl }

        // Try the user-selected provider first, then fall back to the other one.
        val mapboxToken = store.mapboxAccessToken.ifBlank { BuildConfigMapboxToken.value }
        val unsplashKey = store.unsplashAccessKey.ifBlank { BuildConfigUnsplashKey.value }

        suspend fun mapbox(): String? =
            MapboxPhotoSource.staticSatelliteUrl(latitude, longitude, mapboxToken)

        suspend fun unsplash(): String? =
            if (!placeName.isNullOrBlank() && unsplashKey.isNotBlank()) {
                UnsplashPhotoSource(unsplashKey, client).searchPhotoUrl(placeName)
            } else {
                null
            }

        return when (store.backgroundSource) {
            WallpaperImageStore.SOURCE_MAPBOX -> mapbox() ?: unsplash()
            else -> unsplash() ?: mapbox()
        }
    }

    /**
     * Resolves, downloads and caches the background image for the given position.
     *
     * Skips the download when the resolved URL already matches the cached one. Returns the
     * cached [File] on success, or null when no image could be resolved/downloaded (in which
     * case the existing cache, if any, is left untouched).
     */
    suspend fun refreshFor(
        latitude: Double,
        longitude: Double,
        placeName: String?,
    ): File? {
        val url = resolveImageUrl(latitude, longitude, placeName) ?: return null

        val cacheFile = cacheFile()
        if (url == store.cachedPhotoUrl && cacheFile.exists()) {
            return cacheFile
        }

        val downloaded = download(url, cacheFile)
        if (downloaded) {
            store.cachedPhotoUrl = url
            store.cachedPhotoPath = cacheFile.absolutePath
            return cacheFile
        }
        return null
    }

    /** Loads the cached background as a [Bitmap], or null when nothing is cached. */
    fun loadCachedBitmap(): Bitmap? {
        val path = store.cachedPhotoPath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Throwable) {
            null
        }
    }

    fun hasCachedPhoto(): Boolean {
        val path = store.cachedPhotoPath ?: return false
        return File(path).exists()
    }

    fun clearCache() {
        cacheFile().delete()
        store.cachedPhotoPath = null
        store.cachedPhotoUrl = null
    }

    private fun cacheFile(): File = File(context.filesDir, WallpaperImageStore.CACHE_FILE_NAME)

    private suspend fun download(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val bytes = response.body?.bytes() ?: return@withContext false
                // Validate it actually decodes as an image before replacing the cache.
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext false
                bmp.recycle()
                target.outputStream().use { it.write(bytes) }
                true
            }
        } catch (e: Throwable) {
            false
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Optional build-time Unsplash key. Kept in a separate object so the repository compiles
 * even before a key is wired into BuildConfig. Replace [value] (or set the key at runtime
 * via [WallpaperImageStore.unsplashAccessKey]) — see README_LIVEWALLPAPER.md.
 */
private object BuildConfigUnsplashKey {
    // Personal Unsplash access key (Client-ID). For a personal build this is fine here;
    // for sharing, move it to local.properties + a BuildConfig field instead.
    const val value: String = "BDTwXUBorbfO8ZSBOa9_M6Db2gRlZv4zekXQaLrrFT4"
}

/**
 * Optional build-time Mapbox token fallback. Set [value] here, or supply it at runtime via
 * [WallpaperImageStore.mapboxAccessToken] in the live-wallpaper settings.
 */
private object BuildConfigMapboxToken {
    const val value: String = ""
}
