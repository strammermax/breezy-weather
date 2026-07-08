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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.formatPercent
import com.liveweatherwallpaperapp.common.extensions.getCloudCoverColor
import com.liveweatherwallpaperapp.common.extensions.getThemeColor
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendRecyclerView
import com.liveweatherwallpaperapp.ui.common.widgets.trend.chart.PolylineAndHistogramView
import com.liveweatherwallpaperapp.ui.theme.ThemeManager
import com.liveweatherwallpaperapp.ui.theme.weatherView.WeatherViewController
import com.liveweatherwallpaperapp.unit.formatting.UnitWidth
import livewallpaperweather.domain.location.model.Location

/**
 * Hourly Cloud Cover adapter.
 */
class HourlyCloudCoverAdapter(
    activity: BreezyActivity,
    location: Location,
) : AbsHourlyTrendAdapter(activity, location) {

    inner class ViewHolder(itemView: View) : AbsHourlyTrendAdapter.ViewHolder(itemView) {
        private val mPolylineAndHistogramView = PolylineAndHistogramView(itemView.context)

        init {
            hourlyItem.chartItemView = mPolylineAndHistogramView
        }

        @SuppressLint("SetTextI18n, InflateParams", "DefaultLocale")
        fun onBindView(activity: BreezyActivity, location: Location, position: Int) {
            val talkBackBuilder = StringBuilder(activity.getString(R.string.tag_cloud_cover))
            super.onBindView(activity, location, talkBackBuilder, position)
            val hourly = location.weather!!.nextHourlyForecast[position]

            hourly.cloudCover?.let { cloudCover ->
                talkBackBuilder
                    .append(activity.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
                    .append(cloudCover.formatPercent(activity, UnitWidth.NARROW))
            }
            mPolylineAndHistogramView.setData(
                null,
                null,
                null,
                null,
                null,
                null,
                hourly.cloudCover?.inPercent?.toFloat() ?: 0f,
                hourly.cloudCover?.formatPercent(activity, UnitWidth.NARROW),
                100f,
                0f
            )
            mPolylineAndHistogramView.setLineColors(
                hourly.cloudCover?.getCloudCoverColor(activity) ?: Color.TRANSPARENT,
                hourly.cloudCover?.getCloudCoverColor(activity) ?: Color.TRANSPARENT,
                activity.getThemeColor(com.google.android.material.R.attr.colorOutline)
            )

            val themeColors = ThemeManager.getInstance(itemView.context)
                .weatherThemeDelegate
                .getThemeColors(
                    itemView.context,
                    WeatherViewController.getWeatherKind(location),
                    WeatherViewController.isDaylight(location)
                )
            val lightTheme = ThemeManager.isLightTheme(itemView.context, location)
            mPolylineAndHistogramView.setShadowColors(
                themeColors[if (lightTheme) 1 else 2],
                themeColors[2],
                lightTheme
            )
            mPolylineAndHistogramView.setTextColors(
                activity.getThemeColor(R.attr.colorTitleText),
                activity.getThemeColor(R.attr.colorBodyText),
                activity.getThemeColor(R.attr.colorTitleText)
            )
            mPolylineAndHistogramView.setHistogramAlpha(if (lightTheme) 1f else 0.5f)
            hourlyItem.contentDescription = talkBackBuilder.toString()
            hourlyItem.setOnClickListener {
                onItemClicked(activity, location, bindingAdapterPosition, DetailScreen.TAG_CLOUD_COVER)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trend_hourly, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: AbsHourlyTrendAdapter.ViewHolder, position: Int) {
        (holder as ViewHolder).onBindView(activity, location, position)
    }

    override fun getItemCount() = location.weather!!.nextHourlyForecast.size

    override fun isValid(location: Location) = location.weather!!.nextHourlyForecast.any {
        it.cloudCover != null
    }

    override fun getDisplayName(context: Context) = context.getString(R.string.tag_cloud_cover)

    override fun bindBackgroundForHost(host: TrendRecyclerView) {
        val keyLineList = mutableListOf<TrendRecyclerView.KeyLine>()
        /*keyLineList.add(
            TrendRecyclerView.KeyLine(
                CLOUD_COVER_FEW.toFloat(),
                CLOUD_COVER_FEW.percent.formatPercent(activity),
                CLOUD_COVER_FEW.percent.getCloudCoverDescription(activity),
                TrendRecyclerView.KeyLine.ContentPosition.ABOVE_LINE
            )
        )
        keyLineList.add(
            TrendRecyclerView.KeyLine(
                CLOUD_COVER_SCT.toFloat(),
                CLOUD_COVER_SCT.percent.formatPercent(activity),
                CLOUD_COVER_SCT.percent.getCloudCoverDescription(activity),
                TrendRecyclerView.KeyLine.ContentPosition.ABOVE_LINE
            )
        )*/
        host.setData(keyLineList, 100f, 0f)
    }
}
