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

package com.liveweatherwallpaperapp.ui.main

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.maps.android.SphericalUtil
import com.google.maps.android.model.LatLng
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.AppMessageStore
import com.liveweatherwallpaperapp.common.activities.BreezyViewModel
import com.liveweatherwallpaperapp.common.activities.livedata.BusLiveData
import com.liveweatherwallpaperapp.common.extensions.hasPermission
import com.liveweatherwallpaperapp.common.extensions.launchIO
import com.liveweatherwallpaperapp.common.source.RefreshError
import com.liveweatherwallpaperapp.common.utils.helpers.AsyncHelper
import com.liveweatherwallpaperapp.common.utils.helpers.SnackbarHelper
import com.liveweatherwallpaperapp.domain.location.model.applyDefaultPreset
import com.liveweatherwallpaperapp.domain.location.model.isCloseTo
import com.liveweatherwallpaperapp.domain.settings.CurrentLocationStore
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.sources.RefreshHelper
import com.liveweatherwallpaperapp.sources.SourceManager
import com.liveweatherwallpaperapp.ui.main.utils.RefreshErrorType
import com.liveweatherwallpaperapp.ui.main.utils.StatementManager
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperPhotoRefreshWorker
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperRepository
import com.liveweatherwallpaperapp.wallpaper.photo.toWallpaperPlaceQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import livewallpaperweather.data.location.LocationRepository
import livewallpaperweather.data.weather.WeatherRepository
import livewallpaperweather.domain.location.model.Location
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

interface WeatherRequestCallback {
    fun onCompleted(
        location: Location,
        errors: List<RefreshError> = emptyList(),
    )
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    val statementManager: StatementManager,
    private val refreshHelper: RefreshHelper,
    private val sourceManager: SourceManager,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val currentLocationStore: CurrentLocationStore,
    private val wallpaperRepository: WallpaperRepository,
) : BreezyViewModel(application), WeatherRequestCallback {

    // flow
    private val _currentLocation: MutableStateFlow<DayNightLocation?> = MutableStateFlow(null)
    val currentLocation = _currentLocation.asStateFlow()

    private val _validLocationList = MutableStateFlow<List<Location>>(emptyList())
    val validLocationList = _validLocationList.asStateFlow()

    private val _dialogChooseWeatherSourcesOpen = MutableStateFlow(false)
    val dialogChooseWeatherSourcesOpen = _dialogChooseWeatherSourcesOpen.asStateFlow()

    private val _dialogChooseDebugLocationOpen = MutableStateFlow(false)
    val dialogChooseDebugLocationOpen = _dialogChooseDebugLocationOpen.asStateFlow()

    private val _dialogRefreshErrorDetails = MutableStateFlow(false)
    val dialogRefreshErrorDetails = _dialogRefreshErrorDetails.asStateFlow()

    /**
     * Selected location for the “Select sources” dialog in the location list
     */
    private val _selectedLocation: MutableStateFlow<Location?> = MutableStateFlow(null)
    val selectedLocation = _selectedLocation.asStateFlow()

    private val _locationListLoading = MutableStateFlow(false)
    val locationListLoading = _locationListLoading.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _indicator = MutableStateFlow(
        Indicator(
            total = 0,
            index = 0
        )
    ) // Is overwritten on init
    val indicator = _indicator.asStateFlow()

    val locationPermissionsRequest: MutableStateFlow<PermissionsRequest?> = MutableStateFlow(null)
    val snackbarError = BusLiveData<List<RefreshError>>(Handler(Looper.getMainLooper()))

    // inner data.

    private val _initCompleted = MutableStateFlow(false)
    val initCompleted = _initCompleted.asStateFlow()
    private var updating = false

    // life cycle.
    fun init(formattedId: String? = null) {
        onCleared()

        // init live data.
        // TODO: Not doing that causes blinking, to be investigated
        runBlocking {
            val validList = initLocations()

            val id = formattedId ?: validList.getOrNull(0)?.formattedId
            val current = validList.firstOrNull { item -> item.formattedId == id }

            current?.let {
                _currentLocation.value = DayNightLocation(location = it)
            }
            _validLocationList.value = validList

            _loading.value = false
            _indicator.value = Indicator(
                total = validList.size,
                index = validList.indexOfFirst { it.formattedId == id }
            )

            locationPermissionsRequest.value = null
            snackbarError.setValue(emptyList())

            _initCompleted.value = true
        }
    }

    // update inner data.
    private fun updateInnerData(location: Location, oldLocation: Location? = null) {
        val valid = validLocationList.value.toMutableList()

        for (i in valid.indices) {
            if (valid[i].formattedId == (oldLocation?.formattedId ?: location.formattedId)) {
                valid[i] = location
                break
            }
        }

        updateInnerData(valid)
    }

    /**
     * @param location: If null, will create a location of type currentPosition
     */
    fun openChooseWeatherSourcesDialog(location: Location) {
        _selectedLocation.value = location
        _dialogChooseWeatherSourcesOpen.value = true
    }

    fun closeChooseWeatherSourcesDialog() {
        _dialogChooseWeatherSourcesOpen.value = false
        _selectedLocation.value = null
    }

    fun openChooseDebugLocationDialog() {
        _dialogChooseDebugLocationOpen.value = true
    }

    fun closeChooseDebugLocationDialog() {
        _dialogChooseDebugLocationOpen.value = false
    }

    fun setRefreshErrorDetailsDialogVisible(visible: Boolean) {
        _dialogRefreshErrorDetails.value = visible
    }

    private fun updateInnerData(newValid: List<Location>) {
        // get valid locations and current index.
        var index: Int? = null
        if (newValid.size != validLocationList.value.size) {
            if (newValid.size > validLocationList.value.size) {
                // New location added case
                index = newValid.size - 1
            } else {
                // Look for our location by formattedId in the new list (maybe the location deleted
                // is not the currently focused location!)
                for (i in newValid.indices) {
                    if (newValid[i].formattedId == currentLocation.value?.location?.formattedId) {
                        index = i
                        break
                    }
                }
            } // Deleted location case
        } else {
            // If the size didn't change, look for our location by formattedId in the new list
            for (i in newValid.indices) {
                if (newValid[i].formattedId == currentLocation.value?.location?.formattedId) {
                    index = i
                    break
                }
            }

            // If we didn't find it, it means formattedId changed (main weather source was changed)!
            // In that case, pick up latest known index for that location
            if (index == null) {
                for (i in validLocationList.value.indices) {
                    if (validLocationList.value[i].formattedId == currentLocation.value?.location?.formattedId) {
                        index = i
                        break
                    }
                }
            }
        }

        index = index ?: 0

        _indicator.value = Indicator(total = newValid.size, index = index)

        // check difference in valid locations.
        if (newValid.isNotEmpty()) {
            val diffInValidLocations = validLocationList.value != newValid
            if (diffInValidLocations ||
                (currentLocation.value?.location?.formattedId ?: "") != newValid[index].formattedId
            ) {
                // update current location.
                setCurrentLocation(newValid[index])

                _validLocationList.value = newValid
            } else {
                // update current location.
                setCurrentLocation(newValid[index])
            }
        } else {
            setCurrentLocation(null)
            _validLocationList.value = newValid
        }
    }

    private fun setCurrentLocation(location: Location?) {
        _currentLocation.value = location?.let {
            DayNightLocation(location = it)
        }
        savedStateHandle[KEY_FORMATTED_ID] = location?.formattedId

        checkToUpdateCurrentLocation()
    }

    fun locationListSize(): Int {
        return validLocationList.value.size
    }

    /**
     * Called on background thread
     */
    private fun onUpdateResult(
        location: Location,
        errors: List<RefreshError> = emptyList(),
    ) {
        snackbarError.postValue(errors)

        // Manual refreshes run through this ViewModel instead of WeatherUpdateJob. Clear the
        // persisted background-job warning as soon as usable weather arrives, otherwise the
        // refreshed forecast says "0 min ago" while the old failure card remains on screen.
        if (location.weather != null) {
            AppMessageStore(getApplication()).clear("weather_update_failed")
        }

        updateInnerData(location)

        // A newly-added current-position location starts as an unusable 0,0 placeholder. The
        // first photo worker therefore has nothing it can resolve. RefreshHelper has persisted
        // the real GPS coordinates/place by the time this callback runs, so retry now when the
        // installation still has no background photo. Fixed first locations normally get their
        // photo from addLocation's post-write refresh below; this also safely covers a missed or
        // interrupted first attempt.
        if (location.isUsable && wallpaperRepository.loadCachedBitmap() == null) {
            WallpaperPhotoRefreshWorker.startNow(getApplication())
        }

        _loading.value = false
        updating = false
    }

    private fun checkToUpdateCurrentLocation() {
        // is not loading
        if (!updating) {
            // if already valid, just return.
            if (currentLocationIsValid()) return

            // If we don't have any location yet, just return
            if (currentLocation.value?.location == null) {
                updating = false
                return
            }

            // if is not valid, we need:
            // update if init completed.
            // otherwise, mark a loading state and wait the init progress complete.
            if (initCompleted.value) {
                if (dialogRefreshErrorDetails.value) {
                    // When refreshing, some of the errors shown in the dialog might be outdated. We should close it,
                    // so users can directly see new errors if any occur.
                    setRefreshErrorDetailsDialogVisible(false)
                }
                updateWithUpdatingChecking(
                    triggeredByUser = false,
                    checkPermissions = true
                )
            } else {
                _loading.value = true
                updating = false
            }
            return
        }

        // is loading, do nothing.
    }

    private fun currentLocationIsValid() =
        currentLocation.value?.location?.weather?.isValid(
            SettingsManager.getInstance(getApplication()).updateInterval.validity
        ) ?: false

    // update.
    fun updateWithUpdatingChecking(
        triggeredByUser: Boolean,
        checkPermissions: Boolean,
    ) {
        if (updating) return
        val locationToCheck = currentLocation.value?.location
        if (locationToCheck == null) {
            _loading.value = true
            _loading.value = false
            return
        }

        if (SettingsManager.getInstance(getApplication()).weatherManualUpdateLastLocationId ==
            locationToCheck.formattedId &&
            SettingsManager.getInstance(getApplication()).weatherManualUpdateLastTimestamp +
            DELAY_BEFORE_NEXT_MANUAL_REFRESH >
            Date().time
        ) {
            _loading.value = true
            _loading.value = false
            SnackbarHelper.showSnackbar(
                getApplication<Application>()
                    .getString(R.string.weather_message_too_frequent_refreshes)
            )
            return
        }
        _loading.value = true

        if (!checkPermissions) {
            updating = true
            SettingsManager.getInstance(getApplication())
                .weatherManualUpdateLastTimestamp = Date().time
            SettingsManager.getInstance(getApplication())
                .weatherManualUpdateLastLocationId = locationToCheck.formattedId
            viewModelScope.launchIO {
                getWeather(
                    getApplication(),
                    locationToCheck,
                    this@MainActivityViewModel
                )
            }
            return
        }

        // check permissions.
        val locationPermissionList: MutableList<String> = mutableListOf()
        if (locationToCheck.isCurrentPosition) {
            locationPermissionList.addAll(
                getLocatePermissionList(getApplication())
                    .filter { !(getApplication() as Application).hasPermission(it) }
                    .toMutableList()
            )
        }
        if (locationPermissionList.isEmpty()) {
            // already got all permissions -> request data directly.
            updating = true
            SettingsManager.getInstance(getApplication())
                .weatherManualUpdateLastTimestamp = Date().time
            SettingsManager.getInstance(getApplication())
                .weatherManualUpdateLastLocationId = locationToCheck.formattedId
            viewModelScope.launchIO {
                getWeather(
                    getApplication(),
                    locationToCheck,
                    this@MainActivityViewModel
                )
            }
        } else {
            updating = false
            locationPermissionsRequest.value = PermissionsRequest(
                locationPermissionList,
                locationToCheck,
                triggeredByUser
            )
        }
    }

    fun cancelRequest() {
        updating = false
        _loading.value = false
    }

    fun checkToUpdate() {
        checkToUpdateCurrentLocation()
    }

    fun updateLocationFromBackground(location: Location) {
        if (!initCompleted.value) {
            return
        }

        // Only updates are coming here (no location added or deleted)
        // If we don't find the formattedId in the current list, it means main source was changed
        // for currently focused location
        val oldLocation = validLocationList.value.firstOrNull {
            it.formattedId == location.formattedId
        } ?: currentLocation.value?.location

        if (currentLocation.value?.location?.formattedId == (oldLocation?.formattedId ?: location.formattedId)) {
            cancelRequest()
        }
        updateInnerData(location, oldLocation)
    }

    // set location.
    fun setLocation(formattedId: String) {
        val oldFormattedId = currentLocation.value?.location?.formattedId ?: ""
        if (formattedId != oldFormattedId) {
            cancelRequest()

            validLocationList.value.let { locationList ->
                val index = locationList.indexOfFirst { it.formattedId == formattedId }

                if (index >= 0) {
                    setCurrentLocation(locationList[index])

                    _indicator.value = Indicator(
                        total = locationList.size,
                        index = index
                    )

                    // The live wallpaper always reads the first location in this list (it has
                    // no reactive listener of its own), so switching here also moves it to the
                    // top — otherwise the wallpaper would keep showing whichever location used
                    // to be first.
                    if (index != 0) {
                        swapLocations(index, 0)
                    }

                    // Mirror pull-to-refresh: switching to a location you haven't looked at in a
                    // while shouldn't show stale weather until the next scheduled background
                    // update happens to run.
                    updateWithUpdatingChecking(triggeredByUser = true, checkPermissions = true)
                }
            }
        }
    }

    // return true if current location changed.
    fun offsetLocation(offset: Int): Boolean {
        cancelRequest()

        val oldFormattedId = currentLocation.value?.location?.formattedId ?: ""

        // ensure current index.
        var index = 0
        validLocationList.value.let {
            for (i in it.indices) {
                if (it[i].formattedId == currentLocation.value?.location?.formattedId) {
                    index = i
                    break
                }
            }
        }

        // update index.
        index = (index + offset + (validLocationList.value.size)) % (validLocationList.value.size)

        // update location.
        setCurrentLocation(validLocationList.value[index])

        _indicator.value = Indicator(total = validLocationList.value.size, index = index)

        return currentLocation.value?.location?.formattedId != oldFormattedId
    }

    // list.

    // return false if failed.
    fun addLocation(
        location: Location,
        index: Int? = null,
        context: Context? = null, // Needed for timezone
    ): Boolean {
        // do not add an existing location.
        if (validLocationList.value.firstOrNull { it.formattedId == location.formattedId } != null) {
            return false
        }

        _locationListLoading.value = true

        val locationWithValidTimeZone = if (context != null && location.isTimeZoneInvalid) {
            location.copy(
                timeZone = runBlocking {
                    refreshHelper.getTimeZoneForLocation(context, location)
                }
            )
        } else {
            location
        }

        val valid = validLocationList.value.toMutableList()
        valid.add(index ?: valid.size, locationWithValidTimeZone)

        updateInnerData(valid)
        writeLocationList(locationList = valid, refreshWallpaperAfterWrite = true)

        _locationListLoading.value = false

        return true
    }

    /**
     * Does nothing when the location is invalid
     */
    fun askToAddLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
    ) {
        val location = Location(latitude = latitude, longitude = longitude)
        if (validLocationList.value.firstOrNull { it.isCloseTo(location) } != null) {
            SnackbarHelper.showSnackbar(context.getString(R.string.location_message_already_exists))
        } else {
            viewModelScope.launchIO {
                _locationListLoading.value = true
                try {
                    val locationWithInfo = refreshHelper.requestReverseGeocoding(
                        sourceManager.getReverseGeocodingSource(BuildConfig.DEFAULT_GEOCODING_SOURCE)!!,
                        location,
                        context
                    )
                    if (locationWithInfo.hasValidCountryCode) {
                        openChooseWeatherSourcesDialog(
                            locationWithInfo
                                .copy(
                                    // Allows the user to select a one-time address lookup source
                                    needsGeocodeRefresh = true
                                )
                                .applyDefaultPreset(sourceManager)
                        )
                    }
                } catch (_: Exception) {
                    // Do nothing
                }
                _locationListLoading.value = false
            }
        }
    }

    fun swapLocations(from: Int, to: Int) {
        /*val fromItem = _validLocationList.value.first[from]
        val toItem = _validLocationList.value.first[to]
        val newList = _validLocationList.value.first.toMutableList()
        newList[from] = toItem
        newList[to] = fromItem

        _validLocationList.value = Pair(newList, _validLocationList.value.second)*/
        if (from == to) {
            return
        }

        val valid = validLocationList.value.toMutableList()
        val activeBefore = valid.getOrNull(0)?.formattedId
        valid.add(to, valid.removeAt(from))

        updateInnerData(valid)

        // The live wallpaper always reads the first location in this list (it has no reactive
        // listener of its own -- see the setCurrentLocation callsite above). Without this,
        // switching which location is first (e.g. Rome <-> Current location) left the wallpaper
        // showing whatever was active before until the next scheduled tick, up to
        // photoRefreshIntervalMinutes (default 30 min) later.
        val activeAfter = valid.getOrNull(0)?.formattedId
        writeLocationList(
            locationList = validLocationList.value,
            refreshWallpaperAfterWrite = activeAfter != null && activeAfter != activeBefore
        )
    }

    fun updateLocation(newLocation: Location, oldLocation: Location?) {
        // Same reasoning as swapLocations(): only matters when the edited entry is the one
        // actively driving the wallpaper (index 0) -- e.g. re-picking a fixed location's place
        // changes its identity/coordinates in place, without any reorder to hang a refresh off.
        val wasActive = validLocationList.value.getOrNull(0)?.formattedId ==
            (oldLocation?.formattedId ?: newLocation.formattedId)

        updateInnerData(newLocation, oldLocation)
        writeLocationList(
            locationList = validLocationList.value,
            refreshWallpaperAfterWrite = wasActive
        )
    }

    fun locationExists(location: Location): Boolean {
        // formattedId (not raw lat/lon) is the source of truth for a location's identity: it
        // already distinguishes "current position" and fictional locations (e.g. Ghibli) from
        // real coordinates. Comparing lat/lon directly used to falsely match a not-yet-resolved
        // "add current location" placeholder (0.0, 0.0) against a fictional location, which also
        // sits at (0.0, 0.0), turning "add" into a silent no-op "update".
        return validLocationList.value.firstOrNull { item ->
            item.formattedId == location.formattedId
        } != null
    }

    fun deleteLocation(position: Int): Location {
        val valid = validLocationList.value.toMutableList()
        val location = valid.removeAt(position)

        updateInnerData(valid)
        deleteLocation(location = location)
        // If we no longer have any current position locations, clear the current location store data9
        if (location.isCurrentPosition && !valid.any { it.isCurrentPosition }) {
            currentLocationStore.clearCurrentLocation()
        }

        // Same reasoning as swapLocations(): deleting the active (index 0) location promotes
        // whatever's now first -- the wallpaper needs telling, or it keeps showing the deleted
        // location's last photo until the next scheduled tick.
        if (position == 0 && valid.isNotEmpty()) {
            WallpaperPhotoRefreshWorker.startNow(getApplication())
        }

        return location
    }

    fun emptyLocationListFake() {
        updateInnerData(emptyList())
    }

    fun deleteAllLocations() {
        updateInnerData(emptyList())
        viewModelScope.launchIO {
            locationRepository.deleteAll()
        }
    }

    // MARK: - getter.
    fun getValidLocation(offset: Int?): Location? {
        if (offset == null) return null
        // ensure current index.
        var index: Int? = null
        validLocationList.value.let {
            for (i in it.indices) {
                if (it[i].formattedId == currentLocation.value?.location?.formattedId) {
                    index = i
                    break
                }
            }
        }

        return index?.let {
            return validLocationList.value.getOrNull(
                (it + offset + validLocationList.value.size) % validLocationList.value.size
            )
        }
    }

    // impl.

    override fun onCompleted(location: Location, errors: List<RefreshError>) {
        onUpdateResult(location, errors)
    }

    // Repository
    suspend fun initLocations(): List<Location> {
        val list = locationRepository.getAllLocations()

        return list.map {
            it.copy(weather = weatherRepository.getWeatherByLocationId(it.formattedId))
        }
    }

    private fun writeLocationList(
        locationList: List<Location>,
        refreshWallpaperAfterWrite: Boolean = false,
    ) {
        viewModelScope.launchIO {
            locationRepository.addAll(locationList)
            // WorkManager reads its locations back from the repository. Enqueueing before
            // addAll completed caused a fresh installation's first photo refresh to observe an
            // empty list and finish without downloading anything.
            if (refreshWallpaperAfterWrite) {
                WallpaperPhotoRefreshWorker.startNow(getApplication())
            }
        }
    }

    fun deleteLocation(location: Location) {
        viewModelScope.launchIO {
            // Note: we will have a gap in the listOrder, but this doesn't cause any issue, and
            // it will fix by itself on next rewrite of the full location list
            locationRepository.delete(location.formattedId)
            // It cascades delete location parameters, weather and so on

            // Not cascaded by the above: the wallpaper photo cache (files + catalog rows) for
            // this location lives in its own store, keyed by a derived locationKey, not by
            // formattedId -- without this it would linger orphaned forever.
            wallpaperRepository.clearLocation(location.toWallpaperPlaceQuery(), location)
        }
    }

    fun getLocatePermissionList(context: Context) = refreshHelper.getPermissions(context)

    suspend fun getWeather(
        context: Context,
        location: Location,
        callback: WeatherRequestCallback,
    ) {
        try {
            val locationPositionErrors = if (location.isCurrentPosition || location.isFictional) {
                refreshHelper.updateCurrentCoordinates(context, false)
            } else {
                emptyList()
            }

            val locationResult = refreshHelper.getLocation(context, location)
            if (locationResult.location.isUsable && !locationResult.location.needsGeocodeRefresh) {
                val ignoreCaching = SphericalUtil.computeDistanceBetween(
                    LatLng(locationResult.location.latitude, locationResult.location.longitude),
                    LatLng(location.latitude, location.longitude)
                ) > RefreshHelper.CACHING_DISTANCE_LIMIT
                val weatherResult = refreshHelper.getWeather(
                    context,
                    locationResult.location,
                    location.longitude != locationResult.location.longitude ||
                        location.latitude != locationResult.location.latitude,
                    ignoreCaching
                )
                callback.onCompleted(
                    locationResult.location.copy(weather = weatherResult.weather),
                    locationPositionErrors + locationResult.errors + weatherResult.errors
                )
            } else {
                callback.onCompleted(
                    locationResult.location,
                    locationPositionErrors + locationResult.errors
                )
            }
        } catch (e: Throwable) {
            // Should never happen
            e.printStackTrace()
            callback.onCompleted(
                location,
                listOf(RefreshError(RefreshErrorType.DATA_REFRESH_FAILED))
            )
        }
    }

    fun refreshBackgroundViews(context: Context, locationList: List<Location>?) {
        locationList?.let {
            if (it.isNotEmpty()) {
                viewModelScope.launchIO {
                    AsyncHelper.delayRunOnIO({
                        refreshHelper.updateWidgetIfNecessary(context, it)
                        refreshHelper.updateNotificationIfNecessary(context, it)
                        refreshHelper.broadcastDataIfNecessary(context, it)
                    }, 1000)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                        refreshHelper.refreshShortcuts(context, it)
                    }
                }
            }
        }
    }

    companion object {
        private const val KEY_FORMATTED_ID = "formatted_id"
        private val DELAY_BEFORE_NEXT_MANUAL_REFRESH = 10.seconds.inWholeMilliseconds
    }
}
