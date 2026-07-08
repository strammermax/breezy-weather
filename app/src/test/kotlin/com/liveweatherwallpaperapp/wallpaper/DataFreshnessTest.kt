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

package com.liveweatherwallpaperapp.wallpaper

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DataFreshnessTest {

    private val now = 10_000_000_000L
    private val oneHourMillis = 60L * 60L * 1000L

    @Test
    fun `a recent timestamp is not stale`() {
        val refreshedAt = now - oneHourMillis
        DataFreshness.isStale(refreshedAt, now, DataFreshness.MAX_WEATHER_AGE_MILLIS) shouldBe false
    }

    @Test
    fun `a timestamp older than maxAge is stale`() {
        val refreshedAt = now - DataFreshness.MAX_WEATHER_AGE_MILLIS - 1L
        DataFreshness.isStale(refreshedAt, now, DataFreshness.MAX_WEATHER_AGE_MILLIS) shouldBe true
    }

    @Test
    fun `a missing timestamp is treated as stale`() {
        DataFreshness.isStale(null, now, DataFreshness.MAX_WEATHER_AGE_MILLIS) shouldBe true
    }

    @Test
    fun `weather and photo staleness are computed independently`() {
        val freshness = DataFreshness.create(
            weatherRefreshedAtMillis = now - DataFreshness.MAX_WEATHER_AGE_MILLIS - 1L,
            photoRefreshedAtMillis = now - oneHourMillis,
            nowMillis = now
        )

        freshness.isWeatherStale shouldBe true
        freshness.isPhotoStale shouldBe false
    }

    @Test
    fun `staleness is a pure function of timestamp now and maxAge`() {
        val maxAge = 1_000L
        DataFreshness.isStale(refreshedAtMillis = 0L, nowMillis = 1_000L, maxAgeMillis = maxAge) shouldBe false
        DataFreshness.isStale(refreshedAtMillis = 0L, nowMillis = 1_001L, maxAgeMillis = maxAge) shouldBe true
    }

    @Test
    fun `same refresh time is used for derived snapshot values`() {
        val refreshedAt = now - oneHourMillis
        val sceneA = WallpaperSceneStateFactory.create(
            weatherKind = com.liveweatherwallpaperapp.ui.theme.weatherView.WeatherView.WEATHER_KIND_CLEAR,
            daylight = 1f,
            weatherRefreshedAtMillis = refreshedAt
        )
        val sceneB = WallpaperSceneStateFactory.create(
            weatherKind = com.liveweatherwallpaperapp.ui.theme.weatherView.WeatherView.WEATHER_KIND_RAINY,
            daylight = 0f,
            weatherRefreshedAtMillis = refreshedAt
        )

        sceneA.weatherRefreshedAtMillis shouldBe sceneB.weatherRefreshedAtMillis
    }

    @Test
    fun `the freshness calculation is deterministic for the same input`() {
        val first = DataFreshness.create(
            weatherRefreshedAtMillis = now - oneHourMillis,
            photoRefreshedAtMillis = now - oneHourMillis,
            nowMillis = now
        )
        val second = DataFreshness.create(
            weatherRefreshedAtMillis = now - oneHourMillis,
            photoRefreshedAtMillis = now - oneHourMillis,
            nowMillis = now
        )

        first shouldBe second
    }
}
