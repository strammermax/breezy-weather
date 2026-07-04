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

package com.livewallpaperweather.sources.openweather

import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature
import com.livewallpaperweather.common.source.ConfigurableSource
import com.livewallpaperweather.common.source.HttpSource
import com.livewallpaperweather.common.source.NonFreeNetSource
import com.livewallpaperweather.common.source.WeatherSource

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class OpenWeatherServiceStub() :
    HttpSource(),
    WeatherSource,
    ConfigurableSource,
    NonFreeNetSource {

    override val id = "openweather"
    override val name = "OpenWeather"
    override val continent = SourceContinent.WORLDWIDE

    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to name,
        SourceFeature.CURRENT to name,
        SourceFeature.AIR_QUALITY to name
    )
}
