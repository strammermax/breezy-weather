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

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.getSystemService
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.common.bus.EventBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import livewallpaperweather.data.wallpaper.WallpaperPhotoRecord
import livewallpaperweather.data.wallpaper.WallpaperPhotoRepository
import livewallpaperweather.domain.location.model.Location
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class WallpaperCacheStats(
    val photoCount: Int,
    val totalBytes: Long,
)

data class CameraUploadResult(
    val file: File,
    val processedUrl: String,
    val location: String?,
    val depthPath: String? = null,
)

enum class CheckForNewPhotosResult {
    FOUND,
    NONE_FOUND,
    REQUEST_FAILED,
}

/** [EventBus] marker posted whenever a photo is manually activated as the live wallpaper
 *  background outside the normal rotation (see [WallpaperRepository.activateCameraPhoto]) --
 *  lets the already-running [com.liveweatherwallpaperapp.wallpaper.MaterialLiveWallpaperService]
 *  redraw immediately instead of waiting for its next visibility toggle or per-frame self-heal. */
class WallpaperPhotoActivatedMessage

/**
 * The brains of the location-to-image matching for the live wallpaper background.
 *
 * Given the current coordinates (and, when available, the resolved place name) it decides
 * which image best represents that place, downloads it and caches it locally so the
 * [com.liveweatherwallpaperapp.wallpaper.MaterialLiveWallpaperService] can draw it without doing any
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
@Singleton
class WallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoCatalog: WallpaperPhotoRepository,
) {
    private val store: WallpaperImageStore = WallpaperImageStore(context)
    private val client: OkHttpClient = defaultClient(store)

    // In-memory cache for the two hot decode paths (loadCachedBitmap/loadCachedDepthBitmap):
    // both were re-decoding from disk on every call (e.g. every wallpaper redraw-drawable
    // rebuild, every Details/Radar screen open) even when the underlying file hadn't changed
    // since the last decode. Keyed on path + lastModified so a fresh photo download still
    // invalidates the entry.
    private data class CachedBitmapEntry(val path: String, val lastModified: Long, val bitmap: Bitmap)
    private val bitmapCacheLock = Any()
    private var cachedPhotoEntry: CachedBitmapEntry? = null
    private var cachedDepthEntry: CachedBitmapEntry? = null

    // Cap decoded bitmap dimensions to ~1.5x the display's longest side -- enough headroom for
    // the wallpaper's parallax overscan (PARALLAX_FG_FACTOR = 0.15, i.e. up to 1.3x width) while
    // avoiding full-resolution decodes of source photos that can be considerably larger than the
    // screen they'll ever be drawn on.
    private val maxDecodeDimension: Int by lazy {
        val metrics = context.resources.displayMetrics
        (maxOf(metrics.widthPixels, metrics.heightPixels) * 1.5f).toInt()
    }

    /** Halves per-pixel memory for the (fully opaque) background photo on devices the platform
     *  itself flags as memory-constrained -- doesn't apply to the depth map, whose grayscale
     *  pixel values are read back as exact distance data and would be corrupted by RGB_565's
     *  5/6/5-bit quantization. */
    private val isLowRamDevice: Boolean by lazy {
        context.getSystemService<ActivityManager>()?.isLowRamDevice ?: false
    }

    /**
     * Fires whenever the wallpaper photo catalog changes from something other than direct
     * user action in the currently-open screen -- currently just [purgeUrls] (an FCM push
     * can arrive while "Manage background images" is already open). Screens showing a
     * snapshot of [managedPhotos] should collect this and reload, since that list is a
     * one-time load, not an observed Flow.
     */
    private val _catalogChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val catalogChanged: SharedFlow<Unit> = _catalogChanged.asSharedFlow()

    /** Distance under which a non-containing [LocationData] still counts as a match. */
    var maxMatchDistanceKm: Double = 50.0

    /** Lazily-loaded on-device sky-segmentation model (loads its weights on first use). */
    private val skySegmenter by lazy { SkySegmenter(context) }

    /**
     * Builds the provider chain in priority order for the current configuration. The selected
     * source goes first; the keyless Wikimedia fallback is always present; unconfigured
     * (key-less) providers are filtered out.
     */
    private fun providers(excludedUrls: Set<String> = emptySet()): List<ImageSearchProvider> =
        listOf(removeSkyProvider(excludedUrls))

    suspend fun removeSkyHealthStatus(): String? = removeSkyProvider().healthStatus()

    /** See [RemoveSkyProvider.registerFcmToken]. */
    suspend fun registerFcmToken(token: String): Boolean = removeSkyProvider().registerFcmToken(token)

    /** See [RemoveSkyProvider.normalizeServiceUrl]. */
    fun normalizeServiceUrl(url: String): String = removeSkyProvider().normalizeServiceUrl(url)

    /** See [RemoveSkyProvider.fetchNearbyWeetjes]. */
    suspend fun fetchNearbyWeetjes(latitude: Double, longitude: Double, taal: String): List<RemoveSkyWeetje> =
        removeSkyProvider().fetchNearbyWeetjes(latitude, longitude, taal)

    /** See [RemoveSkyProvider.requestMoreWeetjes]. */
    suspend fun requestMoreWeetjes(
        land: String,
        locatie: String,
        latitude: Double,
        longitude: Double,
        taal: String,
    ): RequestMoreWeetjesResult? = removeSkyProvider().requestMoreWeetjes(land, locatie, latitude, longitude, taal)

    /**
     * Startup-only reconciliation: purges anything RemoveSky reports as removed near
     * (latitude, longitude) since this location's last such check -- catches a curator
     * delete/disable that happened while the app was closed (FCM push only reaches a
     * running app) and that hasn't been caught yet by the periodic since/changed poll in
     * [getSortedResultlist]/[checkForNewPhotos] (which only run on their own schedule).
     * Best-effort: silently does nothing on a failed request.
     */
    suspend fun reconcileRemovals(latitude: Double, longitude: Double, location: Location) {
        val locationKey = location.formattedId
        val since =
            store.searchSinceFor(locationKey, REMOVED_SINCE_PURPOSE) ?: return reconcileRemovalsFirstRun(locationKey)
        val (urls, checkedAt) = removeSkyProvider().fetchRemoved(latitude, longitude, since)
        if (checkedAt != null) store.setSearchSince(locationKey, REMOVED_SINCE_PURPOSE, checkedAt)
        if (urls.isNotEmpty()) purgeUrls(urls.map { normalizeServiceUrl(it) })
    }

    /** First-ever check for this location: nothing to reconcile yet, just record a
     * starting point in time so the next call has a `since` to work with. */
    private suspend fun reconcileRemovalsFirstRun(locationKey: String) {
        val checkedAt = java.time.Instant.now().toString()
        store.setSearchSince(locationKey, REMOVED_SINCE_PURPOSE, checkedAt)
    }

    private fun removeSkyProvider(excludedUrls: Set<String> = emptySet()) =
        RemoveSkyProvider(store.removeSkyBaseUrl, client, excludedUrls)

    /**
     * Resolves the most appropriate image for the given position, returning the URL plus its
     * attribution, or null when nothing usable was found.
     */
    suspend fun resolveImage(
        latitude: Double,
        longitude: Double,
        place: PlaceQuery,
        excludedUrls: Set<String> = emptySet(),
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
        for (provider in providers(excludedUrls)) {
            provider.searchImageByLocation(latitude, longitude)?.let {
                if (it.url !in excludedUrls) return it
            }
            for (term in terms) {
                provider.searchImage(term)?.let {
                    if (it.url !in excludedUrls) return it
                }
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
        location: Location,
        forceRefresh: Boolean = false,
        activate: Boolean = true,
        currentWeather: String? = null,
        isCurrentPosition: Boolean = true,
    ): File? {
        val placeKey = place.cacheFileName()
        val locationKey = location.formattedId
        synchronizePhotoCatalog()
        val tried = HashSet<String>()
        tried.addAll(photoCatalog.getDisabledSourceUrls())
        if (forceRefresh) tried.addAll(store.recentUrlsFor(placeKey))

        // buildShowlist's ladder (radius 1/2/5km, season+weather relaxed step by step) picks
        // the best-ranked candidate first; selectWallpaperPhoto's plain single-pass tiering
        // is still used standalone by tests/other callers that don't need the ladder.
        val cachedCandidate = buildShowlist(
            photoCatalog.getForLocation(locationKey).filter { photo ->
                photo.sourceUrl != null && photo.filePath?.let { File(it).isFile } == true
            },
            tried,
            latitude = latitude,
            longitude = longitude,
            currentWeather = currentWeather,
            isCurrentPosition = isCurrentPosition
        ).firstOrNull()
        if (cachedCandidate != null) {
            val cachedFile = File(cachedCandidate.filePath!!)
            cachedFile.setLastModified(System.currentTimeMillis())
            if (activate) {
                activateCatalogPhoto(cachedCandidate, cachedFile)
            }
            return cachedFile
        }

        // Try several candidate photos and keep the first that has enough sky to show the weather
        // (>= MIN_SKY_FRACTION). Photos without sky are skipped and never shown as a background.
        repeat(MAX_SKY_ATTEMPTS) {
            val result = resolveImage(latitude, longitude, place, tried) ?: return@repeat
            val url = result.url
            if (!tried.add(url)) return@repeat

            // null => download failed or the photo has too little sky => try another candidate.
            val bitmap = downloadSkyBitmap(url, result.alreadyProcessed) ?: return@repeat
            val cacheFile = cacheFile(locationKey, url)
            try {
                val written = cacheFile.outputStream().use {
                    bitmap.compress(webpCompressFormat(), BuildConfig.WEBP_QUALITY, it)
                }
                if (!written) {
                    cacheFile.delete()
                    return@repeat
                }
            } finally {
                bitmap.recycle()
            }
            cacheFile.setLastModified(System.currentTimeMillis())
            photoCatalog.upsertDownloaded(
                id = photoId(url),
                sourceUrl = url,
                locationKey = locationKey,
                locationName = place.displayName,
                filePath = cacheFile.absolutePath,
                attribution = result.attribution,
                processed = result.alreadyProcessed,
                dayPeriod = result.dayPeriod,
                country = result.country,
                season = result.season,
                exifLat = result.exifLatitude,
                exifLon = result.exifLongitude,
                source = result.source,
                providerName = result.provider,
                title = result.title,
                pageUrl = result.pageUrl,
                ownerName = result.ownerName,
                license = result.license,
                processedLocation = result.processedLocation,
                status = result.status,
                sourceLocation = result.location,
                capturedAt = result.capturedAt,
                description = result.description,
                resolvedCity = result.resolvedCity,
                isCity = result.isCity,
                sceneType = result.sceneType,
                weather = result.weather,
                resolvedLat = result.resolvedLatitude,
                resolvedLon = result.resolvedLongitude
            )
            store.recordRecentUrl(placeKey, url)
            if (activate) {
                val depthPath = result.depthUrl?.let { downloadAndCacheDepthMap(it, locationKey, url) }
                store.activatePhoto(cacheFile.absolutePath, url, result.attribution, depthPath)
                photoCatalog.markShown(photoId(url))
            }
            pruneLocationCache(cacheFile.parentFile, cacheFile)
            prunePhotoCache(cacheFile)
            return cacheFile
        }
        return null
    }

    /**
     * Uploads a camera photo and caches the processed result. Only activates it immediately as
     * the live wallpaper background when [activate] is true — the single-photo camera flow
     * passes false and activates explicitly (via [activateCameraPhoto]) once the user confirms
     * the suitability check, so a photo the server later rejects never briefly becomes the
     * background. The gallery batch-import flow keeps the previous auto-activate behavior.
     */
    suspend fun uploadCameraPhoto(
        file: File,
        latitude: Double?,
        longitude: Double?,
        activate: Boolean = true,
        /**
         * Fallback capture timestamp for when the photo has no EXIF date - only pass this from
         * the fresh-capture flow, never from a gallery import (see [RemoveSkyProvider.uploadFile]).
         */
        capturedAt: String? = null,
        /** Tag so an in-flight upload can be aborted via [cancelCameraUpload]. */
        cancelTag: Any? = null,
    ): CameraUploadResult {
        val upload = removeSkyProvider().uploadFile(
            file,
            latitude,
            longitude,
            capturedAt = capturedAt,
            cancelTag = cancelTag
        )
        val place = PlaceQuery(city = upload.location)
        val bitmap = downloadSkyBitmap(upload.processedUrl, alreadyProcessed = true)
            ?: throw IllegalStateException("Processed RemoveSky image could not be downloaded")
        val cameraLocationKey = place.cacheFileName().substringBeforeLast('.')
        val cacheFile = cacheFile(cameraLocationKey, upload.processedUrl)
        try {
            val written = cacheFile.outputStream().use {
                bitmap.compress(webpCompressFormat(), BuildConfig.WEBP_QUALITY, it)
            }
            if (!written) {
                cacheFile.delete()
                throw IllegalStateException("Processed RemoveSky image could not be cached")
            }
        } finally {
            bitmap.recycle()
        }
        cacheFile.setLastModified(System.currentTimeMillis())
        photoCatalog.upsertDownloaded(
            id = photoId(upload.processedUrl),
            sourceUrl = upload.processedUrl,
            locationKey = cameraLocationKey,
            locationName = place.displayName,
            filePath = cacheFile.absolutePath,
            attribution = "Camera / RemoveSky",
            processed = true,
            dayPeriod = upload.dayPeriod,
            country = upload.country,
            season = upload.season,
            exifLat = upload.exifLatitude,
            exifLon = upload.exifLongitude
        )
        store.recordRecentUrl(place.cacheFileName(), upload.processedUrl)
        val depthPath = upload.depthUrl?.let { downloadAndCacheDepthMap(it, cameraLocationKey, upload.processedUrl) }
        if (activate) {
            store.activatePhoto(cacheFile.absolutePath, upload.processedUrl, "Camera / RemoveSky", depthPath)
            photoCatalog.markShown(photoId(upload.processedUrl))
        }
        pruneLocationCache(cacheFile.parentFile, cacheFile)
        prunePhotoCache(cacheFile)
        return CameraUploadResult(cacheFile, upload.processedUrl, upload.location, depthPath)
    }

    /** Aborts an in-flight [uploadCameraPhoto] call previously started with the same [cancelTag]. */
    fun cancelCameraUpload(cancelTag: Any) = removeSkyProvider().cancelTaggedCalls(cancelTag)

    /** Activates a photo previously uploaded with `activate = false` as the live wallpaper background. */
    suspend fun activateCameraPhoto(result: CameraUploadResult) {
        store.activatePhoto(result.file.absolutePath, result.processedUrl, "Camera / RemoveSky", result.depthPath)
        photoCatalog.markShown(photoId(result.processedUrl))
        EventBus.instance.with(WallpaperPhotoActivatedMessage::class.java).postValue(WallpaperPhotoActivatedMessage())
    }

    /**
     * Runs RemoveSky's suitability diagnostics against an already-uploaded photo's URL — used to
     * show the user why their own camera/gallery upload was or wasn't a good background photo.
     */
    suspend fun checkUploadedPhoto(url: String): RemoveSkyCheckOutcome = removeSkyProvider().checkImage(url)

    /**
     * Downloads [url] and returns the bitmap to cache: the sky-erased (transparent) version when
     * the model finds at least [SkySegmenter] minimum sky; the opaque image when the model is
     * unavailable; or null when the download fails or there is too little sky (caller skips it).
     */
    private suspend fun downloadSkyBitmap(
        url: String,
        alreadyProcessed: Boolean,
    ): Bitmap? = withContext(Dispatchers.Default) {
        val bytes = downloadBytes(url) ?: return@withContext null
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        // RemoveSky already erased the sky server-side: keep the transparent PNG as-is.
        if (alreadyProcessed) return@withContext source
        if (!skySegmenter.isAvailable()) return@withContext source // can't check sky; use as-is
        val erased = skySegmenter.eraseSky(source)
        source.recycle()
        erased // null => too little sky => skip this candidate
    }

    /**
     * Immediately removes every cached photo whose source URL is in [urls] -- called by
     * [RemoveSkyMessagingService] when RemoveSky pushes a curator soft-delete/disable, so
     * the app doesn't have to wait for its next [getSortedResultlist]/[checkForNewPhotos]
     * poll to stop showing it (see docs/UpdateFLow.md flow 5, child-safety).
     */
    suspend fun purgeUrls(urls: Collection<String>) = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext
        val urlSet = urls.toSet()
        synchronizePhotoCatalog()
        var matched = 0
        var activeLocationKey: String? = null
        for (photo in photoCatalog.getAll()) {
            val url = photo.sourceUrl ?: continue
            if (url !in urlSet) continue
            matched++
            if (url == store.cachedPhotoUrl) activeLocationKey = photo.locationKey
            photo.filePath?.let { path ->
                val file = File(path)
                file.delete()
                depthFileFor(file, url)?.delete()
            }
            photoCatalog.setDisabled(photo.id, true)
            photoCatalog.clearFilePath(photo.id)
        }
        android.util.Log.d("RemoveSkyMessaging", "purgeUrls: $matched/${urlSet.size} matched a cached photo")
        val activeUrl = store.cachedPhotoUrl
        if (activeUrl != null && activeUrl in urlSet) {
            store.cachedPhotoPath?.let { File(it).delete() }
            store.cachedDepthMapPath?.let { File(it).delete() }
            store.deactivatePhoto()

            // The active wallpaper photo was just purged -- don't leave the screen on just
            // the bare sky background until the next scheduled tick. Prefer an already-
            // cached replacement for the same location; fall back to an immediate
            // background refresh (fresh download) if nothing local is left.
            val replacement = activeLocationKey?.let { locationKey ->
                selectWallpaperPhoto(
                    photoCatalog.getForLocation(locationKey).filter { photo ->
                        photo.sourceUrl != null && photo.filePath?.let { File(it).isFile } == true
                    },
                    excludedUrls = urlSet
                )
            }
            if (replacement != null) {
                activateCatalogPhoto(replacement, File(replacement.filePath!!))
                android.util.Log.d("RemoveSkyMessaging", "activated cached replacement: ${replacement.id}")
            } else {
                android.util.Log.d("RemoveSkyMessaging", "no cached replacement left, triggering refresh")
                WallpaperPhotoRefreshWorker.startNow(context)
            }
        }
        store.allRecentUrls().forEach { (placeKey, recent) ->
            val kept = recent.filterNot { it in urlSet }
            if (kept != recent) store.setRecentUrls(placeKey, kept)
        }
        _catalogChanged.tryEmit(Unit)
    }

    /**
     * Wipes every wallpaper photo (cache files, depth maps, and catalog rows) for [place] --
     * call this when the user deletes that location from the weather app itself, so its
     * photos don't linger orphaned in the cache/database forever.
     */
    suspend fun clearLocation(place: PlaceQuery, location: Location) = withContext(Dispatchers.IO) {
        val placeKey = place.cacheFileName()
        val locationKey = location.formattedId

        val activeUrl = store.cachedPhotoUrl
        val wasActive = activeUrl != null && photoCatalog.getForLocation(locationKey).any { it.sourceUrl == activeUrl }
        if (wasActive) {
            store.cachedPhotoPath?.let { File(it).delete() }
            store.cachedDepthMapPath?.let { File(it).delete() }
            store.deactivatePhoto()
        }

        File(photoCacheDir(), locationKey).deleteRecursively()
        photoCatalog.deleteForLocation(locationKey)
        store.setRecentUrls(placeKey, emptyList())
    }

    /**
     * Checks RemoveSky's *full* enabled-photo list for [place] (see [RemoveSkyProvider
     * .fetchEnabledPhotos]) against every URL already known locally for that location — whether
     * currently downloaded, disabled, or merely "recently shown" — and downloads the first one
     * we don't have yet, without activating it as the live background.
     *
     * This deliberately does not reuse [refreshFor]: that function's `cachedCandidate` shortcut
     * returns an already-downloaded-but-not-recently-shown photo before ever asking the server,
     * so it can't tell "nothing new" apart from "we already have it but haven't shown it lately."
     */
    suspend fun checkForNewPhotos(
        latitude: Double,
        longitude: Double,
        place: PlaceQuery,
        location: Location,
    ): CheckForNewPhotosResult {
        val locationKey = location.formattedId
        synchronizePhotoCatalog()

        val since = store.searchSinceFor(locationKey, CHECK_NEW_SINCE_PURPOSE)
        val enabledPhotos = when (val result = removeSkyProvider().fetchEnabledPhotos(latitude, longitude, since)) {
            is EnabledPhotosResult.Failed -> return CheckForNewPhotosResult.REQUEST_FAILED
            is EnabledPhotosResult.Unchanged -> {
                result.checkedAt?.let { store.setSearchSince(locationKey, CHECK_NEW_SINCE_PURPOSE, it) }
                return CheckForNewPhotosResult.NONE_FOUND
            }
            is EnabledPhotosResult.Success -> {
                result.checkedAt?.let { store.setSearchSince(locationKey, CHECK_NEW_SINCE_PURPOSE, it) }
                result.photos
            }
        }
        val known = photoCatalog.getForLocation(locationKey).mapNotNullTo(HashSet()) { it.sourceUrl }
        val newPhoto = enabledPhotos.firstOrNull { it.url !in known } ?: return CheckForNewPhotosResult.NONE_FOUND

        val bitmap = downloadSkyBitmap(newPhoto.url, alreadyProcessed = true)
            ?: return CheckForNewPhotosResult.REQUEST_FAILED
        val cacheFile = cacheFile(locationKey, newPhoto.url)
        try {
            val written = cacheFile.outputStream().use {
                bitmap.compress(webpCompressFormat(), BuildConfig.WEBP_QUALITY, it)
            }
            if (!written) {
                cacheFile.delete()
                return CheckForNewPhotosResult.REQUEST_FAILED
            }
        } finally {
            bitmap.recycle()
        }
        cacheFile.setLastModified(System.currentTimeMillis())
        photoCatalog.upsertDownloaded(
            id = photoId(newPhoto.url),
            sourceUrl = newPhoto.url,
            locationKey = locationKey,
            locationName = place.displayName,
            filePath = cacheFile.absolutePath,
            attribution = newPhoto.attribution,
            processed = true,
            dayPeriod = newPhoto.dayPeriod,
            country = newPhoto.country,
            season = newPhoto.season,
            exifLat = newPhoto.exifLatitude,
            exifLon = newPhoto.exifLongitude,
            source = newPhoto.source,
            providerName = newPhoto.provider,
            title = newPhoto.title,
            status = newPhoto.status,
            sourceLocation = newPhoto.location,
            capturedAt = newPhoto.capturedAt,
            description = newPhoto.description,
            resolvedCity = newPhoto.resolvedCity,
            isCity = newPhoto.isCity,
            sceneType = newPhoto.sceneType,
            weather = newPhoto.weather,
            resolvedLat = newPhoto.resolvedLatitude,
            resolvedLon = newPhoto.resolvedLongitude
        )
        pruneLocationCache(cacheFile.parentFile, cacheFile)
        prunePhotoCache(cacheFile)
        return CheckForNewPhotosResult.FOUND
    }

    /** Loads the cached background as a [Bitmap], or null when nothing is cached. */
    fun loadCachedBitmap(): Bitmap? = loadCachedBitmapCached(store.cachedPhotoPath, isDepth = false)

    /**
     * Loads the cached depth map as a grayscale [Bitmap], or null when not available.
     * Pixel value 255 = nearest to camera, 0 = furthest (matches server-side convention).
     */
    fun loadCachedDepthBitmap(): Bitmap? = loadCachedBitmapCached(store.cachedDepthMapPath, isDepth = true)

    private fun loadCachedBitmapCached(path: String?, isDepth: Boolean): Bitmap? {
        if (path == null) return null
        val file = File(path)
        if (!file.exists()) return null
        val lastModified = file.lastModified()

        synchronized(bitmapCacheLock) {
            val entry = if (isDepth) cachedDepthEntry else cachedPhotoEntry
            if (entry != null && entry.path == path && entry.lastModified == lastModified && !entry.bitmap.isRecycled) {
                return entry.bitmap
            }
        }

        val decoded = try {
            BitmapFactory.decodeFile(file.absolutePath, decodeOptionsFor(file, isDepth))
        } catch (e: Throwable) {
            null
        } ?: return null

        synchronized(bitmapCacheLock) {
            val newEntry = CachedBitmapEntry(path, lastModified, decoded)
            if (isDepth) cachedDepthEntry = newEntry else cachedPhotoEntry = newEntry
        }
        return decoded
    }

    /** Picks an `inSampleSize` (power-of-2 downscale) so decoded bitmaps never exceed
     *  [maxDecodeDimension] on their longest side -- a no-op (sampleSize=1) for photos
     *  already at or below that size, which covers the common case. Also drops the
     *  background photo (never the depth map, see [isLowRamDevice]) to RGB_565 on
     *  low-RAM devices, halving its in-memory footprint. */
    private fun decodeOptionsFor(file: File, isDepth: Boolean): BitmapFactory.Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxDecodeDimension ||
            bounds.outHeight / (sampleSize * 2) >= maxDecodeDimension
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            if (!isDepth && isLowRamDevice) inPreferredConfig = Bitmap.Config.RGB_565
        }
    }

    private suspend fun downloadAndCacheDepthMap(depthUrl: String, locationKey: String, photoUrl: String): String? =
        withContext(Dispatchers.Default) {
            try {
                val bytes = downloadBytes(depthUrl) ?: return@withContext null
                val file = depthCacheFile(locationKey, photoUrl)
                file.outputStream().use { it.write(bytes) }
                file.absolutePath
            } catch (e: Throwable) {
                null
            }
        }

    private fun depthCacheFile(locationKey: String, url: String): File {
        val locationDirectory = File(photoCacheDir(), locationKey).apply { mkdirs() }
        return depthFileInDir(locationDirectory, url)
    }

    /** Same naming convention as [depthCacheFile], keyed off an already-cached photo file's
     * own parent directory rather than re-deriving it from a [PlaceQuery]. */
    private fun depthFileFor(photoFile: File, url: String): File? =
        photoFile.parentFile?.let { depthFileInDir(it, url) }

    private fun depthFileInDir(directory: File, url: String): File {
        val ext = if (url.endsWith(".webp", ignoreCase = true)) "webp" else "png"
        return File(directory, "${url.sha256Prefix()}_depth.$ext")
    }

    fun hasCachedPhoto(): Boolean {
        val path = store.cachedPhotoPath ?: return false
        return File(path).exists()
    }

    fun cacheStats(): WallpaperCacheStats {
        val files = photoCacheDir().walkTopDown().filter(File::isFile).toList()
        return WallpaperCacheStats(
            photoCount = files.size,
            totalBytes = files.sumOf(File::length)
        )
    }

    suspend fun managedPhotos(locationKey: String? = null): List<WallpaperPhotoRecord> {
        synchronizePhotoCatalog()
        return if (locationKey == null) photoCatalog.getAll() else photoCatalog.getForLocation(locationKey)
    }

    suspend fun setPhotoRating(id: String, rating: Int) {
        photoCatalog.setRating(id, rating)
    }

    suspend fun setPhotoDisabled(id: String, disabled: Boolean): Boolean {
        val photo = photoCatalog.getById(id) ?: return false
        if (disabled) {
            photo.filePath?.let { File(it).delete() }
            if (store.cachedPhotoPath == photo.filePath || store.cachedPhotoUrl == photo.sourceUrl) {
                store.deactivatePhoto()
            }
            photoCatalog.setDisabled(id, true)
            return true
        }

        photoCatalog.setDisabled(id, false)
        if (photo.filePath?.let { File(it).isFile } == true) return true
        val url = photo.sourceUrl ?: run {
            photoCatalog.setDisabled(id, true)
            return false
        }
        val bitmap = downloadSkyBitmap(url, photo.processed) ?: run {
            photoCatalog.setDisabled(id, true)
            return false
        }
        val locationDirectory = File(photoCacheDir(), photo.locationKey).apply { mkdirs() }
        val cacheFile = File(locationDirectory, "${url.sha256Prefix()}.webp")
        try {
            val written = cacheFile.outputStream().use {
                bitmap.compress(webpCompressFormat(), BuildConfig.WEBP_QUALITY, it)
            }
            if (!written) {
                cacheFile.delete()
                photoCatalog.setDisabled(id, true)
                return false
            }
        } finally {
            bitmap.recycle()
        }
        cacheFile.setLastModified(System.currentTimeMillis())
        photoCatalog.upsertDownloaded(
            id = photo.id,
            sourceUrl = url,
            locationKey = photo.locationKey,
            locationName = photo.locationName,
            filePath = cacheFile.absolutePath,
            attribution = photo.attribution,
            processed = photo.processed
        )
        return true
    }

    suspend fun synchronizePhotoCatalog() {
        val existing = photoCatalog.getAll()

        // One-time cleanup: earlier versions of the loop at the bottom of this function
        // registered depth-map sidecar files ("<hash>_depth.webp|png") as their own bogus
        // "unknown local file" catalog rows (source_url = local-file://.../_depth.webp),
        // which then surfaced in Manage/Preview as a raw depth-map "photo". Purge any that
        // already exist; the check further down now prevents new ones.
        existing.filter { photo ->
            // Match on sourceUrl too, not just filePath: a row whose file was already
            // cleaned up separately (e.g. by the "clear stale filePath" step just below,
            // or by an earlier pruneLocationCache pass) still has this tell-tale
            // "local-file://.../<hash>_depth.<ext>" sourceUrl even with filePath null.
            val name = photo.filePath?.let { File(it).name } ?: photo.sourceUrl?.substringAfterLast('/')
            name?.substringBeforeLast('.')?.endsWith("_depth") == true
        }.forEach { photoCatalog.deleteById(it.id) }

        existing.filter { photo ->
            photo.filePath?.let { path -> !File(path).isFile } == true
        }.forEach {
            photoCatalog.clearFilePath(it.id)
        }

        // Backfill rows that were imported (e.g. by an older version of this function) without a
        // sourceUrl: refreshFor()'s cache lookup requires one (it's how a cached photo gets
        // re-activated and recorded as "recently shown"), so without it these are downloaded once,
        // catalogued, and then permanently skipped — stuck at "0 keer getoond" forever.
        existing.filter { it.sourceUrl == null && it.filePath?.let(::File)?.isFile == true }
            .forEach { photo ->
                val filePath = photo.filePath ?: return@forEach
                photoCatalog.upsertDownloaded(
                    id = photo.id,
                    sourceUrl = localFileUrl(filePath),
                    locationKey = photo.locationKey,
                    locationName = photo.locationName,
                    filePath = filePath,
                    attribution = photo.attribution,
                    processed = photo.processed
                )
            }

        val knownPaths = existing.mapNotNull { it.filePath }.toSet()
        val urlsByPath = buildMap {
            store.allRecentUrls().forEach { (placeKey, urls) ->
                val directory = File(photoCacheDir(), placeKey.substringBeforeLast('.'))
                urls.forEach { url ->
                    put(File(directory, "${url.sha256Prefix()}.webp").absolutePath, url)
                }
            }
            val activePath = store.cachedPhotoPath
            val activeUrl = store.cachedPhotoUrl
            if (activePath != null && activeUrl != null) put(activePath, activeUrl)
        }
        photoCacheDir().walkTopDown().filter(File::isFile).forEach { file ->
            if (file.absolutePath in knownPaths) return@forEach
            // Depth-map sidecar files (see depthCacheFile/depthFileFor: "<hash>_depth.webp|png",
            // stored next to their photo, never inserted as their own wallpaper_photos row) are
            // not a separate photo -- without this check they get re-registered here as a bogus
            // "unknown local file" catalog entry (source_url = local-file://.../_depth.webp),
            // which then shows up in Manage/Preview screens as a raw depth-map "photo".
            if (file.name.substringBeforeLast('.').endsWith("_depth")) return@forEach
            val locationKey = file.parentFile?.name ?: "wallpaper_location"
            val locationName = locationKey.removePrefix("wallpaper_").replace('_', ' ')
            val sourceUrl = urlsByPath[file.absolutePath] ?: localFileUrl(file.absolutePath)
            photoCatalog.upsertDownloaded(
                id = photoId(sourceUrl),
                sourceUrl = sourceUrl,
                locationKey = locationKey,
                locationName = locationName,
                filePath = file.absolutePath,
                attribution = if (file.absolutePath == store.cachedPhotoPath) {
                    store.cachedPhotoAttribution
                } else {
                    null
                },
                processed = true,
                now = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
            )
        }
    }

    /** Stable pseudo-URL for a cached file with no recorded source, so it remains selectable. */
    private fun localFileUrl(path: String): String = "local-file://$path"

    private suspend fun activateCatalogPhoto(photo: WallpaperPhotoRecord, file: File) {
        val url = photo.sourceUrl ?: return
        store.activatePhoto(file.absolutePath, url, photo.attribution)
        store.recordRecentUrl("${photo.locationKey}.jpg", url)
        photoCatalog.markShown(photo.id)
    }

    /** Clears all downloaded wallpaper photos, their catalog rows and recent-photo history. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        photoCacheDir().deleteRecursively()
        store.deactivatePhoto()
        store.allRecentUrls().keys.forEach { store.setRecentUrls(it, emptyList()) }
        photoCatalog.deleteAll()
        _catalogChanged.tryEmit(Unit)
    }

    // -------------------------------------------------------------------------------------
    // docs/ACT-021: unified sort pipeline (replaces buildShowlist/selectWallpaperPhoto).
    //
    // In-memory only (per §10.0's "restart just starts the rotation over" note) -- keyed by
    // Location.formattedId, per §10.0a, so multiple configured locations never share state.
    // -------------------------------------------------------------------------------------
    private val currentSortedResultlist = mutableMapOf<String, List<WallpaperPhotoRecord>>()
    private val rotationIndex = mutableMapOf<String, Int>()

    /**
     * `GetsortedResultlist()` from docs/ACT-021 section 10.0. Syncs [location] against
     * RemoveSky -- GPS-based ([RemoveSkyProvider.getImagesDataByGPS]/`updateImagesDataByGPS`)
     * for a tracked current position, city-based (`getImagesDataByCity`/`updateImagesDataByCity`)
     * for a fixed or fictional location -- then rebuilds the sorted list only when the sync
     * actually turned up something new. Returns the last known-good list unchanged otherwise
     * (a `{changed:false}` response, or a fresh Resultlist that doesn't clear [minimal]).
     *
     * Does **not** itself gate on "is it too soon since the last check for this location" --
     * that's the caller's job (the existing [WallpaperPhotoRefreshWorker] already has this via
     * `WallpaperPhotoRefreshPlanner.needsRefresh`/`photoRefreshedAtFor`); this function assumes
     * it's only called when the caller has already decided a sync is due.
     *
     * [currentLatitude]/[currentLongitude] are the device's real GPS position -- only read for
     * a fictional [location] (`Werklocation`, docs/ACT-021 section 7a); ignored otherwise.
     */
    suspend fun getSortedResultlist(
        location: Location,
        latitude: Double,
        longitude: Double,
        place: PlaceQuery,
        currentLatitude: Double,
        currentLongitude: Double,
        currentWeather: String? = null,
    ): List<WallpaperPhotoRecord> = withContext(Dispatchers.IO) {
        val locationKey = location.formattedId
        val now = java.time.Instant.now().toString()
        val isNewLocation = photoCatalog.getForLocation(locationKey).isEmpty()
        val since = store.searchSinceFor(locationKey, SORTED_RESULTLIST_SINCE_PURPOSE)
        val provider = removeSkyProvider()

        var hasChanges = isNewLocation
        if (location.isCurrentPosition) {
            if (isNewLocation) {
                val json = provider.getImagesDataByGPS(now, latitude, longitude, SYNC_RANGE_KM)
                upsertDataDB(photoCatalog, locationKey, json)
                recordSince(locationKey, json)
            } else if (since != null) {
                val result = provider.updateImagesDataByGPS(now, latitude, longitude, since, SYNC_RANGE_KM)
                upsertDataDB(photoCatalog, locationKey, result.upserted)
                deleteRecordsDataDB(photoCatalog, result.removed)
                hasChanges = result.upserted?.let { it.has("changed") && !it.optBoolean("changed", true) } != true
                recordSince(locationKey, result.upserted ?: result.removed)
            }
        } else {
            if (isNewLocation) {
                val json = provider.getImagesDataByCity(now, latitude, longitude, place.city)
                upsertDataDB(photoCatalog, locationKey, json)
                recordSince(locationKey, json)
            } else if (since != null) {
                val result = provider.updateImagesDataByCity(now, latitude, longitude, since, place.city)
                upsertDataDB(photoCatalog, locationKey, result.upserted)
                deleteRecordsDataDB(photoCatalog, result.removed)
                hasChanges = result.upserted?.let { it.has("changed") && !it.optBoolean("changed", true) } != true
                recordSince(locationKey, result.upserted ?: result.removed)
            }
        }

        if (!hasChanges) return@withContext currentSortedResultlist[locationKey].orEmpty()

        val minimal = store.minimalLocationRecs
        val records = photoCatalog.getForLocation(locationKey)
        val resultlist = if (location.isCurrentPosition) {
            sortLocationRecsByGPSLocation(
                records,
                latitude,
                longitude,
                minimal,
                store.maxCachedPhotosPerLocation,
                currentWeather
            )
        } else {
            sortLocationRecsByLocation(
                records,
                minimal,
                location.isFictional,
                locationLatitude = latitude,
                locationLongitude = longitude,
                currentLatitude = currentLatitude,
                currentLongitude = currentLongitude,
                currentWeather = currentWeather
            )
        }

        // "Nee, te weinig, doe niks": keep the existing list rather than replace it with one
        // that doesn't even clear the minimal bar.
        if (resultlist.size < minimal) return@withContext currentSortedResultlist[locationKey].orEmpty()

        val sorted = sortByRecencyViewsDistance(resultlist, latitude, longitude)
        downloadMissingImages(sorted)
        currentSortedResultlist[locationKey] = sorted
        sorted
    }

    private fun recordSince(locationKey: String, json: JSONObject?) {
        json?.optString("checked_at")?.ifBlank { null }
            ?.let { store.setSearchSince(locationKey, SORTED_RESULTLIST_SINCE_PURPOSE, it) }
    }

    /**
     * `downloadMissingImages` from docs/ACT-021 section 10.1: downloads (and caches to disk)
     * up to [maxCount] of [sortedRecords]' entries that have no local file yet
     * ([WallpaperPhotoRecord.filePath] `== null`), in [sortedRecords]' own order (most
     * important first) -- the rest of the list stays as metadata-only rows, ready to be
     * downloaded on a later call once cache space frees up. Immediately runs
     * [pruneLocationCache] afterwards so this location's cache never exceeds
     * [WallpaperImageStore.maxCachedPhotosPerLocation] (oldest-downloaded evicted first).
     */
    suspend fun downloadMissingImages(
        sortedRecords: List<WallpaperPhotoRecord>,
        maxCount: Int = store.maxCachedPhotosPerLocation,
    ) = withContext(Dispatchers.IO) {
        val missing = sortedRecords.filter { it.filePath == null }.take(maxCount)
        for (record in missing) {
            downloadOneMissingImage(record)
        }
        val locationKey = sortedRecords.firstOrNull()?.locationKey ?: return@withContext
        val directory = File(photoCacheDir(), locationKey)
        pruneLocationCache(directory, store.cachedPhotoPath?.let(::File))
    }

    /** Downloads and caches a single [record] (used by [downloadMissingImages]'s batch pass,
     * and by [activateRotationItem] when the rotation lands on an item that batch hasn't
     * reached yet). Returns the cached file, or null on any failure (no source URL, download
     * error, decode failure, or a failed write) -- the existing [WallpaperPhotoRecord.filePath]
     * (if any) is left untouched in that case. */
    private suspend fun downloadOneMissingImage(record: WallpaperPhotoRecord): File? {
        val url = record.sourceUrl ?: return null
        val bitmap = downloadSkyBitmap(url, alreadyProcessed = true) ?: return null
        val file = cacheFile(record.locationKey, url)
        try {
            val written = file.outputStream().use {
                bitmap.compress(webpCompressFormat(), BuildConfig.WEBP_QUALITY, it)
            }
            if (!written) {
                file.delete()
                return null
            }
        } finally {
            bitmap.recycle()
        }
        file.setLastModified(System.currentTimeMillis())
        photoCatalog.upsertDownloaded(
            id = record.id,
            sourceUrl = url,
            locationKey = record.locationKey,
            locationName = record.locationName,
            filePath = file.absolutePath,
            attribution = record.attribution,
            processed = true
        )
        return file
    }

    /**
     * `Is item banned by user?` from docs/ACT-021 section 10.3 -- distinct from the
     * curator-driven [WallpaperPhotoRecord.disabled] (see [WallpaperPhotoRecord.userBanned]).
     */
    fun isBannedByUser(record: WallpaperPhotoRecord): Boolean = record.userBanned

    /** Current in-memory sorted list for [locationKey] (docs/ACT-021 section 10.0/10.2's
     * `_currentSortedResultlist`) without triggering a sync -- read this *before* calling
     * [getSortedResultlist] to snapshot "vorige" for the rotation loop's changed-check
     * (see [WallpaperPhotoRefreshWorker]). */
    fun currentSortedResultlistFor(locationKey: String): List<WallpaperPhotoRecord> =
        currentSortedResultlist[locationKey].orEmpty()

    /**
     * Makes [record] the active wallpaper photo: downloads it first if the rotation landed on
     * an entry [downloadMissingImages] hasn't reached yet (e.g. the list grew, or the rotation
     * index moved past the initially-downloaded batch), then writes it into
     * [WallpaperImageStore.cachedPhotoPath]/`cachedPhotoUrl` via the existing
     * [activateCatalogPhoto] -- the same store fields
     * [com.liveweatherwallpaperapp.wallpaper.MaterialLiveWallpaperService] already reads, so
     * the renderer needs no changes of its own (docs/ACT-021 section 10.4). Also this is what
     * increments [WallpaperPhotoRecord.viewCount] (via [WallpaperPhotoRepository.markShown]),
     * feeding back into the next [sortByRecencyViewsDistance] pass. Returns the activated file,
     * or null if no file exists and none could be downloaded.
     */
    suspend fun activateRotationItem(record: WallpaperPhotoRecord): File? =
        withContext(Dispatchers.IO) {
            val file = record.filePath?.let(::File)?.takeIf { it.exists() }
                ?: downloadOneMissingImage(record)
                ?: return@withContext null
            activateCatalogPhoto(record, file)
            file
        }

    /** `Get first sortedResultlist item` from docs/ACT-021 section 10.2: resets [locationKey]'s
     * rotation position to the top of its current sorted list. Null if that list is empty. */
    fun getFirstSortedResultlistItem(locationKey: String): WallpaperPhotoRecord? {
        rotationIndex[locationKey] = 0
        return currentSortedResultlist[locationKey]?.firstOrNull()
    }

    /** `Get next sortedResultlist item` from docs/ACT-021 section 10.2: advances [locationKey]'s
     * rotation position by one, wrapping back to the start at the end of the list. Null if
     * that list is empty. */
    fun getNextSortedResultlistItem(locationKey: String): WallpaperPhotoRecord? {
        val list = currentSortedResultlist[locationKey]
        if (list.isNullOrEmpty()) return null
        val next = ((rotationIndex[locationKey] ?: -1) + 1).mod(list.size)
        rotationIndex[locationKey] = next
        return list[next]
    }

    fun enforceCacheLimit() {
        val activeFile = store.cachedPhotoPath?.let(::File)
        photoCacheDir().listFiles()?.filter(File::isDirectory)?.forEach { directory ->
            pruneLocationCache(directory, activeFile)
        }
        prunePhotoCache(activeFile)
    }

    private fun cacheFile(locationKey: String, url: String): File {
        val locationDirectory = File(photoCacheDir(), locationKey).apply { mkdirs() }
        return File(locationDirectory, "${url.sha256Prefix()}.webp")
    }

    @Suppress("DEPRECATION")
    private fun webpCompressFormat(): Bitmap.CompressFormat =
        if (BuildConfig.WEBP_LOSSLESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun photoCacheDir(): File = File(context.filesDir, PHOTO_CACHE_DIR).apply { mkdirs() }

    private fun pruneLocationCache(directory: File?, activeFile: File?) {
        if (directory == null) return
        val overflow = directory.listFiles()
            ?.filter { it.isFile && it != activeFile }
            ?.sortedBy(File::lastModified)
            ?.dropLast(
                (store.maxCachedPhotosPerLocation - if (activeFile?.parentFile == directory) 1 else 0)
                    .coerceAtLeast(0)
            )
            .orEmpty()
        overflow.forEach(File::delete)
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    private fun prunePhotoCache(activeFile: File?) {
        val limitBytes = store.photoCacheLimitMb.toLong() * BYTES_PER_MB
        val files = photoCacheDir().walkTopDown().filter(File::isFile).toList()
        var totalBytes = files.sumOf(File::length)
        for (file in files.filterNot { it == activeFile }.sortedBy(File::lastModified)) {
            if (totalBytes <= limitBytes) break
            val length = file.length()
            if (file.delete()) totalBytes -= length
        }
    }

    private fun String.sha256Prefix(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { "%02x".format(it) }

    private fun photoId(url: String): String = "url:" + MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

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
                response.body.bytes()
            }
        } catch (e: Throwable) {
            android.util.Log.w("LWWPhoto", "download error for $url", e)
            null
        }
    }

    companion object {
        /** How many candidate photos to try before giving up on finding one with enough sky. */
        private const val MAX_SKY_ATTEMPTS = 10
        private const val PHOTO_CACHE_DIR = "wallpaper_photo_cache"

        // Separate `since` namespaces per caller -- see WallpaperImageStore.searchSinceFor's
        // kdoc for why these must not share one value.
        private const val CHECK_NEW_SINCE_PURPOSE = "checkNew"
        private const val REMOVED_SINCE_PURPOSE = "removedReconcile"
        private const val SORTED_RESULTLIST_SINCE_PURPOSE = "sortedResultlist"

        /** Radius (km) requested from RemoveSky when syncing a current-position location (see
         * [getSortedResultlist]) -- must be at least as wide as the largest ring
         * [sortLocationRecsByGPSLocation]'s cascade considers (its "unrestricted" tier is
         * everything already synced locally, which this radius bounds). */
        private const val SYNC_RANGE_KM = 25.0
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val USER_AGENT =
            "LiveWallpaperWeather/1.0 (https://github.com/strammermax/breezy-weather; " +
                "based on Breezy Weather)"

        private fun defaultClient(store: WallpaperImageStore): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(RemoveSkyInterceptor(store))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
