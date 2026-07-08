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

package com.liveweatherwallpaperapp.sources.baiduip

import android.content.Context
import com.liveweatherwallpaperapp.common.extensions.code
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.getCountryName
import com.liveweatherwallpaperapp.common.source.ConfigurableSource
import com.liveweatherwallpaperapp.common.source.HttpSource
import com.liveweatherwallpaperapp.common.source.LocationSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import livewallpaperweather.domain.source.SourceContinent

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class BaiduIPLocationServiceStub(context: Context) :
    HttpSource(),
    LocationSource,
    ConfigurableSource,
    NonFreeNetSource {

    override val id = "baidu_ip"
    override val name by lazy {
        with(context.currentLocale.code) {
            when {
                startsWith("zh") -> "百度IP定位"
                else -> "Baidu IP location"
            }
        } +
            " (${context.currentLocale.getCountryName("CN")})"
    }
    override val continent = SourceContinent.ASIA

    override fun hasPermissions(context: Context) = true

    override val permissions: Array<String> = emptyArray()
}
