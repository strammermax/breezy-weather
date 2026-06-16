/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.ui.main.adapters.main.holder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.options.appearance.WidgetTileType
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.location.model.isDaylight
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.common.extensions.getWeek
import org.breezyweather.domain.weather.model.getTrendTemperature
import org.breezyweather.domain.weather.model.getWeek
import org.breezyweather.domain.weather.model.isToday
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.ResourcesProviderFactory
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth
import java.util.Date

class WidgetViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_widget, parent, false)
) {
    private val container: FrameLayout = itemView.findViewById(R.id.widget_tile_container)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        container.removeAllViews()

        val widgetType = SettingsManager.getInstance(activity).widgetTileType
        val layoutRes = widgetType.toLayoutRes()
        val widgetView = LayoutInflater.from(activity).inflate(layoutRes, container, false)
        widgetView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        populateWidgetView(widgetView, widgetType, location, provider, activity)
        container.addView(widgetView)
    }
}

private fun WidgetTileType.toLayoutRes(): Int = when (this) {
    WidgetTileType.CLOCK_DAY_VERTICAL,
    WidgetTileType.CLOCK_DAY_HORIZONTAL,
    -> R.layout.widget_clock_day_symmetry_card

    WidgetTileType.DAY_WEEK,
    WidgetTileType.CLOCK_DAY_WEEK,
    -> R.layout.widget_clock_day_week_card

    WidgetTileType.WEEK,
    WidgetTileType.TREND_DAILY,
    WidgetTileType.MATERIAL_YOU_FORECAST,
    -> R.layout.widget_week_card

    else -> R.layout.widget_day_symmetry_card
}

private fun populateWidgetView(
    view: View,
    widgetType: WidgetTileType,
    location: Location,
    provider: ResourceProvider,
    activity: BreezyActivity,
) {
    when (widgetType) {
        WidgetTileType.CLOCK_DAY_VERTICAL,
        WidgetTileType.CLOCK_DAY_HORIZONTAL,
        -> populateClockDay(view, location, provider, activity)

        WidgetTileType.DAY_WEEK,
        WidgetTileType.CLOCK_DAY_WEEK,
        -> populateClockDayWeek(view, location, provider, activity)

        WidgetTileType.WEEK,
        WidgetTileType.TREND_DAILY,
        WidgetTileType.MATERIAL_YOU_FORECAST,
        -> populateWeek(view, location, provider, activity)

        else -> populateDay(view, location, provider, activity)
    }
}

// ─── Day (symmetry card) ──────────────────────────────────────────────────────

private fun populateDay(
    view: View,
    location: Location,
    provider: ResourceProvider,
    activity: BreezyActivity,
) {
    val weather = location.weather ?: return
    val ctx = activity
    val temperatureUnit = SettingsManager.getInstance(ctx).getTemperatureUnit(ctx)

    view.findViewById<ImageView>(R.id.widget_day_card)?.visibility = View.GONE

    val iconView = view.findViewById<ImageView>(R.id.widget_day_icon)
    weather.current?.weatherCode?.let { code ->
        iconView?.setImageDrawable(ResourceHelper.getWeatherIcon(provider, code, location.isDaylight))
        iconView?.visibility = View.VISIBLE
    } ?: run { iconView?.visibility = View.INVISIBLE }

    val titleBuilder = StringBuilder(location.getPlace(ctx))
    weather.current?.temperature?.temperature?.let { temp ->
        titleBuilder.append("\n").append(
            temp.formatMeasure(ctx, temperatureUnit, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW)
        )
    }
    view.findViewById<TextView>(R.id.widget_day_title)?.text = titleBuilder.toString()

    val subtitleBuilder = StringBuilder()
    weather.current?.weatherText?.let { subtitleBuilder.append(it) }
    weather.today?.getTrendTemperature(ctx, temperatureUnit)?.let { range ->
        if (subtitleBuilder.isNotEmpty()) subtitleBuilder.append(" ")
        subtitleBuilder.append(range)
    }
    view.findViewById<TextView>(R.id.widget_day_subtitle)?.text = subtitleBuilder.toString()

    val timeBuilder = StringBuilder(Date().getWeek(location, ctx))
    weather.base.refreshTime?.getFormattedTime(location, ctx, ctx.is12Hour)?.let {
        timeBuilder.append(" ").append(it)
    }
    view.findViewById<TextView>(R.id.widget_day_time)?.text = timeBuilder.toString()
}

// ─── Clock + Day (symmetry card) ─────────────────────────────────────────────

private fun populateClockDay(
    view: View,
    location: Location,
    provider: ResourceProvider,
    activity: BreezyActivity,
) {
    val weather = location.weather ?: return
    val ctx = activity
    val temperatureUnit = SettingsManager.getInstance(ctx).getTemperatureUnit(ctx)

    view.findViewById<ImageView>(R.id.widget_clock_day_card)?.visibility = View.GONE

    // Clock time zone
    val tz = location.timeZone.id
    view.findViewById<TextClock>(R.id.widget_clock_day_clock_light)?.timeZone = tz
    view.findViewById<TextClock>(R.id.widget_clock_day_clock_aa_light)?.timeZone = tz

    val iconView = view.findViewById<ImageView>(R.id.widget_clock_day_icon)
    weather.current?.weatherCode?.let { code ->
        iconView?.setImageDrawable(ResourceHelper.getWeatherIcon(provider, code, location.isDaylight))
        iconView?.visibility = View.VISIBLE
    } ?: run { iconView?.visibility = View.INVISIBLE }

    val titleBuilder = StringBuilder(location.getPlace(ctx))
    weather.current?.temperature?.temperature?.let { temp ->
        titleBuilder.append("\n").append(
            temp.formatMeasure(ctx, temperatureUnit, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW)
        )
    }
    view.findViewById<TextView>(R.id.widget_clock_day_title)?.text = titleBuilder.toString()

    val subtitleBuilder = StringBuilder()
    weather.current?.weatherText?.let { subtitleBuilder.append(it) }
    weather.today?.getTrendTemperature(ctx, temperatureUnit)?.let { range ->
        if (subtitleBuilder.isNotEmpty()) subtitleBuilder.append(" ")
        subtitleBuilder.append(range)
    }
    view.findViewById<TextView>(R.id.widget_clock_day_subtitle)?.text = subtitleBuilder.toString()

    val timeBuilder = StringBuilder()
    weather.base.refreshTime?.getFormattedTime(location, ctx, ctx.is12Hour)?.let {
        timeBuilder.append(location.getPlace(ctx)).append(" ").append(it)
    }
    view.findViewById<TextView>(R.id.widget_clock_day_time)?.text = timeBuilder.toString()
}

// ─── Clock + Day + Week ────────────────────────────────────────────────────────

private fun populateClockDayWeek(
    view: View,
    location: Location,
    provider: ResourceProvider,
    activity: BreezyActivity,
) {
    val weather = location.weather ?: return
    val ctx = activity
    val temperatureUnit = SettingsManager.getInstance(ctx).getTemperatureUnit(ctx)

    view.findViewById<ImageView>(R.id.widget_clock_day_week_card)?.visibility = View.GONE

    val tz = location.timeZone.id
    view.findViewById<TextClock>(R.id.widget_clock_day_week_clock_light)?.timeZone = tz
    view.findViewById<TextClock>(R.id.widget_clock_day_week_clock_aa_light)?.timeZone = tz
    view.findViewById<TextClock>(R.id.widget_clock_day_week_title)?.timeZone = tz

    val iconView = view.findViewById<ImageView>(R.id.widget_clock_day_week_icon)
    weather.current?.weatherCode?.let { code ->
        iconView?.setImageDrawable(ResourceHelper.getWeatherIcon(provider, code, location.isDaylight))
        iconView?.visibility = View.VISIBLE
    } ?: run { iconView?.visibility = View.INVISIBLE }

    val subtitle = StringBuilder(location.getPlace(ctx))
    weather.current?.temperature?.temperature?.let { temp ->
        subtitle.append(" ").append(
            temp.formatMeasure(ctx, temperatureUnit, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW)
        )
    }
    view.findViewById<TextView>(R.id.widget_clock_day_week_subtitle)?.text = subtitle.toString()
    view.findViewById<TextView>(R.id.widget_clock_day_week_alternate_calendar)?.text = ""

    val days = weather.dailyForecastStartingToday.take(5)
    val daylight = location.isDaylight
    val weekIds = listOf(
        R.id.widget_clock_day_week_week_1,
        R.id.widget_clock_day_week_week_2,
        R.id.widget_clock_day_week_week_3,
        R.id.widget_clock_day_week_week_4,
        R.id.widget_clock_day_week_week_5,
    )
    val iconIds = listOf(
        R.id.widget_clock_day_week_icon_1,
        R.id.widget_clock_day_week_icon_2,
        R.id.widget_clock_day_week_icon_3,
        R.id.widget_clock_day_week_icon_4,
        R.id.widget_clock_day_week_icon_5,
    )
    val tempIds = listOf(
        R.id.widget_clock_day_week_temp_1,
        R.id.widget_clock_day_week_temp_2,
        R.id.widget_clock_day_week_temp_3,
        R.id.widget_clock_day_week_temp_4,
        R.id.widget_clock_day_week_temp_5,
    )
    for (i in 0 until 5) {
        val daily = days.getOrNull(i)
        val dayLabel = if (daily?.isToday(location) == true) {
            ctx.getString(R.string.daily_today_short)
        } else {
            daily?.getWeek(location, ctx) ?: ""
        }
        view.findViewById<TextView>(weekIds[i])?.text = dayLabel
        val dayIconView = view.findViewById<ImageView>(iconIds[i])
        val code = daily?.day?.weatherCode
        if (code != null) {
            dayIconView?.setImageDrawable(ResourceHelper.getWeatherIcon(provider, code, daylight))
            dayIconView?.visibility = View.VISIBLE
        } else {
            dayIconView?.visibility = View.INVISIBLE
        }
        view.findViewById<TextView>(tempIds[i])?.text =
            daily?.getTrendTemperature(ctx, temperatureUnit) ?: ""
    }
}

// ─── Week (5-column card) ─────────────────────────────────────────────────────

private fun populateWeek(
    view: View,
    location: Location,
    provider: ResourceProvider,
    activity: BreezyActivity,
) {
    val weather = location.weather ?: return
    val ctx = activity
    val temperatureUnit = SettingsManager.getInstance(ctx).getTemperatureUnit(ctx)

    view.findViewById<ImageView>(R.id.widget_week_card)?.visibility = View.GONE

    val days = weather.dailyForecastStartingToday.take(5)
    val daylight = location.isDaylight
    val weekIds = listOf(
        R.id.widget_week_week_1,
        R.id.widget_week_week_2,
        R.id.widget_week_week_3,
        R.id.widget_week_week_4,
        R.id.widget_week_week_5,
    )
    val iconIds = listOf(
        R.id.widget_week_icon_1,
        R.id.widget_week_icon_2,
        R.id.widget_week_icon_3,
        R.id.widget_week_icon_4,
        R.id.widget_week_icon_5,
    )
    val tempIds = listOf(
        R.id.widget_week_temp_1,
        R.id.widget_week_temp_2,
        R.id.widget_week_temp_3,
        R.id.widget_week_temp_4,
        R.id.widget_week_temp_5,
    )
    for (i in 0 until 5) {
        val daily = days.getOrNull(i)
        val dayLabel = if (daily?.isToday(location) == true) {
            ctx.getString(R.string.daily_today_short)
        } else {
            daily?.getWeek(location, ctx) ?: ""
        }
        view.findViewById<TextView>(weekIds[i])?.text = dayLabel
        val dayIconView = view.findViewById<ImageView>(iconIds[i])
        val code = daily?.day?.weatherCode
        if (code != null) {
            dayIconView?.setImageDrawable(ResourceHelper.getWeatherIcon(provider, code, daylight))
            dayIconView?.visibility = View.VISIBLE
        } else {
            dayIconView?.visibility = View.INVISIBLE
        }
        view.findViewById<TextView>(tempIds[i])?.text =
            daily?.getTrendTemperature(ctx, temperatureUnit) ?: ""
    }
}
