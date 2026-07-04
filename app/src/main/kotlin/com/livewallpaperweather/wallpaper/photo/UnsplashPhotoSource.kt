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

package com.livewallpaperweather.wallpaper.photo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Resolves a background photo URL for a place name using the Unsplash Search API.
 *
 * Requires a free Unsplash access key (Client-ID). See README_LIVEWALLPAPER.md for setup.
 * Returns null on any failure (no key, no network, no results) so the caller can fall back
 * to the default gradient background.
 */
class UnsplashPhotoSource(
    private val accessKey: String,
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * @param query free-text place name, e.g. "Amsterdam" or "Amsterdam Netherlands".
     * @param portrait request a portrait-oriented photo (matches phone wallpapers).
     * @return the URL of a "regular"-sized photo, or null when nothing usable was found.
     */
    suspend fun searchPhotoUrl(query: String, portrait: Boolean = true): String? {
        if (accessKey.isBlank() || query.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val orientation = if (portrait) "portrait" else "landscape"
                // Fetch a page of results and pick a random one, so "Refresh now" (and each
                // wallpaper refresh) shows a different photo of the place instead of always the
                // single top hit.
                val url = "$BASE_URL/search/photos" +
                    "?query=${URLEncoder.encode(query, "UTF-8")}" +
                    "&orientation=$orientation" +
                    "&content_filter=high" +
                    "&per_page=30"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Client-ID $accessKey")
                    .header("Accept-Version", "v1")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val results = JSONObject(body).optJSONArray("results")
                    if (results == null || results.length() == 0) return@use null
                    // Prefer "regular" (~1080px wide); fall back to "full".
                    val urls = (0 until results.length()).mapNotNull { i ->
                        val u = results.getJSONObject(i).optJSONObject("urls") ?: return@mapNotNull null
                        u.optString("regular").ifBlank { u.optString("full") }.ifBlank { null }
                    }
                    urls.randomOrNull()
                }
            } catch (e: Throwable) {
                null
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://api.unsplash.com"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
