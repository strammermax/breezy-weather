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

package com.liveweatherwallpaperapp.sources.atmo

import com.liveweatherwallpaperapp.common.source.ConfigurableSource
import com.liveweatherwallpaperapp.common.source.HttpSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import com.liveweatherwallpaperapp.common.source.WeatherSource
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_NONE
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class AtmoServiceStub() :
    HttpSource(),
    WeatherSource,
    ConfigurableSource,
    NonFreeNetSource {

    protected abstract val attribution: String

    override val continent = SourceContinent.EUROPE

    override val supportedFeatures
        get() = mapOf(
            SourceFeature.AIR_QUALITY to attribution
        )
    protected abstract fun isLocationInRegion(location: Location): Boolean
    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return feature == SourceFeature.AIR_QUALITY &&
            !location.countryCode.isNullOrEmpty() &&
            location.countryCode.equals("FR", ignoreCase = true) &&
            isLocationInRegion(location)
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
}
