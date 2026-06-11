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

import okhttp3.OkHttpClient

/**
 * [ImageSearchProvider] backed by the Unsplash Search API (by place name). Needs a free
 * access key; when none is configured the provider reports [isConfigured] == false and the
 * repository skips it (falling back to the keyless Wikimedia provider).
 */
class UnsplashProvider(
    private val accessKey: String,
    private val client: OkHttpClient,
) : ImageSearchProvider {

    override val id: String = "unsplash"
    override val requiresApiKey: Boolean = true

    override fun isConfigured(): Boolean = accessKey.isNotBlank()

    override suspend fun searchImage(query: String): ImageResult? {
        if (!isConfigured()) return null
        val url = UnsplashPhotoSource(accessKey, client).searchPhotoUrl(query) ?: return null
        return ImageResult(url, attribution = "Photo via Unsplash")
    }
}
