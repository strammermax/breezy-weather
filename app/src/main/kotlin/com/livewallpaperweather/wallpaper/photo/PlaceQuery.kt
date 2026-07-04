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

package com.livewallpaperweather.wallpaper.photo

/**
 * A resolved place, used to build background-image queries. Carries the administrative
 * hierarchy so [searchTerms] can fall back from the most specific name (the city) to the
 * most generic (the country) when a provider returns nothing for the exact place.
 *
 * Privacy: only place names are kept here — never the exact GPS coordinates (those stay in
 * the caller and are passed separately, only to geo-aware providers).
 */
data class PlaceQuery(
    /** City / town / village, e.g. "Hoofddorp". */
    val city: String? = null,
    /** Municipality / county (admin2), e.g. "Haarlemmermeer". */
    val municipality: String? = null,
    /** State / province (admin1), e.g. "Noord-Holland". */
    val state: String? = null,
    /** Country, e.g. "Netherlands". */
    val country: String? = null,
) {

    /** Best human-readable name for this place (used for cache keys / logging). */
    val displayName: String
        get() = sequenceOf(city, municipality, state, country)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?: "location"

    /**
     * Ordered, de-duplicated list of free-text queries to try, from most specific to most
     * generic. Example for Hoofddorp:
     *  `["Hoofddorp", "Hoofddorp Netherlands", "Haarlemmermeer", "Noord-Holland", "Netherlands"]`
     */
    fun searchTerms(): List<String> {
        val terms = LinkedHashSet<String>()
        fun add(term: String?) {
            val t = term?.trim()
            if (!t.isNullOrBlank()) terms.add(t)
        }
        val countryName = country?.trim()?.takeIf { it.isNotBlank() }
        add(city)
        if (!city.isNullOrBlank() && countryName != null) add("${city.trim()} $countryName")
        add(municipality)
        add(state)
        add(countryName)
        return terms.toList()
    }

    /**
     * Filesystem-safe, per-place cache file name, e.g. "Hoofddorp" -> `wallpaper_Hoofddorp.jpg`.
     * Keeps each visited place's photo so revisiting is instant and offline-friendly.
     */
    fun cacheFileName(): String {
        val safe = displayName
            .replace(NON_FILENAME, "_")
            .trim('_')
            .ifBlank { "location" }
        return "wallpaper_$safe.jpg"
    }

    companion object {
        private val NON_FILENAME = Regex("[^A-Za-z0-9]+")
    }
}
