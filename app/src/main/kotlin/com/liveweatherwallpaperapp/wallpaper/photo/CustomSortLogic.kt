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
 * Kotlin translation of `docs/customsortlogic.md` (see docs/ACT-021 section 9): the final
 * presentation order applied exactly once, to the whole `Resultlist`, after
 * [sortLocationRecsByGPSLocation]/[sortLocationRecsByLocation]. Priority: recency margin (>= 5
 * days newer wins outright) > fewest views > closest distance.
 *
 * Replaces the ad hoc tiebreak chains in the old `selectWallpaperPhoto`/`buildShowlist`
 * (`.thenBy { viewCount }.thenBy { lastShownAt }.thenBy { createdAt }` and
 * `.thenByDescending { createdAt }.thenBy { viewCount }` respectively) -- exactly the
 * inconsistency the analysis doc flagged.
 */
fun sortByRecencyViewsDistance(
    records: List<WallpaperPhotoRecord>,
    latitude: Double,
    longitude: Double,
    now: Long = System.currentTimeMillis(),
): List<WallpaperPhotoRecord> = records.sortedWith(
    recencyMarginComparator(now = now)
        .thenComparing(viewCountComparator())
        .thenComparing(distanceComparator(latitude, longitude))
)

/** processedAt (RemoveSky's `processed_at`, NOT [WallpaperPhotoRecord.createdAt] or
 * [WallpaperPhotoRecord.capturedAt] -- see docs/ACT-021 section 6) compared with a fixed
 * margin: an item at least [marginDays] more recent wins outright; within the margin, neither
 * side wins here (0, next comparator decides). Missing/unparseable `processedAt` sorts as
 * "infinitely old" so records still awaiting a `processed_at` never spuriously look "recent". */
internal fun recencyMarginComparator(marginDays: Long = 5, now: Long): Comparator<WallpaperPhotoRecord> =
    Comparator { a, b ->
        val dA = daysSinceProcessed(a, now)
        val dB = daysSinceProcessed(b, now)
        when {
            dB <= dA - marginDays -> 1 // B is at least marginDays days more recent than A
            dA <= dB - marginDays -> -1 // A is at least marginDays days more recent than B
            else -> 0 // within the margin: no verdict, next comparator decides
        }
    }

private fun daysSinceProcessed(record: WallpaperPhotoRecord, now: Long): Long {
    val processedAtMillis =
        record.processedAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: return Long.MAX_VALUE / MS_PER_DAY
    return (now - processedAtMillis) / MS_PER_DAY
}

/** Fewest views wins -- ascending [WallpaperPhotoRecord.viewCount]. */
internal fun viewCountComparator(): Comparator<WallpaperPhotoRecord> =
    Comparator { a, b -> a.viewCount - b.viewCount }

/** Closest wins -- ascending distance to (latitude, longitude), reusing the same
 * EXIF-then-resolved-coordinates fallback as everywhere else (see [gpsDistanceKmOrWorst]). */
internal fun distanceComparator(latitude: Double, longitude: Double): Comparator<WallpaperPhotoRecord> =
    Comparator { a, b ->
        gpsDistanceKmOrWorst(a, latitude, longitude)
            .compareTo(gpsDistanceKmOrWorst(b, latitude, longitude))
    }

private const val MS_PER_DAY = 24L * 60 * 60 * 1000
