/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.ui.main.adapters.main.holder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Daily
import breezyweather.domain.weather.model.Hourly
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.getWeek
import org.breezyweather.common.options.appearance.WidgetTileType
import org.breezyweather.domain.location.model.isDaylight
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.getWeek
import org.breezyweather.domain.weather.model.isToday
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.ResourcesProviderFactory
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth
import kotlin.math.roundToInt

class WidgetViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_widget, parent, false)
) {
    private val composeView: ComposeView = itemView.findViewById(R.id.widget_tile_compose)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        val widgetType = SettingsManager.getInstance(activity).widgetTileType
        composeView.setContent {
            BreezyWeatherTheme {
                WidgetTileContent(location, widgetType, provider)
            }
        }
    }
}

@Composable
private fun WidgetTileContent(
    location: Location,
    widgetType: WidgetTileType,
    provider: ResourceProvider,
) {
    val weather = location.weather ?: return
    val textColor = MaterialTheme.colorScheme.onSurface
    when (widgetType) {
        WidgetTileType.TREND_HOURLY -> HourlyTrendTile(location, provider, textColor)

        WidgetTileType.WEEK,
        WidgetTileType.TREND_DAILY,
        WidgetTileType.MATERIAL_YOU_FORECAST,
        -> WeekForecastTile(location, provider, textColor)

        WidgetTileType.DAY_WEEK,
        WidgetTileType.CLOCK_DAY_WEEK,
        -> DayWeekTile(location, provider, textColor)

        else -> CurrentWeatherTile(location, provider, textColor)
    }
}

@Composable
private fun CurrentWeatherTile(
    location: Location,
    provider: ResourceProvider,
    textColor: Color,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val weather = location.weather ?: return
    val temperatureUnit = SettingsManager.getInstance(ctx).getTemperatureUnit(ctx)

    val tempText = weather.current?.temperature?.temperature?.let {
        it.toDouble(temperatureUnit).roundToInt().toString() +
            temperatureUnit.getNominativeUnit(ctx)
    } ?: ""
    val weatherText = weather.current?.weatherText ?: ""

    val feelsLikeText = weather.current?.temperature?.feelsLikeTemperature?.let {
        it.formatMeasure(ctx, temperatureUnit, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW)
    }
    val precipText = weather.today?.day?.precipitationProbability?.total
        ?.inPercent?.roundToInt()?.let { "$it%" }
    val windText = weather.current?.wind?.speed?.formatMeasure(ctx, valueWidth = UnitWidth.NARROW)
    val humidityText = weather.current?.relativeHumidity?.inPercent?.roundToInt()?.let { "$it%" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: large temperature + description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tempText,
                color = textColor,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 52.sp,
            )
            if (weatherText.isNotEmpty()) {
                Text(
                    text = weatherText,
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                )
            }
        }

        // Right: detail rows
        Column(horizontalAlignment = Alignment.End) {
            if (feelsLikeText != null) {
                Text(
                    text = "Voelt als $feelsLikeText",
                    color = textColor.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
            }
            if (precipText != null || windText != null) {
                val combined = listOfNotNull(
                    precipText?.let { "☔ $it" },
                    windText?.let { "💨 $it" },
                ).joinToString("  ")
                if (combined.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = combined,
                        color = textColor.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                }
            }
            if (humidityText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "💧 $humidityText",
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun WeekForecastTile(
    location: Location,
    provider: ResourceProvider,
    textColor: Color,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val weather = location.weather ?: return
    val temperatureUnit = SettingsManager.getInstance(ctx).getTemperatureUnit(ctx)
    val days = weather.dailyForecastStartingToday.take(5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        days.forEach { daily ->
            DayColumn(daily, location, provider, temperatureUnit, textColor, ctx)
        }
    }
}

@Composable
private fun DayWeekTile(
    location: Location,
    provider: ResourceProvider,
    textColor: Color,
) {
    Column {
        CurrentWeatherTile(location, provider, textColor)
        HorizontalDivider(
            color = textColor.copy(alpha = 0.15f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        WeekForecastTile(location, provider, textColor)
    }
}

@Composable
private fun HourlyTrendTile(
    location: Location,
    provider: ResourceProvider,
    textColor: Color,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val weather = location.weather ?: return
    val temperatureUnit = SettingsManager.getInstance(ctx).getTemperatureUnit(ctx)
    val now = java.util.Date()
    val hours = weather.hourlyForecast.filter { it.date.after(now) }.take(6)
    if (hours.isEmpty()) {
        CurrentWeatherTile(location, provider, textColor)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        hours.forEach { hourly ->
            HourColumn(hourly, location, provider, temperatureUnit, textColor, ctx)
        }
    }
}

@Composable
private fun HourColumn(
    hourly: Hourly,
    location: Location,
    provider: ResourceProvider,
    temperatureUnit: org.breezyweather.unit.temperature.TemperatureUnit,
    textColor: Color,
    ctx: android.content.Context,
) {
    val hour = java.util.Calendar.getInstance(location.timeZone).also {
        it.time = hourly.date
    }[java.util.Calendar.HOUR_OF_DAY]
    val label = String.format("%02d:00", hour)
    val weatherCode = hourly.weatherCode
    val daylight = location.isDaylight
    val tempText = hourly.temperature?.temperature?.let {
        it.toDouble(temperatureUnit).roundToInt().toString() + "°"
    } ?: "-"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp),
    ) {
        Text(
            text = label,
            color = textColor.copy(alpha = 0.8f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        if (weatherCode != null) {
            AndroidView(
                factory = { context -> android.widget.ImageView(context) },
                update = { iv ->
                    iv.setImageDrawable(ResourceHelper.getWeatherIcon(provider, weatherCode, daylight))
                },
                modifier = Modifier.size(28.dp),
            )
        } else {
            Spacer(Modifier.size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = tempText,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DayColumn(
    daily: Daily,
    location: Location,
    provider: ResourceProvider,
    temperatureUnit: org.breezyweather.unit.temperature.TemperatureUnit,
    textColor: Color,
    ctx: android.content.Context,
) {
    val dayLabel = if (daily.isToday(location)) {
        ctx.getString(R.string.daily_today_short)
    } else {
        daily.getWeek(location, ctx)
    }
    val daylight = location.isDaylight
    val weatherCode = daily.day?.weatherCode
    val highText = daily.day?.temperature?.temperature?.let {
        it.toDouble(temperatureUnit).roundToInt().toString() + "°"
    } ?: "-"
    val lowText = daily.night?.temperature?.temperature?.let {
        it.toDouble(temperatureUnit).roundToInt().toString() + "°"
    } ?: "-"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp),
    ) {
        Text(
            text = dayLabel,
            color = textColor.copy(alpha = 0.8f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        if (weatherCode != null) {
            AndroidView(
                factory = { context -> android.widget.ImageView(context) },
                update = { iv ->
                    iv.setImageDrawable(ResourceHelper.getWeatherIcon(provider, weatherCode, daylight))
                },
                modifier = Modifier.size(32.dp),
            )
        } else {
            Spacer(Modifier.size(32.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = highText,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = lowText,
            color = textColor.copy(alpha = 0.6f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}
