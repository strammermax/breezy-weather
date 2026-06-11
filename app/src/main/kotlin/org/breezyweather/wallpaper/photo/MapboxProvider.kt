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

/**
 * [ImageSearchProvider] backed by the Mapbox Static Images API (satellite view of the exact
 * coordinates). Purely coordinate-based, so [searchImage] (by name) is unsupported and only
 * [searchImageByLocation] returns a result. Needs a free Mapbox token.
 */
class MapboxProvider(
    private val accessToken: String,
) : ImageSearchProvider {

    override val id: String = "mapbox"
    override val requiresApiKey: Boolean = true

    override fun isConfigured(): Boolean = accessToken.isNotBlank()

    override suspend fun searchImage(query: String): ImageResult? = null

    override suspend fun searchImageByLocation(latitude: Double, longitude: Double): ImageResult? {
        if (!isConfigured()) return null
        val url = MapboxPhotoSource.staticSatelliteUrl(latitude, longitude, accessToken) ?: return null
        return ImageResult(url, attribution = "© Mapbox © OpenStreetMap")
    }
}
