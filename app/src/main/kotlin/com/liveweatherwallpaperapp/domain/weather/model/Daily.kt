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

package com.liveweatherwallpaperapp.domain.weather.model

import android.content.Context
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.capitalize
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.common.extensions.getFormattedDate
import com.liveweatherwallpaperapp.common.extensions.getLongWeekdayDayMonth
import com.liveweatherwallpaperapp.common.extensions.getWeek
import com.liveweatherwallpaperapp.unit.temperature.TemperatureUnit
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.weather.model.Daily
import java.util.Calendar
import kotlin.time.Duration.Companion.days

/**
 * Shows one of the following valid label:
 * - Yesterday
 * - Today
 * - Tomorrow
 * - Monday, Tuesday, etc
 * - Monday DD MMMM, Tuesday DD MMMM, etc
 */
fun Daily.getFullLabel(location: Location, context: Context): String {
    val current = Calendar.getInstance(location.timeZone)

    // In more than a week? Show full weekday + day + month
    if ((date.time > current.time.time.plus(6.days.inWholeMilliseconds))) {
        return date.getFormattedDate(getLongWeekdayDayMonth(context), location, context)
            .capitalize(context.currentLocale)
    }

    val thisDay = Calendar.getInstance(location.timeZone)
    thisDay.time = date

    return if (current[Calendar.YEAR] == thisDay[Calendar.YEAR] &&
        current[Calendar.DAY_OF_YEAR] == thisDay[Calendar.DAY_OF_YEAR]
    ) {
        context.getString(R.string.daily_today)
    } else if (
        (
            current[Calendar.YEAR] == thisDay[Calendar.YEAR] &&
                current[Calendar.DAY_OF_YEAR] - 1 == thisDay[Calendar.DAY_OF_YEAR]
            ) ||
        ( // Special new year case
            (current[Calendar.YEAR] - 1 == thisDay[Calendar.YEAR]) &&
                current[Calendar.DAY_OF_YEAR] == 1 &&
                thisDay[Calendar.DAY_OF_YEAR] in 365..366
            )
    ) {
        context.getString(R.string.daily_yesterday)
    } else if (
        (
            current[Calendar.YEAR] == thisDay[Calendar.YEAR] &&
                current[Calendar.DAY_OF_YEAR] + 1 == thisDay[Calendar.DAY_OF_YEAR]
            ) ||
        ( // Special new year case
            (current[Calendar.YEAR] + 1 == thisDay[Calendar.YEAR]) &&
                thisDay[Calendar.DAY_OF_YEAR] == 1 &&
                current[Calendar.DAY_OF_YEAR] in 365..366
            )
    ) {
        context.getString(R.string.daily_tomorrow)
    } else if (date < current.time) { // In the past? Show full date
        date.getFormattedDate(getLongWeekdayDayMonth(context), location, context)
    } else {
        date.getWeek(location, context, full = true)
    }.capitalize(context.currentLocale)
}

fun Daily.getWeek(location: Location, context: Context?, full: Boolean = false): String {
    return date.getWeek(location, context, full)
}

fun Daily.isToday(location: Location): Boolean {
    val current = Calendar.getInstance(location.timeZone)
    val thisDay = Calendar.getInstance(location.timeZone)
    thisDay.time = date
    return current[Calendar.YEAR] == thisDay[Calendar.YEAR] &&
        current[Calendar.DAY_OF_YEAR] == thisDay[Calendar.DAY_OF_YEAR]
}

/**
 * This day's (Calendar.MONTH, day-of-month) pair, or null when it's today -- for
 * [com.liveweatherwallpaperapp.ui.details.components.VentuskyDetailTile], which only needs to
 * navigate its embedded map away from "now" when browsing a different day.
 */
fun Daily.ventuskyTargetDate(location: Location): Pair<Int, Int>? {
    if (isToday(location)) return null
    val thisDay = Calendar.getInstance(location.timeZone)
    thisDay.time = date
    return thisDay[Calendar.MONTH] to thisDay[Calendar.DAY_OF_MONTH]
}

fun Daily.getTrendTemperature(context: Context, temperatureUnit: TemperatureUnit): String? {
    if (day?.temperature?.temperature == null || night?.temperature?.temperature == null) {
        return null
    }
    return day!!.temperature!!.temperature!!.formatMeasure(
        context,
        temperatureUnit,
        valueWidth = com.liveweatherwallpaperapp.unit.formatting.UnitWidth.NARROW,
        unitWidth = com.liveweatherwallpaperapp.unit.formatting.UnitWidth.NARROW
    ) +
        "/" +
        night!!.temperature!!.temperature!!.formatMeasure(
            context,
            temperatureUnit,
            valueWidth = com.liveweatherwallpaperapp.unit.formatting.UnitWidth.NARROW,
            unitWidth = com.liveweatherwallpaperapp.unit.formatting.UnitWidth.NARROW
        )
}

fun Daily.getTrendFeelsLikeTemperature(context: Context, temperatureUnit: TemperatureUnit): String? {
    if (day?.temperature?.feelsLikeTemperature == null || night?.temperature?.feelsLikeTemperature == null) {
        return null
    }
    return day!!.temperature!!.feelsLikeTemperature!!.formatMeasure(
        context,
        temperatureUnit,
        valueWidth = com.liveweatherwallpaperapp.unit.formatting.UnitWidth.NARROW,
        unitWidth = com.liveweatherwallpaperapp.unit.formatting.UnitWidth.NARROW
    ) +
        "/" +
        night!!.temperature!!.feelsLikeTemperature!!.formatMeasure(
            context,
            temperatureUnit,
            valueWidth = com.liveweatherwallpaperapp.unit.formatting.UnitWidth.NARROW,
            unitWidth = com.liveweatherwallpaperapp.unit.formatting.UnitWidth.NARROW
        )
}
