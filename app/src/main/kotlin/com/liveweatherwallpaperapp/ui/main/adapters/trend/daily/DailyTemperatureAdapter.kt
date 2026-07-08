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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Size
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.common.extensions.formatPercent
import com.liveweatherwallpaperapp.common.extensions.formatValue
import com.liveweatherwallpaperapp.common.extensions.getCalendarMonth
import com.liveweatherwallpaperapp.common.extensions.getThemeColor
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.domain.weather.model.drawableArrow
import com.liveweatherwallpaperapp.domain.weather.model.getColor
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendRecyclerView
import com.liveweatherwallpaperapp.ui.common.widgets.trend.chart.PolylineAndHistogramView
import com.liveweatherwallpaperapp.ui.theme.ThemeManager
import com.liveweatherwallpaperapp.ui.theme.resource.ResourceHelper
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import com.liveweatherwallpaperapp.unit.formatting.UnitWidth
import com.liveweatherwallpaperapp.unit.precipitation.Precipitation.Companion.millimeters
import com.liveweatherwallpaperapp.unit.temperature.TemperatureUnit
import livewallpaperweather.domain.location.model.Location
import java.util.Date
import kotlin.math.max

/**
 * Daily temperature adapter.
 */
class DailyTemperatureAdapter(
    activity: BreezyActivity,
    location: Location,
    provider: ResourceProvider,
    private val temperatureUnit: TemperatureUnit,
    private val showPrecipitationProbability: Boolean = true,
) : AbsDailyTrendAdapter(activity, location) {
    private val mResourceProvider: ResourceProvider = provider
    private val mDaytimeTemperatures: Array<Float?>
    private val mNighttimeTemperatures: Array<Float?>
    private val mDailyPrecipitation: Array<Float?>
    private var mHighestTemperature: Float? = null
    private var mLowestTemperature: Float? = null

    // Scaled to the highest *actual* daily total below (init block), not a fixed heavy-rain
    // threshold — otherwise routine light rain would render as a barely-visible sliver next to
    // an arbitrary, usually-unreached maximum.
    private var mHighestDailyPrecipitation = 1.0.millimeters.inMicrometers.toFloat()

    inner class ViewHolder(itemView: View) : AbsDailyTrendAdapter.ViewHolder(itemView) {
        private val mPolylineAndHistogramView = PolylineAndHistogramView(itemView.context)

        init {
            dailyItem.chartItemView = mPolylineAndHistogramView
        }

        @SuppressLint("SetTextI18n, InflateParams")
        fun onBindView(activity: BreezyActivity, location: Location, position: Int) {
            val talkBackBuilder = StringBuilder(activity.getString(R.string.tag_temperature))
            super.onBindView(activity, location, talkBackBuilder, position)
            val daily = location.weather!!.dailyForecast[position]
            daily.day?.let { day ->
                talkBackBuilder.append(activity.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
                    .append(activity.getString(R.string.daytime))
                    .append(activity.getString(R.string.colon_separator))
                day.temperature?.temperature?.let {
                    talkBackBuilder.append(it.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG))
                        .append(activity.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
                }
                if (!day.weatherText.isNullOrEmpty()) {
                    talkBackBuilder.append(day.weatherText)
                }
                if (showPrecipitationProbability) {
                    day.precipitationProbability?.total?.let { p ->
                        talkBackBuilder.append(
                            activity.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator)
                        )
                            .append(activity.getString(R.string.precipitation_probability))
                            .append(activity.getString(R.string.colon_separator))
                            .append(p.formatPercent(activity))
                    }
                }
            }
            daily.night?.let { night ->
                talkBackBuilder.append(activity.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
                    .append(activity.getString(R.string.nighttime))
                    .append(activity.getString(R.string.colon_separator))
                night.temperature?.temperature?.let {
                    talkBackBuilder.append(it.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG))
                        .append(activity.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
                }
                if (!night.weatherText.isNullOrEmpty()) {
                    talkBackBuilder.append(night.weatherText)
                }
                if (showPrecipitationProbability) {
                    night.precipitationProbability?.total?.let { p ->
                        talkBackBuilder.append(
                            activity.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator)
                        )
                            .append(activity.getString(R.string.precipitation_probability))
                            .append(activity.getString(R.string.colon_separator))
                            .append(p.formatPercent(activity))
                    }
                }
            }
            dailyItem.setDayIconDrawable(
                daily.day?.weatherCode?.let { ResourceHelper.getWeatherIcon(mResourceProvider, it, true) },
                missingIconVisibility = View.INVISIBLE
            )
            val daytimePrecipitationProbability = daily.day?.precipitationProbability?.total
            val nighttimePrecipitationProbability = daily.night?.precipitationProbability?.total
            val daytimeIsSnow = (daily.day?.precipitationProbability?.snow?.value ?: 0L) >
                (daily.day?.precipitationProbability?.rain?.value ?: 0L)
            val nighttimeIsSnow = (daily.night?.precipitationProbability?.snow?.value ?: 0L) >
                (daily.night?.precipitationProbability?.rain?.value ?: 0L)
            val dayPrecipitation = daily.day?.precipitation?.total
            val nightPrecipitation = daily.night?.precipitation?.total
            // Distinguish "known to be 0mm" (show it, connect the line) from "no data at all"
            // (leave a gap) — both day and night absent is the only case treated as unknown.
            val totalPrecipitation = if (dayPrecipitation != null || nightPrecipitation != null) {
                ((dayPrecipitation?.inMillimeters ?: 0.0) + (nightPrecipitation?.inMillimeters ?: 0.0)).millimeters
            } else {
                null
            }
            mPolylineAndHistogramView.setData(
                buildTemperatureArrayForItem(mDaytimeTemperatures, position),
                buildTemperatureArrayForItem(mNighttimeTemperatures, position),
                daily.day?.temperature?.temperature?.formatMeasure(
                    activity,
                    temperatureUnit,
                    valueWidth = UnitWidth.NARROW,
                    unitWidth = UnitWidth.NARROW
                ),
                daily.night?.temperature?.temperature?.formatMeasure(
                    activity,
                    temperatureUnit,
                    valueWidth = UnitWidth.NARROW,
                    unitWidth = UnitWidth.NARROW
                ),
                mHighestTemperature,
                mLowestTemperature,
                null,
                null,
                100f,
                0f
            )
            mPolylineAndHistogramView.setPrecipPolylineData(
                buildTemperatureArrayForItem(mDailyPrecipitation, position),
                totalPrecipitation?.formatMeasure(
                    activity,
                    valueWidth = UnitWidth.SHORT,
                    unitWidth = UnitWidth.SHORT
                ),
                mHighestDailyPrecipitation,
                0f
            )
            val lightTheme = ThemeManager.isLightTheme(itemView.context, location)
            val dayColor = ContextCompat.getColor(itemView.context, R.color.colorTemperatureDay)
            val nightColor = ContextCompat.getColor(itemView.context, R.color.colorTemperatureNight)
            mPolylineAndHistogramView.setLineColors(
                dayColor,
                nightColor,
                activity.getThemeColor(com.google.android.material.R.attr.colorOutline)
            )
            mPolylineAndHistogramView.setShadowColors(
                dayColor,
                nightColor,
                lightTheme
            )
            mPolylineAndHistogramView.setTextColors(
                activity.getThemeColor(R.attr.colorTitleText),
                activity.getThemeColor(R.attr.colorBodyText),
                activity.getThemeColor(R.attr.colorPrecipitationProbability)
            )
            mPolylineAndHistogramView.setPrecipColors(
                activity.getThemeColor(R.attr.colorPrecipitationProbability),
                activity.getThemeColor(R.attr.colorPrecipitationProbability)
            )
            dailyItem.setPrecipitationProbabilityDay(
                daytimePrecipitationProbability?.takeIf { showPrecipitationProbability }
                    ?.formatPercent(activity, UnitWidth.NARROW),
                daytimeIsSnow
            )
            dailyItem.setPrecipitationProbabilityNight(
                nighttimePrecipitationProbability?.takeIf { showPrecipitationProbability }
                    ?.formatPercent(activity, UnitWidth.NARROW),
                nighttimeIsSnow
            )
            dailyItem.setPrecipitationProbabilityColor(
                activity.getThemeColor(R.attr.colorTitleText)
            )
            dailyItem.setWindForceTextColor(
                activity.getThemeColor(R.attr.colorTitleText)
            )
            val dayWind = daily.day?.wind
            val dayWindIcon = dayWind?.drawableArrow?.let { AppCompatResources.getDrawable(activity, it) }
            dayWindIcon?.colorFilter = PorterDuffColorFilter(dayWind?.getColor(activity) ?: 0, PorterDuff.Mode.SRC_ATOP)
            dailyItem.setWindDirectionDay(dayWindIcon, dayWind?.speed?.inBeaufort?.toString())
            val nightWind = daily.night?.wind
            val nightWindIcon = nightWind?.drawableArrow?.let { AppCompatResources.getDrawable(activity, it) }
            nightWindIcon?.colorFilter =
                PorterDuffColorFilter(nightWind?.getColor(activity) ?: 0, PorterDuff.Mode.SRC_ATOP)
            dailyItem.setWindDirectionNight(nightWindIcon, nightWind?.speed?.inBeaufort?.toString())
            dailyItem.setNightIconDrawable(
                daily.night?.weatherCode?.let { ResourceHelper.getWeatherIcon(mResourceProvider, it, false) },
                missingIconVisibility = View.INVISIBLE
            )
            dailyItem.contentDescription = talkBackBuilder.toString()
            dailyItem.setOnClickListener {
                onItemClicked(activity, location, bindingAdapterPosition, DetailScreen.TAG_CONDITIONS)
            }
        }

        @Size(3)
        private fun buildTemperatureArrayForItem(temps: Array<Float?>, adapterPosition: Int): Array<Float?> {
            val a = arrayOfNulls<Float>(3)
            a[1] = temps[2 * adapterPosition]
            if (2 * adapterPosition - 1 < 0) {
                a[0] = null
            } else {
                a[0] = temps[2 * adapterPosition - 1]
            }
            if (2 * adapterPosition + 1 >= temps.size) {
                a[2] = null
            } else {
                a[2] = temps[2 * adapterPosition + 1]
            }
            return a
        }
    }

    init {
        val weather = location.weather!!
        mDaytimeTemperatures = arrayOfNulls(max(0, weather.dailyForecast.size * 2 - 1))
        run {
            var i = 0
            while (i < mDaytimeTemperatures.size) {
                mDaytimeTemperatures[i] =
                    weather.dailyForecast.getOrNull(i / 2)?.day?.temperature?.temperature?.value?.toFloat()
                i += 2
            }
        }
        run {
            var i = 1
            while (i < mDaytimeTemperatures.size) {
                if (mDaytimeTemperatures[i - 1] != null && mDaytimeTemperatures[i + 1] != null) {
                    mDaytimeTemperatures[i] = (mDaytimeTemperatures[i - 1]!! + mDaytimeTemperatures[i + 1]!!) * 0.5f
                } else {
                    mDaytimeTemperatures[i] = null
                }
                i += 2
            }
        }
        mNighttimeTemperatures = arrayOfNulls(max(0, weather.dailyForecast.size * 2 - 1))
        run {
            var i = 0
            while (i < mNighttimeTemperatures.size) {
                mNighttimeTemperatures[i] =
                    weather.dailyForecast.getOrNull(i / 2)?.night?.temperature?.temperature?.value?.toFloat()
                i += 2
            }
        }
        run {
            var i = 1
            while (i < mNighttimeTemperatures.size) {
                if (mNighttimeTemperatures[i - 1] != null && mNighttimeTemperatures[i + 1] != null) {
                    mNighttimeTemperatures[i] =
                        (mNighttimeTemperatures[i - 1]!! + mNighttimeTemperatures[i + 1]!!) * 0.5f
                } else {
                    mNighttimeTemperatures[i] = null
                }
                i += 2
            }
        }
        mDailyPrecipitation = arrayOfNulls(max(0, weather.dailyForecast.size * 2 - 1))
        run {
            var i = 0
            while (i < mDailyPrecipitation.size) {
                val daily = weather.dailyForecast.getOrNull(i / 2)
                val day = daily?.day?.precipitation?.total
                val night = daily?.night?.precipitation?.total
                mDailyPrecipitation[i] = if (day != null || night != null) {
                    ((day?.inMicrometers ?: 0.0) + (night?.inMicrometers ?: 0.0)).toFloat()
                } else {
                    null
                }
                i += 2
            }
        }
        run {
            var i = 1
            while (i < mDailyPrecipitation.size) {
                if (mDailyPrecipitation[i - 1] != null && mDailyPrecipitation[i + 1] != null) {
                    mDailyPrecipitation[i] = (mDailyPrecipitation[i - 1]!! + mDailyPrecipitation[i + 1]!!) * 0.5f
                } else {
                    mDailyPrecipitation[i] = null
                }
                i += 2
            }
        }
        weather.normals.getOrElse(Date().getCalendarMonth(location)) { null }?.let { normals ->
            mHighestTemperature = normals.daytimeTemperature?.value?.toFloat()
            mLowestTemperature = normals.nighttimeTemperature?.value?.toFloat()
        }
        weather.dailyForecast.forEach { daily ->
            daily.day?.temperature?.temperature?.value?.let {
                if (mHighestTemperature == null || it > mHighestTemperature!!) {
                    mHighestTemperature = it.toFloat()
                }
                if (mLowestTemperature == null || it < mLowestTemperature!!) {
                    mLowestTemperature = it.toFloat()
                }
            }
            daily.night?.temperature?.temperature?.value?.let {
                if (mHighestTemperature == null || it > mHighestTemperature!!) {
                    mHighestTemperature = it.toFloat()
                }
                if (mLowestTemperature == null || it < mLowestTemperature!!) {
                    mLowestTemperature = it.toFloat()
                }
            }
            val dailyTotalPrecipitation = (daily.day?.precipitation?.total?.inMicrometers ?: 0.0) +
                (daily.night?.precipitation?.total?.inMicrometers ?: 0.0)
            if (dailyTotalPrecipitation > mHighestDailyPrecipitation) {
                mHighestDailyPrecipitation = dailyTotalPrecipitation.toFloat()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trend_daily, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: AbsDailyTrendAdapter.ViewHolder, position: Int) {
        (holder as ViewHolder).onBindView(activity, location, position)
    }

    override fun getItemCount() = location.weather!!.dailyForecast.size

    // FIXME
    override fun isValid(location: Location) = true

    override fun getDisplayName(context: Context) = context.getString(R.string.tag_temperature)

    override fun bindBackgroundForHost(host: TrendRecyclerView) {
        val normals = location.weather?.normals?.getOrElse(Date().getCalendarMonth(location)) { null }
        if (normals?.daytimeTemperature == null || normals.nighttimeTemperature == null) {
            host.setData(null, 0f, 0f)
        } else {
            val keyLineList = mutableListOf<TrendRecyclerView.KeyLine>()
            keyLineList.add(
                TrendRecyclerView.KeyLine(
                    normals.daytimeTemperature!!.value.toFloat(),
                    normals.daytimeTemperature!!.formatMeasure(
                        activity,
                        temperatureUnit,
                        valueWidth = UnitWidth.NARROW,
                        unitWidth = UnitWidth.NARROW
                    ),
                    activity.getString(R.string.temperature_normal_short),
                    TrendRecyclerView.KeyLine.ContentPosition.ABOVE_LINE
                )
            )
            keyLineList.add(
                TrendRecyclerView.KeyLine(
                    normals.nighttimeTemperature!!.value.toFloat(),
                    normals.nighttimeTemperature!!.formatMeasure(
                        activity,
                        temperatureUnit,
                        valueWidth = UnitWidth.NARROW,
                        unitWidth = UnitWidth.NARROW
                    ),
                    activity.getString(R.string.temperature_normal_short),
                    TrendRecyclerView.KeyLine.ContentPosition.BELOW_LINE
                )
            )
            host.setData(keyLineList, mHighestTemperature!!, mLowestTemperature!!)
        }
    }
}
