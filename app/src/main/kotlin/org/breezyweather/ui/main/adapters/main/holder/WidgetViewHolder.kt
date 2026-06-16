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
    val textColor = Color.White
    when (widgetType) {
        WidgetTileType.DAY,
        WidgetTileType.CLOCK_DAY_VERTICAL,
        WidgetTileType.CLOCK_DAY_HORIZONTAL,
        -> CurrentWeatherTile(location, provider, textColor)
        WidgetTileType.WEEK -> WeekForecastTile(location, provider, textColor)
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
    val daylight = location.isDaylight
    val weatherCode = weather.current?.weatherCode
    val tempValue = weather.current?.temperature?.temperature
    val tempText = tempValue?.let {
        it.toDouble(temperatureUnit).roundToInt().toString() +
            temperatureUnit.getNominativeUnit(ctx)
    } ?: ""
    val weatherText = weather.current?.weatherText ?: ""
    val todayHighText = weather.today?.day?.temperature?.temperature?.let {
        it.formatMeasure(ctx, temperatureUnit, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW)
    } ?: ""
    val todayLowText = weather.today?.night?.temperature?.temperature?.let {
        it.formatMeasure(ctx, temperatureUnit, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW)
    } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (weatherCode != null) {
            AndroidView(
                factory = { context ->
                    android.widget.ImageView(context).apply {
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { imageView ->
                    imageView.setImageDrawable(
                        ResourceHelper.getWeatherIcon(provider, weatherCode, daylight)
                    )
                },
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column {
            Text(
                text = tempText,
                color = textColor,
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 40.sp,
            )
            if (weatherText.isNotEmpty()) {
                Text(
                    text = weatherText,
                    color = textColor.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
            }
            if (todayHighText.isNotEmpty() || todayLowText.isNotEmpty()) {
                Text(
                    text = "$todayHighText · $todayLowText",
                    color = textColor.copy(alpha = 0.7f),
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
