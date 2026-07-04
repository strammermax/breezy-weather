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

package com.livewallpaperweather.wallpaper.photo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.rx3.awaitFirstOrElse
import com.livewallpaperweather.BuildConfig
import com.livewallpaperweather.sources.SourceManager
import com.livewallpaperweather.sources.android.AndroidLocationService
import javax.inject.Inject
import javax.inject.Singleton

/** A fresh, network-positioned fix resolved to a place, for the wallpaper photo rotation. */
data class WallpaperLocationFix(
    val latitude: Double,
    val longitude: Double,
    val place: PlaceQuery,
)

/**
 * Resolves a fresh device position into a [WallpaperLocationFix] for [WallpaperPhotoRefreshWorker],
 * independent of the app's regular weather/location refresh job (which may run on a much longer
 * interval). Deliberately network/WiFi-based (see [AndroidLocationService.requestNetworkLocation])
 * rather than GPS, since city-level accuracy is enough and this runs far more often than a typical
 * weather refresh.
 */
@Singleton
class WallpaperLocationResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val androidLocationService: AndroidLocationService,
    private val sourceManager: SourceManager,
) {

    suspend fun resolve(): WallpaperLocationFix? {
        val position = androidLocationService.requestNetworkLocation(context) ?: return null

        val address = try {
            sourceManager
                .getReverseGeocodingSourceOrDefault(BuildConfig.DEFAULT_GEOCODING_SOURCE)
                .requestNearestLocation(context, position.latitude, position.longitude)
                .awaitFirstOrElse { emptyList() }
                .firstOrNull()
        } catch (e: Throwable) {
            null
        }

        val place = PlaceQuery(
            city = address?.city?.ifBlank { null },
            municipality = address?.admin2?.ifBlank { null },
            state = address?.admin1?.ifBlank { null },
            country = address?.country?.ifBlank { null },
        )
        return WallpaperLocationFix(position.latitude, position.longitude, place)
    }
}
