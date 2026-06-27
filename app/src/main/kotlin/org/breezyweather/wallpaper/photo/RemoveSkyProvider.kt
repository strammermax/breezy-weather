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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
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
     * Returns the full set of currently `enabled` processed-image URLs RemoveSky knows for this
     * location, or null when the request failed (caller must then skip pruning — a failed
     * request is not evidence that images were disabled).
     */
    suspend fun fetchEnabledUrls(latitude: Double, longitude: Double): Set<String>? =
        withContext(Dispatchers.IO) {
            val url = "$apiBase/search?source=local&limit=$MAX_ENABLED_URLS&lat=$latitude&lon=$longitude"
            val results = search(url) ?: return@withContext null
            buildSet {
                for (i in 0 until results.length()) {
                    val processedUrl = normalizeServiceUrl(results.getJSONObject(i).optString("processed_url"))
                    if (processedUrl.isNotBlank()) add(processedUrl)
                }
            }
        }

    suspend fun healthStatus(): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(get("$apiBase/health")).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()?.let { JSONObject(it).optString("status").ifBlank { null } }
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
    ): RemoveSkyUploadResult = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("image/jpeg".toMediaTypeOrNull()),
            )
            .apply {
                location?.takeIf { it.isNotBlank() }?.let { addFormDataPart("location", it) }
                latitude?.let { addFormDataPart("lat", it.toString()) }
                longitude?.let { addFormDataPart("lon", it.toString()) }
            }
            .build()
        val request = Request.Builder()
            .url("$apiBase/upload")
            .post(form)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RemoveSkyHttpException(response.code, body)
            }
            val json = JSONObject(body)
            val url = json.optString("url").ifBlank {
                throw RemoveSkyHttpException(response.code, "Missing processed image URL")
            }
            val resolvedLocation = json.optString("location").trim().ifBlank {
                throw RemoveSkyHttpException(response.code, "Missing processed image location")
            }
            RemoveSkyUploadResult(
                processedUrl = normalizeServiceUrl(url),
                location = resolvedLocation,
            )
        }
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
                )
            }
        }

        // 2) Otherwise ask RemoveSky to process candidates until one is accepted (it validates
        //    that the image is a usable landscape and rejects the rest).
        var attempts = 0
        for (i in 0 until results.length()) {
            if (attempts >= MAX_PROCESS_ATTEMPTS) break
            val imageUrl = results.getJSONObject(i).optString("image_url")
            if (imageUrl.isBlank()) continue
            attempts++
            val processedUrl = upload(imageUrl) ?: continue
            if (processedUrl in excludedUrls) continue
            return@withContext ImageResult(
                url = processedUrl,
                attribution = attributionOf(results.getJSONObject(i)),
                alreadyProcessed = true,
            )
        }
        null
    }

    private fun search(url: String): JSONArray? = try {
        val request = get(url)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body?.string()?.let { JSONObject(it).optJSONArray("results") }
            }
        }
    } catch (e: Throwable) {
        log("search error for $url: ${e.message}")
        null
    }

    /** POST upload with `url=` so RemoveSky removes the sky; returns the processed PNG URL. */
    private fun upload(imageUrl: String): String? = try {
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
                // 400 = not a usable landscape (e.g. no sky) -> caller tries the next candidate.
                log("upload HTTP ${response.code} for $imageUrl")
                null
            } else {
                response.body?.string()?.let {
                    JSONObject(it).optString("url").ifBlank { null }?.let(::normalizeServiceUrl)
                }
            }
        }
    } catch (e: Throwable) {
        log("upload error for $imageUrl: ${e.message}")
        null
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

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Keeps API-returned service URLs on the configured HTTPS origin behind a TLS proxy. */
    private fun normalizeServiceUrl(url: String): String {
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
         * or check for new ones. The `/search` endpoint rejects `limit` above 25 with a 400
         * (confirmed against the RemoveSky v0.3.1 API), so anything higher silently breaks
         * every caller of [fetchEnabledUrls] by making the whole request fail.
         */
        private const val MAX_ENABLED_URLS = 25
        private const val USER_AGENT =
            "LiveWallpaperWeather/1.0 (https://github.com/strammermax/breezy-weather; " +
                "based on Breezy Weather)"
    }
}

data class RemoveSkyUploadResult(
    val processedUrl: String,
    val location: String,
)

class RemoveSkyHttpException(
    val statusCode: Int,
    val responseBody: String,
) : Exception("RemoveSky HTTP $statusCode")
