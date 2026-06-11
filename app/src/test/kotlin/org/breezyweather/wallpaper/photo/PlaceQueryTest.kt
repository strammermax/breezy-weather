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

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlaceQueryTest {

    @Test
    fun `search terms go from most specific to most generic`() {
        val place = PlaceQuery(
            city = "Hoofddorp",
            municipality = "Haarlemmermeer",
            state = "Noord-Holland",
            country = "Netherlands",
        )
        place.searchTerms() shouldBe listOf(
            "Hoofddorp",
            "Hoofddorp Netherlands",
            "Haarlemmermeer",
            "Noord-Holland",
            "Netherlands",
        )
    }

    @Test
    fun `search terms skip blank and duplicate fields`() {
        val place = PlaceQuery(
            city = "Paris",
            municipality = "  ",
            state = "Paris", // duplicate of city -> dropped
            country = "France",
        )
        place.searchTerms() shouldBe listOf(
            "Paris",
            "Paris France",
            "France",
        )
    }

    @Test
    fun `search terms fall back to country when no city is known`() {
        val place = PlaceQuery(city = null, country = "Netherlands")
        place.searchTerms() shouldBe listOf("Netherlands")
    }

    @Test
    fun `display name prefers the most specific non-blank field`() {
        PlaceQuery(city = "", municipality = "Haarlemmermeer").displayName shouldBe "Haarlemmermeer"
        PlaceQuery(city = "Hoofddorp").displayName shouldBe "Hoofddorp"
        PlaceQuery().displayName shouldBe "location"
    }

    @Test
    fun `cache file name is filesystem-safe and per place`() {
        PlaceQuery(city = "Hoofddorp").cacheFileName() shouldBe "wallpaper_Hoofddorp.jpg"
        PlaceQuery(city = "'s-Hertogenbosch").cacheFileName() shouldBe "wallpaper_s_Hertogenbosch.jpg"
        PlaceQuery(city = "São Paulo").cacheFileName() shouldBe "wallpaper_S_o_Paulo.jpg"
        PlaceQuery().cacheFileName() shouldBe "wallpaper_location.jpg"
    }
}
