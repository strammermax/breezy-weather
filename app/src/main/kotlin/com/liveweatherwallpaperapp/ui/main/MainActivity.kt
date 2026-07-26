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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.liveweatherwallpaperapp.BreezyWeather
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.Migrations
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.background.findmyphone.FindMyPhoneService
import com.liveweatherwallpaperapp.background.findmyphone.FindMyPhoneStore
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.bus.EventBus
import com.liveweatherwallpaperapp.common.extensions.conditional
import com.liveweatherwallpaperapp.common.extensions.doOnApplyWindowInsets
import com.liveweatherwallpaperapp.common.extensions.hasPermission
import com.liveweatherwallpaperapp.common.extensions.isBackgroundAnimationEnabled
import com.liveweatherwallpaperapp.common.extensions.isLandscape
import com.liveweatherwallpaperapp.common.extensions.isOnline
import com.liveweatherwallpaperapp.common.extensions.isRtl
import com.liveweatherwallpaperapp.common.snackbar.SnackbarContainer
import com.liveweatherwallpaperapp.common.update.GithubReleaseNotesSource
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.common.utils.helpers.LogHelper
import com.liveweatherwallpaperapp.common.utils.helpers.SnackbarHelper
import com.liveweatherwallpaperapp.databinding.ActivityMainBinding
import com.liveweatherwallpaperapp.domain.settings.SettingsChangedMessage
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.remoteviews.Notifications
import com.liveweatherwallpaperapp.sources.SourceManager
import com.liveweatherwallpaperapp.ui.common.composables.AlertDialogConfirmOnly
import com.liveweatherwallpaperapp.ui.common.composables.AlertDialogNoPadding
import com.liveweatherwallpaperapp.ui.common.composables.LocationPreference
import com.liveweatherwallpaperapp.ui.main.dialogs.LicenseComplianceDialog
import com.liveweatherwallpaperapp.ui.main.fragments.HomeFragment
import com.liveweatherwallpaperapp.ui.main.fragments.ManagementFragment
import com.liveweatherwallpaperapp.ui.main.fragments.PushedManagementFragment
import com.liveweatherwallpaperapp.ui.search.SearchActivity
import com.liveweatherwallpaperapp.ui.theme.ThemeManager
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import com.liveweatherwallpaperapp.ui.theme.weatherView.WeatherViewController
import com.liveweatherwallpaperapp.wallpaper.CelestialTiming
import com.liveweatherwallpaperapp.wallpaper.WallpaperEffectView
import com.liveweatherwallpaperapp.wallpaper.WallpaperSceneSnapshot
import com.liveweatherwallpaperapp.wallpaper.WallpaperSceneStateFactory
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperImageStore
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperRepository
import com.liveweatherwallpaperapp.wallpaper.photo.toWallpaperPlaceQuery
import com.liveweatherwallpaperapp.wallpaper.toFrostedBackground
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import livewallpaperweather.data.location.LocationRepository
import livewallpaperweather.data.weather.WeatherRepository
import livewallpaperweather.domain.location.model.Location
import javax.inject.Inject
import com.liveweatherwallpaperapp.domain.location.model.isDaylight as locationIsDaylight

@AndroidEntryPoint
class MainActivity : BreezyActivity(), HomeFragment.Callback, ManagementFragment.Callback {

    @Inject
    lateinit var sourceManager: SourceManager

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainActivityViewModel
    private var liveWallpaperPhotoRefreshInProgress = false
    private lateinit var effectView: WallpaperEffectView

    private val _dialogPerLocationSettingsOpen = MutableStateFlow(false)
    val dialogPerLocationSettingsOpen = _dialogPerLocationSettingsOpen.asStateFlow()
    private val _isLocationBasedLightTheme: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val isLocationBasedLightTheme = _isLocationBasedLightTheme.asStateFlow()

    companion object {
        const val SEARCH_ACTIVITY = 4

        const val ACTION_MAIN = "${BuildConfig.APPLICATION_ID}.Main"
        const val KEY_MAIN_ACTIVITY_LOCATION_FORMATTED_ID = "MAIN_ACTIVITY_LOCATION_FORMATTED_ID"
        const val KEY_MAIN_ACTIVITY_ALERT_ID = "MAIN_ACTIVITY_ALERT_ID"

        const val ACTION_MANAGEMENT = "${BuildConfig.APPLICATION_ID}.ACTION_MANAGEMENT"
        const val ACTION_SHOW_ALERTS = "${BuildConfig.APPLICATION_ID}.ACTION_SHOW_ALERTS"

        const val ACTION_SHOW_DAILY_FORECAST = "${BuildConfig.APPLICATION_ID}.ACTION_SHOW_DAILY_FORECAST"
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val KEY_DAILY_INDEX = "DAILY_INDEX"

        private const val TAG_FRAGMENT_HOME = "fragment_main"
        private const val TAG_FRAGMENT_MANAGEMENT = "fragment_management"

        private const val PERMISSION_CODE_LOCATION_ACCESS = 1
        private const val PERMISSION_CODE_ACCESS_BACKGROUND_LOCATION = 2
    }

    private val backgroundUpdateObserver: Observer<Location> = Observer { location ->
        viewModel.updateLocationFromBackground(location)
    }

    private val openSearchActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK && it.data != null) {
                BreezyWeather.instance.setTopActivity(this)
                val location = IntentCompat.getParcelableExtra(
                    it.data!!,
                    SearchActivity.KEY_LOCATION,
                    Location::class.java
                )
                if (location != null) {
                    if (viewModel.locationExists(location)) {
                        SnackbarHelper.showSnackbar(getString(R.string.location_message_already_exists))
                    } else {
                        viewModel.addLocation(location, null, this)
                        SnackbarHelper.showSnackbar(getString(R.string.location_message_added))
                    }
                }
            }
        }

    fun updateLocation(location: Location) {
        if (!viewModel.initCompleted.value) {
            return
        }

        // Only updates are coming here (no location added or deleted)
        // If we don't find the formattedId in the current list, it means main source was changed
        // for currently focused location
        // TODO: This shouldn't be the case anymore, as only WeatherUpdateJob comes here
        val oldLocation = viewModel.validLocationList.value.firstOrNull {
            it.formattedId == location.formattedId
        } ?: viewModel.currentLocation.value?.location

        if (viewModel.currentLocation.value?.location?.formattedId ==
            (oldLocation?.formattedId ?: location.formattedId)
        ) {
            viewModel.cancelRequest()
        }
        viewModel.updateLocation(location, oldLocation)
    }

    fun deleteLocation(location: Location) {
        if (locationListSize() > 1) {
            val position: Int = viewModel.validLocationList.value.indexOfFirst {
                it.formattedId == location.formattedId
            }
            if (position >= 0) {
                viewModel.deleteLocation(position)
                SnackbarHelper.showSnackbar(
                    this.getString(R.string.location_message_deleted)
                )
            }
        } else {
            SnackbarHelper.showSnackbar(
                this.getString(R.string.location_message_list_cannot_be_empty)
            )
        }
    }

    fun locationListSize(): Int {
        return viewModel.locationListSize()
    }

    private val fragmentsLifecycleCallback = object : FragmentManager.FragmentLifecycleCallbacks() {

        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?,
        ) {
            updateSystemBarStyle()
        }

        override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
            updateSystemBarStyle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        val isLaunch = savedInstanceState == null

        super.onCreate(savedInstanceState)

        if (isLaunch) {
            Migrations.upgrade(applicationContext, sourceManager, locationRepository, weatherRepository)

            // Notify (instead of forcing the release notes screen open) the first time the app
            // is opened after an update -- never on a fresh install (empty lastSeenAppVersion),
            // since there's nothing to diff. This is the "you've actually installed the new
            // version" moment, unlike sendAppUpdateNotification's server push sent the instant
            // a release goes out, before most people have installed anything.
            val settings = SettingsManager.getInstance(this)
            val currentVersion = BuildConfig.VERSION_NAME
            if (
                !BuildConfig.DEBUG &&
                settings.lastSeenAppVersion.isNotBlank() &&
                settings.lastSeenAppVersion != currentVersion
            ) {
                lifecycleScope.launch {
                    val highlight = GithubReleaseNotesSource(applicationContext).getLatest()?.changes?.firstOrNull()
                    Notifications.sendVersionInstalledNotification(applicationContext, currentVersion, highlight)
                }
            }
            settings.lastSeenAppVersion = currentVersion
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentsLifecycleCallback, false)
        setContentView(binding.root)

        initModel(savedInstanceState == null)
        initView()

        if (viewModel.validLocationList.value.isEmpty()) {
            setManagementFragmentVisibility(true)
        } else if (viewModel.validLocationList.value.size == 1 && isLandscape && isDrawerLayoutVisible) {
            setManagementFragmentVisibility(false)
        }

        consumeIntentAction(intent)

        EventBus.instance
            .with(Location::class.java)
            .observeForever(backgroundUpdateObserver) // Only comes from WeatherUpdateJob
        EventBus.instance.with(SettingsChangedMessage::class.java).observe(this) {
            // Force refresh but with latest location used
            viewModel.init(viewModel.currentLocation.value?.location?.formattedId)

            findHomeFragment()?.updateViews()

            refreshBackgroundViews(viewModel.validLocationList.value)
            updateLiveWallpaperBackground()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIntentAction(getIntent())
    }

    override fun onActivityReenter(resultCode: Int, data: Intent) {
        super.onActivityReenter(resultCode, data)
        if (resultCode == SEARCH_ACTIVITY) {
            val f = findManagementFragment()
            f?.prepareReenterTransition()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBarStyle()
        updateDayNightColors()
    }

    override fun onStart() {
        super.onStart()

        // Opening the app -- including bringing it back from the background, not just a cold
        // launch -- is how the user tells "Find my phone" they've found it: silence any
        // in-progress alarm, without touching the background listening itself.
        if (FindMyPhoneStore(this).enabled) {
            FindMyPhoneService.stopAlarm(this)
        }

        if (BreezyWeather.instance.isImpersonatingBreezyWeather) {
            viewModel.emptyLocationListFake()
            LicenseComplianceDialog.show(this)
        } else {
            viewModel.checkToUpdate()
        }

        binding.root.doOnApplyWindowInsets { view, insets ->
            if (this.getResources().configuration.orientation == 2) {
                // Apply root insets in landscape mode for a consistent look across different
                // device types and navigation modes.
                view.updatePadding(
                    left = insets.left,
                    right = insets.right
                )
            } else {
                view.updatePadding(
                    left = 0,
                    right = 0
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::effectView.isInitialized) effectView.setDrawable(isBackgroundAnimationEnabled)
    }

    override fun onPause() {
        super.onPause()
        if (::effectView.isInitialized) effectView.setDrawable(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentsLifecycleCallback)
        EventBus.instance
            .with(Location::class.java)
            .removeObserver(backgroundUpdateObserver)
    }

    override val snackbarContainer: SnackbarContainer
        get() {
            if (binding.drawerLayout != null) {
                return super.snackbarContainer
            }

            val f = if (isManagementFragmentVisible) {
                findManagementFragment()
            } else {
                findHomeFragment()
            }

            return f?.snackbarContainer ?: super.snackbarContainer
        }

    // init.

    private fun initModel(newActivity: Boolean) {
        viewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]
        if (!viewModel.checkIsNewInstance()) return
        if (newActivity) {
            viewModel.init(formattedId = getLocationId(intent))
        } else {
            viewModel.init()
        }
    }

    private fun getLocationId(intent: Intent?): String? {
        return intent?.getStringExtra(KEY_MAIN_ACTIVITY_LOCATION_FORMATTED_ID)
    }

    private fun initView() {
        // Animated weather effect overlay (clouds, rain, fog …) on top of the static snapshot
        // background. Insert at index 0 so it sits behind all fragment content.
        effectView = WallpaperEffectView(this)
        SettingsManager.getInstance(this).let {
            effectView.setFrosted(it.appBackgroundFrosted, it.appBackgroundFrostStrength, it.appBackgroundFrostTint)
        }
        binding.root.addView(
            effectView,
            0,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        binding.root.post {
            if (isActivityCreated) {
                updateDayNightColors()
            }
        }

        // Start a coroutine in the lifecycle scope
        lifecycleScope.launch {
            // repeatOnLifecycle launches the block in a new coroutine every time the
            // lifecycle is in the STARTED state (or above) and cancels it when it's STOPPED.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Trigger the flow and start listening for values.
                // Note that this happens when lifecycle is STARTED and stops
                // collecting when the lifecycle is STOPPED
                viewModel.validLocationList.collect {
                    refreshBackgroundViews(it)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentLocation.collect {
                    if (it?.location?.weather != null) {
                        updateLiveWallpaperBackground()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.locationPermissionsRequest.collect {
                    if (it != null &&
                        it.permissionList.isNotEmpty() &&
                        it.consume()
                    ) {
                        // only show dialog if we need request basic location permissions.
                        var showLocationPermissionDialog = false
                        for (permission in it.permissionList) {
                            if (isLocationPermission(permission)) {
                                showLocationPermissionDialog = true
                                break
                            }
                        }

                        if (showLocationPermissionDialog &&
                            !viewModel.statementManager.isLocationPermissionDialogAlreadyShown
                        ) {
                            val dialogLocationPermissionOpenState = mutableStateOf(true)
                            binding.locationPermissionDialog.setContent {
                                BreezyWeatherTheme(
                                    !ThemeManager.isLightTheme(this@MainActivity, daylight = isDaylight)
                                ) {
                                    if (dialogLocationPermissionOpenState.value) {
                                        AlertDialogConfirmOnly(
                                            title = R.string.dialog_permissions_location_title,
                                            content = R.string.dialog_permissions_location_content,
                                            confirmButtonText = R.string.action_next,
                                            onConfirm = {
                                                // mark declared.
                                                viewModel.statementManager
                                                    .setLocationPermissionDialogAlreadyShown()

                                                val request = viewModel.locationPermissionsRequest.value
                                                if (request != null &&
                                                    request.permissionList.isNotEmpty() &&
                                                    request.target != null
                                                ) {
                                                    requestPermissions(
                                                        request.permissionList.toTypedArray(),
                                                        PERMISSION_CODE_LOCATION_ACCESS
                                                    )
                                                }

                                                dialogLocationPermissionOpenState.value = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            requestPermissions(it.permissionList.toTypedArray(), PERMISSION_CODE_LOCATION_ACCESS)
                        }
                    }
                }
            }
        }
        viewModel.snackbarError.observe(this) { errors ->
            if (errors.size > 1) {
                SnackbarHelper.showSnackbar(
                    content = getString(R.string.message_multiple_refresh_errors),
                    action = getString(R.string.action_show)
                ) {
                    // Inefficient workaround to apply the correct theme while taking the location-based theme setting
                    // into account. TODO: replace with a central solution which can be used in all composables
                    _isLocationBasedLightTheme.value = if (isDrawerLayoutVisible || !isManagementFragmentVisible) {
                        viewModel.currentLocation.value?.daylight
                    } else {
                        null
                    }
                    viewModel.setRefreshErrorDetailsDialogVisible(true)
                }
            } else {
                errors.firstOrNull()?.let { error ->
                    val shortMessage = error.getMessage(this, sourceManager)
                    error.error.showDialogAction?.let { showDialogAction ->
                        SnackbarHelper.showSnackbar(
                            content = shortMessage,
                            action = getString(error.error.actionButtonMessage)
                        ) {
                            showDialogAction(this)
                        }
                    } ?: SnackbarHelper.showSnackbar(shortMessage)
                }
            }
        }

        initPerLocationSettingsView()

        binding.refreshErrorDialog.setContent {
            val isLightTheme = isLocationBasedLightTheme.collectAsState()

            BreezyWeatherTheme(
                !ThemeManager.isLightTheme(this, isLightTheme.value)
            ) {
                RefreshErrorDetails()
            }
        }
    }

    private fun initPerLocationSettingsView() {
        binding.perLocationSettings.setContent {
            val validLocation = viewModel.currentLocation.collectAsState()

            BreezyWeatherTheme(
                !ThemeManager.isLightTheme(this, validLocation.value?.daylight)
            ) {
                PerLocationSettingsDialog(location = validLocation.value?.location)
            }
        }
    }

    @Composable
    fun PerLocationSettingsDialog(
        location: Location?,
        modifier: Modifier = Modifier,
    ) {
        val dialogPerLocationSettingsOpenState = dialogPerLocationSettingsOpen.collectAsState()
        if (dialogPerLocationSettingsOpenState.value) {
            location?.let {
                val dialogDeleteLocationOpenState = remember { mutableStateOf(false) }
                AlertDialogNoPadding(
                    modifier = modifier,
                    onDismissRequest = {
                        _dialogPerLocationSettingsOpen.value = false
                    },
                    title = {
                        Text(
                            text = stringResource(R.string.action_edit_location),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        LocationPreference(
                            activity = this,
                            location = it,
                            onClose = { newLocation: Location? ->
                                if (newLocation != null) {
                                    updateLocation(newLocation)
                                }
                                _dialogPerLocationSettingsOpen.value = false
                            },
                            locationExists = { loc: Location ->
                                viewModel.locationExists(loc)
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                _dialogPerLocationSettingsOpen.value = false
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.action_close),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    },
                    dismissButton = if (locationListSize() > 1) {
                        {
                            TextButton(
                                onClick = {
                                    dialogDeleteLocationOpenState.value = true
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else {
                        null
                    }
                )

                if (dialogDeleteLocationOpenState.value) {
                    AlertDialog(
                        onDismissRequest = {
                            dialogDeleteLocationOpenState.value = false
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.location_delete_location_dialog_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },
                        text = {
                            Text(
                                text = if (it.city.isNotEmpty()) {
                                    stringResource(
                                        R.string.location_delete_location_dialog_message,
                                        it.city
                                    )
                                } else {
                                    stringResource(R.string.location_delete_location_dialog_message_no_name)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dialogDeleteLocationOpenState.value = false
                                    _dialogPerLocationSettingsOpen.value = false
                                    deleteLocation(it)
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.action_confirm),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    dialogDeleteLocationOpenState.value = false
                                }
                            ) {
                                Text(
                                    text = stringResource(android.R.string.cancel),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun RefreshErrorDetails(
        modifier: Modifier = Modifier,
    ) {
        val dialogRefreshErrorDetailsOpenState = viewModel.dialogRefreshErrorDetails.collectAsState()

        if (dialogRefreshErrorDetailsOpenState.value) {
            AlertDialogNoPadding(
                modifier = modifier,
                onDismissRequest = { /* do nothing */ },
                title = {
                    Column {
                        Text(stringResource(R.string.dialog_refresh_error_details_title))
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                        Text(
                            text = stringResource(R.string.dialog_refresh_error_details_content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.setRefreshErrorDetailsDialogVisible(false) },
                        content = {
                            Text(stringResource(R.string.action_close))
                        }
                    )
                },
                text = {
                    LazyColumn {
                        items(viewModel.snackbarError.value!!) {
                            val source = it.getSourceWithOptionalFeature(this@MainActivity, sourceManager)
                            val message = this@MainActivity.getString(it.error.shortMessage)

                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                modifier = Modifier
                                    .conditional(it.error.showDialogAction != null, {
                                        clickable { it.error.showDialogAction!!(this@MainActivity) }
                                    })
                                    .padding(vertical = dimensionResource(R.dimen.small_margin)),
                                headlineContent = {
                                    Text(
                                        text = source ?: message,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                },
                                supportingContent = {
                                    source?.let {
                                        Column {
                                            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                                            Text(
                                                text = message,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    it.error.showDialogAction?.let {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                            contentDescription = stringResource(R.string.action_help)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_CODE_LOCATION_ACCESS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
            !viewModel.statementManager.isBackgroundLocationPermissionDialogAlreadyShown &&
            !this.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            val dialogBackgroundLocationPermissionOpenState = mutableStateOf(true)
            binding.locationPermissionDialog.setContent {
                BreezyWeatherTheme(
                    !ThemeManager.isLightTheme(this@MainActivity, daylight = isDaylight)
                ) {
                    if (dialogBackgroundLocationPermissionOpenState.value) {
                        AlertDialogConfirmOnly(
                            title = R.string.dialog_permissions_location_background_title,
                            content = R.string.dialog_permissions_location_background_content,
                            confirmButtonText = R.string.action_set,
                            onConfirm = {
                                // mark background location permission declared.
                                viewModel.statementManager
                                    .setBackgroundLocationPermissionDialogAlreadyShown()
                                // request background location permission.
                                requestPermissions(
                                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                    PERMISSION_CODE_ACCESS_BACKGROUND_LOCATION
                                )
                                dialogBackgroundLocationPermissionOpenState.value = false
                            }
                        )
                    }
                }
            }
            return
        }

        if (requestCode == PERMISSION_CODE_LOCATION_ACCESS ||
            requestCode == PERMISSION_CODE_ACCESS_BACKGROUND_LOCATION
        ) {
            viewModel.updateWithUpdatingChecking(
                triggeredByUser = false,
                checkPermissions = false
            )
        }
    }

    private fun isLocationPermission(
        permission: String,
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        permission == Manifest.permission.ACCESS_BACKGROUND_LOCATION || isEssentialLocationPermission(permission)
    } else {
        isEssentialLocationPermission(permission)
    }

    private fun isEssentialLocationPermission(permission: String): Boolean {
        return permission == Manifest.permission.ACCESS_COARSE_LOCATION ||
            permission == Manifest.permission.ACCESS_FINE_LOCATION
    }

    val isDaylight: Boolean
        get() = viewModel.currentLocation.value?.daylight ?: true

    private fun consumeIntentAction(intent: Intent) {
        val action = intent.action
        if (action.isNullOrEmpty()) return
        val formattedId = intent.getStringExtra(KEY_MAIN_ACTIVITY_LOCATION_FORMATTED_ID)
        when (action) {
            ACTION_SHOW_ALERTS -> {
                val alertId = intent.getStringExtra(KEY_MAIN_ACTIVITY_ALERT_ID)
                if (!alertId.isNullOrEmpty()) {
                    IntentHelper.startAlertActivity(this, formattedId, alertId)
                } else {
                    IntentHelper.startAlertActivity(this, formattedId)
                }
            }
            ACTION_SHOW_DAILY_FORECAST -> {
                val index = intent.getIntExtra(KEY_DAILY_INDEX, 0)
                IntentHelper.startDailyWeatherActivity(this, formattedId, index)
            }
            ACTION_MANAGEMENT -> {
                setManagementFragmentVisibility(true)
            }
            ACTION_VIEW -> {
                val uri = intent.data
                if (uri?.scheme.equals("geo", ignoreCase = true) && !uri?.schemeSpecificPart.isNullOrEmpty()) {
                    if (BreezyWeather.instance.debugMode) {
                        LogHelper.log(msg = "Geo URI found")
                    }
                    val regex = Regex(
                        "^(?<lat>^[-+]?(?:[1-8]?\\d(?:\\.\\d+)?|90(?:\\.0+)?))\\s*,\\s*" +
                            "(?<lng>[-+]?(?:180(?:\\.0+)?|(?:1[0-7]\\d|[1-9]?\\d)(?:\\.\\d+)?))(.*)$"
                    )
                    val matching = regex.find(uri.schemeSpecificPart!!)
                    if (matching != null) {
                        setManagementFragmentVisibility(true)
                        viewModel.askToAddLocation(
                            context = this,
                            latitude = matching.groups[1]!!.value.toDouble(),
                            longitude = matching.groups[2]!!.value.toDouble()
                        )
                    } else {
                        if (BreezyWeather.instance.debugMode) {
                            LogHelper.log(msg = "Geo URI malformed: ${uri.schemeSpecificPart}")
                        }
                    }
                }
            }
        }
    }

    private fun updateSystemBarStyle() {
        if (binding.drawerLayout != null) {
            findHomeFragment()?.setSystemBarStyle()
            return
        }

        if (isOrWillManagementFragmentVisible) {
            findManagementFragment()?.setSystemBarStyle()
        } else {
            findHomeFragment()?.setSystemBarStyle()
        }
    }

    private fun updateDayNightColors() {
        if (this.getResources().configuration.orientation == 2) {
            // Set a black background to keep the background of the system bars black when root
            // insets are applied in landscape mode.
            binding.root.setBackgroundColor(Color.BLACK)
        } else {
            // ACT-014: show the day/night sky gradient behind the ACT-013 glass cards as a
            // fallback while the live-wallpaper snapshot (below) is generated/refreshed.
            binding.root.background = ContextCompat.getDrawable(this, R.drawable.bg_glass_sky)
            updateLiveWallpaperBackground()
        }
    }

    /**
     * ACT-014: renders a one-time snapshot of the live wallpaper scene (sky gradient, sun/moon,
     * weather background pass, location photo) for the current location/weather and sets it as
     * the home screen's background behind the ACT-013 glass cards.
     */
    private fun updateLiveWallpaperBackground(forcePhotoRefresh: Boolean = false) {
        if (!forcePhotoRefresh && liveWallpaperPhotoRefreshInProgress) return
        if (forcePhotoRefresh) {
            if (liveWallpaperPhotoRefreshInProgress) return
            liveWallpaperPhotoRefreshInProgress = true
        }
        val location = viewModel.currentLocation.value?.location
        val weather = location?.weather
        val width = binding.root.width
        val height = binding.root.height
        if (weather == null || width <= 0 || height <= 0) {
            liveWallpaperPhotoRefreshInProgress = false
            return
        }

        // Resolve sun/moon intervals the same way the live wallpaper does, so the snapshot
        // background renders the same sky gradient (rather than naively trusting the first
        // daily forecast entry, whose astro times may already lie outside "now").
        val now = System.currentTimeMillis()
        val sunInterval = CelestialTiming.closestAstroInterval(CelestialTiming.sunIntervals(location, now), now)
            ?: CelestialTiming.approximateSunInterval(location, now)
        val moonInterval = CelestialTiming.closestAstroInterval(CelestialTiming.moonIntervals(location, now), now)
        // Derive daylight from the same freshly-resolved sunInterval above instead of the
        // separately-cached location.locationIsDaylight (tied to the last weather refresh's
        // today.sun) -- the two could disagree right after a location switch or a long sleep,
        // showing e.g. a "day" sky gradient with night-oriented moon/star logic or vice versa.
        val daylight = CelestialTiming.sunVisibility(
            now,
            sunInterval?.first,
            sunInterval?.second,
            location.locationIsDaylight
        )
        val sceneState = WallpaperSceneStateFactory.create(
            weatherKind = WeatherViewController.getWeatherKind(location),
            daylight = daylight,
            windSpeedMetersPerSecond = weather.current?.wind?.speed?.value?.toFloat() ?: 0f,
            windGustMetersPerSecond = weather.current?.wind?.gusts?.value?.toFloat() ?: 0f,
            windDirectionDegrees = weather.current?.wind?.degree?.toFloat(),
            sunriseMillis = sunInterval?.first,
            sunsetMillis = sunInterval?.second,
            moonriseMillis = moonInterval?.first,
            moonsetMillis = moonInterval?.second
        )

        effectView.setWeather(sceneState.weatherKind, sceneState.daylight)
        effectView.setDrawable(isBackgroundAnimationEnabled)

        lifecycleScope.launch {
            try {
                if (forcePhotoRefresh) {
                    withContext(Dispatchers.IO) {
                        val store = WallpaperImageStore(this@MainActivity)
                        if (store.photoBackgroundEnabled && this@MainActivity.isOnline()) {
                            val file = wallpaperRepository.refreshFor(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                place = location.toWallpaperPlaceQuery(),
                                location = location,
                                forceRefresh = true,
                                activate = true
                            )
                            if (file != null) {
                                store.setPhotoRefreshedAt(location.formattedId, System.currentTimeMillis())
                            }
                        }
                    }
                }
                val photo = withContext(Dispatchers.IO) {
                    wallpaperRepository.loadCachedBitmap()
                }
                // Same depth map the live wallpaper uses to keep clouds behind near/foreground
                // photo content (e.g. a building) instead of painting over the whole photo.
                val depth = withContext(Dispatchers.IO) {
                    wallpaperRepository.loadCachedDepthBitmap()
                }
                var nearPhoto: Bitmap? = null
                val bitmap = withContext(Dispatchers.Default) {
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        nearPhoto =
                            WallpaperSceneSnapshot.render(
                                Canvas(it),
                                width,
                                height,
                                photo,
                                sceneState,
                                resources,
                                depth
                            )
                    }
                }
                val settings = SettingsManager.getInstance(this@MainActivity)
                val frosted = settings.appBackgroundFrosted
                effectView.setFrosted(frosted, settings.appBackgroundFrostStrength, settings.appBackgroundFrostTint)
                effectView.setForegroundPhoto(
                    if (frosted) nearPhoto?.toFrostedBackground(settings.appBackgroundFrostStrength) else nearPhoto,
                    if (sceneState.usesGreyscalePhoto) sceneState.photoGreyscaleAmount else 0f
                )
                if (this@MainActivity.getResources().configuration.orientation != 2) {
                    binding.root.background = BitmapDrawable(
                        resources,
                        if (frosted) bitmap.toFrostedBackground(settings.appBackgroundFrostStrength) else bitmap
                    )
                }
            } finally {
                if (forcePhotoRefresh) {
                    liveWallpaperPhotoRefreshInProgress = false
                }
            }
        }
    }

    /** Pull-to-refresh also requests and activates a different RemoveSky image immediately. */
    fun refreshLiveWallpaperPhoto() {
        updateLiveWallpaperBackground(forcePhotoRefresh = true)
    }

    /** Starts the same weather refresh as pull-to-refresh, without opening a settings screen. */
    fun retryWeatherUpdate() {
        viewModel.updateWithUpdatingChecking(
            triggeredByUser = true,
            checkPermissions = true
        )
    }

    private val isOrWillManagementFragmentVisible: Boolean
        get() = binding.drawerLayout?.isUnfold
            ?: findManagementFragment()?.let { !it.isRemoving }
            ?: false

    val isManagementFragmentVisible: Boolean
        get() = binding.drawerLayout?.isUnfold
            ?: findManagementFragment()?.isVisible
            ?: false

    val isDrawerLayoutVisible: Boolean
        get() = binding.drawerLayout?.isVisible ?: false

    fun setManagementFragmentVisibility(visible: Boolean) {
        val drawerLayout = binding.drawerLayout
        if (drawerLayout != null) {
            drawerLayout.isUnfold = visible
            return
        }
        if (visible == isOrWillManagementFragmentVisible) return
        if (!visible) {
            supportFragmentManager.popBackStack()
            return
        }

        val transaction = supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                if (isRtl) R.anim.slide_in_right else R.anim.slide_in_left,
                if (isRtl) R.anim.slide_out_left else R.anim.slide_out_right,
                if (isRtl) R.anim.slide_in_left else R.anim.slide_in_right,
                if (isRtl) R.anim.slide_out_right else R.anim.slide_out_left
            )
            .add(
                R.id.fragment,
                PushedManagementFragment.getInstance(),
                TAG_FRAGMENT_MANAGEMENT
            )
            .addToBackStack(null)

        findHomeFragment()?.let {
            transaction.hide(it)
        }

        transaction.commit()
    }

    private fun findHomeFragment(): HomeFragment? {
        return if (binding.drawerLayout == null) {
            supportFragmentManager.findFragmentByTag(TAG_FRAGMENT_HOME) as HomeFragment?
        } else {
            supportFragmentManager.findFragmentById(R.id.fragment_home) as HomeFragment?
        }
    }

    private fun findManagementFragment(): ManagementFragment? {
        return if (binding.drawerLayout == null) {
            supportFragmentManager.findFragmentByTag(TAG_FRAGMENT_MANAGEMENT) as ManagementFragment?
        } else {
            supportFragmentManager.findFragmentById(R.id.fragment_drawer) as ManagementFragment?
        }
    }

    private fun refreshBackgroundViews(locationList: List<Location>?) {
        viewModel.refreshBackgroundViews(this, locationList)
    }

    // interface.

    // main fragment callback.
    override fun onEditIconClicked() {
        if (viewModel.loading.value) {
            SnackbarHelper.showSnackbar(getString(R.string.message_please_wait_refresh))
        } else {
            _dialogPerLocationSettingsOpen.value = true
            initPerLocationSettingsView()
        }
    }

    /**
     * Opens the per-location "Edit location" dialog for a specific [location]. Used by the
     * pencil button on each row of the Locations list (the action that used to live in the
     * home toolbar).
     */
    fun openPerLocationSettings(location: Location) {
        if (viewModel.loading.value) {
            SnackbarHelper.showSnackbar(getString(R.string.message_please_wait_refresh))
            return
        }
        binding.perLocationSettings.setContent {
            BreezyWeatherTheme(
                !ThemeManager.isLightTheme(this, location.locationIsDaylight)
            ) {
                PerLocationSettingsDialog(location = location)
            }
        }
        _dialogPerLocationSettingsOpen.value = true
    }

    override fun onOpenInOtherAppIconClicked() {
        viewModel.currentLocation.value?.location?.let {
            try {
                IntentHelper.startBreezyActivity(this@MainActivity, it)
            } catch (ignored: Exception) {
                SnackbarHelper.showSnackbar(getString(R.string.action_open_in_other_app_no_app))
            }
        }
    }

    override fun onManageIconClicked() {
        setManagementFragmentVisibility(!isOrWillManagementFragmentVisible)
    }

    // management fragment callback.

    override fun onSearchBarClicked() {
        openSearchActivity.launch(
            IntentHelper.buildSearchActivityIntent(this)
        )
    }
}
