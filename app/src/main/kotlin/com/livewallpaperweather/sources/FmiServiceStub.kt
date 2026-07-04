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

package com.livewallpaperweather.sources.fmi

import android.content.Context
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature
import com.google.maps.android.model.LatLng
import com.google.maps.android.model.LatLngBounds
import com.livewallpaperweather.common.extensions.code
import com.livewallpaperweather.common.extensions.currentLocale
import com.livewallpaperweather.common.extensions.getCountryName
import com.livewallpaperweather.common.source.HttpSource
import com.livewallpaperweather.common.source.LocationParametersSource
import com.livewallpaperweather.common.source.NonFreeNetSource
import com.livewallpaperweather.common.source.WeatherSource
import com.livewallpaperweather.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import com.livewallpaperweather.common.source.WeatherSource.Companion.PRIORITY_NONE

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class FmiServiceStub(context: Context) :
    HttpSource(),
    WeatherSource,
    LocationParametersSource,
    NonFreeNetSource {

    override val id = "fmi"
    override val name = "FMI (${context.currentLocale.getCountryName("FI")})"
    override val continent = SourceContinent.EUROPE

    private val weatherAttribution by lazy {
        with(context.currentLocale.code) {
            when {
                startsWith("fi") -> "Ilmatieteen Laitos"
                startsWith("sv") -> "Meteorologiska Institutet"
                else -> "Finnish Meteorological Institute"
            }
        }
    }

    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to weatherAttribution,
        SourceFeature.CURRENT to weatherAttribution,
        SourceFeature.AIR_QUALITY to weatherAttribution,
        SourceFeature.ALERT to weatherAttribution,
        SourceFeature.NORMALS to weatherAttribution
    )

    override fun isFeatureSupportedForLocation(location: Location, feature: SourceFeature): Boolean {
        return when (feature) {
            SourceFeature.FORECAST -> FMI_FORECAST_BBOX.contains(LatLng(location.latitude, location.longitude))
            SourceFeature.AIR_QUALITY -> FMI_SILAM_BBOX.contains(LatLng(location.latitude, location.longitude))
            else -> arrayOf("FI", "AX").any { location.countryCode.equals(it, ignoreCase = true) }
        }
    }

    override fun getFeaturePriorityForLocation(location: Location, feature: SourceFeature): Int {
        return when {
            arrayOf("FI", "AX").any { location.countryCode.equals(it, ignoreCase = true) } -> PRIORITY_HIGHEST
            else -> PRIORITY_NONE
        }
    }

    companion object {
        private val FMI_FORECAST_BBOX = LatLngBounds(
            LatLng(53.900000, 4.888770),
            LatLng(72.269067, 32.931465)
        )
        private val FMI_SILAM_BBOX = LatLngBounds(
            LatLng(30.050000, -24.950000),
            LatLng(72.049999, 45.049999)
        )
    }
}
