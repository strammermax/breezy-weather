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

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Couples a geographic area (e.g. "Amsterdam") to a latitude/longitude bounding box
 * and a specific background image URL.
 *
 * This is the manually-curated layer of the wallpaper background system: the user (or a
 * future remote database) can pin a hand-picked photo to a region. When the current GPS
 * position falls inside [contains] this box, [imageUrl] is used directly. When no box
 * matches, [WallpaperRepository] falls back to an Unsplash search by place name.
 */
data class LocationData(
    val name: String,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val imageUrl: String,
) {
    val centerLatitude: Double get() = (minLatitude + maxLatitude) / 2.0
    val centerLongitude: Double get() = (minLongitude + maxLongitude) / 2.0

    /** True when the given coordinates fall inside this area's bounding box. */
    fun contains(latitude: Double, longitude: Double): Boolean {
        return latitude in minLatitude..maxLatitude &&
            longitude in minLongitude..maxLongitude
    }

    /** Great-circle distance in kilometres from the given point to this area's center. */
    fun distanceKmTo(latitude: Double, longitude: Double): Double {
        return haversineKm(centerLatitude, centerLongitude, latitude, longitude)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_NAME, name)
        put(KEY_MIN_LAT, minLatitude)
        put(KEY_MAX_LAT, maxLatitude)
        put(KEY_MIN_LON, minLongitude)
        put(KEY_MAX_LON, maxLongitude)
        put(KEY_IMAGE_URL, imageUrl)
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_MIN_LAT = "minLat"
        private const val KEY_MAX_LAT = "maxLat"
        private const val KEY_MIN_LON = "minLon"
        private const val KEY_MAX_LON = "maxLon"
        private const val KEY_IMAGE_URL = "imageUrl"

        private const val EARTH_RADIUS_KM = 6371.0

        fun fromJson(json: JSONObject): LocationData = LocationData(
            name = json.optString(KEY_NAME),
            minLatitude = json.optDouble(KEY_MIN_LAT),
            maxLatitude = json.optDouble(KEY_MAX_LAT),
            minLongitude = json.optDouble(KEY_MIN_LON),
            maxLongitude = json.optDouble(KEY_MAX_LON),
            imageUrl = json.optString(KEY_IMAGE_URL)
        )

        fun listToJson(items: List<LocationData>): String {
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(raw: String?): List<LocationData> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
            } catch (e: Throwable) {
                emptyList()
            }
        }

        /** Haversine great-circle distance in kilometres between two coordinates. */
        fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
