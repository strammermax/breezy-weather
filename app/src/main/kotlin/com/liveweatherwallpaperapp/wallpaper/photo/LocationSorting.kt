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

import com.liveweatherwallpaperapp.wallpaper.WallpaperSeasonGrading
import livewallpaperweather.data.wallpaper.WallpaperPhotoRecord
import java.time.Instant
import java.time.ZoneId

/** What [getMinimalLocationRecs] sorts its result by -- see docs/ACT-021 section 7/8. */
enum class SortItem { DISTANCE, CREATED_DATE }

/** Reference point for [getMinimalLocationRecs]'s is_day/season filtering and (for
 * [SortItem.DISTANCE]) the distance calculation itself. For a GPS-tracked "current position"
 * this is the device's own coordinates; for a fixed location it's that place's own
 * coordinates; for a fictional location it's the device's real position (see
 * `SortLocationRecsByLocation`'s `Werklocation`, docs/ACT-021 section 7a). */
data class LocationContext(val latitude: Double, val longitude: Double)

/**
 * `GetMinimalLocationRecs(locationrecs, minimal, sortitem, location)` from docs/ACT-021
 * section 8: a filter-relaxation cascade shared by both `SortLocationRecsByGPSLocation` and
 * `SortLocationRecsByLocation` -- narrows [locationRecs] by context (day/night, season,
 * weather), relaxing one criterion at a time until more than [minimal] records remain.
 *
 * **Vervangend, niet optellend** (decided): the first stage that on its own already exceeds
 * [minimal] becomes the entire result -- the stricter stage it came from is discarded, not
 * merged in. This deliberately differs from the existing `buildShowlist`, which accumulates
 * across stages.
 */
fun getMinimalLocationRecs(
    locationRecs: List<WallpaperPhotoRecord>,
    minimal: Int,
    sortitem: SortItem,
    location: LocationContext,
    currentWeather: String? = null,
    now: Long = System.currentTimeMillis(),
): List<WallpaperPhotoRecord> {
    val isNight = isCurrentlyNight(location.latitude, location.longitude, now)
    val currentSeason = WallpaperSeasonGrading.seasonFor(
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate(),
        location.latitude
    ).name.lowercase()

    val stages = listOf(
        filterDaySeasonWeather(locationRecs, isNight, currentSeason, currentWeather),
        filterDayWeather(locationRecs, isNight, currentWeather),
        filterDayOnly(locationRecs, isNight),
        noFilter(locationRecs)
    )
    for (stage in stages) {
        val sorted = sortBySortItem(stage, sortitem, location)
        if (enoughForMinimal(sorted, minimal) || stage === stages.last()) return sorted
    }
    return noFilter(locationRecs) // unreachable (last stage always returns above), keeps the compiler happy
}

/** Stage 1: strictest -- matches current day/night, season and weather. */
internal fun filterDaySeasonWeather(
    records: List<WallpaperPhotoRecord>,
    isNight: Boolean,
    currentSeason: String,
    currentWeather: String?,
): List<WallpaperPhotoRecord> = records.filter {
    dayNightMatches(it, isNight) && seasonTier(it, currentSeason) == 2 && weatherMatches(it, currentWeather)
}

/** Stage 2: day/night + weather, season dropped. */
internal fun filterDayWeather(
    records: List<WallpaperPhotoRecord>,
    isNight: Boolean,
    currentWeather: String?,
): List<WallpaperPhotoRecord> = records.filter {
    dayNightMatches(it, isNight) && weatherMatches(it, currentWeather)
}

/** Stage 3: day/night only, weather also dropped. */
internal fun filterDayOnly(records: List<WallpaperPhotoRecord>, isNight: Boolean): List<WallpaperPhotoRecord> =
    records.filter { dayNightMatches(it, isNight) }

/** Stage 4: last resort -- no context filter at all. */
internal fun noFilter(records: List<WallpaperPhotoRecord>): List<WallpaperPhotoRecord> = records

/** True when [currentWeather] is unset (weather matching skipped entirely) or matches the
 * record's own classified weather. */
internal fun weatherMatches(record: WallpaperPhotoRecord, currentWeather: String?): Boolean =
    currentWeather == null || record.weather == currentWeather

/** Generic sort dispatch: [SortItem.DISTANCE] sorts by distance to [location] (nearest
 * first); [SortItem.CREATED_DATE] sorts by [WallpaperPhotoRecord.processedAt] (oldest first --
 * the caller, `sortByRecencyViewsDistance`, is what actually prioritizes newest, this is just
 * the tie-break ordering fed into it). */
internal fun sortBySortItem(
    records: List<WallpaperPhotoRecord>,
    sortitem: SortItem,
    location: LocationContext,
): List<WallpaperPhotoRecord> = when (sortitem) {
    SortItem.DISTANCE -> records.sortedBy { gpsDistanceKmOrWorst(it, location.latitude, location.longitude) }
    SortItem.CREATED_DATE -> records.sortedBy { it.processedAt.orEmpty() }
}

/** Whether [records] has more than [minimal] entries -- the cascade's stopping condition. */
internal fun enoughForMinimal(records: List<WallpaperPhotoRecord>, minimal: Int): Boolean = records.size > minimal
