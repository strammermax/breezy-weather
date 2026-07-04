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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Global animated precipitation radar frames from RainViewer's free public API.
 *
 * Fetches `weather-maps.json`, which lists recent past radar frames plus short-term nowcast
 * frames. Combined with [RadarFrames.tileUrl] these drive a frame-by-frame animation. Used
 * as the worldwide radar map; for NL rain-trend numbers use [BuienradarNowcastSource].
 */
class RainViewerRadarSource(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun getFrames(): RadarFrames? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(WEATHER_MAPS_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    parse(response.body.string())
                }
            } catch (e: Throwable) {
                null
            }
        }
    }

    private fun parse(body: String): RadarFrames? {
        val root = JSONObject(body)
        val host = root.optString("host").ifBlank { return null }
        val radar = root.optJSONObject("radar") ?: return null

        val frames = mutableListOf<RadarFrame>()
        radar.optJSONArray("past")?.let { past ->
            for (i in 0 until past.length()) {
                val o = past.getJSONObject(i)
                frames.add(
                    RadarFrame(
                        timeSeconds = o.optLong("time"),
                        path = o.optString("path"),
                        isForecast = false
                    )
                )
            }
        }
        radar.optJSONArray("nowcast")?.let { nowcast ->
            for (i in 0 until nowcast.length()) {
                val o = nowcast.getJSONObject(i)
                frames.add(
                    RadarFrame(
                        timeSeconds = o.optLong("time"),
                        path = o.optString("path"),
                        isForecast = true
                    )
                )
            }
        }

        if (frames.isEmpty()) return null
        return RadarFrames(host = host, frames = frames)
    }

    companion object {
        private const val WEATHER_MAPS_URL = "https://api.rainviewer.com/public/weather-maps.json"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
