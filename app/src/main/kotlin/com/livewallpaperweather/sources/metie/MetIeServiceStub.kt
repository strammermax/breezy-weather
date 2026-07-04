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

package com.livewallpaperweather.sources.metie

import android.content.Context
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature
import com.livewallpaperweather.R
import com.livewallpaperweather.common.extensions.currentLocale
import com.livewallpaperweather.common.extensions.getCountryName
import com.livewallpaperweather.common.source.ConfigurableSource
import com.livewallpaperweather.common.source.HttpSource
import com.livewallpaperweather.common.source.LocationParametersSource
import com.livewallpaperweather.common.source.NonFreeNetSource
import com.livewallpaperweather.common.source.ReverseGeocodingSource
import com.livewallpaperweather.common.source.WeatherSource
import com.livewallpaperweather.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import com.livewallpaperweather.common.source.WeatherSource.Companion.PRIORITY_NONE

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class MetIeServiceStub(context: Context) :
    HttpSource(),
    WeatherSource,
    ReverseGeocodingSource,
    LocationParametersSource,
    ConfigurableSource,
    NonFreeNetSource {

    override val id = "metie"
    private val countryName = context.currentLocale.getCountryName("IE")
    override val name = "MET Éireann".let {
        if (it.contains(countryName)) {
            it
        } else {
            "$it ($countryName)"
        }
    }
    override val continent = SourceContinent.EUROPE

    // Terms require: copyright + source + license (with link) + disclaimer + mention of modified data
    private val weatherAttribution = "Copyright Met Éireann. Source met.ie. This data is published under a " +
        "Commons Attribution 4.0 International (CC BY 4.0). Met Éireann does not accept any liability whatsoever " +
        "for any error or omission in the data, their availability, or for any loss or damage arising from their " +
        "use. ${context.getString(R.string.data_modified, context.getString(R.string.brand_name))}"
    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to weatherAttribution,
        SourceFeature.ALERT to weatherAttribution,
        SourceFeature.REVERSE_GEOCODING to weatherAttribution
    )

    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return location.countryCode.equals("IE", ignoreCase = true)
    }

    override fun getFeaturePriorityForLocation(
        location: Location,
        feature: SourceFeature,
    ): Int {
        return when {
            isFeatureSupportedForLocation(location, feature) -> PRIORITY_HIGHEST
            else -> PRIORITY_NONE
        }
    }

    // Only supports its own country
    override val knownAmbiguousCountryCodes: Array<String>? = null
}
