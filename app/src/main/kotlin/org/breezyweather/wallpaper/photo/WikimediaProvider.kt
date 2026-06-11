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

/**
 * Keyless [ImageSearchProvider] backed by the Wikimedia Commons MediaWiki API. Because it
 * needs no API key it is always available, so [WallpaperRepository] uses it as the universal
 * fallback that keeps the photo background working even without an Unsplash/Mapbox key.
 *
 * Two strategies are offered:
 *  - [searchImageByLocation]: `generator=geosearch` finds files geotagged near the point.
 *  - [searchImage]: `generator=search` finds files matching the place name.
 *
 * Only real raster images (jpg/jpeg/png) are returned; SVGs/PDFs and the like are skipped.
 * Attribution (license + author) is extracted from `extmetadata` so it can be credited as
 * required by the Commons licenses.
 */
class WikimediaProvider(
    private val client: OkHttpClient,
) : ImageSearchProvider {

    override val id: String = "wikimedia"
    override val requiresApiKey: Boolean = false

    override fun isConfigured(): Boolean = true

    override suspend fun searchImage(query: String): ImageResult? {
        if (query.isBlank()) return null
        val url = "$API?$COMMON_PARAMS" +
            "&generator=search" +
            "&gsrnamespace=6" +
            "&gsrlimit=$LIMIT" +
            "&gsrsearch=${enc(query)}"
        return request(url)
    }

    override suspend fun searchImageByLocation(latitude: Double, longitude: Double): ImageResult? {
        val url = "$API?$COMMON_PARAMS" +
            "&generator=geosearch" +
            "&ggsnamespace=6" +
            "&ggsradius=$GEO_RADIUS_M" +
            "&ggslimit=$LIMIT" +
            "&ggscoord=${enc("$latitude|$longitude")}"
        return request(url)
    }

    private suspend fun request(url: String): ImageResult? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parse(body)
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** Picks a random usable raster image from a MediaWiki `query.pages` response. */
    private fun parse(body: String): ImageResult? {
        val pages = JSONObject(body)
            .optJSONObject("query")
            ?.optJSONObject("pages")
            ?: return null

        // Collect all usable raster hits, then pick a random one so each refresh varies.
        val rasters = pages.keys().asSequence()
            .map { pages.getJSONObject(it) }
            .mapNotNull { page ->
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0)
                    ?: return@mapNotNull null
                val thumb = info.optString("thumburl").ifBlank { info.optString("url") }
                if (thumb.isBlank() || !isRaster(thumb)) null else info to thumb
            }
            .toList()

        val pick = rasters.randomOrNull() ?: return null
        return ImageResult(url = pick.second, attribution = attributionOf(pick.first))
    }

    private fun isRaster(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
    }

    private fun attributionOf(info: JSONObject): String {
        val meta = info.optJSONObject("extmetadata")
        val license = meta?.optJSONObject("LicenseShortName")?.optString("value").orEmpty()
        val artist = meta?.optJSONObject("Artist")?.optString("value").orEmpty()
            .replace(HTML_TAG, "")
            .trim()
        return buildString {
            if (artist.isNotBlank()) append(artist).append(" / ")
            if (license.isNotBlank()) append(license).append(" — ")
            append("Wikimedia Commons")
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val API = "https://commons.wikimedia.org/w/api.php"
        private const val LIMIT = 20
        private const val GEO_RADIUS_M = 10000
        private const val THUMB_WIDTH = 1080

        // Wikimedia requires a descriptive, contactable User-Agent for API access; a generic
        // one risks HTTP 429. See https://meta.wikimedia.org/wiki/User-Agent_policy
        private const val USER_AGENT =
            "LiveWallpaperWeather/1.0 (https://github.com/strammermax/breezy-weather; " +
                "based on Breezy Weather)"

        private const val COMMON_PARAMS =
            "action=query&format=json&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=$THUMB_WIDTH"

        private val HTML_TAG = Regex("<[^>]*>")
    }
}
