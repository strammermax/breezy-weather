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

package com.liveweatherwallpaperapp.wallpaper

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.getRelativeTime
import com.liveweatherwallpaperapp.common.extensions.openApplicationDetailsSettings
import com.liveweatherwallpaperapp.ui.common.widgets.Material3Scaffold
import com.liveweatherwallpaperapp.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.liveweatherwallpaperapp.ui.settings.preference.composables.SwitchPreferenceView
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import com.liveweatherwallpaperapp.ui.theme.compose.themeRipple
import com.liveweatherwallpaperapp.unit.formatting.format
import com.liveweatherwallpaperapp.wallpaper.photo.PlaceQuery
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperImageStore
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperPhotoRefreshWorker
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import livewallpaperweather.data.location.LocationRepository
import livewallpaperweather.data.weather.WeatherRepository
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class LiveWallpaperConfigActivity : BreezyActivity() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository
    private lateinit var previewBitmapValue: MutableState<Bitmap?>
    private lateinit var refreshBusyValue: MutableState<Boolean>
    private lateinit var refreshStatusValue: MutableState<String>
    private lateinit var attributionValue: MutableState<String>
    private lateinit var currentLocationValue: MutableState<String>
    private lateinit var photoCacheLimitMbValue: MutableState<Float>
    private lateinit var maxPhotosPerLocationValue: MutableState<Float>
    private lateinit var recentUrlCountValue: MutableState<Float>
    private lateinit var cachedPhotoCountValue: MutableState<Int>
    private lateinit var cachedPhotoBytesValue: MutableState<Long>

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
    private lateinit var photoRefreshIntervalMinutesValue: MutableState<Float>

    /** ACT-011: when the current location's weather/photo were last refreshed, or null if unknown. */
    private lateinit var weatherRefreshedAtValue: MutableState<Long?>
    private lateinit var photoRefreshedAtValue: MutableState<Long?>

    /** ACT-012: experimental seasonal colour/light grading. */
    private lateinit var seasonGradingEnabledValue: MutableState<Boolean>
    private lateinit var seasonGradingStrengthValue: MutableState<Float>

    /** Experimental: render clouds via the `:cloud-engine` module (wolke-refactor-plan, Stap 6). */
    private lateinit var newCloudsEnabledValue: MutableState<Boolean>

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

        newCloudsEnabledValue = mutableStateOf(liveWallpaperConfigManager.newCloudsEnabled)

        wallpaperImageStore = WallpaperImageStore(this)
        photoBackgroundEnabledValue = mutableStateOf(wallpaperImageStore.photoBackgroundEnabled)
        photoRefreshIntervalMinutesValue =
            mutableFloatStateOf(wallpaperImageStore.photoRefreshIntervalMinutes.toFloat())

        previewBitmapValue = mutableStateOf(null)
        refreshBusyValue = mutableStateOf(false)
        refreshStatusValue = mutableStateOf("")
        attributionValue = mutableStateOf(wallpaperImageStore.cachedPhotoAttribution.orEmpty())
        currentLocationValue = mutableStateOf("")
        photoCacheLimitMbValue = mutableFloatStateOf(wallpaperImageStore.photoCacheLimitMb.toFloat())
        maxPhotosPerLocationValue =
            mutableFloatStateOf(wallpaperImageStore.maxCachedPhotosPerLocation.toFloat())
        recentUrlCountValue =
            mutableFloatStateOf(wallpaperImageStore.recentUrlCount.toFloat())
        cachedPhotoCountValue = mutableIntStateOf(0)
        cachedPhotoBytesValue = mutableStateOf(0L)
        weatherRefreshedAtValue = mutableStateOf(null)
        photoRefreshedAtValue = mutableStateOf(null)
        // Preload the currently cached photo (decode off the main thread).
        reloadPreview()

        // Reload if the catalog changes from something other than this screen's own actions
        // (an FCM push purging the currently-active photo while this screen is already open)
        // -- the preview above is a one-time snapshot, not an observed Flow, so without this
        // it silently keeps showing a photo that was just removed.
        lifecycleScope.launch {
            wallpaperRepository.catalogChanged.collect { reloadPreview() }
        }

        setContent {
            BreezyWeatherTheme {
                ContentView()
            }
        }
    }

    private fun reloadPreview() {
        lifecycleScope.launch {
            val (bitmap, location, cacheStats) = withContext(Dispatchers.IO) {
                Triple(
                    wallpaperRepository.loadCachedBitmap(),
                    locationRepository.getFirstLocation(withParameters = false),
                    wallpaperRepository.cacheStats()
                )
            }
            previewBitmapValue.value = bitmap
            attributionValue.value = wallpaperImageStore.cachedPhotoAttribution.orEmpty()
            cachedPhotoCountValue.value = cacheStats.photoCount
            cachedPhotoBytesValue.value = cacheStats.totalBytes
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
                refreshStatusValue.value = getString(R.string.widget_live_wallpaper_removesky_health_failed)
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
                    forceRefresh = true
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
            refreshCacheStats()
            refreshBusyValue.value = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (::previewBitmapValue.isInitialized) {
            lifecycleScope.launch {
                previewBitmapValue.value = withContext(Dispatchers.IO) {
                    wallpaperRepository.loadCachedBitmap()
                }
                attributionValue.value = wallpaperImageStore.cachedPhotoAttribution.orEmpty()
                refreshCacheStats()
            }
        }
    }

    private fun refreshCacheStats() {
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) { wallpaperRepository.cacheStats() }
            cachedPhotoCountValue.value = stats.photoCount
            cachedPhotoBytesValue.value = stats.totalBytes
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

    /** Persists weather kind/day-night/animations/parallax/season-grading immediately on change. */
    private fun persistCoreSettings() {
        LiveWallpaperConfigManager.update(
            this,
            weatherKindValueNow.value,
            dayNightTypeValueNow.value,
            animationsEnabledValue.value,
            parallaxEnabledValue.value,
            seasonGradingEnabled = seasonGradingEnabledValue.value,
            seasonGradingStrength = seasonGradingStrengthValue.value,
            newCloudsEnabled = newCloudsEnabledValue.value
        )
    }

    private fun persistPhotoBackgroundEnabled(enabled: Boolean) {
        wallpaperImageStore.photoBackgroundEnabled = enabled
        if (enabled) {
            WallpaperPhotoRefreshWorker.setupTask(this)
        } else {
            WallpaperPhotoRefreshWorker.cancel(this)
        }
    }

    private fun persistPhotoRefreshIntervalMinutes(minutes: Int) {
        wallpaperImageStore.photoRefreshIntervalMinutes = minutes
        if (photoBackgroundEnabledValue.value) {
            WallpaperPhotoRefreshWorker.setupTask(this)
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    private fun ContentView() {
        val dialogOpenState = remember { mutableStateOf(false) }
        val backgroundLocationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            rememberPermissionState(permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            null
        }
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
                        titleId = R.string.widget_live_wallpaper_weather_kind,
                        onSelected = { persistCoreSettings() }
                    )
                }
                item {
                    Spinner(
                        currentVal = dayNightTypeValueNow,
                        names = dayNightTypeKinds,
                        values = dayNightTypeValues,
                        titleId = R.string.widget_live_wallpaper_day_night_type,
                        onSelected = { persistCoreSettings() }
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
                            persistCoreSettings()
                        }
                    }
                }
                item {
                    SwitchPreferenceView(
                        title = stringResource(R.string.widget_live_wallpaper_parallax),
                        summary = { context: Context, _: Boolean ->
                            context.getString(R.string.widget_live_wallpaper_parallax_summary)
                        },
                        checked = parallaxEnabledValue.value,
                        withState = false,
                        card = false
                    ) { newValue ->
                        parallaxEnabledValue.value = newValue
                        persistCoreSettings()
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
                        persistPhotoBackgroundEnabled(newValue)
                    }
                }
                if (photoBackgroundEnabledValue.value) {
                    item {
                        Column(
                            modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin))
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.widget_live_wallpaper_refresh_interval,
                                    photoRefreshIntervalMinutesValue.value.roundToInt()
                                ),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.widget_live_wallpaper_refresh_interval_summary),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            val photoRefreshMinMinutes = WallpaperImageStore.MIN_REFRESH_INTERVAL_MINUTES.toFloat()
                            val photoRefreshMaxMinutes = WallpaperImageStore.MAX_REFRESH_INTERVAL_MINUTES.toFloat()
                            val photoRefreshRange = WallpaperImageStore.MAX_REFRESH_INTERVAL_MINUTES -
                                WallpaperImageStore.MIN_REFRESH_INTERVAL_MINUTES
                            val photoRefreshSteps =
                                photoRefreshRange / WallpaperImageStore.REFRESH_INTERVAL_STEP_MINUTES - 1
                            Slider(
                                value = photoRefreshIntervalMinutesValue.value,
                                onValueChange = { value ->
                                    photoRefreshIntervalMinutesValue.value =
                                        (
                                            (value / WallpaperImageStore.REFRESH_INTERVAL_STEP_MINUTES).roundToInt() *
                                                WallpaperImageStore.REFRESH_INTERVAL_STEP_MINUTES
                                            ).toFloat()
                                },
                                valueRange = photoRefreshMinMinutes..photoRefreshMaxMinutes,
                                steps = photoRefreshSteps,
                                onValueChangeFinished = {
                                    persistPhotoRefreshIntervalMinutes(
                                        photoRefreshIntervalMinutesValue.value.roundToInt()
                                    )
                                }
                            )
                            if (backgroundLocationPermissionState != null &&
                                backgroundLocationPermissionState.status != PermissionStatus.Granted
                            ) {
                                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                                Text(
                                    text = stringResource(R.string.widget_live_wallpaper_background_location_summary),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(
                                    onClick = {
                                        if (
                                            ActivityCompat.shouldShowRequestPermissionRationale(
                                                this@LiveWallpaperConfigActivity,
                                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                            )
                                        ) {
                                            backgroundLocationPermissionState.launchPermissionRequest()
                                        } else {
                                            openApplicationDetailsSettings()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                ) {
                                    Text(stringResource(R.string.widget_live_wallpaper_background_location_action))
                                }
                            }
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = dimensionResource(R.dimen.normal_margin)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.widget_live_wallpaper_experimental),
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    SwitchPreferenceView(
                        title = stringResource(R.string.widget_live_wallpaper_seasonal_grading),
                        summary = { context: Context, _: Boolean ->
                            context.getString(R.string.widget_live_wallpaper_seasonal_grading_summary)
                        },
                        checked = seasonGradingEnabledValue.value,
                        withState = false,
                        card = false
                    ) { newValue ->
                        seasonGradingEnabledValue.value = newValue
                        persistCoreSettings()
                    }
                }
                if (seasonGradingEnabledValue.value) {
                    item {
                        Column(
                            modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin))
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.widget_live_wallpaper_grading_strength,
                                    (seasonGradingStrengthValue.value * 100).roundToInt()
                                ),
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = seasonGradingStrengthValue.value,
                                onValueChange = { seasonGradingStrengthValue.value = it },
                                valueRange = 0f..1f,
                                onValueChangeFinished = { persistCoreSettings() }
                            )
                        }
                    }
                }
                item {
                    SwitchPreferenceView(
                        title = stringResource(R.string.widget_live_wallpaper_new_clouds),
                        summary = { context: Context, _: Boolean ->
                            context.getString(R.string.widget_live_wallpaper_new_clouds_summary)
                        },
                        checked = newCloudsEnabledValue.value,
                        withState = false,
                        card = false
                    ) { newValue ->
                        newCloudsEnabledValue.value = newValue
                        persistCoreSettings()
                    }
                }
                // wolke-refactor-plan Stap 6: the tuning screen is deliberately debug-build-only,
                // not just hidden-by-default, so it can never ship reachable in a release build.
                if (BuildConfig.DEBUG && newCloudsEnabledValue.value) {
                    item {
                        OutlinedButton(
                            onClick = {
                                startActivity(Intent(this@LiveWallpaperConfigActivity, CloudTuningActivity::class.java))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimensionResource(R.dimen.normal_margin))
                        ) {
                            Text(stringResource(R.string.widget_live_wallpaper_new_clouds_tuning))
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin))
                    ) {
                        val cacheLimitMb = photoCacheLimitMbValue.value.roundToInt()
                        Text(
                            text = stringResource(R.string.widget_live_wallpaper_photo_cache, cacheLimitMb),
                            fontWeight = FontWeight.Bold
                        )
                        val cacheUsedMb = cachedPhotoBytesValue.value / BYTES_PER_MB.toDouble()
                        val cacheUsage = (cacheUsedMb / cacheLimitMb.coerceAtLeast(1))
                            .toFloat()
                            .coerceIn(0f, 1f)
                        Text(
                            text = stringResource(
                                R.string.widget_live_wallpaper_cache_usage,
                                cachedPhotoCountValue.value,
                                cacheUsedMb
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { cacheUsage },
                            modifier = Modifier
                                .padding(top = 6.dp, bottom = 4.dp)
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(4.dp)
                                ),
                            color = Color(0xFFE4003B),
                            trackColor = Color.Transparent,
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {}
                        )
                        OutlinedButton(
                            onClick = {
                                startActivity(
                                    Intent(
                                        this@LiveWallpaperConfigActivity,
                                        WallpaperPhotoManagerActivity::class.java
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.wallpaper_photo_manager_title))
                        }
                        val recentUrlCount = recentUrlCountValue.value.roundToInt()
                        Text(
                            text = stringResource(
                                R.string.widget_live_wallpaper_recent_photos_skipped,
                                recentUrlCount
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        val minRecentUrlCount = WallpaperImageStore.MIN_RECENT_URL_COUNT
                        val maxRecentUrlCount = WallpaperImageStore.MAX_RECENT_URL_COUNT
                        Slider(
                            value = recentUrlCountValue.value,
                            onValueChange = { recentUrlCountValue.value = it.roundToInt().toFloat() },
                            valueRange = minRecentUrlCount.toFloat()..maxRecentUrlCount.toFloat(),
                            steps = maxRecentUrlCount - minRecentUrlCount - 1,
                            onValueChangeFinished = {
                                wallpaperImageStore.recentUrlCount = recentUrlCountValue.value.roundToInt()
                            }
                        )
                        val cacheLimitMinMb = WallpaperImageStore.MIN_CACHE_LIMIT_MB.toFloat()
                        val cacheLimitMaxMb = WallpaperImageStore.MAX_CACHE_LIMIT_MB.toFloat()
                        Slider(
                            value = photoCacheLimitMbValue.value,
                            onValueChange = { value ->
                                photoCacheLimitMbValue.value =
                                    (value / CACHE_LIMIT_STEP_MB).roundToInt() * CACHE_LIMIT_STEP_MB
                            },
                            valueRange = cacheLimitMinMb..cacheLimitMaxMb,
                            steps = CACHE_LIMIT_STEPS,
                            onValueChangeFinished = {
                                wallpaperImageStore.photoCacheLimitMb =
                                    photoCacheLimitMbValue.value.roundToInt()
                                lifecycleScope.launch(Dispatchers.IO) {
                                    wallpaperRepository.enforceCacheLimit()
                                    refreshCacheStats()
                                }
                            }
                        )
                        val maxPhotos = maxPhotosPerLocationValue.value.roundToInt()
                        Text(
                            text = stringResource(R.string.widget_live_wallpaper_max_per_location, maxPhotos),
                            fontWeight = FontWeight.Bold
                        )
                        val minPhotosPerLocation = WallpaperImageStore.MIN_PHOTOS_PER_LOCATION
                        val maxPhotosPerLocation = WallpaperImageStore.MAX_PHOTOS_PER_LOCATION
                        Slider(
                            value = maxPhotosPerLocationValue.value,
                            onValueChange = { maxPhotosPerLocationValue.value = it.roundToInt().toFloat() },
                            valueRange = minPhotosPerLocation.toFloat()..maxPhotosPerLocation.toFloat(),
                            steps = maxPhotosPerLocation - minPhotosPerLocation - 1,
                            onValueChangeFinished = {
                                wallpaperImageStore.maxCachedPhotosPerLocation =
                                    maxPhotosPerLocationValue.value.roundToInt()
                                lifecycleScope.launch(Dispatchers.IO) {
                                    wallpaperRepository.enforceCacheLimit()
                                    refreshCacheStats()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
                        run {
                            val context = LocalContext.current
                            val freshness = DataFreshness.create(
                                weatherRefreshedAtMillis = weatherRefreshedAtValue.value,
                                photoRefreshedAtMillis = photoRefreshedAtValue.value,
                                nowMillis = System.currentTimeMillis()
                            )
                            val weatherAge = dataAgeLabel(
                                context,
                                weatherRefreshedAtValue.value,
                                freshness.isWeatherStale
                            )
                            val photoAge = dataAgeLabel(
                                context,
                                photoRefreshedAtValue.value,
                                freshness.isPhotoStale
                            )
                            Text(
                                text = stringResource(R.string.widget_live_wallpaper_weather_age, weatherAge),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = stringResource(R.string.widget_live_wallpaper_photo_age, photoAge),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                        }
                        if (currentLocationValue.value.isNotBlank()) {
                            Text(
                                text = stringResource(
                                    R.string.widget_live_wallpaper_current_location,
                                    currentLocationValue.value
                                ),
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
                var timeLeft by remember { mutableIntStateOf(3) }
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
                                persistCoreSettings()
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
                        Text(stringResource(R.string.settings_main_section_animations))
                    },
                    text = {
                        Text(stringResource(R.string.widget_live_wallpaper_animations_enable_warning1))
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
        onSelected: () -> Unit = {},
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
                            onSelected()
                        }
                    )
                }
            }
        }
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
        const val CACHE_LIMIT_STEP_MB = 25f
        const val CACHE_LIMIT_STEPS = 18
    }
}
