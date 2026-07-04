/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.common.options.appearance

import androidx.annotation.StringRes
import com.livewallpaperweather.R

enum class WidgetTileType(val id: String, @StringRes val nameRes: Int) {
    DAY("day", R.string.widget_day),
    WEEK("week", R.string.widget_week),
    DAY_WEEK("day_week", R.string.widget_day_week),
    CLOCK_DAY_HORIZONTAL("clock_day_horizontal", R.string.widget_clock_day_horizontal),
    CLOCK_DAY_DETAILS("clock_day_details", R.string.widget_clock_day_details),
    CLOCK_DAY_VERTICAL("clock_day_vertical", R.string.widget_clock_day_vertical),
    CLOCK_DAY_WEEK("clock_day_week", R.string.widget_clock_day_week),
    TEXT("text", R.string.widget_text),
    TREND_DAILY("trend_daily", R.string.widget_trend_daily),
    TREND_HOURLY("trend_hourly", R.string.widget_trend_hourly),
    MULTI_CITY("multi_city", R.string.widget_multi_city),
    MATERIAL_YOU_FORECAST("material_you_forecast", R.string.widget_material_you_forecast),
    MATERIAL_YOU_CURRENT("material_you_current", R.string.widget_material_you_current),
    ;

    companion object {
        fun fromId(id: String): WidgetTileType =
            entries.firstOrNull { it.id == id } ?: CLOCK_DAY_VERTICAL
    }
}
