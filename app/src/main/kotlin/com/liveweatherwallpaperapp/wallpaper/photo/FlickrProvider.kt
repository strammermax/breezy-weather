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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Keyless [ImageSearchProvider] backed by Flickr's public photo feed
 * (`services/feeds/photos_public.gne`). Flickr disabled API-key creation for free accounts, but
 * this feed needs no key, so it is always available. It is tag-based (no geo), so only
 * [searchImage] is implemented: the query words are used as tags.
 *
 * The feed returns a medium (`_m`, 240px) URL; it is upsized to large (`_b`, 1024px). A random
 * item is picked so each refresh varies, and the author is used for attribution.
 */
class FlickrProvider(
    private val client: OkHttpClient,
) : ImageSearchProvider {

    override val id: String = "flickr"
    override val requiresApiKey: Boolean = false

    override fun isConfigured(): Boolean = true

    override suspend fun searchImage(query: String): ImageResult? {
        if (query.isBlank()) return null
        // The public feed is tag-based; use the query words as (AND-combined) tags.
        val tags = query.trim().split(WHITESPACE).joinToString(",") { enc(it) }
        val url = "$ENDPOINT?format=json&nojsoncallback=1&tagmode=all&tags=$tags"
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

    private fun parse(body: String): ImageResult? {
        val items = JSONObject(body).optJSONArray("items") ?: return null
        val usable = (0 until items.length())
            .map { items.getJSONObject(it) }
            .mapNotNull { item ->
                val medium = item.optJSONObject("media")?.optString("m").orEmpty()
                if (medium.isBlank()) {
                    null
                } else {
                    largeUrl(medium) to authorOf(item.optString("author"))
                }
            }
        val pick = usable.randomOrNull() ?: return null
        return ImageResult(url = pick.first, attribution = pick.second)
    }

    /** Upsizes a Flickr static URL from medium (`_m`) to large (`_b`, ~1024px) when possible. */
    private fun largeUrl(medium: String): String =
        if (medium.endsWith("_m.jpg")) medium.removeSuffix("_m.jpg") + "_b.jpg" else medium

    /** Extracts the display name from `nobody@flickr.com ("Real Name")`. */
    private fun authorOf(author: String): String {
        val name = AUTHOR_NAME.find(author)?.groupValues?.getOrNull(1)?.trim()
        return if (!name.isNullOrBlank()) "$name / Flickr" else "Flickr"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val ENDPOINT = "https://www.flickr.com/services/feeds/photos_public.gne"
        private const val USER_AGENT =
            "LiveWallpaperWeather/1.0 (https://github.com/strammermax/breezy-weather; " +
                "based on Breezy Weather)"
        private val WHITESPACE = Regex("\\s+")
        private val AUTHOR_NAME = Regex("\\(\"(.*)\"\\)")
    }
}
