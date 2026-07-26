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

import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/**
 * [ImageSearchProvider] backed by the self-hosted **RemoveSky** service
 * (https://removesky.vanburik.info).
 *
 * RemoveSky does both halves of the job server-side:
 *  - `GET /api/v1/search?source=local&location=|lat=&lon=` returns only already-processed photos
 *    from RemoveSky's local database for that place. If the local database has nothing for this
 *    location, RemoveSky itself starts a background task that searches external providers
 *    (Wikimedia Commons, Flickr, Openverse, Unsplash), validates and processes suitable
 *    candidates, so a later search finds them locally.
 *  - `POST /api/v1/upload` removes the sky from a chosen photo and returns a **transparent PNG**.
 *
 * So the [ImageResult] returned here already has its sky erased — the caller must NOT run the
 * on-device [SkySegmenter] again ([ImageResult.alreadyProcessed] is `true`). Results that
 * RemoveSky already processed are reused instantly via their `processed_url`.
 */
class RemoveSkyProvider(
    baseUrl: String,
    private val client: OkHttpClient,
    excludedUrls: Set<String> = emptySet(),
) : ImageSearchProvider {

    /** Service base URL without a trailing slash (falls back to the public instance). */
    private val base = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
    private val apiBase = if (base.endsWith(API_PATH)) base else "$base$API_PATH"
    private val excludedUrls = excludedUrls.map(::normalizeServiceUrl).toSet()

    override val id: String = "removesky"
    override val requiresApiKey: Boolean = false
    override fun isConfigured(): Boolean = base.isNotBlank()

    override suspend fun searchImage(query: String): ImageResult? {
        if (query.isBlank()) return null
        return resolve("$apiBase/search?source=local&limit=$LIMIT&location=${enc(query)}")
    }

    override suspend fun searchImageByLocation(latitude: Double, longitude: Double): ImageResult? {
        return resolve("$apiBase/search?source=local&limit=$LIMIT&lat=$latitude&lon=$longitude")
    }

    /**
     * Returns every currently `enabled` processed photo (URL plus day/country/season metadata)
     * RemoveSky knows for this location.
     *
     * Pass the `checkedAt` from a previous [EnabledPhotosResult] as [since] to let RemoveSky
     * answer with [EnabledPhotosResult.Unchanged] (no photo list, just a fresh `checked_at`)
     * when nothing changed for this location since then — the caller then skips
     * pruning/diffing entirely instead of re-downloading and re-comparing the full list on
     * every call. Omit [since] for a first-ever fetch of a location.
     */
    suspend fun fetchEnabledPhotos(
        latitude: Double,
        longitude: Double,
        since: String? = null,
    ): EnabledPhotosResult = withContext(Dispatchers.IO) {
        val sinceParam = since?.let { "&since=${enc(it)}" }.orEmpty()
        val url = "$apiBase/search?source=local&limit=$MAX_ENABLED_URLS&lat=$latitude&lon=$longitude$sinceParam"
        val json = searchRaw(url) ?: return@withContext EnabledPhotosResult.Failed
        if (json.has("changed") && !json.optBoolean("changed", true)) {
            return@withContext EnabledPhotosResult.Unchanged(json.optStringOrNull("checked_at"))
        }
        val results = json.optJSONArray("results") ?: return@withContext EnabledPhotosResult.Failed
        val photos = buildList {
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val processedUrl = normalizeServiceUrl(item.optString("processed_url"))
                if (processedUrl.isBlank()) continue
                add(
                    RemoveSkyEnabledPhoto(
                        url = processedUrl,
                        attribution = attributionOf(item),
                        dayPeriod = item.optStringOrNull("day_period"),
                        country = item.optStringOrNull("country"),
                        season = item.optStringOrNull("season"),
                        exifLatitude = item.optDoubleOrNull("exif_lat"),
                        exifLongitude = item.optDoubleOrNull("exif_lon"),
                        depthUrl = item.optStringOrNull("depth_url")?.let(::normalizeServiceUrl),
                        id = item.optStringOrNull("id"),
                        source = item.optStringOrNull("source"),
                        provider = item.optStringOrNull("provider"),
                        title = item.optStringOrNull("title"),
                        status = item.optStringOrNull("status"),
                        location = item.optStringOrNull("location"),
                        processedAt = item.optStringOrNull("processed_at"),
                        capturedAt = item.optStringOrNull("captured_at"),
                        description = item.optStringOrNull("description"),
                        resolvedCity = item.optStringOrNull("resolved_city"),
                        isCity = item.optBooleanOrNull("is_city"),
                        sceneType = item.optStringOrNull("scene_type"),
                        weather = item.optStringOrNull("weather"),
                        resolvedLatitude = item.optDoubleOrNull("resolved_lat"),
                        resolvedLongitude = item.optDoubleOrNull("resolved_lon")
                    )
                )
            }
        }
        EnabledPhotosResult.Success(photos, json.optStringOrNull("checked_at"))
    }

    /**
     * Runs RemoveSky's suitability diagnostics (sky-at-top, outdoor, color, GPS, date, season)
     * against an already-hosted image, e.g. right after [uploadFile] — used to show the user
     * why their own upload was or wasn't a good background photo.
     *
     * Distinguishes a timeout (the /check endpoint runs sky/CLIP inference synchronously and can
     * legitimately take a long time on a CPU-only backend) from a genuine server-side error, so
     * the caller can show the user a message that actually matches what happened instead of a
     * silent blank result.
     */
    suspend fun checkImage(url: String): RemoveSkyCheckOutcome = withContext(Dispatchers.IO) {
        try {
            val request = get("$apiBase/check?url=${enc(url)}")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext RemoveSkyCheckOutcome.ServerError
                val body = response.body.string()
                val json = JSONObject(body)
                val info = json.optJSONObject("info")
                val checksJson = info?.optJSONObject("checks")
                RemoveSkyCheckOutcome.Success(
                    RemoveSkyCheckResult(
                        ok = json.optBoolean("ok"),
                        reason = json.optStringOrNull("reason"),
                        skyFraction = if (json.has("sky_fraction") && !json.isNull("sky_fraction")) {
                            json.optDouble("sky_fraction")
                        } else {
                            null
                        },
                        checks = checksJson?.let {
                            RemoveSkyChecks(
                                hasSkyTop = it.optBooleanOrNull("has_sky_top"),
                                isOutdoor = it.optBooleanOrNull("is_outdoor"),
                                isCity = it.optBooleanOrNull("is_city"),
                                hasColor = it.optBooleanOrNull("has_color"),
                                hasGps = it.optBooleanOrNull("has_gps"),
                                hasDate = it.optBooleanOrNull("has_date"),
                                isNightVisual = it.optBooleanOrNull("is_night_visual"),
                                seasonVisual = it.optStringOrNull("season_visual")
                            )
                        }
                    )
                )
            }
        } catch (e: java.net.SocketTimeoutException) {
            log("check timeout for $url: ${e.message}")
            RemoveSkyCheckOutcome.Timeout
        } catch (e: Throwable) {
            log("check error for $url: ${e.message}")
            RemoveSkyCheckOutcome.ServerError
        }
    }

    /** Aborts any in-flight or queued request previously tagged with [cancelTag] (see [uploadFile]). */
    fun cancelTaggedCalls(cancelTag: Any) {
        (client.dispatcher.queuedCalls() + client.dispatcher.runningCalls())
            .filter { it.request().tag(Any::class.java) === cancelTag }
            .forEach { it.cancel() }
    }

    /**
     * All enabled weetjes (fun facts) RemoveSky has near (latitude, longitude) in [taal] --
     * meant to be fetched once per new location and cached locally (see [WeetjeStore]), not
     * re-fetched on every dwell check. Empty list on any failure or when nothing is nearby
     * yet, same "no error handling needed" contract as the server side.
     */
    suspend fun fetchNearbyWeetjes(latitude: Double, longitude: Double, taal: String): List<RemoveSkyWeetje> =
        withContext(Dispatchers.IO) {
            val url = "$apiBase/weetjes/nearby?lat=$latitude&lon=$longitude&taal=${enc(taal)}"
            val json = searchRaw(url) ?: return@withContext emptyList()
            json.optJSONArray("weetjes")?.let(::parseWeetjes).orEmpty()
        }

    /**
     * Called when the app has already shown every locally-cached weetje for a location and
     * wants fresh ones. The server rate-limits actual generation per place (see
     * REQUEST_MORE_COOLDOWN_HOURS server-side); [RequestMoreWeetjesResult.generated] tells
     * the caller whether it got genuinely new content or just the existing set again.
     */
    suspend fun requestMoreWeetjes(
        land: String,
        locatie: String,
        latitude: Double,
        longitude: Double,
        taal: String,
        count: Int = 5,
    ): RequestMoreWeetjesResult? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("land", land)
                .put("locatie", locatie)
                .put("gps_lat", latitude)
                .put("gps_lon", longitude)
                .put("taal", taal)
                .put("count", count)
                .toString()
                .toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$apiBase/weetjes/request-more")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = response.body.string().let(::JSONObject)
                RequestMoreWeetjesResult(
                    generated = json.optBoolean("generated"),
                    weetjes = json.optJSONArray("weetjes")?.let(::parseWeetjes).orEmpty()
                )
            }
        } catch (e: Throwable) {
            log("request-more weetjes error: ${e.message}")
            null
        }
    }

    private fun parseWeetjes(array: JSONArray): List<RemoveSkyWeetje> = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.optInt("id", -1)
            val weetje = item.optStringOrNull("weetje")
            if (id < 0 || weetje == null) continue
            add(
                RemoveSkyWeetje(
                    id = id,
                    land = item.optString("land"),
                    locatie = item.optString("locatie"),
                    taal = item.optString("taal", "nl"),
                    weetje = weetje,
                    omschrijving = item.optStringOrNull("omschrijving"),
                    linkDetail = item.optStringOrNull("link_detail")
                )
            )
        }
    }

    suspend fun healthStatus(): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(get("$apiBase/health")).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body.string().let { JSONObject(it).optString("status").ifBlank { null } }
            }
        } catch (e: Throwable) {
            log("health error for $apiBase/health: ${e.message}")
            null
        }
    }

    suspend fun uploadFile(
        file: File,
        latitude: Double?,
        longitude: Double?,
        location: String? = null,
        /**
         * ISO-8601 timestamp (with UTC offset, e.g. "2026-07-13T14:23:01-05:00") used by the
         * server as a fallback for [captured_at] when the photo's own EXIF has no date - only
         * meaningful for a fresh camera capture (device clock = capture time); the gallery
         * import flow must NOT pass this, since an imported photo's "now" has nothing to do
         * with when it was actually taken.
         */
        capturedAt: String? = null,
        /** Tag applied to the request so [cancelTaggedCalls] can abort it mid-flight. */
        cancelTag: Any? = null,
    ): RemoveSkyUploadResult = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody(
                    (if (file.extension.equals("webp", ignoreCase = true)) "image/webp" else "image/jpeg")
                        .toMediaTypeOrNull()
                )
            )
            .apply {
                location?.takeIf { it.isNotBlank() }?.let { addFormDataPart("location", it) }
                latitude?.let { addFormDataPart("lat", it.toString()) }
                longitude?.let { addFormDataPart("lon", it.toString()) }
                capturedAt?.takeIf { it.isNotBlank() }?.let { addFormDataPart("captured_at", it) }
                // Lets the server's async pipeline (finish_upload_processing, see
                // app/services/push.py's send_to_token) push the sky/CLIP result back to
                // just this device once the heavy pass finishes -- best-effort, so a
                // failure to fetch the token here must not block the upload itself.
                fcmTokenOrNull()?.let { addFormDataPart("fcm_token", it) }
            }
            .build()
        val request = Request.Builder()
            .url("$apiBase/upload")
            .post(form)
            .header("User-Agent", USER_AGENT)
            .apply { cancelTag?.let { tag(Any::class.java, it) } }
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw RemoveSkyHttpException(response.code, body)
            }
            val json = JSONObject(body)
            // "pending" (direct file uploads only): the fast half saved the file and
            // returned immediately -- the sky/CLIP/depth pass runs later via Prefect (see
            // processing.save_upload_pending's docstring) and its result arrives as a
            // later FCM push, not in this response. No processedUrl yet is expected, not
            // an error.
            if (json.optString("status") == "pending") {
                val resolvedLocation = json.optString("location").trim().ifBlank {
                    throw RemoveSkyHttpException(response.code, "Missing location")
                }
                return@use RemoveSkyUploadResult(
                    processedUrl = null,
                    pending = true,
                    recordId = json.optInt("id").takeIf { json.has("id") },
                    location = resolvedLocation,
                    country = json.optStringOrNull("country"),
                )
            }
            val url = json.optString("url").ifBlank {
                throw RemoveSkyHttpException(response.code, "Missing processed image URL")
            }
            val resolvedLocation = json.optString("location").trim().ifBlank {
                throw RemoveSkyHttpException(response.code, "Missing processed image location")
            }
            // exif_gps is [lat, lon] (the photo's own EXIF GPS, distinct from the lat/lon we
            // sent for place matching) — null when the source has no GPS EXIF.
            val exifGps = json.optJSONArray("exif_gps")
            RemoveSkyUploadResult(
                processedUrl = normalizeServiceUrl(url),
                location = resolvedLocation,
                dayPeriod = json.optStringOrNull("day_period"),
                country = json.optStringOrNull("country"),
                season = json.optStringOrNull("season"),
                exifLatitude = exifGps?.takeIf { it.length() >= 2 }?.optDouble(0)?.takeIf { it.isFinite() },
                exifLongitude = exifGps?.takeIf { it.length() >= 2 }?.optDouble(1)?.takeIf { it.isFinite() },
                depthUrl = json.optStringOrNull("depth_url")?.let(::normalizeServiceUrl)
            )
        }
    }

    /** Current FCM registration token, or null if unavailable (no Play Services, no
     * network yet, etc.) -- never throws, upload must proceed either way. */
    private fun fcmTokenOrNull(): String? = try {
        Tasks.await(FirebaseMessaging.getInstance().token)
    } catch (e: Exception) {
        null
    }

    /** Run a search, then return the first candidate as a sky-removed (transparent) PNG URL. */
    private suspend fun resolve(searchUrl: String): ImageResult? = withContext(Dispatchers.IO) {
        val results = search(searchUrl) ?: return@withContext null

        // 1) Reuse anything RemoveSky already processed — instant, no extra server work.
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val processedUrl = normalizeServiceUrl(item.optString("processed_url"))
            if (
                item.optBoolean("already_processed") &&
                processedUrl.isNotBlank() &&
                processedUrl !in excludedUrls
            ) {
                return@withContext ImageResult(
                    url = processedUrl,
                    attribution = attributionOf(item),
                    alreadyProcessed = true,
                    dayPeriod = item.optStringOrNull("day_period"),
                    country = item.optStringOrNull("country"),
                    season = item.optStringOrNull("season"),
                    exifLatitude = item.optDoubleOrNull("exif_lat"),
                    exifLongitude = item.optDoubleOrNull("exif_lon"),
                    depthUrl = item.optStringOrNull("depth_url")?.let(::normalizeServiceUrl),
                    id = item.optStringOrNull("id"),
                    source = item.optStringOrNull("source"),
                    provider = item.optStringOrNull("provider"),
                    title = item.optStringOrNull("title"),
                    pageUrl = item.optStringOrNull("page_url")?.let(::normalizeServiceUrl),
                    ownerName = item.optStringOrNull("owner_name"),
                    license = item.optStringOrNull("license"),
                    processedLocation = item.optStringOrNull("processed_location"),
                    status = item.optStringOrNull("status"),
                    location = item.optStringOrNull("location"),
                    capturedAt = item.optStringOrNull("captured_at"),
                    description = item.optStringOrNull("description"),
                    resolvedCity = item.optStringOrNull("resolved_city"),
                    isCity = item.optBooleanOrNull("is_city"),
                    sceneType = item.optStringOrNull("scene_type"),
                    weather = item.optStringOrNull("weather"),
                    resolvedLatitude = item.optDoubleOrNull("resolved_lat"),
                    resolvedLongitude = item.optDoubleOrNull("resolved_lon")
                )
            }
        }

        // 2) Otherwise ask RemoveSky to process candidates until one is accepted (it validates
        //    that the image is a usable landscape and rejects the rest).
        var attempts = 0
        for (i in 0 until results.length()) {
            if (attempts >= MAX_PROCESS_ATTEMPTS) break
            val item = results.getJSONObject(i)
            val imageUrl = item.optString("image_url")
            if (imageUrl.isBlank()) continue
            attempts++
            val uploadResult = uploadFull(imageUrl) ?: continue
            val processedUrl = uploadResult.first
            if (processedUrl in excludedUrls) continue
            return@withContext ImageResult(
                url = processedUrl,
                attribution = attributionOf(item),
                alreadyProcessed = true,
                dayPeriod = item.optStringOrNull("day_period"),
                country = item.optStringOrNull("country"),
                season = item.optStringOrNull("season"),
                exifLatitude = item.optDoubleOrNull("exif_lat"),
                exifLongitude = item.optDoubleOrNull("exif_lon"),
                depthUrl = uploadResult.second,
                id = item.optStringOrNull("id"),
                source = item.optStringOrNull("source"),
                provider = item.optStringOrNull("provider"),
                title = item.optStringOrNull("title"),
                pageUrl = item.optStringOrNull("page_url")?.let(::normalizeServiceUrl),
                ownerName = item.optStringOrNull("owner_name"),
                license = item.optStringOrNull("license"),
                processedLocation = item.optStringOrNull("processed_location"),
                status = item.optStringOrNull("status"),
                location = item.optStringOrNull("location"),
                capturedAt = item.optStringOrNull("captured_at"),
                description = item.optStringOrNull("description"),
                resolvedCity = item.optStringOrNull("resolved_city"),
                isCity = item.optBooleanOrNull("is_city"),
                sceneType = item.optStringOrNull("scene_type"),
                weather = item.optStringOrNull("weather"),
                resolvedLatitude = item.optDoubleOrNull("resolved_lat"),
                resolvedLongitude = item.optDoubleOrNull("resolved_lon")
            )
        }
        null
    }

    private fun search(url: String): JSONArray? = searchRaw(url)?.optJSONArray("results")

    /** Parses the full `/search` response body, or null on a failed/errored request. */
    private fun searchRaw(url: String): JSONObject? = try {
        val request = get(url)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body.string().let(::JSONObject)
            }
        }
    } catch (e: Throwable) {
        log("search error for $url: ${e.message}")
        null
    }

    /** POST upload with `url=` so RemoveSky removes the sky; returns (processedUrl, depthUrl?). */
    private fun uploadFull(imageUrl: String): Pair<String, String?>? = try {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("url", imageUrl)
            .build()
        val request = Request.Builder()
            .url("$apiBase/upload")
            .post(form)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                log("upload HTTP ${response.code} for $imageUrl")
                null
            } else {
                response.body.string().let { body ->
                    val json = JSONObject(body)
                    val processedUrl = json.optString("url").ifBlank { null }
                        ?.let(::normalizeServiceUrl) ?: return@let null
                    val depthUrl = json.optStringOrNull("depth_url")?.let(::normalizeServiceUrl)
                    Pair(processedUrl, depthUrl)
                }
            }
        }
    } catch (e: Throwable) {
        log("upload error for $imageUrl: ${e.message}")
        null
    }

    /**
     * Registers (or refreshes) this device's FCM token with RemoveSky, so it receives an
     * immediate push when a curator soft-deletes/disables a photo (see
     * [RemoveSkyMessagingService] and docs/UpdateFLow.md flow 5). Call on app start and
     * again from FCM's onNewToken. Best-effort: returns false on any failure, caller just
     * retries next time -- the polling fallback (fetchEnabledPhotos' `since`) still applies.
     */
    suspend fun registerFcmToken(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().put("token", token).toString()
            val body = json.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$apiBase/fcm/register")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response -> response.isSuccessful }
        } catch (e: Throwable) {
            log("fcm register error: ${e.message}")
            false
        }
    }

    /**
     * Polls `/removed` for photos purged (curator soft-delete/disable) near (lat, lon)
     * since [since] -- a startup-only reconciliation pass for purges possibly missed while
     * the app was closed (FCM only reaches a running app; the periodic since/changed poll
     * in [fetchEnabledPhotos] is the other fallback, but only runs on its own schedule).
     * Returns (urls, checkedAt); urls are NOT normalized here, callers must run them through
     * [normalizeServiceUrl] before matching against a cached photo's sourceUrl, same as a
     * push payload.
     */
    suspend fun fetchRemoved(latitude: Double, longitude: Double, since: String): Pair<List<String>, String?> =
        withContext(Dispatchers.IO) {
            val url = "$apiBase/removed?lat=$latitude&lon=$longitude&since=${enc(since)}"
            val json = searchRaw(url) ?: return@withContext emptyList<String>() to null
            val urls = json.optJSONArray("removed_urls")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it, null) }
            }.orEmpty()
            urls to json.optStringOrNull("checked_at")
        }

    // -------------------------------------------------------------------------------------
    // First-time / incremental sync entry points.
    //
    // [datetime] is the caller's local request timestamp (for logging/telemetry on the
    // caller's side only -- no endpoint here takes it, unlike [lastUpdated]/`since`, which
    // is the server's own `checked_at` from a *previous* call and actually drives the
    // incremental-fetch logic).
    //
    // Callers are responsible for persistence: pass the returned JSON for a "get"/"update"
    // call to upsertDataDB(json), and -- for the update variants -- the second (removed)
    // JSON to deleteRecordsDataDB(json) for records whose status is now disabled or that
    // were soft-deleted.
    // -------------------------------------------------------------------------------------

    /**
     * First-time fetch of every `enabled` processed image for a named place (e.g. "Paris"),
     * regardless of how far individual photos sit from that place's own centroid -- see the
     * location-based `since` support in `/search`/`/removed` for why this differs from the
     * GPS+radius variant below.
     */
    suspend fun getImagesDataByCity(
        datetime: String,
        latitude: Double,
        longitude: Double,
        city: String? = null,
    ): JSONObject? = withContext(Dispatchers.IO) {
        val locationParam = city?.takeIf { it.isNotBlank() }?.let { "&location=${enc(it)}" }.orEmpty()
        searchRaw("$apiBase/search?source=local&limit=$MAX_ENABLED_URLS&lat=$latitude&lon=$longitude$locationParam")
    }

    /** First-time fetch of every `enabled` processed image within [range] km of (lat, lon). */
    suspend fun getImagesDataByGPS(
        datetime: String,
        latitude: Double,
        longitude: Double,
        range: Double,
    ): JSONObject? = withContext(Dispatchers.IO) {
        searchRaw("$apiBase/search?source=local&limit=$MAX_ENABLED_URLS&lat=$latitude&lon=$longitude&radius_km=$range")
    }

    /**
     * Incremental sync for a named place since [lastUpdated] (a previous call's
     * `checked_at`): fetches images that are new/still-enabled ([ImagesSyncResult.upserted],
     * or `{changed:false}` when nothing moved) plus images that dropped out (disabled or
     * soft-deleted) in the same window ([ImagesSyncResult.removed]).
     */
    suspend fun updateImagesDataByCity(
        datetime: String,
        latitude: Double,
        longitude: Double,
        lastUpdated: String,
        city: String? = null,
    ): ImagesSyncResult = withContext(Dispatchers.IO) {
        val locationParam = city?.takeIf { it.isNotBlank() }?.let { "&location=${enc(it)}" }.orEmpty()
        val upserted = searchRaw(
            "$apiBase/search?source=local&limit=$MAX_ENABLED_URLS&lat=$latitude&lon=$longitude" +
                "$locationParam&since=${enc(lastUpdated)}"
        )
        val removed = if (!city.isNullOrBlank()) {
            searchRaw("$apiBase/removed?location=${enc(city)}&since=${enc(lastUpdated)}")
        } else {
            searchRaw("$apiBase/removed?lat=$latitude&lon=$longitude&since=${enc(lastUpdated)}")
        }
        ImagesSyncResult(upserted, removed)
    }

    /** Same as [updateImagesDataByCity], keyed on GPS + [range] instead of a place name. */
    suspend fun updateImagesDataByGPS(
        datetime: String,
        latitude: Double,
        longitude: Double,
        lastUpdated: String,
        range: Double,
    ): ImagesSyncResult = withContext(Dispatchers.IO) {
        val upserted = searchRaw(
            "$apiBase/search?source=local&limit=$MAX_ENABLED_URLS&lat=$latitude&lon=$longitude" +
                "&radius_km=$range&since=${enc(lastUpdated)}"
        )
        val removed =
            searchRaw("$apiBase/removed?lat=$latitude&lon=$longitude&radius_km=$range&since=${enc(lastUpdated)}")
        ImagesSyncResult(upserted, removed)
    }

    private fun get(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .build()

    private fun attributionOf(item: JSONObject): String {
        val author = item.optString("owner_name").trim()
        val title = item.optString("title").trim()
        val provider = item.optString("provider").ifBlank { "RemoveSky" }
        return buildString {
            when {
                author.isNotBlank() -> append(author).append(" / ")
                title.isNotBlank() -> append(title).append(" / ")
            }
            append(provider)
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).trim().ifBlank { null } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /**
     * Rewrites [url]'s scheme/host/port to match this provider's configured [base] (when the
     * host already matches, otherwise returns [url] unchanged). Used both when parsing a
     * `/search` response and when normalizing a URL received via FCM push
     * ([WallpaperRepository.normalizeServiceUrl]) -- the server can't always know its own
     * public scheme (e.g. running plain HTTP behind a TLS-terminating proxy without
     * `--proxy-headers`), so an `http://` vs `https://` mismatch must not break an exact
     * URL match against a locally cached [photo.sourceUrl].
     */
    fun normalizeServiceUrl(url: String): String {
        if (url.isBlank()) return url
        val serviceBase = base.toHttpUrlOrNull() ?: return url
        val parsed = url.toHttpUrlOrNull() ?: serviceBase.resolve(url) ?: return url
        if (!parsed.host.equals(serviceBase.host, ignoreCase = true)) return url
        return parsed.newBuilder()
            .scheme(serviceBase.scheme)
            .host(serviceBase.host)
            .port(serviceBase.port)
            .build()
            .toString()
    }

    private fun log(message: String) = android.util.Log.w("LWWPhoto", "removesky $message")

    companion object {
        /** Public RemoveSky instance used when no custom base URL is configured. */
        const val DEFAULT_BASE_URL = "https://removesky.vanburik.info"

        private const val API_PATH = "/api/v1"
        private const val LIMIT = 12
        private const val MAX_PROCESS_ATTEMPTS = 6

        /**
         * Upper bound when fetching all enabled URLs for a location to prune the local cache
         * or check for new ones. Must stay <= the server's `REMOVESKY_SEARCH_MAX_LIMIT` (200 by
         * default) — the `/search` endpoint rejects a higher `limit` with a 400, which would
         * break every caller of [fetchEnabledPhotos].
         */
        private const val MAX_ENABLED_URLS = 200
        private const val USER_AGENT =
            "LiveWallpaperWeather/1.0 (https://github.com/strammermax/breezy-weather; " +
                "based on Breezy Weather)"
    }
}

data class RemoveSkyUploadResult(
    /** Null while [pending] is true -- the async pass hasn't produced one yet. */
    val processedUrl: String?,
    val location: String,
    val dayPeriod: String? = null,
    val country: String? = null,
    val season: String? = null,
    val exifLatitude: Double? = null,
    val exifLongitude: Double? = null,
    /** Depth map URL (grayscale PNG, 255=near, 0=far). Null if not yet generated. */
    val depthUrl: String? = null,
    /** True for the async camera-upload path's immediate response: the file is saved and
     * a `processed` row exists ([recordId]), but the sky/CLIP/depth pass hasn't run yet --
     * its outcome (done/rejected/failed) arrives later as an "upload_result" FCM push (see
     * RemoveSkyMessagingService), not in this result. */
    val pending: Boolean = false,
    val recordId: Int? = null,
)

/** One entry of [RemoveSkyProvider.fetchEnabledPhotos]: a known-enabled photo plus its metadata. */
data class RemoveSkyEnabledPhoto(
    val url: String,
    val attribution: String?,
    val dayPeriod: String?,
    val country: String?,
    val season: String?,
    val exifLatitude: Double? = null,
    val exifLongitude: Double? = null,
    /** Depth map URL (grayscale PNG, 255=near, 0=far). Null if not yet generated. */
    val depthUrl: String? = null,
    val id: String? = null,
    val source: String? = null,
    val provider: String? = null,
    val title: String? = null,
    val status: String? = null,
    val location: String? = null,
    /** When RemoveSky processed this photo (server's `processed_at`) -- the "created_date"
     * customsortlogic.md's recency-margin rule sorts on, distinct from [capturedAt] (the
     * photo's own EXIF capture date) and from the local first-sync time. Null if unknown
     * (e.g. an older server that doesn't send this field yet). */
    val processedAt: String? = null,
    val capturedAt: String? = null,
    val description: String? = null,
    val resolvedCity: String? = null,
    val isCity: Boolean? = null,
    /** "urban"/"rural"/etc., as classified by RemoveSky. Null if unknown. */
    val sceneType: String? = null,
    /** "sunny"/"cloudy"/"rain"/"snow"/"windy"/"hail", as classified by RemoveSky. Null if unknown. */
    val weather: String? = null,
    /** RemoveSky's resolved location coordinates (city/place center) -- distinct from
     * [exifLatitude]/[exifLongitude] (the photo's own EXIF GPS, often absent). Used as the
     * distance fallback when EXIF GPS is unknown. Null if unknown. */
    val resolvedLatitude: Double? = null,
    val resolvedLongitude: Double? = null,
)

/** Outcome of [RemoveSkyProvider.fetchEnabledPhotos]. */
sealed class EnabledPhotosResult {
    /** Fresh photo list for the location, with the server's `checked_at` for the next [since]. */
    data class Success(val photos: List<RemoveSkyEnabledPhoto>, val checkedAt: String?) : EnabledPhotosResult()

    /** Nothing changed for this location since the `since` that was sent — caller should skip
     * pruning/diffing and just remember [checkedAt] as the new `since`. */
    data class Unchanged(val checkedAt: String?) : EnabledPhotosResult()

    /** Request failed — not evidence that images were disabled or absent; caller must skip
     * pruning/diffing rather than treat this as an empty result. */
    object Failed : EnabledPhotosResult()
}

/**
 * Result of [RemoveSkyProvider.updateImagesDataByCity]/[RemoveSkyProvider.updateImagesDataByGPS]:
 * [upserted] is the `/search` response (either a fresh result list to upsert, or
 * `{changed:false}` when nothing moved since `lastUpdated`), [removed] is the `/removed`
 * response (records to delete -- disabled or soft-deleted). Either may be null if that
 * particular request failed; callers must not treat a null as "nothing changed", same
 * failure contract as [EnabledPhotosResult.Failed].
 */
data class ImagesSyncResult(
    val upserted: JSONObject?,
    val removed: JSONObject?,
)

class RemoveSkyHttpException(
    val statusCode: Int,
    val responseBody: String,
) : Exception("RemoveSky HTTP $statusCode")

/** Outcome of [RemoveSkyProvider.checkImage] -- distinguishes a real answer from the two ways
 *  the call itself can fail, so the UI can show a message that matches what happened. */
sealed class RemoveSkyCheckOutcome {
    data class Success(val result: RemoveSkyCheckResult) : RemoveSkyCheckOutcome()

    /** The request timed out -- most likely RemoveSky's CPU-only sky/CLIP inference (see
     *  [RemoveSkyProvider.checkImage]'s kdoc) taking longer than the client's read timeout. */
    object Timeout : RemoveSkyCheckOutcome()

    /** A non-2xx response, or the body couldn't be parsed. */
    object ServerError : RemoveSkyCheckOutcome()
}

/** Result of [RemoveSkyProvider.checkImage] — why a photo was or wasn't accepted as a background. */
data class RemoveSkyCheckResult(
    val ok: Boolean,
    /** Short failure code, e.g. "no_sky_at_top", "clip_not_landscape". Null when [ok]. */
    val reason: String?,
    val skyFraction: Double?,
    val checks: RemoveSkyChecks?,
)

data class RemoveSkyChecks(
    val hasSkyTop: Boolean?,
    val isOutdoor: Boolean?,
    val isCity: Boolean?,
    val hasColor: Boolean?,
    val hasGps: Boolean?,
    val hasDate: Boolean?,
    val isNightVisual: Boolean?,
    val seasonVisual: String?,
)

/** One "wist je dat" fun fact about a location, as returned by [RemoveSkyProvider.fetchNearbyWeetjes]
 * and [RemoveSkyProvider.requestMoreWeetjes]. [id] is the server's row id -- used as the local
 * cache key (see [WeetjeStore]) so a re-fetch doesn't create duplicates or reset shown-state. */
data class RemoveSkyWeetje(
    val id: Int,
    val land: String,
    val locatie: String,
    val taal: String,
    val weetje: String,
    val omschrijving: String?,
    val linkDetail: String?,
)

/** Result of [RemoveSkyProvider.requestMoreWeetjes]. */
data class RequestMoreWeetjesResult(
    /** False when the server's cooldown blocked a fresh generation -- [weetjes] is then just
     * the location's existing set, not new content. */
    val generated: Boolean,
    val weetjes: List<RemoveSkyWeetje>,
)
