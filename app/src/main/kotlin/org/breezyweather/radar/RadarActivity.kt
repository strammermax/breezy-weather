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
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import breezyweather.domain.location.model.Location
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
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

    private var loading by mutableStateOf(true)
    private var placeName by mutableStateOf<String?>(null)
    private var latitude by mutableStateOf<Double?>(null)
    private var longitude by mutableStateOf<Double?>(null)
    private var rainTrend by mutableStateOf<List<RainTrendPoint>>(emptyList())

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
            val location: Location? = locationRepository.getFirstLocation(withParameters = false)
            if (location == null || !location.isUsable) {
                loading = false
                return@launch
            }
            placeName = location.city.ifBlank { location.admin1 ?: location.country }
            latitude = location.latitude
            longitude = location.longitude
            rainTrend = BuienradarNowcastSource().getRainTrend(location.latitude, location.longitude)
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

                SectionTitle(stringResource(R.string.radar_section_radar_map))
                val lat = latitude
                val lon = longitude
                if (lat != null && lon != null) {
                    RadarMap(lat, lon, modifier = Modifier.fillMaxWidth().height(420.dp))
                } else {
                    Text(
                        text = stringResource(R.string.radar_frames_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SectionTitle(stringResource(R.string.radar_section_rain_trend))
                RainTrendChart(
                    points = rainTrend,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.radar_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
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
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    // Leaflet tiles load but stay invisible under hardware acceleration in some
                    // WebViews (CSS transforms aren't composited); software rendering fixes it.
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                            Log.i("RadarWeb", "${cm.message()} (line ${cm.lineNumber()})")
                            return true
                        }
                    }
                    loadDataWithBaseURL(
                        "https://www.rainviewer.com",
                        radarHtml(latitude, longitude),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )
    }

    companion object {
        /**
         * Self-contained Leaflet + RainViewer page: an OpenStreetMap base layer with the
         * animated precipitation radar frames (past + nowcast) playing in a loop, centred on
         * the user's location.
         */
        private fun radarHtml(latitude: Double, longitude: Double): String {
            val lat = String.format(Locale.US, "%.5f", latitude)
            val lon = String.format(Locale.US, "%.5f", longitude)
            return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
 html,body{margin:0;padding:0;background:#0e1116}
 #map{width:100%;height:420px}
 .ts{position:absolute;top:8px;left:50%;transform:translateX(-50%);z-index:1000;
     background:rgba(0,0,0,.65);color:#fff;padding:4px 12px;border-radius:14px;
     font:13px/1.4 sans-serif}
</style></head><body>
<div id="map"></div><div class="ts" id="ts">…</div>
<script>
 var lat=$lat, lon=$lon;
 var map=L.map('map',{zoomControl:true,attributionControl:false}).setView([lat,lon],7);
 var base=L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png',{subdomains:'abcd',maxZoom:12});
 base.on('tileerror',function(e){ console.log('base tileerror'); });
 base.on('load',function(){ console.log('base tiles loaded'); });
 base.addTo(map);
 L.circleMarker([lat,lon],{radius:5,color:'#fff',weight:2,fillColor:'#2196f3',fillOpacity:1}).addTo(map);
 // The WebView often has size 0 when Leaflet initialises, which stops tiles from loading.
 // Re-measure once the view has been laid out.
 function fixSize(){ try{ map.invalidateSize(true); }catch(e){} }
 map.whenReady(function(){ console.log('map ready'); setTimeout(function(){ fixSize(); var s=map.getSize(); console.log('size '+s.x+'x'+s.y); },150); });
 window.addEventListener('load', fixSize);
 [150,400,800,1500,2500].forEach(function(t){ setTimeout(fixSize, t); });
 var frames=[],layers={},idx=0,host='',tsEl=document.getElementById('ts');
 function layerFor(f){
   if(!layers[f.path]){
     layers[f.path]=L.tileLayer(host+f.path+'/256/{z}/{x}/{y}/2/1_1.png',{opacity:0,zIndex:Math.round(f.time/1000),maxNativeZoom:7,maxZoom:12});
     layers[f.path].addTo(map);
   }
   return layers[f.path];
 }
 function show(i){
   for(var j=0;j<frames.length;j++){
     var l=layers[frames[j].path]; if(l) l.setOpacity(j===i?0.75:0);
   }
   var d=new Date(frames[i].time*1000);
   tsEl.textContent=(frames[i].nowcast?'⏩ ':'')+d.toLocaleTimeString();
 }
 fetch('https://api.rainviewer.com/public/weather-maps.json').then(function(r){return r.json();}).then(function(d){
   host=d.host;
   var past=(d.radar&&d.radar.past)||[], now=(d.radar&&d.radar.nowcast)||[];
   now.forEach(function(f){f.nowcast=true;});
   frames=past.concat(now);
   console.log('frames '+frames.length+' host '+host);
   frames.forEach(layerFor);
   idx=Math.max(0,past.length-1); show(idx);
   setInterval(function(){ idx=(idx+1)%frames.length; show(idx); },700);
 }).catch(function(e){ tsEl.textContent='radar n/a'; });
</script></body></html>
            """.trimIndent()
        }
    }
}
