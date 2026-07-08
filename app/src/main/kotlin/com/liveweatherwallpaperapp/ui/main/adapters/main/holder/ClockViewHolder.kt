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

package com.liveweatherwallpaperapp.ui.main.adapters.main.holder

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AnalogClock
import android.widget.TextClock
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.getFormattedTime
import com.liveweatherwallpaperapp.common.extensions.is12Hour
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import livewallpaperweather.domain.location.model.Location
import java.util.Date
import java.util.TimeZone

class ClockViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_clock, parent, false)
) {
    private val clockAnalogView: AnalogClock = itemView.findViewById(R.id.clock_analog)
    private val clockTextHourView: TextClock = itemView.findViewById(R.id.clock_text_hour)
    private val clockTextMinuteView: TextClock = itemView.findViewById(R.id.clock_text_minute)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            clockAnalogView.visibility = View.VISIBLE
            clockAnalogView.timeZone = location.timeZone.id
        } else if (location.timeZone == TimeZone.getDefault()) {
            clockAnalogView.visibility = View.VISIBLE
        } else {
            clockAnalogView.visibility = View.GONE
        }

        clockTextHourView.timeZone = location.timeZone.id
        clockTextMinuteView.timeZone = location.timeZone.id

        val talkBackBuilder = StringBuilder(context.getString(R.string.clock))
        talkBackBuilder.append(context.getString(R.string.colon_separator))
        talkBackBuilder.append(Date().getFormattedTime(location, activity, context.is12Hour))

        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_CONDITIONS
            )
        }
    }
}
