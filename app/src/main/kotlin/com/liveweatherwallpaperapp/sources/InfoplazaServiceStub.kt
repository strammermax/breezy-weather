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

package com.liveweatherwallpaperapp.sources.infoplaza

import com.liveweatherwallpaperapp.common.source.ConfigurableSource
import com.liveweatherwallpaperapp.common.source.HttpSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import com.liveweatherwallpaperapp.common.source.WeatherSource
import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature

abstract class InfoplazaServiceStub :
    HttpSource(),
    WeatherSource,
    ConfigurableSource,
    NonFreeNetSource {

    override val id = "infoplaza"
    override val name = "Infoplaza"
    override val continent = SourceContinent.WORLDWIDE

    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to name,
        SourceFeature.CURRENT to name,
        SourceFeature.MINUTELY to name,
        SourceFeature.NORMALS to name
    )

    override val isRestricted = false
}
