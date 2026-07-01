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
 * The image a provider returned for a query, plus the attribution to show/keep.
 */
data class ImageResult(
    val url: String,
    val attribution: String? = null,
    /**
     * True when [url] already points at a sky-erased (transparent) image — e.g. from the
     * RemoveSky service. The caller must then NOT run the on-device [SkySegmenter] again.
     */
    val alreadyProcessed: Boolean = false,
    /** "day" or "night", as classified by RemoveSky from the source image. Null if unknown. */
    val dayPeriod: String? = null,
    /** Country the photo was geotagged/located in, as resolved by RemoveSky. Null if unknown. */
    val country: String? = null,
    /** "winter"/"spring"/"summer"/"autumn", as classified by RemoveSky. Null if unknown. */
    val season: String? = null,
    /**
     * The photo's own EXIF GPS coordinates (where the camera was, not just the matched place) —
     * only present for a minority of photos. Null if unknown.
     */
    val exifLatitude: Double? = null,
    val exifLongitude: Double? = null,
    /**
     * URL of a grayscale depth map (PNG, same resolution as the processed RGBA). Pixel value
     * 255 = nearest to camera, 0 = furthest. Used to split the foreground into near/far layers
     * so cloud animations can appear to fly between scene elements. Null if not yet generated.
     */
    val depthUrl: String? = null,
)

/**
 * Abstraction over a background-image source, so providers (Unsplash, Wikimedia Commons,
 * Mapbox, …) can be swapped or chained. Add a new provider by implementing this interface
 * and registering it in [WallpaperRepository].
 */
interface ImageSearchProvider {
    /** Stable identifier, e.g. "unsplash", "wikimedia", "mapbox". */
    val id: String

    /** Whether this provider needs an API key/token to work. */
    val requiresApiKey: Boolean

    /** True when the provider can be used right now (e.g. a key is present, or none needed). */
    fun isConfigured(): Boolean

    /**
     * Search by a free-text place query (e.g. "Hoofddorp", "Hoofddorp Netherlands").
     * Returns null when nothing usable was found.
     */
    suspend fun searchImage(query: String): ImageResult?

    /**
     * Optional: search by coordinates (for geo-aware providers such as Mapbox satellite or
     * Wikimedia geosearch). Default: not supported.
     */
    suspend fun searchImageByLocation(latitude: Double, longitude: Double): ImageResult? = null
}
