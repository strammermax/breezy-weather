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

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.IntentCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.hasPermission
import com.liveweatherwallpaperapp.common.extensions.openApplicationDetailsSettings
import com.liveweatherwallpaperapp.common.extensions.toBitmap
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.common.utils.helpers.SnackbarHelper
import com.liveweatherwallpaperapp.domain.location.model.getPlace
import com.liveweatherwallpaperapp.domain.settings.SetupWizardStore
import com.liveweatherwallpaperapp.domain.source.resourceName
import com.liveweatherwallpaperapp.ui.search.SearchActivity
import com.liveweatherwallpaperapp.ui.settings.preference.composables.SwitchPreferenceView
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import com.liveweatherwallpaperapp.ui.theme.compose.themeRipple
import com.liveweatherwallpaperapp.wallpaper.LiveWallpaperConfigManager
import com.liveweatherwallpaperapp.wallpaper.launchLiveWallpaperPicker
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperPhotoRefreshWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceFeature

/**
 * First-run setup wizard: covers the whole first-run flow the wireframe specifies --
 * location(s), per-location weather sources, live wallpaper (preview/apply/motion), main
 * screen layout, and a home screen widget. Also reachable any time from Settings ("Redo setup
 * wizard") -- see RootSettingsScreen.
 *
 * Unlike the app's normal location-management screen (ManagementFragment, tightly coupled to
 * MainActivityViewModel/MainActivity), this activity owns the whole location-adding step
 * itself via [SetupWizardViewModel] -- MainActivity's first-run gate (validLocationList.isEmpty())
 * starts this activity instead of showing ManagementFragment directly.
 */
@AndroidEntryPoint
class SetupWizardActivity : BreezyActivity() {

    private val viewModel: SetupWizardViewModel by viewModels()

    private var onLocationPicked: ((Location) -> Unit)? = null

    private val searchActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val location = IntentCompat.getParcelableExtra(
                result.data!!,
                SearchActivity.KEY_LOCATION,
                Location::class.java
            )
            if (location != null) {
                onLocationPicked?.invoke(location)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BreezyWeatherTheme {
                SetupWizardScreen(
                    viewModel = viewModel,
                    onSearchLocation = { onPicked ->
                        onLocationPicked = onPicked
                        searchActivityLauncher.launch(IntentHelper.buildSearchActivityIntent(this))
                    },
                    onFinish = {
                        SetupWizardStore(this).completed = true
                        finish()
                    }
                )
            }
        }
    }
}

private sealed interface WizardStep {
    data object Welcome : WizardStep
    data object LocationChoice : WizardStep
    data class SourcesSummary(val location: Location, val isCurrentLocation: Boolean) : WizardStep
    data object LocationPermission : WizardStep
    data object LocationPermissionBackground : WizardStep
    data object AddOtherCity : WizardStep
    data object PickWallpaperLocation : WizardStep
    data object WallpaperPreview : WizardStep
    data object WallpaperApply : WizardStep
    data object WallpaperAppearance : WizardStep
    data object MainScreen : WizardStep
    data object Widget : WizardStep
}

private val WEATHER_SOURCE_FEATURES = listOf(
    SourceFeature.FORECAST,
    SourceFeature.CURRENT,
    SourceFeature.AIR_QUALITY,
    SourceFeature.POLLEN,
    SourceFeature.MINUTELY,
    SourceFeature.ALERT,
    SourceFeature.NORMALS
)

@Composable
private fun SetupWizardScreen(
    viewModel: SetupWizardViewModel,
    onSearchLocation: ((Location) -> Unit) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf<WizardStep>(WizardStep.Welcome) }
    val scope = rememberCoroutineScope()

    fun proceedAfterLocationsGathered() {
        scope.launch {
            val locations = viewModel.locations
            step = if (locations.size <= 1) {
                viewModel.persistLocations(locations.firstOrNull()?.formattedId)
                WizardStep.WallpaperPreview
            } else {
                WizardStep.PickWallpaperLocation
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = step) {
                WizardStep.Welcome -> WelcomeStep(
                    onStart = { step = WizardStep.LocationChoice },
                    onClose = onFinish
                )

                WizardStep.LocationChoice -> LocationChoiceStep(
                    hasCurrentLocation = viewModel.hasCurrentLocation,
                    onAddNewLocation = {
                        onSearchLocation { location ->
                            scope.launch {
                                viewModel.addSearchedLocation(context, location)
                                step = WizardStep.SourcesSummary(
                                    viewModel.locations.last(),
                                    isCurrentLocation = false
                                )
                            }
                        }
                    },
                    onAddCurrentLocation = {
                        viewModel.addCurrentLocation()
                        step = WizardStep.SourcesSummary(viewModel.locations.last(), isCurrentLocation = true)
                    }
                )

                is WizardStep.SourcesSummary -> SourcesSummaryStep(
                    viewModel = viewModel,
                    location = current.location,
                    isCurrentLocation = current.isCurrentLocation,
                    onNext = {
                        step = if (current.isCurrentLocation) {
                            WizardStep.LocationPermission
                        } else {
                            WizardStep.AddOtherCity
                        }
                    }
                )

                WizardStep.LocationPermission -> LocationPermissionStep(
                    onDone = { granted ->
                        step = if (granted &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            !context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        ) {
                            WizardStep.LocationPermissionBackground
                        } else {
                            WizardStep.AddOtherCity
                        }
                    }
                )

                WizardStep.LocationPermissionBackground -> LocationPermissionBackgroundStep(
                    onDone = { step = WizardStep.AddOtherCity }
                )

                WizardStep.AddOtherCity -> AddOtherCityStep(
                    onYes = { step = WizardStep.LocationChoice },
                    onNo = { proceedAfterLocationsGathered() }
                )

                WizardStep.PickWallpaperLocation -> PickWallpaperLocationStep(
                    locations = viewModel.locations,
                    onPicked = { location ->
                        scope.launch {
                            viewModel.persistLocations(location.formattedId)
                            step = WizardStep.WallpaperPreview
                        }
                    }
                )

                WizardStep.WallpaperPreview -> WallpaperPreviewStep(
                    viewModel = viewModel,
                    onBack = {
                        step = if (viewModel.locations.size > 1) {
                            WizardStep.PickWallpaperLocation
                        } else {
                            WizardStep.AddOtherCity
                        }
                    },
                    onNext = { step = WizardStep.WallpaperApply }
                )

                WizardStep.WallpaperApply -> WallpaperApplyStep(
                    onBack = { step = WizardStep.WallpaperPreview },
                    onNext = { step = WizardStep.WallpaperAppearance }
                )

                WizardStep.WallpaperAppearance -> WallpaperAppearanceStep(
                    onBack = { step = WizardStep.WallpaperApply },
                    onNext = { step = WizardStep.MainScreen }
                )

                WizardStep.MainScreen -> MainScreenStep(
                    onBack = { step = WizardStep.WallpaperAppearance },
                    onNext = { step = WizardStep.Widget }
                )

                WizardStep.Widget -> WidgetStep(
                    onBack = { step = WizardStep.MainScreen },
                    onFinish = onFinish
                )
            }
        }
}

@Composable
private fun WizardStepScaffold(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
    buttons: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(dimensionResource(R.dimen.large_margin))) {
        // Content-heavy steps (e.g. the weather-sources summary, which can list up to 8 rows)
        // can overflow the screen -- scroll just this part, with title/summary scrolling along
        // and the buttons pinned below, so the buttons are always reachable regardless of how
        // much content a given step has.
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.normal_margin))
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            content()
        }
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
        buttons()
    }
}

/** Simple "Next"-only footer, for steps with no meaningful "Back". */
@Composable
private fun NextOnlyButton(label: String, onNext: () -> Unit) {
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

/** Back/Next footer pair, for the wallpaper -> main screen -> widget tail. */
@Composable
private fun BackNextButtons(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String = stringResource(R.string.setup_wizard_action_next),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.normal_margin))
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.setup_wizard_action_back))
        }
        Button(onClick = onNext, modifier = Modifier.weight(1f)) {
            Text(nextLabel)
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit, onClose: () -> Unit) {
    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_welcome_title),
        summary = stringResource(R.string.setup_wizard_welcome_summary),
        buttons = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin))
            ) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_wizard_action_start))
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_wizard_action_close))
                }
            }
        }
    )
}

@Composable
private fun LocationChoiceStep(
    hasCurrentLocation: Boolean,
    onAddNewLocation: () -> Unit,
    onAddCurrentLocation: () -> Unit,
) {
    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_location_choice_title),
        summary = stringResource(R.string.setup_wizard_location_choice_summary),
        buttons = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin))
            ) {
                Button(onClick = onAddNewLocation, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_add_new_location))
                }
                if (!hasCurrentLocation) {
                    OutlinedButton(onClick = onAddCurrentLocation, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_add_current_location))
                    }
                }
            }
        }
    )
}

@Composable
private fun SourcesSummaryStep(
    viewModel: SetupWizardViewModel,
    location: Location,
    isCurrentLocation: Boolean,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val features = if (isCurrentLocation) {
        WEATHER_SOURCE_FEATURES + SourceFeature.REVERSE_GEOCODING
    } else {
        WEATHER_SOURCE_FEATURES
    }
    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_sources_title),
        summary = stringResource(R.string.setup_wizard_sources_summary),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                features.forEach { feature ->
                    val label = viewModel.sourceLabelFor(context, location, feature)
                        ?: stringResource(R.string.setup_wizard_sources_unavailable)
                    ListItem(
                        headlineContent = { Text(stringResource(feature.resourceName)) },
                        supportingContent = { Text(label) }
                    )
                }
            }
        },
        buttons = { NextOnlyButton(stringResource(R.string.setup_wizard_action_next), onNext) }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocationPermissionStep(onDone: (granted: Boolean) -> Unit) {
    var requested by remember { mutableStateOf(false) }
    val permissionsState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    ) {
        requested = true
    }
    LaunchedEffect(Unit) {
        if (permissionsState.allPermissionsGranted) onDone(true)
    }
    LaunchedEffect(requested) {
        if (requested) onDone(permissionsState.allPermissionsGranted)
    }
    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_location_permission_title),
        summary = stringResource(R.string.setup_wizard_location_permission_summary),
        buttons = {
            NextOnlyButton(stringResource(R.string.setup_wizard_action_next)) {
                permissionsState.launchMultiplePermissionRequest()
            }
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocationPermissionBackgroundStep(onDone: () -> Unit) {
    val activity = LocalContext.current as BreezyActivity
    val permissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
        onDone()
    }
    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_location_permission_background_title),
        summary = stringResource(R.string.setup_wizard_location_permission_background_summary),
        buttons = {
            NextOnlyButton(stringResource(R.string.action_set)) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                ) {
                    permissionState.launchPermissionRequest()
                } else {
                    activity.openApplicationDetailsSettings()
                    onDone()
                }
            }
        }
    )
}

@Composable
private fun AddOtherCityStep(onYes: () -> Unit, onNo: () -> Unit) {
    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_add_other_city_title),
        summary = stringResource(R.string.setup_wizard_add_other_city_summary),
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.normal_margin))
            ) {
                OutlinedButton(onClick = onNo, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setup_wizard_action_no))
                }
                Button(onClick = onYes, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.setup_wizard_action_yes))
                }
            }
        }
    )
}

@Composable
private fun PickWallpaperLocationStep(locations: List<Location>, onPicked: (Location) -> Unit) {
    val context = LocalContext.current
    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_pick_wallpaper_location_title),
        summary = stringResource(R.string.setup_wizard_pick_wallpaper_location_summary),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                locations.forEach { location ->
                    ListItem(
                        headlineContent = { Text(location.getPlace(context)) },
                        supportingContent = location.weather?.current?.weatherText?.let { text ->
                            { Text(text) }
                        },
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = themeRipple(),
                            onClick = { onPicked(location) }
                        )
                    )
                }
            }
        },
        buttons = {}
    )
}

@Composable
private fun WallpaperPreviewStep(viewModel: SetupWizardViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        bitmap = withContext(Dispatchers.IO) { viewModel.wallpaperRepository.loadCachedBitmap() }
    }

    LaunchedEffect(Unit) {
        reload()
        if (bitmap == null) {
            busy = true
            withContext(Dispatchers.IO) { WallpaperPhotoRefreshWorker.startNowAndAwait(context) }
            reload()
            busy = false
        }
    }

    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_step_wallpaper_title),
        summary = stringResource(R.string.setup_wizard_step_wallpaper_summary),
        content = {
            if (bitmap == null) {
                Text(
                    text = stringResource(R.string.setup_wizard_wallpaper_no_photo_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin)))
                }
            } else {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin))
            ) {
                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            busy = true
                            withContext(Dispatchers.IO) { WallpaperPhotoRefreshWorker.startNowAndAwait(context) }
                            reload()
                            busy = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.setup_wizard_wallpaper_refresh_now))
                }
                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            busy = true
                            val location = viewModel.firstLocation()
                            if (location != null) {
                                val next = withContext(Dispatchers.IO) {
                                    viewModel.wallpaperRepository.getNextSortedResultlistItem(location.formattedId)
                                }
                                next?.let {
                                    withContext(Dispatchers.IO) {
                                        viewModel.wallpaperRepository.activateRotationItem(it)
                                    }
                                }
                                reload()
                            }
                            busy = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.setup_wizard_wallpaper_next_image))
                }
            }
        },
        buttons = { BackNextButtons(onBack = onBack, onNext = onNext) }
    )
}

@Composable
private fun WallpaperApplyStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val resources = context.resources
    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_step_wallpaper_title),
        summary = stringResource(R.string.setup_wizard_step_wallpaper_summary),
        content = {
            Button(
                onClick = {
                    if (!launchLiveWallpaperPicker(context)) {
                        SnackbarHelper.showSnackbar(
                            resources.getString(
                                R.string.settings_modules_live_wallpaper_error,
                                resources.getString(R.string.brand_name)
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.setup_wizard_step_wallpaper_button))
            }
        },
        buttons = { BackNextButtons(onBack = onBack, onNext = onNext) }
    )
}

@Composable
private fun WallpaperAppearanceStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val configManager = remember { LiveWallpaperConfigManager(context) }
    var animationsEnabled by remember { mutableStateOf(configManager.animationsEnabled) }
    var parallaxEnabled by remember { mutableStateOf(configManager.parallaxEnabled) }

    fun persist() {
        LiveWallpaperConfigManager.update(
            context,
            configManager.weatherKind,
            configManager.dayNightType,
            animationsEnabled,
            parallaxEnabled
        )
    }

    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_step_appearance_title),
        summary = stringResource(R.string.setup_wizard_step_wallpaper_summary),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SwitchPreferenceView(
                    title = stringResource(R.string.settings_main_section_animations),
                    summary = { c, enabled ->
                        if (enabled) c.getString(R.string.settings_enabled) else c.getString(R.string.settings_disabled)
                    },
                    checked = animationsEnabled,
                    withState = false,
                    card = false
                ) { newValue ->
                    animationsEnabled = newValue
                    persist()
                }
                SwitchPreferenceView(
                    title = stringResource(R.string.widget_live_wallpaper_parallax),
                    summary = { c, _ -> c.getString(R.string.widget_live_wallpaper_parallax_summary) },
                    checked = parallaxEnabled,
                    withState = false,
                    card = false
                ) { newValue ->
                    parallaxEnabled = newValue
                    persist()
                }
            }
        },
        buttons = { BackNextButtons(onBack = onBack, onNext = onNext) }
    )
}

@Composable
private fun MainScreenStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    WizardStepScaffold(
        title = stringResource(R.string.setup_wizard_step_main_screen_title),
        summary = stringResource(R.string.setup_wizard_step_main_screen_summary),
        content = {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { IntentHelper.startCardDisplayManageActivity(context as BreezyActivity) }
            ) {
                Text(stringResource(R.string.setup_wizard_step_main_screen_button))
            }
        },
        buttons = { BackNextButtons(onBack = onBack, onNext = onNext) }
    )
}

@Composable
private fun WidgetStep(onBack: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.large_margin)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.normal_margin))
    ) {
        Text(
            text = stringResource(R.string.setup_wizard_step_widget_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.setup_wizard_step_widget_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        val providers = remember {
            AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin)),
            contentPadding = PaddingValues(vertical = dimensionResource(R.dimen.small_margin))
        ) {
            items(providers) { provider ->
                WidgetGalleryItem(provider = provider, onPinned = onFinish)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.normal_margin))
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.setup_wizard_action_back))
            }
            TextButton(onClick = onFinish, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.setup_wizard_action_skip))
            }
            Button(onClick = onFinish, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.setup_wizard_action_finish))
            }
        }
    }
}

@Composable
private fun WidgetGalleryItem(provider: AppWidgetProviderInfo, onPinned: () -> Unit) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val label = remember(provider) { provider.loadLabel(context.packageManager) }
    val previewDrawable: Drawable? = remember(provider) {
        provider.loadPreviewImage(context, 0) ?: runCatching { provider.loadIcon(context, 0) }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimensionResource(R.dimen.small_margin)))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = themeRipple(),
                onClick = {
                    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        appWidgetManager.isRequestPinAppWidgetSupported
                    if (supported) {
                        appWidgetManager.requestPinAppWidget(
                            ComponentName(provider.provider.packageName, provider.provider.className),
                            null,
                            null
                        )
                        onPinned()
                    } else {
                        SnackbarHelper.showSnackbar(
                            context.getString(R.string.setup_wizard_widget_pin_unsupported)
                        )
                    }
                }
            )
            .padding(dimensionResource(R.dimen.normal_margin)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin))
    ) {
        previewDrawable?.let { drawable ->
            Image(
                bitmap = drawable.toBitmap().asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
