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

import java.util.Locale

/**
 * Builds a Mapbox Static Images API URL for a satellite view centred on the given
 * coordinates. The URL itself is the image endpoint, so no separate "search" call is
 * needed — [WallpaperRepository] downloads it directly.
 *
 * Requires a free Mapbox access token (see README). Unlike Google Street View, Mapbox's
 * terms allow displaying and caching these static images. Attribution to Mapbox/OSM is
 * required by their terms; keep a credit visible in the app.
 */
object MapboxPhotoSource {

    /**
     * @param zoom 0-22; ~15-16 shows a neighbourhood, higher zooms in closer.
     * @param retina request @2x pixel density (sharper on phones).
     */
    fun staticSatelliteUrl(
        latitude: Double,
        longitude: Double,
        accessToken: String,
        widthPx: Int = 600,
        heightPx: Int = 900,
        zoom: Double = 16.0,
        retina: Boolean = true,
    ): String? {
        if (accessToken.isBlank()) return null
        // Mapbox static size is capped at 1280 per side (before the @2x multiplier).
        val w = widthPx.coerceIn(1, 1280)
        val h = heightPx.coerceIn(1, 1280)
        val size = "${w}x$h" + if (retina) "@2x" else ""
        // Path: /static/{style}/{lon},{lat},{zoom}/{width}x{height}
        val center = String.format(Locale.US, "%.6f,%.6f,%.1f", longitude, latitude, zoom)
        return "$BASE_URL/$center/$size?access_token=$accessToken&logo=false&attribution=false"
    }

    private const val BASE_URL =
        "https://api.mapbox.com/styles/v1/mapbox/satellite-v9/static"
}
