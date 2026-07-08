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

package com.liveweatherwallpaperapp.sources.bmkg

import android.content.Context
import com.liveweatherwallpaperapp.common.exceptions.NonFreeNetSourceException
import com.liveweatherwallpaperapp.common.preference.Preference
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.location.model.LocationAddressInfo
import livewallpaperweather.domain.source.SourceFeature
import livewallpaperweather.domain.weather.wrappers.WeatherWrapper
import javax.inject.Inject

class BmkgService @Inject constructor(
    @ApplicationContext context: Context,
) : BmkgServiceStub(context) {

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        throw NonFreeNetSourceException()
    }

    override fun requestNearestLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): Observable<List<LocationAddressInfo>> {
        throw NonFreeNetSourceException()
    }

    override val isConfigured = true
    override val isRestricted = false

    override fun getPreferences(context: Context): List<Preference> = emptyList()
}
