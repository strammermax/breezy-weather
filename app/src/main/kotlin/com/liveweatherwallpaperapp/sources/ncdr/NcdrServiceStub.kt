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

package com.liveweatherwallpaperapp.sources.ncdr

import android.content.Context
import com.google.maps.android.model.LatLng
import com.liveweatherwallpaperapp.common.extensions.code
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.getCountryName
import com.liveweatherwallpaperapp.common.source.HttpSource
import com.liveweatherwallpaperapp.common.source.LocationParametersSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import com.liveweatherwallpaperapp.common.source.WeatherSource
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_NONE
import com.liveweatherwallpaperapp.sources.nlsc.NlscServiceStub.Companion.KINMEN_BBOX
import com.liveweatherwallpaperapp.sources.nlsc.NlscServiceStub.Companion.MATSU_BBOX
import com.liveweatherwallpaperapp.sources.nlsc.NlscServiceStub.Companion.PENGHU_BBOX
import com.liveweatherwallpaperapp.sources.nlsc.NlscServiceStub.Companion.TAIWAN_BBOX
import com.liveweatherwallpaperapp.sources.nlsc.NlscServiceStub.Companion.WUQIU_BBOX
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class NcdrServiceStub(context: Context) :
    HttpSource(),
    WeatherSource,
    LocationParametersSource,
    NonFreeNetSource {

    override val id = "ncdr"
    override val name by lazy {
        with(context.currentLocale.code) {
            when {
                startsWith("zh") -> "國家災害防救科技中心"
                else -> "NCDR"
            }
        } +
            " (${context.currentLocale.getCountryName("TW")})"
    }
    override val continent = SourceContinent.ASIA

    protected val weatherAttribution by lazy {
        with(context.currentLocale.code) {
            when {
                startsWith("zh") -> "國家災害防救科技中心"
                else -> "National Science and Technology Center for Disaster Reduction"
            }
        }
    }
    override val supportedFeatures = mapOf(
        SourceFeature.ALERT to weatherAttribution
    )

    override fun isFeatureSupportedForLocation(location: Location, feature: SourceFeature): Boolean {
        val latLng = LatLng(location.latitude, location.longitude)
        return location.countryCode.equals("TW", ignoreCase = true) ||
            TAIWAN_BBOX.contains(latLng) ||
            PENGHU_BBOX.contains(latLng) ||
            KINMEN_BBOX.contains(latLng) ||
            WUQIU_BBOX.contains(latLng) ||
            MATSU_BBOX.contains(latLng)
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
