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

import livewallpaperweather.data.wallpaper.WallpaperPhotoRecord

/**
 * `SortLocationRecsByGPSLocation(location, minimal)` from docs/ACT-021 section 7.4 --
 * scenario 1, "current position": narrows [records] to the smallest radius ring that still
 * has at least [maxImages] candidates (trying tighter and tighter rings, not wider ones --
 * see section 7.4 for why), then hands that ring to [getMinimalLocationRecs] for the final
 * context-relaxation + [SortItem.DISTANCE] sort.
 *
 * A ring needs at least four photos; otherwise the search widens again. The separate cache limit
 * still controls how many selected photos are downloaded, not whether a radius is usable.
 */
fun sortLocationRecsByGPSLocation(
    records: List<WallpaperPhotoRecord>,
    latitude: Double,
    longitude: Double,
    minimal: Int,
    currentWeather: String? = null,
    now: Long = System.currentTimeMillis(),
): List<WallpaperPhotoRecord> {
    val location = LocationContext(latitude, longitude)
    val rings = listOf(
        locationRecsUnrestricted(records),
        locationRecs5km(records, latitude, longitude),
        locationRecs2km(records, latitude, longitude),
        locationRecs1km(records, latitude, longitude),
        locationRecs500m(records, latitude, longitude),
        locationRecs200m(records, latitude, longitude),
        locationRecs100m(records, latitude, longitude)
    )
    val selectedRing = smallestRingWithEnoughPhotos(rings, MIN_NEARBY_PHOTOS)
    return getMinimalLocationRecs(selectedRing, minimal, SortItem.DISTANCE, location, currentWeather, now)
}

/**
 * Returns the narrowest radius ring that still contains [maxImages]. If even the unrestricted
 * ring has fewer records, it remains the best available fallback. Crucially, the first ring that
 * is *too small* must not be selected: doing that used to turn a healthy catalog (for example
 * 190 records location-wide but fewer than 12 inside 5 km) into an empty wallpaper list.
 */
internal fun <T> smallestRingWithEnoughPhotos(rings: List<List<T>>, minimumRequired: Int): List<T> {
    require(rings.isNotEmpty())
    var selected = rings.first()
    for (ring in rings.drop(1)) {
        if (ring.size < minimumRequired) break
        selected = ring
    }
    return selected
}

private const val MIN_NEARBY_PHOTOS = 4

/**
 * `SortLocationRecsByLocation(location, minimal, fictief)` from docs/ACT-021 section 7a --
 * scenarios 2/3, a fixed or fictional location (e.g. Rome or Ghibli): there is no meaningful
 * "distance to me" (the user isn't physically there), so unlike
 * [sortLocationRecsByGPSLocation] there is no radius cascade at all -- straight to
 * [getMinimalLocationRecs] with [SortItem.CREATED_DATE].
 *
 * [fictief] decides which coordinates supply the is_day/season context ("Werklocation" in
 * the source diagram): a fixed real place (Rome) has its own genuine day/night and seasonal
 * cycle, so [locationLatitude]/[locationLongitude] apply; a fictional place (Ghibli) has none,
 * so the device's real [currentLatitude]/[currentLongitude] apply instead.
 */
fun sortLocationRecsByLocation(
    records: List<WallpaperPhotoRecord>,
    minimal: Int,
    fictief: Boolean,
    locationLatitude: Double,
    locationLongitude: Double,
    currentLatitude: Double,
    currentLongitude: Double,
    currentWeather: String? = null,
    now: Long = System.currentTimeMillis(),
): List<WallpaperPhotoRecord> {
    val werklocation = if (fictief) {
        LocationContext(currentLatitude, currentLongitude)
    } else {
        LocationContext(locationLatitude, locationLongitude)
    }
    return getMinimalLocationRecs(records, minimal, SortItem.CREATED_DATE, werklocation, currentWeather, now)
}

// -- Cascade rings (docs/ACT-021 section 7.4.2): each a separate, named function so adding,
// removing or reordering a ring is a one-function change, not a loop-body edit. --

internal fun withinRadiusKm(
    records: List<WallpaperPhotoRecord>,
    latitude: Double,
    longitude: Double,
    radiusKm: Double,
): List<WallpaperPhotoRecord> = records.filter { gpsDistanceKmOrWorst(it, latitude, longitude) <= radiusKm }

internal fun locationRecsUnrestricted(records: List<WallpaperPhotoRecord>): List<WallpaperPhotoRecord> = records

internal fun locationRecs5km(records: List<WallpaperPhotoRecord>, lat: Double, lon: Double) =
    withinRadiusKm(records, lat, lon, 5.0)

internal fun locationRecs2km(records: List<WallpaperPhotoRecord>, lat: Double, lon: Double) =
    withinRadiusKm(records, lat, lon, 2.0)

internal fun locationRecs1km(records: List<WallpaperPhotoRecord>, lat: Double, lon: Double) =
    withinRadiusKm(records, lat, lon, 1.0)

internal fun locationRecs500m(records: List<WallpaperPhotoRecord>, lat: Double, lon: Double) =
    withinRadiusKm(records, lat, lon, 0.5)

internal fun locationRecs200m(records: List<WallpaperPhotoRecord>, lat: Double, lon: Double) =
    withinRadiusKm(records, lat, lon, 0.2)

internal fun locationRecs100m(records: List<WallpaperPhotoRecord>, lat: Double, lon: Double) =
    withinRadiusKm(records, lat, lon, 0.1)

/** Whether [records] meets or exceeds [maxImages] -- the cascade's "go narrower" gate. */
internal fun hasEnough(records: List<WallpaperPhotoRecord>, maxImages: Int): Boolean = records.size >= maxImages
