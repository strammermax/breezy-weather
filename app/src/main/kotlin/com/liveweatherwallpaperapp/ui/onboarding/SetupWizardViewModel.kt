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

package com.liveweatherwallpaperapp.ui.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.liveweatherwallpaperapp.common.source.getName
import com.liveweatherwallpaperapp.domain.location.model.applyDefaultPreset
import com.liveweatherwallpaperapp.sources.RefreshHelper
import com.liveweatherwallpaperapp.sources.SourceManager
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperImageStore
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import livewallpaperweather.data.location.LocationRepository
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceFeature
import javax.inject.Inject

/**
 * Backs [SetupWizardActivity]. Unlike [com.liveweatherwallpaperapp.ui.main.MainActivityViewModel],
 * this only runs during first-run setup, before any location exists yet -- so locations added
 * during the wizard are kept in session-local state ([locations]) and only written to
 * [LocationRepository] once, via [persistLocations], right before the wizard moves on to the
 * wallpaper steps (which need at least one location to already be in the DB). This also lets
 * [persistLocations] put whichever location the user picked as the wallpaper source first in
 * list order, since [LocationRepository.addAll] always treats its whole input list as the new
 * list order and [WallpaperRepository]'s photo pipeline always operates on
 * `getFirstLocation()`.
 */
@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    application: Application,
    val sourceManager: SourceManager,
    private val locationRepository: LocationRepository,
    private val refreshHelper: RefreshHelper,
    val wallpaperRepository: WallpaperRepository,
) : AndroidViewModel(application) {

    val wallpaperImageStore = WallpaperImageStore(application)

    var locations by mutableStateOf<List<Location>>(emptyList())
        private set

    val hasCurrentLocation: Boolean
        get() = locations.any { it.isCurrentPosition }

    fun locationExists(location: Location): Boolean =
        locations.any { it.formattedId == location.formattedId }

    /** Adds a location returned by [com.liveweatherwallpaperapp.ui.search.SearchActivity] --
     * already has its weather sources resolved (default-applied, or user-tweaked in that
     * activity's own source picker) by the time it comes back. Only the timezone still needs
     * fixing up, same as [com.liveweatherwallpaperapp.ui.main.MainActivityViewModel.addLocation]. */
    suspend fun addSearchedLocation(context: Context, location: Location) {
        val withValidTimeZone = if (location.isTimeZoneInvalid) {
            location.copy(timeZone = refreshHelper.getTimeZoneForLocation(context, location))
        } else {
            location
        }
        locations = locations + withValidTimeZone
    }

    /** Adds "my current position" -- no fixed coordinates yet, resolved live at weather-fetch
     * time, same as the FAB "Add current location" action in ManagementFragment. */
    fun addCurrentLocation() {
        locations = locations + Location(isCurrentPosition = true).applyDefaultPreset(sourceManager)
    }

    /** Read-only weather-source summary row: category label + resolved source name, or "not
     * available" when the location has no source for that category. */
    fun sourceLabelFor(context: Context, location: Location, feature: SourceFeature): String? {
        val sourceId = when (feature) {
            SourceFeature.FORECAST -> location.forecastSource
            SourceFeature.CURRENT -> location.currentSource ?: location.forecastSource
            SourceFeature.AIR_QUALITY -> location.airQualitySource
            SourceFeature.POLLEN -> location.pollenSource
            SourceFeature.MINUTELY -> location.minutelySource
            SourceFeature.ALERT -> location.alertSource
            SourceFeature.NORMALS -> location.normalsSource
            SourceFeature.REVERSE_GEOCODING -> location.reverseGeocodingSource
        }
        if (sourceId.isNullOrEmpty()) return null
        val source = if (feature == SourceFeature.REVERSE_GEOCODING) {
            sourceManager.getReverseGeocodingSource(sourceId)
        } else {
            sourceManager.getWeatherSource(sourceId)
        }
        return source?.getName(context, feature, location) ?: sourceId
    }

    /** The location the live-wallpaper photo pipeline currently targets -- always whichever
     * location is first in list order (see [persistLocations]). */
    suspend fun firstLocation(): Location? = locationRepository.getFirstLocation(withParameters = false)

    /** Writes every session-added location in one go. [wallpaperLocationFormattedId] (when set)
     * is moved to the front so [LocationRepository.getFirstLocation] -- which the live-wallpaper
     * photo pipeline always targets -- resolves to it. */
    suspend fun persistLocations(wallpaperLocationFormattedId: String?): List<Location> {
        val ordered = if (wallpaperLocationFormattedId != null) {
            val chosen = locations.first { it.formattedId == wallpaperLocationFormattedId }
            listOf(chosen) + locations.filterNot { it.formattedId == wallpaperLocationFormattedId }
        } else {
            locations
        }
        return locationRepository.addAll(ordered)
    }
}
