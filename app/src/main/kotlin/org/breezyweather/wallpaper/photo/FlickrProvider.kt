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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * [ImageSearchProvider] backed by the Flickr REST API (`flickr.photos.search`). Supports both
 * a free-text place query and a geo search around coordinates. Needs a free Flickr API key;
 * without one [isConfigured] is false and the repository skips it (falling back to Wikimedia).
 *
 * `extras=url_l` gives a ~1024px image URL directly, and `owner_name` is used for attribution.
 * A random result is picked so each refresh varies.
 */
class FlickrProvider(
    private val apiKey: String,
    private val client: OkHttpClient,
) : ImageSearchProvider {

    override val id: String = "flickr"
    override val requiresApiKey: Boolean = true

    override fun isConfigured(): Boolean = apiKey.isNotBlank()

    override suspend fun searchImage(query: String): ImageResult? {
        if (!isConfigured() || query.isBlank()) return null
        return request("$BASE&text=${enc(query)}&sort=relevance")
    }

    override suspend fun searchImageByLocation(latitude: Double, longitude: Double): ImageResult? {
        if (!isConfigured()) return null
        val lat = String.format(Locale.US, "%.5f", latitude)
        val lon = String.format(Locale.US, "%.5f", longitude)
        return request("$BASE&lat=$lat&lon=$lon&radius=$GEO_RADIUS_KM&radius_units=km")
    }

    private suspend fun request(url: String): ImageResult? = withContext(Dispatchers.IO) {
        try {
            val fullUrl = "$ENDPOINT?method=flickr.photos.search&api_key=${enc(apiKey)}$url"
            val request = Request.Builder().url(fullUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parse(body)
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun parse(body: String): ImageResult? {
        val photos = JSONObject(body).optJSONObject("photos")?.optJSONArray("photo")
            ?: return null
        // Keep only entries that actually expose a usable image URL, then pick one at random.
        val usable = (0 until photos.length())
            .map { photos.getJSONObject(it) }
            .mapNotNull { p ->
                val imageUrl = p.optString("url_l").ifBlank { p.optString("url_c") }
                if (imageUrl.isBlank()) {
                    null
                } else {
                    val owner = p.optString("ownername").ifBlank { "Flickr" }
                    imageUrl to "$owner / Flickr"
                }
            }
        val pick = usable.randomOrNull() ?: return null
        return ImageResult(url = pick.first, attribution = pick.second)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val ENDPOINT = "https://api.flickr.com/services/rest/"
        private const val GEO_RADIUS_KM = 15

        // Common query params shared by text and geo searches.
        private const val BASE =
            "&extras=url_l%2Curl_c%2Cowner_name&content_type=1&media=photos&safe_search=1" +
                "&per_page=30&format=json&nojsoncallback=1"
    }
}
