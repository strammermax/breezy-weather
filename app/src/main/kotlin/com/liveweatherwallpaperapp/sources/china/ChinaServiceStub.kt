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

package com.liveweatherwallpaperapp.sources.china

import android.content.Context
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.getCountryName
import com.liveweatherwallpaperapp.common.source.HttpSource
import com.liveweatherwallpaperapp.common.source.LocationParametersSource
import com.liveweatherwallpaperapp.common.source.LocationSearchSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import com.liveweatherwallpaperapp.common.source.ReverseGeocodingSource
import com.liveweatherwallpaperapp.common.source.WeatherSource
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_NONE
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature

abstract class ChinaServiceStub(context: Context) :
    HttpSource(),
    WeatherSource,
    LocationSearchSource,
    ReverseGeocodingSource,
    LocationParametersSource,
    NonFreeNetSource {

    override val id = "china"
    override val name = context.currentLocale.getCountryName("CN")
    override val continent = SourceContinent.ASIA

    override val locationSearchAttribution = "北京天气、彩云天气、中国环境监测总站"

    protected val weatherAttribution = "北京天气、彩云天气、中国环境监测总站"
    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to weatherAttribution,
        SourceFeature.CURRENT to weatherAttribution,
        SourceFeature.AIR_QUALITY to weatherAttribution,
        SourceFeature.MINUTELY to weatherAttribution,
        SourceFeature.ALERT to weatherAttribution,
        SourceFeature.REVERSE_GEOCODING to name
    )
    override val attributionLinks = mapOf(
        "彩云天气" to "https://caiyunapp.com/",
        "中国环境监测总站" to "https://www.cnemc.cn/"
    )

    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return location.countryCode.equals("CN", ignoreCase = true)
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
