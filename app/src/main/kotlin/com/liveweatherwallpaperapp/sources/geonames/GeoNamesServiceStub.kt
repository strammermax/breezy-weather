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

package com.liveweatherwallpaperapp.sources.geonames

import com.liveweatherwallpaperapp.common.source.ConfigurableSource
import com.liveweatherwallpaperapp.common.source.HttpSource
import com.liveweatherwallpaperapp.common.source.LocationSearchSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import livewallpaperweather.domain.source.SourceContinent

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class GeoNamesServiceStub() :
    HttpSource(),
    LocationSearchSource,
    ConfigurableSource,
    NonFreeNetSource {

    override val id = "geonames"
    override val name = "GeoNames"
    override val continent = SourceContinent.WORLDWIDE
    override val privacyPolicyUrl = ""

    override val locationSearchAttribution = "GeoNames (CC BY 4.0)"

    // No known ambiguous code
    override val knownAmbiguousCountryCodes: Array<String> = emptyArray()
}
