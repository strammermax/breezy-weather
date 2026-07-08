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

package com.liveweatherwallpaperapp.ui.main.adapters.trend.hourly

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.getHour
import com.liveweatherwallpaperapp.common.extensions.getThemeColor
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendRecyclerView
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendRecyclerViewAdapter
import com.liveweatherwallpaperapp.ui.common.widgets.trend.item.HourlyTrendItemView
import livewallpaperweather.domain.location.model.Location
import kotlin.time.Duration.Companion.days

abstract class AbsHourlyTrendAdapter(
    val activity: BreezyActivity,
    location: Location,
) : TrendRecyclerViewAdapter<AbsHourlyTrendAdapter.ViewHolder>(location) {

    open class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val hourlyItem: HourlyTrendItemView = itemView.findViewById(R.id.item_trend_hourly)

        fun onBindView(
            activity: BreezyActivity,
            location: Location,
            talkBackBuilder: StringBuilder,
            position: Int,
        ) {
            val context = itemView.context
            val weather = location.weather!!
            val hourly = weather.nextHourlyForecast[position]
            talkBackBuilder
                .append(context.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
                .append(hourly.date.getHour(location, activity))
            hourlyItem.setHourText(hourly.date.getHour(location, activity))
            // Hour text is always shown in the title color (white), matching the
            // live wallpaper background.
            hourlyItem.setTextColor(context.getThemeColor(R.attr.colorTitleText))
            hourlyItem.setHighlighted(position == 0)
        }

        protected fun onItemClicked(
            activity: BreezyActivity,
            location: Location,
            adapterPosition: Int,
            detailScreen: DetailScreen,
        ) {
            if (activity.isActivityResumed) {
                val hourlyDate = location.weather!!.nextHourlyForecast[adapterPosition].date
                // Might not work with sources like AccuWeather not starting the day at 00:00
                val dailyIndex = location.weather!!.dailyForecast.indexOfFirst {
                    it.date.time > hourlyDate.time - 1.days.inWholeMilliseconds
                }.let { if (it == -1) null else it }
                IntentHelper.startDailyWeatherActivity(activity, location.formattedId, dailyIndex, detailScreen)
            }
        }
    }

    abstract fun isValid(location: Location): Boolean
    abstract fun getDisplayName(context: Context): String
    abstract fun bindBackgroundForHost(host: TrendRecyclerView)
}
