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
import com.liveweatherwallpaperapp.domain.settings.AppDefaults
import com.liveweatherwallpaperapp.domain.settings.ConfigStore

/** Master switch for the "Find my phone" clap/whistle-detection feature. */
class FindMyPhoneStore(context: Context) {

    private val config = ConfigStore(context, SP_NAME)

    var enabled: Boolean
        get() = config.getBoolean(KEY_ENABLED, false)
        set(value) {
            config.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** Whether clap detection (3 claps -> ringtone) is active. Independent of [whistleEnabled]
     * so either pattern can be turned off on its own once "Find my phone" itself is on. */
    var clapEnabled: Boolean
        get() = config.getBoolean(KEY_CLAP_ENABLED, true)
        set(value) {
            config.edit().putBoolean(KEY_CLAP_ENABLED, value).apply()
        }

    /** Whether whistle detection (1 whistle -> notification-sound reply) is active. */
    var whistleEnabled: Boolean
        get() = config.getBoolean(KEY_WHISTLE_ENABLED, true)
        set(value) {
            config.edit().putBoolean(KEY_WHISTLE_ENABLED, value).apply()
        }

    /** The user's own whistle pitch (Hz), captured by [FindMyPhoneCalibrator] right after they
     * enable the feature. Null means "not calibrated yet, use the bundled default band" --
     * everyone whistles at a different pitch, so a fixed band tuned for nobody in particular is
     * only ever a fallback. */
    var whistleCenterHz: Float?
        get() = if (config.contains(KEY_WHISTLE_CENTER_HZ)) {
            config.getFloat(KEY_WHISTLE_CENTER_HZ, 0f)
        } else {
            null
        }
        set(value) {
            if (value == null) {
                config.edit().remove(KEY_WHISTLE_CENTER_HZ).apply()
            } else {
                config.edit().putFloat(KEY_WHISTLE_CENTER_HZ, value).apply()
            }
        }

    /** Tester-mode override for [AppDefaults.findMyPhone.rmsGateDb], null meaning "use the bundled
     * default". Lets testers dial the threshold in without needing a new build. Picked up the
     * next time the service (re-)arms, not instantly while it's already listening. */
    var testerRmsGateDbOverride: Float?
        get() = if (config.contains(KEY_TESTER_RMS_GATE_DB)) {
            config.getFloat(KEY_TESTER_RMS_GATE_DB, 0f)
        } else {
            null
        }
        set(value) {
            if (value == null) {
                config.edit().remove(KEY_TESTER_RMS_GATE_DB).apply()
            } else {
                config.edit().putFloat(KEY_TESTER_RMS_GATE_DB, value).apply()
            }
        }

    /** Tester-mode override for [AppDefaults.findMyPhone.armDelayMinutes], null meaning "use the
     * bundled default". Picked up the next time the screen turns off and the arm delay starts. */
    var testerArmDelayMinutesOverride: Int?
        get() = if (config.contains(KEY_TESTER_ARM_DELAY_MINUTES)) {
            config.getInt(KEY_TESTER_ARM_DELAY_MINUTES, 0)
        } else {
            null
        }
        set(value) {
            if (value == null) {
                config.edit().remove(KEY_TESTER_ARM_DELAY_MINUTES).apply()
            } else {
                config.edit().putInt(KEY_TESTER_ARM_DELAY_MINUTES, value).apply()
            }
        }

    companion object {
        /**
         * Temporarily disables the whole "Find my phone" feature (settings UI, service
         * start/stop calls) without deleting its code. Its permissions and service declaration
         * are absent from AndroidManifest.xml, so disabled builds do not declare microphone
         * access. Re-enabling the feature requires a separate manifest review.
         */
        const val FEATURE_ENABLED = false

        private const val SP_NAME = "find_my_phone"
        private const val KEY_ENABLED = "find_my_phone_enabled"
        private const val KEY_CLAP_ENABLED = "find_my_phone_clap_enabled"
        private const val KEY_WHISTLE_ENABLED = "find_my_phone_whistle_enabled"
        private const val KEY_WHISTLE_CENTER_HZ = "find_my_phone_whistle_center_hz"
        private const val KEY_TESTER_RMS_GATE_DB = "find_my_phone_tester_rms_gate_db"
        private const val KEY_TESTER_ARM_DELAY_MINUTES = "find_my_phone_tester_arm_delay_minutes"
    }
}
