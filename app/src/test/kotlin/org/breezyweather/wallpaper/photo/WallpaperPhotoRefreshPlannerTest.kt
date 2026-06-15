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
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WallpaperPhotoRefreshPlannerTest {

    private val oneDayMillis = WallpaperPhotoRefreshPlanner.DEFAULT_MIN_REFRESH_INTERVAL_MILLIS
    private val now = 10_000_000_000L

    @Test
    fun `a location with a stale photo needs a refresh`() {
        val lastRefreshedAt = now - oneDayMillis - 1L
        WallpaperPhotoRefreshPlanner.needsRefresh(lastRefreshedAt, now) shouldBe true
    }

    @Test
    fun `a location with a recent photo is skipped`() {
        val lastRefreshedAt = now - oneDayMillis + 1L
        WallpaperPhotoRefreshPlanner.needsRefresh(lastRefreshedAt, now) shouldBe false
    }

    @Test
    fun `a location that was never refreshed needs a refresh`() {
        WallpaperPhotoRefreshPlanner.needsRefresh(lastRefreshedAtMillis = 0L, nowMillis = now) shouldBe true
    }

    @Test
    fun `a recent identical url is not re-downloaded`() {
        WallpaperPhotoRefreshPlanner.shouldSkipDownload(
            candidateUrl = "https://example.com/photo.jpg",
            cachedUrl = "https://example.com/photo.jpg"
        ) shouldBe true
    }

    @Test
    fun `a different url is downloaded`() {
        WallpaperPhotoRefreshPlanner.shouldSkipDownload(
            candidateUrl = "https://example.com/new.jpg",
            cachedUrl = "https://example.com/photo.jpg"
        ) shouldBe false
    }

    @Test
    fun `no cached url means the candidate is downloaded`() {
        WallpaperPhotoRefreshPlanner.shouldSkipDownload(
            candidateUrl = "https://example.com/new.jpg",
            cachedUrl = null
        ) shouldBe false
    }

    @Test
    fun `a location without valid coordinates is dropped as fallback`() {
        val invalid = Location(latitude = 0.0, longitude = 0.0)
        val valid = Location(latitude = 52.3, longitude = 4.7)

        WallpaperPhotoRefreshPlanner.locationsToProcess(listOf(invalid, valid)) shouldBe listOf(valid)
    }

    @Test
    fun `selection logic is deterministic for the same input`() {
        val locations = listOf(
            Location(latitude = 52.3, longitude = 4.7),
            Location(latitude = 0.0, longitude = 0.0),
            Location(latitude = 48.8, longitude = 2.3)
        )

        val first = WallpaperPhotoRefreshPlanner.locationsToProcess(locations)
        val second = WallpaperPhotoRefreshPlanner.locationsToProcess(locations)

        first shouldBe second
    }
}
