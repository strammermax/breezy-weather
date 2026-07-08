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

package com.liveweatherwallpaperapp.ui.main.adapters.trend.daily

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.getFormattedFullDayAndMonth
import com.liveweatherwallpaperapp.common.extensions.getFormattedShortDayAndMonth
import com.liveweatherwallpaperapp.common.extensions.getThemeColor
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.domain.weather.model.getWeek
import com.liveweatherwallpaperapp.domain.weather.model.isToday
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendRecyclerView
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendRecyclerViewAdapter
import com.liveweatherwallpaperapp.ui.common.widgets.trend.item.DailyTrendItemView
import livewallpaperweather.domain.location.model.Location
import java.util.Date

abstract class AbsDailyTrendAdapter(
    val activity: BreezyActivity,
    location: Location,
) : TrendRecyclerViewAdapter<AbsDailyTrendAdapter.ViewHolder>(location) {

    open class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dailyItem: DailyTrendItemView = itemView.findViewById(R.id.item_trend_daily)

        @SuppressLint("SetTextI18n, InflateParams", "DefaultLocale")
        fun onBindView(
            activity: BreezyActivity,
            location: Location,
            talkBackBuilder: StringBuilder,
            position: Int,
        ) {
            val context = itemView.context
            // ACT-013: against the transparent glass card, a leftover opaque item
            // background (seen on the trailing day column) reads as a solid black bar.
            itemView.background = null
            val weather = location.weather
            val daily = weather!!.dailyForecast[position]
            val todayIndex = weather.todayIndex
            talkBackBuilder.append(context.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
            if (todayIndex != null) {
                when (position) {
                    todayIndex -> talkBackBuilder.append(context.getString(R.string.daily_today))
                    todayIndex - 1 -> talkBackBuilder.append(context.getString(R.string.daily_yesterday))
                    todayIndex + 1 -> talkBackBuilder.append(context.getString(R.string.daily_tomorrow))
                    else -> talkBackBuilder.append(daily.getWeek(location, context, full = true))
                }
            } else {
                talkBackBuilder.append(daily.getWeek(location, context, full = true))
            }
            if (position == todayIndex) {
                dailyItem.setWeekText(context.getString(R.string.daily_today_short))
            } else {
                dailyItem.setWeekText(daily.getWeek(location, context))
            }
            dailyItem.setHighlighted(position == todayIndex)
            talkBackBuilder.append(context.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
                .append(daily.date.getFormattedFullDayAndMonth(location, context))
            dailyItem.setDateText(daily.date.getFormattedShortDayAndMonth(location, context))
            val useAccentColorForDate = daily.isToday(location) || daily.date > Date()
            dailyItem.setTextColor(
                activity.getThemeColor(if (useAccentColorForDate) R.attr.colorTitleText else R.attr.colorBodyText),
                // Date text is always shown in the title color (white), matching the
                // live wallpaper background regardless of whether the day is "today".
                activity.getThemeColor(R.attr.colorTitleText)
            )
        }

        protected fun onItemClicked(
            activity: BreezyActivity,
            location: Location,
            adapterPosition: Int,
            detailScreen: DetailScreen,
        ) {
            if (activity.isActivityResumed) {
                IntentHelper.startDailyWeatherActivity(activity, location.formattedId, adapterPosition, detailScreen)
            }
        }
    }

    val key: String = javaClass.name
    abstract fun isValid(location: Location): Boolean
    abstract fun getDisplayName(context: Context): String
    abstract fun bindBackgroundForHost(host: TrendRecyclerView)
}
