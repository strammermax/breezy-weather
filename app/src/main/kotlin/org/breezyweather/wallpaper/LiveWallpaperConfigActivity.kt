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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
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
import org.breezyweather.wallpaper.photo.WallpaperPhotoRefreshWorker
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
import breezyweather.data.weather.WeatherRepository
import dagger.hilt.android.AndroidEntryPoint
import org.breezyweather.common.extensions.getRelativeTime
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class LiveWallpaperConfigActivity : BreezyActivity() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    private lateinit var wallpaperRepository: WallpaperRepository
    private lateinit var previewBitmapValue: MutableState<Bitmap?>
    private lateinit var refreshBusyValue: MutableState<Boolean>
    private lateinit var refreshStatusValue: MutableState<String>
    private lateinit var attributionValue: MutableState<String>
    private lateinit var currentLocationValue: MutableState<String>
    private lateinit var photoCacheLimitMbValue: MutableState<Float>
    private lateinit var maxPhotosPerLocationValue: MutableState<Float>

    private lateinit var weatherKindValueNow: MutableState<String>
    private lateinit var weatherKinds: Array<String>
    private lateinit var weatherKindValues: Array<String>

    private lateinit var dayNightTypeValueNow: MutableState<String>
    private lateinit var dayNightTypeKinds: Array<String>
    private lateinit var dayNightTypeValues: Array<String>

    private lateinit var animationsEnabledValue: MutableState<Boolean>
    private lateinit var parallaxEnabledValue: MutableState<Boolean>

    private lateinit var wallpaperImageStore: WallpaperImageStore
    private lateinit var photoBackgroundEnabledValue: MutableState<Boolean>

    /** ACT-011: when the current location's weather/photo were last refreshed, or null if unknown. */
    private lateinit var weatherRefreshedAtValue: MutableState<Long?>
    private lateinit var photoRefreshedAtValue: MutableState<Long?>

    /** ACT-012: experimental seasonal colour/light grading. */
    private lateinit var seasonGradingEnabledValue: MutableState<Boolean>
    private lateinit var seasonGradingStrengthValue: MutableState<Float>

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
        parallaxEnabledValue = mutableStateOf(liveWallpaperConfigManager.parallaxEnabled)

        seasonGradingEnabledValue = mutableStateOf(liveWallpaperConfigManager.seasonGradingEnabled)
        seasonGradingStrengthValue = mutableFloatStateOf(liveWallpaperConfigManager.seasonGradingStrength)

        wallpaperImageStore = WallpaperImageStore(this)
        photoBackgroundEnabledValue = mutableStateOf(wallpaperImageStore.photoBackgroundEnabled)

        wallpaperRepository = WallpaperRepository(this)
        previewBitmapValue = mutableStateOf(null)
        refreshBusyValue = mutableStateOf(false)
        refreshStatusValue = mutableStateOf("")
        attributionValue = mutableStateOf(wallpaperImageStore.cachedPhotoAttribution.orEmpty())
        currentLocationValue = mutableStateOf("")
        photoCacheLimitMbValue = mutableFloatStateOf(wallpaperImageStore.photoCacheLimitMb.toFloat())
        maxPhotosPerLocationValue =
            mutableFloatStateOf(wallpaperImageStore.maxCachedPhotosPerLocation.toFloat())
        weatherRefreshedAtValue = mutableStateOf(null)
        photoRefreshedAtValue = mutableStateOf(null)
        // Preload the currently cached photo (decode off the main thread).
        lifecycleScope.launch {
            val (bitmap, location) = withContext(Dispatchers.IO) {
                wallpaperRepository.loadCachedBitmap() to
                    locationRepository.getFirstLocation(withParameters = false)
            }
            previewBitmapValue.value = bitmap
            currentLocationValue.value = location?.city?.takeIf { it.isNotBlank() }
                ?: location?.country.orEmpty()

            if (location != null) {
                photoRefreshedAtValue.value = wallpaperImageStore.photoRefreshedAtFor(location.formattedId)
                    .takeIf { it > 0L }
                weatherRefreshedAtValue.value = withContext(Dispatchers.IO) {
                    weatherRepository.getWeatherByLocationId(location.formattedId)
                }?.base?.refreshTime?.time
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

        wallpaperImageStore.photoBackgroundEnabled = photoBackgroundEnabledValue.value

        refreshBusyValue.value = true
        refreshStatusValue.value = getString(R.string.widget_live_wallpaper_refresh_running)

        lifecycleScope.launch {
            val removeSkyHealthStatus = withContext(Dispatchers.IO) {
                wallpaperRepository.removeSkyHealthStatus()
            }
            if (removeSkyHealthStatus != "ok") {
                refreshStatusValue.value = "RemoveSky health check failed"
                refreshBusyValue.value = false
                return@launch
            }

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
            currentLocationValue.value = place.displayName
            val file = withContext(Dispatchers.IO) {
                wallpaperRepository.refreshFor(
                    location.latitude,
                    location.longitude,
                    place,
                    forceRefresh = true,
                )
            }
            if (file != null) {
                val refreshedAt = System.currentTimeMillis()
                wallpaperImageStore.setPhotoRefreshedAt(location.formattedId, refreshedAt)
                photoRefreshedAtValue.value = refreshedAt
                previewBitmapValue.value = withContext(Dispatchers.IO) {
                    wallpaperRepository.loadCachedBitmap()
                }
                attributionValue.value = wallpaperImageStore.cachedPhotoAttribution.orEmpty()
                refreshStatusValue.value = buildString {
                    append(getString(R.string.widget_live_wallpaper_refresh_ok))
                    append(" (RemoveSky: $removeSkyHealthStatus)")
                }
            } else {
                refreshStatusValue.value = getString(R.string.widget_live_wallpaper_refresh_none)
            }
            refreshBusyValue.value = false
        }
    }

    /**
     * Compact "X ago"-style label for [refreshedAtMillis], with a stale marker (ACT-011 section 9).
     * Never shows an exact timestamp or location.
     */
    private fun dataAgeLabel(context: Context, refreshedAtMillis: Long?, isStale: Boolean): String {
        val age = if (refreshedAtMillis != null) {
            Date(refreshedAtMillis).getRelativeTime(context)
        } else {
            getString(R.string.live_wallpaper_data_age_unknown)
        }
        return if (isStale) "$age (${getString(R.string.live_wallpaper_data_stale)})" else age
    }

    private fun saveAndFinish() {
        LiveWallpaperConfigManager.update(
            this,
            weatherKindValueNow.value,
            dayNightTypeValueNow.value,
            animationsEnabledValue.value,
            parallaxEnabledValue.value,
            seasonGradingEnabled = seasonGradingEnabledValue.value,
            seasonGradingStrength = seasonGradingStrengthValue.value,
        )
        wallpaperImageStore.photoBackgroundEnabled = photoBackgroundEnabledValue.value
        wallpaperImageStore.photoCacheLimitMb = photoCacheLimitMbValue.value.roundToInt()
        wallpaperImageStore.maxCachedPhotosPerLocation = maxPhotosPerLocationValue.value.roundToInt()
        if (photoBackgroundEnabledValue.value) {
            WallpaperPhotoRefreshWorker.setupTask(this)
        } else {
            WallpaperPhotoRefreshWorker.cancel(this)
        }
        finish()
    }

    @Composable
    private fun ContentView() {
        val dialogOpenState = remember { mutableStateOf(false) }
        Material3Scaffold(
            topBar = {
                FitStatusBarTopAppBar(
                    title = "Live wallpaper",
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
                        title = "Parallax effect",
                        summary = { _: Context, _: Boolean ->
                            "Shifts layers when scrolling between home screens"
                        },
                        checked = parallaxEnabledValue.value,
                        withState = false,
                        card = false
                    ) { newValue ->
                        parallaxEnabledValue.value = newValue
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
                    Column(
                        modifier = Modifier.padding(
                            horizontal = dimensionResource(R.dimen.normal_margin)
                        )
                    ) {
                        Text(
                            text = "Experimental",
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    SwitchPreferenceView(
                        title = "Seasonal grading (experimental)",
                        summary = { _: Context, _: Boolean ->
                            "Subtle, season-based colour and light shift on top of the scene. " +
                                "Does not change the background photo."
                        },
                        checked = seasonGradingEnabledValue.value,
                        withState = false,
                        card = false
                    ) { newValue ->
                        seasonGradingEnabledValue.value = newValue
                    }
                }
                if (seasonGradingEnabledValue.value) {
                    item {
                        Column(
                            modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin))
                        ) {
                            Text(
                                text = "Grading strength: " +
                                    "${(seasonGradingStrengthValue.value * 100).roundToInt()}%",
                                fontWeight = FontWeight.Bold,
                            )
                            Slider(
                                value = seasonGradingStrengthValue.value,
                                onValueChange = { seasonGradingStrengthValue.value = it },
                                valueRange = 0f..1f,
                            )
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin))
                    ) {
                        val cacheLimitMb = photoCacheLimitMbValue.value.roundToInt()
                        Text(
                            text = "Photo cache: $cacheLimitMb MB",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "The last ${WallpaperImageStore.RECENT_URL_COUNT} photos per location " +
                                "are skipped when choosing a new background.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = photoCacheLimitMbValue.value,
                            onValueChange = { value ->
                                photoCacheLimitMbValue.value =
                                    (value / CACHE_LIMIT_STEP_MB).roundToInt() * CACHE_LIMIT_STEP_MB
                            },
                            valueRange = WallpaperImageStore.MIN_CACHE_LIMIT_MB.toFloat()..
                                WallpaperImageStore.MAX_CACHE_LIMIT_MB.toFloat(),
                            steps = CACHE_LIMIT_STEPS,
                            onValueChangeFinished = {
                                wallpaperImageStore.photoCacheLimitMb =
                                    photoCacheLimitMbValue.value.roundToInt()
                                lifecycleScope.launch(Dispatchers.IO) {
                                    wallpaperRepository.enforceCacheLimit()
                                }
                            },
                        )
                        val maxPhotos = maxPhotosPerLocationValue.value.roundToInt()
                        Text(
                            text = "Maximum per location: $maxPhotos photos",
                            fontWeight = FontWeight.Bold,
                        )
                        Slider(
                            value = maxPhotosPerLocationValue.value,
                            onValueChange = { maxPhotosPerLocationValue.value = it.roundToInt().toFloat() },
                            valueRange = WallpaperImageStore.MIN_PHOTOS_PER_LOCATION.toFloat()..
                                WallpaperImageStore.MAX_PHOTOS_PER_LOCATION.toFloat(),
                            steps = WallpaperImageStore.MAX_PHOTOS_PER_LOCATION -
                                WallpaperImageStore.MIN_PHOTOS_PER_LOCATION - 1,
                            onValueChangeFinished = {
                                wallpaperImageStore.maxCachedPhotosPerLocation =
                                    maxPhotosPerLocationValue.value.roundToInt()
                                lifecycleScope.launch(Dispatchers.IO) {
                                    wallpaperRepository.enforceCacheLimit()
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
                        run {
                            val context = LocalContext.current
                            val freshness = DataFreshness.create(
                                weatherRefreshedAtMillis = weatherRefreshedAtValue.value,
                                photoRefreshedAtMillis = photoRefreshedAtValue.value,
                                nowMillis = System.currentTimeMillis(),
                            )
                            Text(
                                text = "Weather: " + dataAgeLabel(context, weatherRefreshedAtValue.value, freshness.isWeatherStale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "Photo: " + dataAgeLabel(context, photoRefreshedAtValue.value, freshness.isPhotoStale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                        }
                        if (currentLocationValue.value.isNotBlank()) {
                            Text(
                                text = "Current location: ${currentLocationValue.value}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                        }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                            Button(
                                onClick = { saveAndFinish() },
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
                        if (refreshStatusValue.value.isNotBlank()) {
                            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                            Text(
                                text = refreshStatusValue.value,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
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

    private companion object {
        const val CACHE_LIMIT_STEP_MB = 25f
        const val CACHE_LIMIT_STEPS = 18
    }
}
