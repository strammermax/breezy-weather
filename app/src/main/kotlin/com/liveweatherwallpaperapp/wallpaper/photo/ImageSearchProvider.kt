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
    /** Result id as reported by the provider, e.g. "local-3056". Null if unknown. */
    val id: String? = null,
    /** Provider source key, e.g. "local", "mediawiki", "flickr". Null if unknown. */
    val source: String? = null,
    /** Human-readable provider name, e.g. "lokale database". Null if unknown. */
    val provider: String? = null,
    /** Photo title as reported by the provider. Null if unknown. */
    val title: String? = null,
    /** Page the photo was sourced from (e.g. Wikimedia file page). Null if unknown. */
    val pageUrl: String? = null,
    /** Owner/photographer credited by the provider. Null if unknown. */
    val ownerName: String? = null,
    /** License string as reported by the provider, e.g. "CC-BY-SA-4.0". Null if unknown. */
    val license: String? = null,
    /** RemoveSky's resolved place name for the match. Null if unknown. */
    val processedLocation: String? = null,
    /** "enabled"/"disabled" — whether RemoveSky currently serves this photo. Null if unknown. */
    val status: String? = null,
    /** Raw `location` field the record was matched/stored under. Null if unknown. */
    val location: String? = null,
    /** ISO timestamp the photo was taken, from EXIF. Null if unknown. */
    val capturedAt: String? = null,
    /** Free-text description RemoveSky stored for the record. Null if unknown. */
    val description: String? = null,
    /** Nearest known city RemoveSky resolved the coordinates to. Null if unknown. */
    val resolvedCity: String? = null,
    /** Whether RemoveSky classified the scene as a city/urban shot. Null if unknown. */
    val isCity: Boolean? = null,
    /** Not yet populated by RemoveSky (no scene classifier exists server-side). Null for now. */
    val sceneType: String? = null,
    /** Not yet populated by RemoveSky (no weather classifier exists server-side). Null for now. */
    val weather: String? = null,
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
