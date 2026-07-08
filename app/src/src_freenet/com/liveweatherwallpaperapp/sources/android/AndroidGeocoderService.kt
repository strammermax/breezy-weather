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

package com.liveweatherwallpaperapp.sources.android

import android.content.Context
import com.liveweatherwallpaperapp.common.exceptions.NonFreeNetSourceException
import io.reactivex.rxjava3.core.Observable
import livewallpaperweather.domain.location.model.LocationAddressInfo
import javax.inject.Inject

class AndroidGeocoderService @Inject constructor() : AndroidGeocoderServiceStub() {

    override fun requestNearestLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): Observable<List<LocationAddressInfo>> {
        throw NonFreeNetSourceException()
    }
}
