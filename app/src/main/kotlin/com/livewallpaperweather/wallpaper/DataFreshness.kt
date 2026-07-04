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

package com.livewallpaperweather.wallpaper

/**
 * Pure data-freshness model for the live wallpaper's local snapshot (ACT-011).
 *
 * Built from timestamps already recorded in the central local data layer (the weather's
 * `refreshTime` and [com.livewallpaperweather.wallpaper.photo.WallpaperImageStore]'s
 * `photoRefreshedAtFor`, see ACT-010). Never performs network or location lookups itself.
 *
 * A missing timestamp is treated as "unknown", and unknown counts as stale (ACT-011 section 7/11):
 * the wallpaper has no evidence the data is recent, so it falls back to showing the last valid
 * scene with a stale indication rather than claiming freshness it cannot verify.
 */
data class DataFreshness(
    val weatherRefreshedAtMillis: Long?,
    val photoRefreshedAtMillis: Long?,
    val isWeatherStale: Boolean,
    val isPhotoStale: Boolean,
) {
    companion object {
        /** Weather older than this (or with no known refresh time) is considered stale. */
        const val MAX_WEATHER_AGE_MILLIS: Long = 24L * 60L * 60L * 1000L

        /** Photos older than this (or with no known refresh time) are considered stale. */
        const val MAX_PHOTO_AGE_MILLIS: Long = 48L * 60L * 60L * 1000L

        /**
         * A timestamp is stale when it is missing (unknown) or older than [maxAgeMillis]
         * relative to [nowMillis]. Pure function of its inputs, never the real wall clock
         * directly.
         */
        fun isStale(refreshedAtMillis: Long?, nowMillis: Long, maxAgeMillis: Long): Boolean =
            refreshedAtMillis == null || nowMillis - refreshedAtMillis > maxAgeMillis

        /** Builds a [DataFreshness] snapshot, deriving weather/photo staleness independently. */
        fun create(
            weatherRefreshedAtMillis: Long?,
            photoRefreshedAtMillis: Long?,
            nowMillis: Long,
            maxWeatherAgeMillis: Long = MAX_WEATHER_AGE_MILLIS,
            maxPhotoAgeMillis: Long = MAX_PHOTO_AGE_MILLIS,
        ): DataFreshness = DataFreshness(
            weatherRefreshedAtMillis = weatherRefreshedAtMillis,
            photoRefreshedAtMillis = photoRefreshedAtMillis,
            isWeatherStale = isStale(weatherRefreshedAtMillis, nowMillis, maxWeatherAgeMillis),
            isPhotoStale = isStale(photoRefreshedAtMillis, nowMillis, maxPhotoAgeMillis),
        )
    }
}
