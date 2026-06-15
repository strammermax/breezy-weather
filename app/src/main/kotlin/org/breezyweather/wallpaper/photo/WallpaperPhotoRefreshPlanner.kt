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

import breezyweather.domain.location.model.Location

/**
 * Pure per-location selection logic for [WallpaperPhotoRefreshWorker] (ACT-010 section 7).
 *
 * Cache-limit eviction and the per-location photo count limit are already enforced by
 * [WallpaperRepository.refreshFor] (via `pruneLocationCache`/`prunePhotoCache`); this object only
 * decides *whether* a location should be (re)checked and whether a download can be skipped.
 */
object WallpaperPhotoRefreshPlanner {

    /** Default minimum age of the last refresh before a location is checked again. */
    const val DEFAULT_MIN_REFRESH_INTERVAL_MILLIS: Long = 24L * 60L * 60L * 1000L

    /**
     * A location needs a refresh once at least [minIntervalMillis] have passed since
     * [lastRefreshedAtMillis] (0 = never refreshed, so always due).
     */
    fun needsRefresh(
        lastRefreshedAtMillis: Long,
        nowMillis: Long,
        minIntervalMillis: Long = DEFAULT_MIN_REFRESH_INTERVAL_MILLIS,
    ): Boolean = nowMillis - lastRefreshedAtMillis >= minIntervalMillis

    /**
     * True when the resolved candidate is the same photo that is already active, so the
     * download can be skipped (ACT-010 section 8: avoid re-fetching a recent identical URL).
     */
    fun shouldSkipDownload(candidateUrl: String?, cachedUrl: String?): Boolean =
        !candidateUrl.isNullOrBlank() && candidateUrl == cachedUrl

    /**
     * Locations the worker should consider, in order. Locations without usable coordinates are
     * dropped (ACT-010 section 7: documented fallback for locations without valid data).
     */
    fun locationsToProcess(locations: List<Location>): List<Location> = locations.filter { it.isUsable }
}
