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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.liveweatherwallpaperapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import livewallpaperweather.data.wallpaper.WallpaperPhotoRecord
import livewallpaperweather.data.wallpaper.WallpaperPhotoRepository
import okhttp3.OkHttpClient
import okhttp3.Request
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
     * [pruneDisabledPhotos]/[checkForNewPhotos] (which only run on their own schedule).
     * Best-effort: silently does nothing on a failed request.
     */
    suspend fun reconcileRemovals(latitude: Double, longitude: Double, place: PlaceQuery) {
        val locationKey = place.cacheFileName().substringBeforeLast('.')
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
        forceRefresh: Boolean = false,
        activate: Boolean = true,
        currentWeather: String? = null,
        isCurrentPosition: Boolean = true,
    ): File? {
        val placeKey = place.cacheFileName()
        val locationKey = placeKey.substringBeforeLast('.')
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
            val cacheFile = cacheFile(place, url)
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
                val depthPath = result.depthUrl?.let { downloadAndCacheDepthMap(it, place, url) }
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
        val cacheFile = cacheFile(place, upload.processedUrl)
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
            locationKey = place.cacheFileName().substringBeforeLast('.'),
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
        val depthPath = upload.depthUrl?.let { downloadAndCacheDepthMap(it, place, upload.processedUrl) }
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
    }

    /**
     * Runs RemoveSky's suitability diagnostics against an already-uploaded photo's URL — used to
     * show the user why their own camera/gallery upload was or wasn't a good background photo.
     */
    suspend fun checkUploadedPhoto(url: String): RemoveSkyCheckResult? = removeSkyProvider().checkImage(url)

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
     * the app doesn't have to wait for its next [pruneDisabledPhotos]/[checkForNewPhotos]
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
    suspend fun clearLocation(place: PlaceQuery) = withContext(Dispatchers.IO) {
        val placeKey = place.cacheFileName()
        val locationKey = placeKey.substringBeforeLast('.')

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
     * Removes cached photos for [place] that RemoveSky no longer reports as `enabled`.
     * Returns true when the currently active photo for this place was removed (caller should
     * trigger a forced [refreshFor] to pick a replacement).
     */
    suspend fun pruneDisabledPhotos(latitude: Double, longitude: Double, place: PlaceQuery): Boolean {
        val placeKey = place.cacheFileName()
        val locationKey = placeKey.substringBeforeLast('.')
        val since = store.searchSinceFor(locationKey, PRUNE_SINCE_PURPOSE)
        val enabledPhotos = when (val result = removeSkyProvider().fetchEnabledPhotos(latitude, longitude, since)) {
            is EnabledPhotosResult.Failed -> return false
            is EnabledPhotosResult.Unchanged -> {
                result.checkedAt?.let { store.setSearchSince(locationKey, PRUNE_SINCE_PURPOSE, it) }
                return false
            }
            is EnabledPhotosResult.Success -> {
                result.checkedAt?.let { store.setSearchSince(locationKey, PRUNE_SINCE_PURPOSE, it) }
                result.photos
            }
        }
        val enabled = enabledPhotos.mapTo(HashSet()) { it.url }

        // 1. Trim recent-URL history to only still-enabled URLs.
        val recent = store.recentUrlsFor(placeKey)
        val keptRecent = recent.filter { it in enabled }
        if (keptRecent != recent) store.setRecentUrls(placeKey, keptRecent)

        // 2. Delete cached files whose URL is no longer enabled.
        val locationDir = File(photoCacheDir(), placeKey.substringBeforeLast('.'))
        val activeFile = store.cachedPhotoPath?.let(::File)
        val allowedNames = enabled.map { "${it.sha256Prefix()}.webp" }.toSet()
        locationDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in allowedNames && file != activeFile) {
                file.delete()
            }
        }

        // 3. If the active photo for this place is no longer enabled, deactivate it.
        val activeUrl = store.cachedPhotoUrl
        if (activeUrl != null && activeUrl !in enabled && activeFile?.parentFile == locationDir) {
            activeFile.delete()
            store.cachedPhotoPath = null
            store.cachedPhotoUrl = null
            store.cachedPhotoAttribution = null
            return true
        }
        return false
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
    suspend fun checkForNewPhotos(latitude: Double, longitude: Double, place: PlaceQuery): CheckForNewPhotosResult {
        val placeKey = place.cacheFileName()
        val locationKey = placeKey.substringBeforeLast('.')
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
        val cacheFile = cacheFile(place, newPhoto.url)
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

    /**
     * Downloads up to [maxToDownload] of the best-ranked not-yet-cached candidates for [place]
     * (per [buildShowlist]'s radius/season/weather ladder, ranked together with what's already
     * cached so a genuinely better new candidate can still outrank a mediocre cached one)
     * without activating any of them -- warms the cache ahead of time so the next
     * [refreshFor] pick doesn't need an on-demand download. See docs/UpdateFLow.md flow 2/3
     * step 3.0. Returns how many were actually downloaded (0 on a failed request too --
     * best-effort, same as [pruneDisabledPhotos]/[checkForNewPhotos]).
     */
    suspend fun prefetchShowlist(
        latitude: Double,
        longitude: Double,
        place: PlaceQuery,
        currentWeather: String? = null,
        isCurrentPosition: Boolean = true,
        maxToDownload: Int = 4,
    ): Int {
        val placeKey = place.cacheFileName()
        val locationKey = placeKey.substringBeforeLast('.')
        synchronizePhotoCatalog()

        val since = store.searchSinceFor(locationKey, PREFETCH_SINCE_PURPOSE)
        val enabledPhotos = when (val result = removeSkyProvider().fetchEnabledPhotos(latitude, longitude, since)) {
            is EnabledPhotosResult.Failed -> return 0
            is EnabledPhotosResult.Unchanged -> {
                result.checkedAt?.let { store.setSearchSince(locationKey, PREFETCH_SINCE_PURPOSE, it) }
                return 0
            }
            is EnabledPhotosResult.Success -> {
                result.checkedAt?.let { store.setSearchSince(locationKey, PREFETCH_SINCE_PURPOSE, it) }
                result.photos
            }
        }

        val cachedRecords = photoCatalog.getForLocation(locationKey)
        val knownUrls = cachedRecords.mapNotNullTo(HashSet()) { it.sourceUrl }
        val now = System.currentTimeMillis()
        val uncachedByUrl = enabledPhotos.filter { it.url !in knownUrls }.associateBy { it.url }
        // Placeholder rows (filePath = null) just so buildShowlist can rank not-yet-downloaded
        // candidates alongside already-cached ones on equal footing -- never persisted as-is.
        val placeholders = uncachedByUrl.values.map { photo ->
            WallpaperPhotoRecord(
                id = photoId(photo.url),
                sourceUrl = photo.url,
                locationKey = locationKey,
                locationName = place.displayName,
                filePath = null,
                attribution = photo.attribution,
                processed = true,
                rating = 0,
                disabled = false,
                viewCount = 0,
                createdAt = now,
                updatedAt = now,
                lastShownAt = null,
                dayPeriod = photo.dayPeriod,
                country = photo.country,
                season = photo.season,
                exifLat = photo.exifLatitude,
                exifLon = photo.exifLongitude,
                sceneType = photo.sceneType,
                weather = photo.weather,
                resolvedLat = photo.resolvedLatitude,
                resolvedLon = photo.resolvedLongitude
            )
        }

        val disabledUrls = photoCatalog.getDisabledSourceUrls()
        val showlist = buildShowlist(
            cachedRecords + placeholders,
            excludedUrls = disabledUrls,
            latitude = latitude,
            longitude = longitude,
            currentWeather = currentWeather,
            isCurrentPosition = isCurrentPosition
        )

        var downloaded = 0
        for (candidate in showlist) {
            if (downloaded >= maxToDownload) break
            if (candidate.filePath != null) continue // already cached, nothing to prefetch
            val photo = uncachedByUrl[candidate.sourceUrl] ?: continue

            val bitmap = downloadSkyBitmap(photo.url, alreadyProcessed = true) ?: continue
            val cacheFile = cacheFile(place, photo.url)
            try {
                val written = cacheFile.outputStream().use {
                    bitmap.compress(webpCompressFormat(), BuildConfig.WEBP_QUALITY, it)
                }
                if (!written) {
                    cacheFile.delete()
                    continue
                }
            } finally {
                bitmap.recycle()
            }
            cacheFile.setLastModified(System.currentTimeMillis())
            photoCatalog.upsertDownloaded(
                id = photoId(photo.url),
                sourceUrl = photo.url,
                locationKey = locationKey,
                locationName = place.displayName,
                filePath = cacheFile.absolutePath,
                attribution = photo.attribution,
                processed = true,
                dayPeriod = photo.dayPeriod,
                country = photo.country,
                season = photo.season,
                exifLat = photo.exifLatitude,
                exifLon = photo.exifLongitude,
                source = photo.source,
                providerName = photo.provider,
                title = photo.title,
                status = photo.status,
                sourceLocation = photo.location,
                capturedAt = photo.capturedAt,
                description = photo.description,
                resolvedCity = photo.resolvedCity,
                isCity = photo.isCity,
                sceneType = photo.sceneType,
                weather = photo.weather,
                resolvedLat = photo.resolvedLatitude,
                resolvedLon = photo.resolvedLongitude
            )
            pruneLocationCache(cacheFile.parentFile, cacheFile)
            prunePhotoCache(cacheFile)
            downloaded++
        }
        return downloaded
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

    /**
     * Loads the cached depth map as a grayscale [Bitmap], or null when not available.
     * Pixel value 255 = nearest to camera, 0 = furthest (matches server-side convention).
     */
    fun loadCachedDepthBitmap(): Bitmap? {
        val path = store.cachedDepthMapPath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Throwable) {
            null
        }
    }

    private suspend fun downloadAndCacheDepthMap(depthUrl: String, place: PlaceQuery, photoUrl: String): String? =
        withContext(Dispatchers.Default) {
            try {
                val bytes = downloadBytes(depthUrl) ?: return@withContext null
                val file = depthCacheFile(place, photoUrl)
                file.outputStream().use { it.write(bytes) }
                file.absolutePath
            } catch (e: Throwable) {
                null
            }
        }

    private fun depthCacheFile(place: PlaceQuery, url: String): File {
        val placeName = place.cacheFileName().substringBeforeLast('.')
        val locationDirectory = File(photoCacheDir(), placeName).apply { mkdirs() }
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

    /** Clears the active photo and all downloaded wallpaper-photo cache files. */
    fun clearCache() {
        photoCacheDir().deleteRecursively()
        store.cachedPhotoPath = null
        store.cachedPhotoUrl = null
        store.cachedPhotoAttribution = null
    }

    fun enforceCacheLimit() {
        val activeFile = store.cachedPhotoPath?.let(::File)
        photoCacheDir().listFiles()?.filter(File::isDirectory)?.forEach { directory ->
            pruneLocationCache(directory, activeFile)
        }
        prunePhotoCache(activeFile)
    }

    private fun cacheFile(place: PlaceQuery, url: String): File {
        val placeName = place.cacheFileName().substringBeforeLast('.')
        val locationDirectory = File(photoCacheDir(), placeName).apply { mkdirs() }
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
                response.body?.bytes()
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

        // Separate `since` namespaces for pruneDisabledPhotos() vs checkForNewPhotos() -- see
        // WallpaperImageStore.searchSinceFor's kdoc for why these must not share one value.
        private const val PRUNE_SINCE_PURPOSE = "prune"
        private const val CHECK_NEW_SINCE_PURPOSE = "checkNew"
        private const val REMOVED_SINCE_PURPOSE = "removedReconcile"
        private const val PREFETCH_SINCE_PURPOSE = "prefetch"
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
