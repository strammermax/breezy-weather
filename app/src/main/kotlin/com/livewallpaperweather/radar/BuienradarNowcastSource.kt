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
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * Precipitation nowcast (rain trend) for the Netherlands / Benelux from Buienradar.
 *
 * Uses the free "raintext" endpoint, which returns ~2 hours of forecast in 5-minute steps,
 * one line per step formatted as `value|HH:MM` where `value` is 0-255. The intensity in
 * mm/h is `10^((value - 109) / 32)`.
 *
 * Attribution: when using Buienradar data you must credit buienradar.nl in the app.
 * Coverage is limited to NL/Benelux; outside that area use [RainViewerRadarSource] instead.
 */
class BuienradarNowcastSource(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun getRainTrend(latitude: Double, longitude: Double): List<RainTrendPoint> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL?lat=${String.format(Locale.US, "%.2f", latitude)}" +
                    "&lon=${String.format(Locale.US, "%.2f", longitude)}"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    parse(response.body.string())
                }
            } catch (e: Throwable) {
                emptyList()
            }
        }
    }

    private fun parse(body: String): List<RainTrendPoint> {
        return body.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val parts = trimmed.split("|")
                if (parts.size != 2) return@mapNotNull null
                val value = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
                val time = parts[1].trim()
                if (time.isEmpty()) return@mapNotNull null
                RainTrendPoint(timeLabel = time, intensityMmH = valueToMmH(value))
            }
            .toList()
    }

    companion object {
        // gpsgadget is the modern host; gadgets.buienradar.nl is the legacy alias.
        private const val BASE_URL = "https://gpsgadget.buienradar.nl/data/raintext"

        /** Converts Buienradar's 0-255 intensity code to millimetres per hour. */
        fun valueToMmH(value: Int): Double {
            if (value <= 0) return 0.0
            return 10.0.pow((value - 109.0) / 32.0)
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
