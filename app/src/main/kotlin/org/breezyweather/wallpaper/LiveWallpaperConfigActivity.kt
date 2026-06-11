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

package org.breezyweather.wallpaper

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import org.breezyweather.ui.settings.preference.composables.SwitchPreferenceView
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
import org.breezyweather.ui.theme.compose.themeRipple
import org.breezyweather.unit.formatting.format
import org.breezyweather.wallpaper.photo.PlaceQuery
import org.breezyweather.wallpaper.photo.WallpaperImageStore
import org.breezyweather.wallpaper.photo.WallpaperRepository
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import breezyweather.data.location.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class LiveWallpaperConfigActivity : BreezyActivity() {

    @Inject
    lateinit var locationRepository: LocationRepository

    private lateinit var wallpaperRepository: WallpaperRepository
    private lateinit var previewBitmapValue: MutableState<Bitmap?>
    private lateinit var refreshBusyValue: MutableState<Boolean>
    private lateinit var refreshStatusValue: MutableState<String>
    private lateinit var attributionValue: MutableState<String>

    private lateinit var weatherKindValueNow: MutableState<String>
    private lateinit var weatherKinds: Array<String>
    private lateinit var weatherKindValues: Array<String>

    private lateinit var dayNightTypeValueNow: MutableState<String>
    private lateinit var dayNightTypeKinds: Array<String>
    private lateinit var dayNightTypeValues: Array<String>

    private lateinit var animationsEnabledValue: MutableState<Boolean>

    private lateinit var wallpaperImageStore: WallpaperImageStore
    private lateinit var photoBackgroundEnabledValue: MutableState<Boolean>
    private lateinit var unsplashKeyValue: MutableState<String>
    private lateinit var mapboxTokenValue: MutableState<String>
    private lateinit var backgroundSourceValueNow: MutableState<String>
    private lateinit var backgroundSourceNames: Array<String>
    private lateinit var backgroundSourceValues: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val liveWallpaperConfigManager = LiveWallpaperConfigManager(this)
        weatherKindValueNow = mutableStateOf(liveWallpaperConfigManager.weatherKind)
        weatherKinds = resources.getStringArray(R.array.live_wallpaper_weather_kinds)
        weatherKindValues = resources.getStringArray(R.array.live_wallpaper_weather_kind_values)

        dayNightTypeValueNow = mutableStateOf(liveWallpaperConfigManager.dayNightType)
        dayNightTypeKinds = resources.getStringArray(R.array.live_wallpaper_day_night_types)
        dayNightTypeValues = resources.getStringArray(R.array.live_wallpaper_day_night_type_values)

        animationsEnabledValue = mutableStateOf(liveWallpaperConfigManager.animationsEnabled)

        wallpaperImageStore = WallpaperImageStore(this)
        photoBackgroundEnabledValue = mutableStateOf(wallpaperImageStore.photoBackgroundEnabled)
        unsplashKeyValue = mutableStateOf(wallpaperImageStore.unsplashAccessKey)
        mapboxTokenValue = mutableStateOf(wallpaperImageStore.mapboxAccessToken)
        backgroundSourceValueNow = mutableStateOf(wallpaperImageStore.backgroundSource)
        backgroundSourceNames = resources.getStringArray(R.array.live_wallpaper_bg_sources)
        backgroundSourceValues = resources.getStringArray(R.array.live_wallpaper_bg_source_values)

        wallpaperRepository = WallpaperRepository(this)
        previewBitmapValue = mutableStateOf(null)
        refreshBusyValue = mutableStateOf(false)
        refreshStatusValue = mutableStateOf("")
        attributionValue = mutableStateOf(wallpaperImageStore.cachedPhotoAttribution.orEmpty())
        // Preload the currently cached photo (decode off the main thread).
        lifecycleScope.launch {
            previewBitmapValue.value = withContext(Dispatchers.IO) {
                wallpaperRepository.loadCachedBitmap()
            }
        }

        setContent {
            BreezyWeatherTheme {
                ContentView()
            }
        }
    }

    /**
     * Applies the currently selected settings, then resolves+downloads a photo for the app's
     * current location through the provider chain and updates the in-screen preview. Lets the
     * user test a source (incl. keyless Wikimedia) without waiting for a wallpaper redraw.
     */
    private fun runRefresh() {
        if (refreshBusyValue.value) return

        // Persist the current UI selections so the chosen source/keys are actually used.
        wallpaperImageStore.photoBackgroundEnabled = photoBackgroundEnabledValue.value
        wallpaperImageStore.unsplashAccessKey = unsplashKeyValue.value.trim()
        wallpaperImageStore.mapboxAccessToken = mapboxTokenValue.value.trim()
        wallpaperImageStore.backgroundSource = backgroundSourceValueNow.value

        refreshBusyValue.value = true
        refreshStatusValue.value = getString(R.string.widget_live_wallpaper_refresh_running)

        lifecycleScope.launch {
            val location = withContext(Dispatchers.IO) {
                locationRepository.getFirstLocation(withParameters = false)
            }
            if (location == null || !location.isUsable) {
                refreshStatusValue.value = getString(R.string.widget_live_wallpaper_refresh_no_location)
                refreshBusyValue.value = false
                return@launch
            }
            val place = PlaceQuery(
                city = location.city.ifBlank { null },
                municipality = location.admin2,
                state = location.admin1,
                country = location.country.ifBlank { null }
            )
            val file = withContext(Dispatchers.IO) {
                wallpaperRepository.refreshFor(location.latitude, location.longitude, place)
            }
            if (file != null) {
                previewBitmapValue.value = withContext(Dispatchers.IO) {
                    wallpaperRepository.loadCachedBitmap()
                }
                attributionValue.value = wallpaperImageStore.cachedPhotoAttribution.orEmpty()
                refreshStatusValue.value = getString(R.string.widget_live_wallpaper_refresh_ok)
            } else {
                refreshStatusValue.value = getString(R.string.widget_live_wallpaper_refresh_none)
            }
            refreshBusyValue.value = false
        }
    }

    @Composable
    private fun ContentView() {
        val dialogOpenState = remember { mutableStateOf(false) }
        Material3Scaffold(
            topBar = {
                FitStatusBarTopAppBar(
                    title = stringResource(R.string.settings_modules_live_wallpaper_title),
                    onBackPressed = { finish() }
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                contentPadding = it
            ) {
                item {
                    Spinner(
                        currentVal = weatherKindValueNow,
                        names = weatherKinds,
                        values = weatherKindValues,
                        titleId = R.string.widget_live_wallpaper_weather_kind
                    )
                }
                item {
                    Spinner(
                        currentVal = dayNightTypeValueNow,
                        names = dayNightTypeKinds,
                        values = dayNightTypeValues,
                        titleId = R.string.widget_live_wallpaper_day_night_type
                    )
                }
                item {
                    SwitchPreferenceView(
                        title = stringResource(R.string.settings_main_section_animations),
                        summary = { context: Context, enabled: Boolean ->
                            if (enabled) {
                                "⚠️ ${context.getString(R.string.settings_enabled)}"
                            } else {
                                context.getString(R.string.settings_disabled)
                            }
                        },
                        checked = animationsEnabledValue.value,
                        withState = false,
                        card = false
                    ) { newValue ->
                        if (newValue) {
                            dialogOpenState.value = true
                        } else {
                            animationsEnabledValue.value = false
                        }
                    }
                }
                item {
                    SwitchPreferenceView(
                        title = stringResource(R.string.widget_live_wallpaper_photo_background),
                        summary = { _: Context, _: Boolean ->
                            this@LiveWallpaperConfigActivity
                                .getString(R.string.widget_live_wallpaper_photo_background_summary)
                        },
                        checked = photoBackgroundEnabledValue.value,
                        withState = false,
                        card = false
                    ) { newValue ->
                        photoBackgroundEnabledValue.value = newValue
                    }
                }
                item {
                    Spinner(
                        currentVal = backgroundSourceValueNow,
                        names = backgroundSourceNames,
                        values = backgroundSourceValues,
                        titleId = R.string.widget_live_wallpaper_bg_source
                    )
                }
                item {
                    Column(
                        modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin))
                    ) {
                        OutlinedTextField(
                            value = mapboxTokenValue.value,
                            onValueChange = { mapboxTokenValue.value = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = {
                                Text(
                                    text = stringResource(R.string.widget_live_wallpaper_mapbox_token),
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            placeholder = {
                                Text(stringResource(R.string.widget_live_wallpaper_mapbox_token_hint))
                            }
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin))
                    ) {
                        OutlinedTextField(
                            value = unsplashKeyValue.value,
                            onValueChange = { unsplashKeyValue.value = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = {
                                Text(
                                    text = stringResource(R.string.widget_live_wallpaper_unsplash_key),
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            placeholder = {
                                Text(stringResource(R.string.widget_live_wallpaper_unsplash_key_hint))
                            }
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin))
                    ) {
                        Text(
                            text = stringResource(R.string.widget_live_wallpaper_preview),
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                        val bmp = previewBitmapValue.value
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentScale = ContentScale.Crop
                            )
                            if (attributionValue.value.isNotBlank()) {
                                Spacer(
                                    modifier = Modifier.height(dimensionResource(R.dimen.small_margin))
                                )
                                Text(
                                    text = attributionValue.value,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { runRefresh() },
                                enabled = !refreshBusyValue.value
                            ) {
                                if (refreshBusyValue.value) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.small_margin)))
                                }
                                Text(stringResource(R.string.widget_live_wallpaper_refresh_now))
                            }
                            if (refreshStatusValue.value.isNotBlank()) {
                                Spacer(
                                    modifier = Modifier.width(dimensionResource(R.dimen.normal_margin))
                                )
                                Text(
                                    text = refreshStatusValue.value,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensionResource(R.dimen.normal_margin)),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Button(
                            onClick = {
                                LiveWallpaperConfigManager.update(
                                    this@LiveWallpaperConfigActivity,
                                    weatherKindValueNow.value,
                                    dayNightTypeValueNow.value,
                                    animationsEnabledValue.value
                                )
                                wallpaperImageStore.photoBackgroundEnabled =
                                    photoBackgroundEnabledValue.value
                                wallpaperImageStore.unsplashAccessKey =
                                    unsplashKeyValue.value.trim()
                                wallpaperImageStore.mapboxAccessToken =
                                    mapboxTokenValue.value.trim()
                                wallpaperImageStore.backgroundSource =
                                    backgroundSourceValueNow.value
                                finish()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.action_save),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
            if (dialogOpenState.value) {
                var timeLeft by remember { mutableIntStateOf(10) }
                LaunchedEffect(key1 = timeLeft) {
                    while (timeLeft > 0) {
                        delay(1000L)
                        --timeLeft
                    }
                }
                AlertDialog(
                    onDismissRequest = {
                        dialogOpenState.value = false
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                dialogOpenState.value = false
                            }
                        ) {
                            Text(stringResource(R.string.action_close))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                animationsEnabledValue.value = true
                                dialogOpenState.value = false
                            },
                            enabled = timeLeft == 0
                        ) {
                            Text(
                                text = if (timeLeft > 0) {
                                    stringResource(
                                        R.string.parenthesis,
                                        stringResource(R.string.action_enable),
                                        timeLeft.format(decimals = 0, locale = currentLocale)
                                    )
                                } else {
                                    stringResource(R.string.action_enable)
                                },
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    title = {
                        Text(
                            stringResource(
                                R.string.parenthesis,
                                stringResource(R.string.settings_main_section_animations),
                                stringResource(R.string.widget_live_wallpaper_animations_enable_dangerous)
                            )
                        )
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.widget_live_wallpaper_animations_enable_warning1)
                            )
                            Spacer(
                                modifier = Modifier.height(dimensionResource(R.dimen.normal_margin))
                            )
                            Text(
                                stringResource(R.string.widget_live_wallpaper_animations_enable_warning2)
                            )
                        }
                    },
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconContentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    @Composable
    private fun Spinner(
        currentVal: MutableState<String>,
        names: Array<String>,
        values: Array<String>,
        @StringRes titleId: Int,
    ) {
        val expanded = remember { mutableStateOf(false) }
        val textFieldSize = remember { mutableStateOf(Size.Zero) }

        val icon = if (expanded.value) {
            Icons.Filled.ArrowDropUp
        } else {
            Icons.Filled.ArrowDropDown
        }
        val label = stringResource(titleId)

        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin))
        ) {
            OutlinedTextField(
                value = names[if (values.indexOf(currentVal.value) != -1) values.indexOf(currentVal.value) else 0],
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        textFieldSize.value = coordinates.size.toSize()
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = themeRipple(),
                        onClick = { expanded.value = !expanded.value }
                    ),
                label = {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.clickable {
                            expanded.value = !expanded.value
                        },
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                readOnly = true,
                enabled = false,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            DropdownMenu(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false },
                modifier = Modifier
                    .width(with(LocalDensity.current) { textFieldSize.value.width.toDp() })
            ) {
                names.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = item,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        onClick = {
                            currentVal.value = values[index]
                            expanded.value = false
                        }
                    )
                }
            }
        }
    }
}
