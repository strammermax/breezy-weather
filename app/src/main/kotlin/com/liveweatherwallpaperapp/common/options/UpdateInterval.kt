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

package com.liveweatherwallpaperapp.common.options

import android.content.Context
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.utils.UnitUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

enum class UpdateInterval(
    override val id: String,
    val interval: Duration?,
) : BaseEnum {

    INTERVAL_NEVER("never", null),

    /**
     * A 1-hour baseline, battery-friendly when nothing is happening — the adaptive refresh (see
     * [com.liveweatherwallpaperapp.background.weather.needsAdaptiveRefresh]) fills in the gaps with a
     * short-interval follow-up whenever there's an active alert or the forecast is about to
     * change, so this combines a calm default with fast reaction when it actually matters.
     */
    INTERVAL_AUTO("auto", 1.hours),
    INTERVAL_0_15("0:15", 15.minutes),
    INTERVAL_0_30("0:30", 30.minutes),
    INTERVAL_1_00("1:00", 1.hours),
    INTERVAL_1_30("1:30", 1.5.hours),
    INTERVAL_2_00("2:00", 2.hours),
    INTERVAL_3_00("3:00", 3.hours),
    INTERVAL_6_00("6:00", 6.hours),
    INTERVAL_12_00("12:00", 12.hours),
    INTERVAL_24_00("24:00", 24.hours),
    ;

    companion object {

        fun getInstance(
            value: String,
        ) = entries.firstOrNull {
            it.id == value
        } ?: INTERVAL_AUTO
    }

    override val valueArrayId = R.array.automatic_refresh_rate_values
    override val nameArrayId = R.array.automatic_refresh_rates

    override fun getName(context: Context) = UnitUtils.getName(context, this)

    // Makes locations valid for 1.5 hours when background updates are disabled
    val validity = interval ?: 1.5.hours
}
