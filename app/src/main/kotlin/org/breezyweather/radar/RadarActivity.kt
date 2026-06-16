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

package org.breezyweather.radar

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import breezyweather.data.location.LocationRepository
import breezyweather.data.weather.WeatherRepository
import breezyweather.domain.location.model.Location
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
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

    private var loading by mutableStateOf(true)
    private var placeName by mutableStateOf<String?>(null)
    private var latitude by mutableStateOf<Double?>(null)
    private var longitude by mutableStateOf<Double?>(null)
    private var rainTrend by mutableStateOf<List<RainTrendPoint>>(emptyList())
    private var hourlyTrend by mutableStateOf<List<RainTrendPoint>>(emptyList())
    private var trendRange by mutableStateOf(2)
    private var radarSource by mutableStateOf("rainviewer") // "rainviewer" | "buienradar"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BreezyWeatherTheme {
                ContentView()
            }
        }
        loadData()
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
                    withDaily = false,
                    withHourly = true,
                    withMinutely = false,
                    withAlerts = false,
                    withNormals = false
                )
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

    @Composable
    private fun ContentView() {
        Material3Scaffold(
            topBar = {
                FitStatusBarTopAppBar(
                    title = stringResource(R.string.radar_title),
                    onBackPressed = { finish() }
                )
            }
        ) { paddings ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddings)
                    .padding(16.dp)
            ) {
                placeName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                    return@Column
                }

                // Source selector
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = radarSource == "rainviewer",
                        onClick = { radarSource = "rainviewer" },
                        label = { Text(stringResource(R.string.radar_source_world)) }
                    )
                    FilterChip(
                        selected = radarSource == "buienradar",
                        onClick = { radarSource = "buienradar" },
                        label = { Text(stringResource(R.string.radar_source_nl)) }
                    )
                }

                // Map — RainViewer (worldwide) or Buienradar gadget (NL, 5 days)
                if (radarSource == "rainviewer") {
                    val lat = latitude
                    val lon = longitude
                    if (lat != null && lon != null) {
                        RadarMap(lat, lon, modifier = Modifier.fillMaxWidth().height(480.dp))
                    } else {
                        Text(
                            text = stringResource(R.string.radar_frames_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    BuienradarGadgetMap(modifier = Modifier.fillMaxWidth().height(480.dp))
                }

                // Rain trend chart — only shown for the RainViewer tab
                if (radarSource == "rainviewer") {
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

                Text(
                    text = stringResource(
                        if (radarSource == "buienradar") R.string.radar_attribution_buienradar
                        else R.string.radar_attribution
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
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
        val dark = isSystemInDarkTheme()
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
    private fun BuienradarGadgetMap(modifier: Modifier = Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = WebViewClient()
                    loadUrl("https://gadgets.buienradar.nl/gadget/radarfivedays")
                }
            }
        )
    }

    companion object {
        private const val EXTRA_LOCATION_ID = "radar_location_id"

        fun createIntent(context: Context, location: Location): Intent {
            return Intent(context, RadarActivity::class.java).apply {
                putExtra(EXTRA_LOCATION_ID, location.formattedId)
            }
        }
    }
}
