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
 *  3. The configured [ImageSearchProvider]s in priority order (e.g. Unsplash → Wikimedia →
 *     Mapbox). For each provider, coordinates are tried first (geo-aware providers) then the
 *     [PlaceQuery.searchTerms] from most specific (city) to most generic (country).
 *  4. null — caller keeps the default gradient background.
 *
 * The keyless [WikimediaProvider] is always in the chain, so the feature keeps working even
 * when no API key is configured.
 */
class WallpaperRepository(
    private val context: Context,
    private val store: WallpaperImageStore = WallpaperImageStore(context),
    private val client: OkHttpClient = defaultClient(),
) {

    /** Distance under which a non-containing [LocationData] still counts as a match. */
    var maxMatchDistanceKm: Double = 50.0

    /** Lazily-loaded on-device sky-segmentation model (loads its weights on first use). */
    private val skySegmenter by lazy { SkySegmenter(context) }

    /**
     * Builds the provider chain in priority order for the current configuration. The selected
     * source goes first; the keyless Wikimedia fallback is always present; unconfigured
     * (key-less) providers are filtered out.
     */
    private fun providers(): List<ImageSearchProvider> {
        val mapboxToken = store.mapboxAccessToken.ifBlank { BuildConfigMapboxToken.value }
        val unsplashKey = store.unsplashAccessKey.ifBlank { BuildConfigUnsplashKey.value }

        val unsplash = UnsplashProvider(unsplashKey, client)
        val wikimedia = WikimediaProvider(client)
        val mapbox = MapboxProvider(mapboxToken)
        val flickr = FlickrProvider(client)

        val ordered = when (store.backgroundSource) {
            WallpaperImageStore.SOURCE_MAPBOX -> listOf(mapbox, unsplash, wikimedia, flickr)
            WallpaperImageStore.SOURCE_WIKIMEDIA -> listOf(wikimedia, unsplash, flickr, mapbox)
            WallpaperImageStore.SOURCE_FLICKR -> listOf(flickr, wikimedia, unsplash, mapbox)
            else -> listOf(unsplash, wikimedia, flickr, mapbox)
        }
        return ordered.filter { it.isConfigured() }
    }

    /**
     * Resolves the most appropriate image for the given position, returning the URL plus its
     * attribution, or null when nothing usable was found.
     */
    suspend fun resolveImage(
        latitude: Double,
        longitude: Double,
        place: PlaceQuery,
    ): ImageResult? {
        val manual = store.locationData

        manual.firstOrNull { it.contains(latitude, longitude) }
            ?.let { return ImageResult(it.imageUrl) }

        manual
            .map { it to it.distanceKmTo(latitude, longitude) }
            .filter { it.second <= maxMatchDistanceKm }
            .minByOrNull { it.second }
            ?.let { return ImageResult(it.first.imageUrl) }

        val terms = place.searchTerms()
        for (provider in providers()) {
            provider.searchImageByLocation(latitude, longitude)?.let { return it }
            for (term in terms) {
                provider.searchImage(term)?.let { return it }
            }
        }
        return null
    }

    /** Back-compatible URL-only resolution using just a single place name. */
    suspend fun resolveImageUrl(
        latitude: Double,
        longitude: Double,
        placeName: String?,
    ): String? = resolveImage(latitude, longitude, PlaceQuery(city = placeName))?.url

    /**
     * Resolves, downloads and caches the background image for the given position. Each place
     * is cached under its own file ([PlaceQuery.cacheFileName]) so revisiting is instant.
     *
     * Skips the download when the resolved URL already matches what was cached for that place.
     * Returns the cached [File] on success, or null when no image could be resolved/downloaded
     * (in which case the existing cache, if any, is left untouched).
     */
    suspend fun refreshFor(
        latitude: Double,
        longitude: Double,
        place: PlaceQuery,
    ): File? {
        val cacheFile = cacheFile(place)
        val tried = HashSet<String>()
        // Try several candidate photos and keep the first that has enough sky to show the weather
        // (>= MIN_SKY_FRACTION). Photos without sky are skipped and never shown as a background.
        repeat(MAX_SKY_ATTEMPTS) {
            val result = resolveImage(latitude, longitude, place) ?: return@repeat
            val url = result.url
            if (!tried.add(url)) return@repeat

            // Fast path: this exact image already passed the sky check and is cached for the place.
            if (url == store.cachedUrlFor(cacheFile.name) && cacheFile.exists()) {
                store.cachedPhotoPath = cacheFile.absolutePath
                store.cachedPhotoUrl = url
                store.cachedPhotoAttribution = result.attribution
                return cacheFile
            }

            // null => download failed or the photo has too little sky => try another candidate.
            val bitmap = downloadSkyBitmap(url) ?: return@repeat
            try {
                cacheFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            store.setCachedUrl(cacheFile.name, url)
            store.cachedPhotoUrl = url
            store.cachedPhotoPath = cacheFile.absolutePath
            store.cachedPhotoAttribution = result.attribution
            return cacheFile
        }
        return null
    }

    /**
     * Downloads [url] and returns the bitmap to cache: the sky-erased (transparent) version when
     * the model finds at least [SkySegmenter] minimum sky; the opaque image when the model is
     * unavailable; or null when the download fails or there is too little sky (caller skips it).
     */
    private suspend fun downloadSkyBitmap(url: String): Bitmap? = withContext(Dispatchers.Default) {
        val bytes = downloadBytes(url) ?: return@withContext null
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        if (!skySegmenter.isAvailable()) return@withContext source // can't check sky; use as-is
        val erased = skySegmenter.eraseSky(source)
        source.recycle()
        erased // null => too little sky => skip this candidate
    }

    /** Back-compatible overload taking a single place name. */
    suspend fun refreshFor(
        latitude: Double,
        longitude: Double,
        placeName: String?,
    ): File? = refreshFor(latitude, longitude, PlaceQuery(city = placeName))

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

    /** Clears the currently active cached photo (the per-place files are left on disk). */
    fun clearCache() {
        store.cachedPhotoPath?.let { File(it).delete() }
        store.cachedPhotoPath = null
        store.cachedPhotoUrl = null
        store.cachedPhotoAttribution = null
    }

    private fun cacheFile(place: PlaceQuery): File = File(context.filesDir, place.cacheFileName())

    private suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // A descriptive User-Agent is required by Wikimedia (upload.wikimedia.org returns
            // 403/429 for requests with a blocked/empty UA) and is harmless for other hosts.
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("LWWPhoto", "download HTTP ${response.code} for $url")
                    return@withContext null
                }
                response.body?.bytes()
            }
        } catch (e: Throwable) {
            android.util.Log.w("LWWPhoto", "download error for $url", e)
            null
        }
    }

    companion object {
        /** How many candidate photos to try before giving up on finding one with enough sky. */
        private const val MAX_SKY_ATTEMPTS = 5

        private const val USER_AGENT =
            "LiveWallpaperWeather/1.0 (https://github.com/strammermax/breezy-weather; " +
                "based on Breezy Weather)"

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
    // Read from BuildConfig (sourced from local.properties `lww.unsplash.key`), so the key
    // is never committed. Empty when not configured — the app still works (Mapbox/keyless
    // providers + the in-app key field remain available).
    val value: String get() = org.breezyweather.BuildConfig.UNSPLASH_KEY
}

/**
 * Optional build-time Mapbox token fallback. Set [value] here, or supply it at runtime via
 * [WallpaperImageStore.mapboxAccessToken] in the live-wallpaper settings.
 */
private object BuildConfigMapboxToken {
    const val value: String = ""
}
