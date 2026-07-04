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

package com.livewallpaperweather.radar

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.lifecycleScope
import livewallpaperweather.data.location.LocationRepository
import livewallpaperweather.data.weather.WeatherRepository
import livewallpaperweather.domain.location.model.Location
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.livewallpaperweather.R
import com.livewallpaperweather.common.activities.BreezyActivity
import com.livewallpaperweather.common.extensions.isBackgroundAnimationEnabled
import com.livewallpaperweather.domain.location.model.isDaylight
import com.livewallpaperweather.domain.settings.SettingsManager
import com.livewallpaperweather.ui.common.widgets.Material3Scaffold
import com.livewallpaperweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.livewallpaperweather.ui.theme.compose.BreezyWeatherTheme
import com.livewallpaperweather.ui.theme.weatherView.WeatherViewController
import com.livewallpaperweather.wallpaper.CelestialTiming
import com.livewallpaperweather.wallpaper.WallpaperEffectView
import com.livewallpaperweather.wallpaper.WallpaperSceneSnapshot
import com.livewallpaperweather.wallpaper.WallpaperSceneStateFactory
import com.livewallpaperweather.wallpaper.photo.WallpaperRepository
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/**
 * Precipitation radar screen. Combines two sources so they can be compared:
 * Buienradar (NL rain-trend nowcast graph) and RainViewer (worldwide animated radar map,
 * rendered with Leaflet inside a WebView).
 */
@AndroidEntryPoint
class RadarActivity : BreezyActivity() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository

    private lateinit var effectView: WallpaperEffectView

    private var loading by mutableStateOf(true)
    private var placeName by mutableStateOf<String?>(null)
    private var latitude by mutableStateOf<Double?>(null)
    private var longitude by mutableStateOf<Double?>(null)
    private var rainTrend by mutableStateOf<List<RainTrendPoint>>(emptyList())
    private var hourlyTrend by mutableStateOf<List<RainTrendPoint>>(emptyList())
    private var trendRange by mutableStateOf(2)
    private var radarSource by mutableStateOf("rainviewer") // "rainviewer" | "buienradar" | "windy"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        radarSource = intent.getStringExtra(EXTRA_RADAR_SOURCE)
            ?.takeIf { it in RADAR_SOURCES }
            ?: "rainviewer"
        // Same sky-gradient backdrop as the main screen / details screen (ACT-013/ACT-014),
        // instead of an opaque Material surface, so this screen doesn't look like a different app.
        window.setBackgroundDrawableResource(R.drawable.bg_glass_sky)
        setContent {
            BreezyWeatherTheme {
                ContentView()
            }
        }

        // Animated weather effects (clouds, rain, snow, fog …) via the same
        // WallpaperWeatherEffectRenderer used by the live wallpaper and the details
        // screen, so this screen doesn't look static compared to the rest of the app.
        effectView = WallpaperEffectView(this)
        val contentFrame = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        contentFrame.addView(
            effectView,
            0,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        loadData()
    }

    override fun onResume() {
        super.onResume()
        if (::effectView.isInitialized) effectView.setDrawable(isBackgroundAnimationEnabled)
    }

    override fun onPause() {
        super.onPause()
        if (::effectView.isInitialized) effectView.setDrawable(false)
    }

    private fun loadData() {
        lifecycleScope.launch {
            loading = true
            val requestedLocationId = intent.getStringExtra(EXTRA_LOCATION_ID)
            val location: Location? = if (requestedLocationId != null) {
                locationRepository.getLocation(requestedLocationId, withParameters = false)
            } else {
                locationRepository.getFirstLocation(withParameters = false)
            }
            if (location == null || !location.isUsable) {
                loading = false
                return@launch
            }
            placeName = location.city.ifBlank { location.admin1 ?: location.country }
            latitude = location.latitude
            longitude = location.longitude
            rainTrend = BuienradarNowcastSource().getRainTrend(location.latitude, location.longitude)
            hourlyTrend = try {
                val weather = weatherRepository.getWeatherByLocationId(
                    location.formattedId,
                    withDaily = true,
                    withHourly = true,
                    withMinutely = false,
                    withAlerts = false,
                    withNormals = false
                )
                val locationWithWeather = location.copy(weather = weather)
                val weatherKind = WeatherViewController.getWeatherKind(locationWithWeather)
                val daylight = if (locationWithWeather.isDaylight) 1f else 0f
                effectView.setWeather(weatherKind, daylight)
                effectView.setDrawable(isBackgroundAnimationEnabled)
                renderPhotoBackground(locationWithWeather, weatherKind, daylight)
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .apply { timeZone = location.timeZone }
                weather?.nextHourlyForecast?.take(24)?.map { h ->
                    RainTrendPoint(
                        timeLabel = fmt.format(h.date),
                        intensityMmH = h.precipitation?.total?.inMillimeters ?: 0.0
                    )
                } ?: emptyList()
            } catch (e: Throwable) {
                emptyList()
            }
            loading = false
        }
    }

    /**
     * Renders the same static sky+photo snapshot as the home screen / details screen
     * (ACT-013/ACT-014) as this screen's window background, behind the animated
     * [effectView] layer. Without this, the screen only shows a flat sky gradient
     * instead of the cached location photo.
     */
    private suspend fun renderPhotoBackground(location: Location, weatherKind: Int, daylight: Float) {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        if (width <= 0 || height <= 0) return

        val wind = location.weather?.current?.wind
        val now = System.currentTimeMillis()
        val sunInterval = CelestialTiming.closestAstroInterval(CelestialTiming.sunIntervals(location, now), now)
            ?: CelestialTiming.approximateSunInterval(location, now)
        val moonInterval = CelestialTiming.closestAstroInterval(CelestialTiming.moonIntervals(location, now), now)

        val sceneState = WallpaperSceneStateFactory.create(
            weatherKind = weatherKind,
            daylight = daylight,
            windSpeedMetersPerSecond = wind?.speed?.value?.toFloat() ?: 0f,
            windGustMetersPerSecond = wind?.gusts?.value?.toFloat() ?: 0f,
            windDirectionDegrees = wind?.degree?.toFloat(),
            sunriseMillis = sunInterval?.first,
            sunsetMillis = sunInterval?.second,
            moonriseMillis = moonInterval?.first,
            moonsetMillis = moonInterval?.second,
        )
        val photo = withContext(Dispatchers.IO) { wallpaperRepository.loadCachedBitmap() }
        // Same depth map the live wallpaper uses to keep clouds behind near/foreground photo
        // content (e.g. a building) instead of painting over the whole photo.
        val depth = withContext(Dispatchers.IO) { wallpaperRepository.loadCachedDepthBitmap() }
        var nearPhoto: Bitmap? = null
        val bitmap = withContext(Dispatchers.Default) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                nearPhoto = WallpaperSceneSnapshot.render(Canvas(it), width, height, photo, sceneState, resources, depth)
            }
        }
        effectView.setForegroundPhoto(
            nearPhoto,
            if (sceneState.usesGreyscalePhoto) sceneState.photoGreyscaleAmount else 0f,
        )
        window.setBackgroundDrawable(BitmapDrawable(resources, bitmap))
    }

    @Composable
    private fun ContentView() {
        // ACT-013: same glass-card color scheme as the details screen, so cards/tabs/text
        // read as translucent glass over the sky-gradient background instead of assuming an
        // opaque white surface behind them.
        val glassContentColor = colorResource(R.color.colorGlassTopBarText)
        val glassColorScheme = MaterialTheme.colorScheme.copy(
            surface = colorResource(R.color.colorGlassCardBackground),
            surfaceVariant = colorResource(R.color.colorGlassCardBackground),
            onSurface = glassContentColor,
            onSurfaceVariant = glassContentColor.copy(alpha = 0.75f),
            outline = colorResource(R.color.colorGlassCardStroke),
            outlineVariant = colorResource(R.color.colorGlassCardStroke)
        )
        MaterialTheme(colorScheme = glassColorScheme) {
            Material3Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.radar_title)) },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back)
                                )
                            }
                        },
                        actions = {
                            placeName?.let {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(text = it, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            titleContentColor = glassContentColor,
                            navigationIconContentColor = glassContentColor,
                            actionIconContentColor = glassContentColor
                        )
                    )
                },
                containerColor = Color.Transparent,
                contentColor = glassContentColor
            ) { paddings ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddings)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp).padding(top = 24.dp))
                    return@Column
                }

                // The Meteoblue and Buienradar widgets render their maps via WebGL/canvas, which
                // appears to fight the animated background's hardware layer for the window's
                // surface and blanks the whole screen. Pausing that layer while either tab is
                // visible avoids it — RainViewer's Leaflet map doesn't have this conflict.
                LaunchedEffect(radarSource) {
                    if (::effectView.isInitialized) {
                        effectView.setDrawable(radarSource == "rainviewer" && isBackgroundAnimationEnabled)
                    }
                }

                // Source selector
                val radarTabs = listOf(
                    "rainviewer" to R.string.radar_source_world,
                    "buienradar" to R.string.radar_source_nl,
                    "meteoblue" to R.string.radar_source_meteoblue
                )
                TabRow(
                    selectedTabIndex = radarTabs.indexOfFirst { it.first == radarSource }.coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = glassContentColor
                ) {
                    radarTabs.forEach { (source, labelRes) ->
                        Tab(
                            selected = radarSource == source,
                            onClick = { radarSource = source },
                            text = { Text(stringResource(labelRes)) },
                            selectedContentColor = glassContentColor,
                            unselectedContentColor = glassContentColor.copy(alpha = 0.7f)
                        )
                    }
                }

                // Map — RainViewer (worldwide), Buienradar gadget (NL, 5 days) or Windy (wind/pressure)
                when (radarSource) {
                    "rainviewer" -> {
                        val lat = latitude
                        val lon = longitude
                        if (lat != null && lon != null) {
                            RadarMap(lat, lon, modifier = Modifier.fillMaxWidth().height(480.dp))
                        } else {
                            Text(
                                text = stringResource(R.string.radar_frames_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    "meteoblue" -> {
                        MeteoblueMap(latitude, longitude, modifier = Modifier.fillMaxWidth().height(480.dp))
                    }
                    else -> BuienradarMap(latitude, longitude, modifier = Modifier.fillMaxWidth().height(480.dp))
                }

                // Rain trend chart — only shown for the RainViewer tab
                if (radarSource == "rainviewer") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle(stringResource(R.string.radar_section_rain_trend))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(2, 3, 6, 12, 24).forEach { hours ->
                                FilterChip(
                                    selected = trendRange == hours,
                                    onClick = { trendRange = hours },
                                    label = { Text("${hours}u") }
                                )
                            }
                        }
                        RainTrendChart(
                            points = if (trendRange <= 2) rainTrend else hourlyTrend.take(trendRange),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Text(
                    text = stringResource(
                        when (radarSource) {
                            "buienradar" -> R.string.radar_attribution_buienradar
                            "meteoblue" -> R.string.radar_attribution_meteoblue
                            else -> R.string.radar_attribution
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 16.dp)
                )
            }
            }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun RadarMap(latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
        val dark = when (SettingsManager.getInstance(this).radarTileMapStyle) {
            "dark" -> true
            "light" -> false
            else -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    RainViewerMap.load(this, latitude, longitude, dark, compact = false)
                }
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun MeteoblueMap(latitude: Double?, longitude: Double?, modifier: Modifier = Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    RadarWebMapLoader.loadMeteoblue(this, latitude, longitude)
                }
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun BuienradarMap(latitude: Double?, longitude: Double?, modifier: Modifier = Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    RadarWebMapLoader.loadBuienradar(this, latitude, longitude)
                }
            }
        )
    }

    companion object {
        private const val EXTRA_LOCATION_ID = "radar_location_id"
        private const val EXTRA_RADAR_SOURCE = "radar_source"
        private val RADAR_SOURCES = setOf("rainviewer", "buienradar", "meteoblue")

        fun createIntent(context: Context, location: Location, radarSource: String? = null): Intent {
            return Intent(context, RadarActivity::class.java).apply {
                putExtra(EXTRA_LOCATION_ID, location.formattedId)
                radarSource?.let { putExtra(EXTRA_RADAR_SOURCE, it) }
            }
        }
    }
}
