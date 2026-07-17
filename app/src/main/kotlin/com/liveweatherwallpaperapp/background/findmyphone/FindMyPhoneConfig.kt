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

package com.liveweatherwallpaperapp.background.findmyphone

import android.content.Context
import com.liveweatherwallpaperapp.R
import org.json.JSONObject

/**
 * Tunables for the "Find my phone" feature, bundled as [R.raw.find_my_phone_config] rather than
 * hardcoded, so the sound threshold and lock-screen arm delay can be tuned without a code change.
 * Read once at app startup via [load]; [current] then serves the in-memory values for the rest
 * of the process lifetime.
 */
data class FindMyPhoneConfig(
    /** RMS level (dB relative to full scale) that ambient sound must clear before the detector
     * bothers analyzing a buffer for claps/whistles. */
    val rmsGateDb: Float = DEFAULT_RMS_GATE_DB,
    /** How long the screen must stay continuously off/locked before the microphone starts
     * listening. */
    val armDelayMinutes: Int = DEFAULT_ARM_DELAY_MINUTES,
) {
    companion object {
        private const val DEFAULT_RMS_GATE_DB = -35f
        private const val DEFAULT_ARM_DELAY_MINUTES = 15

        @Volatile
        var current: FindMyPhoneConfig = FindMyPhoneConfig()
            private set

        /** Parses [R.raw.find_my_phone_config]; falls back to defaults if the asset is missing
         * or malformed, so a packaging mistake never prevents the app from starting. */
        fun load(context: Context) {
            current = try {
                val json = context.resources.openRawResource(R.raw.find_my_phone_config)
                    .bufferedReader()
                    .use { it.readText() }
                val root = JSONObject(json)
                FindMyPhoneConfig(
                    rmsGateDb = root.optDouble("rmsGateDb", DEFAULT_RMS_GATE_DB.toDouble()).toFloat(),
                    armDelayMinutes = root.optInt("armDelayMinutes", DEFAULT_ARM_DELAY_MINUTES)
                )
            } catch (e: Exception) {
                FindMyPhoneConfig()
            }
        }
    }
}
