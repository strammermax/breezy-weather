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

package com.liveweatherwallpaperapp.sources.meteolux

import android.content.Context
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.code
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.getCountryName
import com.liveweatherwallpaperapp.common.source.HttpSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import com.liveweatherwallpaperapp.common.source.ReverseGeocodingSource
import com.liveweatherwallpaperapp.common.source.WeatherSource
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_NONE
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class MeteoLuxServiceStub(context: Context) :
    HttpSource(),
    WeatherSource,
    ReverseGeocodingSource,
    NonFreeNetSource {

    override val id = "meteolux"
    override val name = "MeteoLux (${context.currentLocale.getCountryName("LU")})"
    override val continent = SourceContinent.EUROPE

    private val weatherAttribution by lazy {
        with(context.currentLocale.code) {
            when {
                startsWith("fr") -> "MeteoLux (Transfert universel dans le Domaine Public Creative Commons CC0 1.0)"
                startsWith("de") ->
                    "MeteoLux (universellen Transfers in die Gemeinfreiheit (Public Domain) Creative Commons CC0 1.0)"
                else -> "MeteoLux (Universal transfer into the Public Domain Creative Commons CC0 1.0)."
            }
        } +
            " ${context.getString(R.string.data_modified, context.getString(R.string.brand_name))}"
    }
    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to weatherAttribution,
        SourceFeature.CURRENT to weatherAttribution,
        SourceFeature.ALERT to weatherAttribution,
        SourceFeature.REVERSE_GEOCODING to weatherAttribution
    )

    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return location.countryCode.equals("LU", ignoreCase = true)
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
