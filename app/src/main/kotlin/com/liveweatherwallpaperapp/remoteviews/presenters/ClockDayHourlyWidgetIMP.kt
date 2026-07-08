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

package com.liveweatherwallpaperapp.remoteviews.presenters

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.background.receiver.widget.WidgetClockDayHourlyProvider
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.common.extensions.getFormattedMediumDayAndMonthInAdditionalCalendar
import com.liveweatherwallpaperapp.common.extensions.getShortWeekdayDayMonth
import com.liveweatherwallpaperapp.common.extensions.getTabletListAdaptiveWidth
import com.liveweatherwallpaperapp.common.options.appearance.CalendarHelper
import com.liveweatherwallpaperapp.domain.location.model.getPlace
import com.liveweatherwallpaperapp.domain.location.model.isDaylight
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.domain.weather.model.getTrendTemperature
import com.liveweatherwallpaperapp.remoteviews.Widgets
import com.liveweatherwallpaperapp.remoteviews.trend.WidgetItemView
import com.liveweatherwallpaperapp.ui.theme.resource.ResourceHelper
import com.liveweatherwallpaperapp.ui.theme.resource.ResourcesProviderFactory
import com.liveweatherwallpaperapp.unit.formatting.UnitWidth
import livewallpaperweather.domain.location.model.Location
import java.util.Date
import kotlin.math.roundToInt

/**
 * "Uurlijks weer & tijd" (4x2): clock + date + current temperature/high-low/icon, plus the
 * next 5 hours strip below it. The hourly strip rendering itself is the exact same baked-bitmap
 * view used by [HourlyTrendWidgetIMP] (TrendLinearLayout/WidgetItemView can't be hosted directly
 * inside a RemoteViews layout), reused here instead of duplicated.
 */
object ClockDayHourlyWidgetIMP : AbstractRemoteViewsPresenter() {

    fun updateWidgetView(
        context: Context,
        location: Location?,
    ) {
        val config = getWidgetConfig(context, context.getString(R.string.sp_widget_clock_day_hourly_setting))
        val views = getRemoteViews(
            context,
            location,
            context.getTabletListAdaptiveWidth(context.resources.displayMetrics.widthPixels),
            config.cardStyle,
            config.cardAlpha,
            config.textColor,
            config.textSize,
            config.clockFont,
            config.hideAlternateCalendar
        )
        AppWidgetManager.getInstance(context).updateAppWidget(
            ComponentName(context, WidgetClockDayHourlyProvider::class.java),
            views
        )
    }

    fun getRemoteViews(
        context: Context,
        location: Location?,
        width: Int,
        cardStyle: String?,
        cardAlpha: Int,
        textColor: String?,
        textSize: Int,
        clockFont: String?,
        hideAlternateCalendar: Boolean,
    ): RemoteViews {
        val color = WidgetColor(context, cardStyle!!, textColor!!, location?.isDaylight ?: true)
        val views = RemoteViews(
            context.packageName,
            if (!color.showCard) R.layout.widget_clock_day_hourly else R.layout.widget_clock_day_hourly_card
        )
        val weather = location?.weather ?: return views
        val provider = ResourcesProviderFactory.newInstance
        val dayTime = location.isDaylight
        val settings = SettingsManager.getInstance(context)
        val temperatureUnit = settings.getTemperatureUnit(context)
        val minimalIcon = settings.isWidgetUsingMonochromeIcons

        listOf(
            R.id.widget_clock_day_clock_light,
            R.id.widget_clock_day_clock_normal,
            R.id.widget_clock_day_clock_black,
            R.id.widget_clock_day_clock_aa_light,
            R.id.widget_clock_day_clock_aa_normal,
            R.id.widget_clock_day_clock_aa_black
        ).forEach {
            views.setString(it, "setTimeZone", location.timeZone.id)
        }

        val dateFormat = getShortWeekdayDayMonth(context)
        views.setString(R.id.widget_clock_day_title, "setTimeZone", location.timeZone.id)
        views.setCharSequence(R.id.widget_clock_day_title, "setFormat12Hour", dateFormat)
        views.setCharSequence(R.id.widget_clock_day_title, "setFormat24Hour", dateFormat)

        weather.current?.weatherCode?.let {
            views.setViewVisibility(R.id.widget_clock_day_icon, View.VISIBLE)
            views.setImageViewUri(
                R.id.widget_clock_day_icon,
                ResourceHelper.getWidgetNotificationIconUri(provider, it, dayTime, minimalIcon, color.minimalIconColor)
            )
        } ?: views.setViewVisibility(R.id.widget_clock_day_icon, View.INVISIBLE)
        views.setTextViewText(
            R.id.widget_clock_day_alternate_calendar,
            if (CalendarHelper.getAlternateCalendarSetting(context) != null && !hideAlternateCalendar) {
                " – " + Date().getFormattedMediumDayAndMonthInAdditionalCalendar(location, context)
            } else {
                ""
            }
        )
        val builder = StringBuilder()
        builder.append(location.getPlace(context))
        weather.current?.temperature?.temperature?.let {
            builder.append(" ").append(
                it.formatMeasure(context, temperatureUnit, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW)
            )
        }
        weather.today?.getTrendTemperature(context, temperatureUnit)?.let {
            builder.append(" (").append(it).append(")")
        }
        views.setTextViewText(R.id.widget_clock_day_subtitle, builder.toString())

        if (color.textColor != Color.TRANSPARENT) {
            views.apply {
                setTextColor(R.id.widget_clock_day_clock_light, color.textColor)
                setTextColor(R.id.widget_clock_day_clock_normal, color.textColor)
                setTextColor(R.id.widget_clock_day_clock_black, color.textColor)
                setTextColor(R.id.widget_clock_day_clock_aa_light, color.textColor)
                setTextColor(R.id.widget_clock_day_clock_aa_normal, color.textColor)
                setTextColor(R.id.widget_clock_day_clock_aa_black, color.textColor)
                setTextColor(R.id.widget_clock_day_title, color.textColor)
                setTextColor(R.id.widget_clock_day_alternate_calendar, color.textColor)
                setTextColor(R.id.widget_clock_day_subtitle, color.textColor)
            }
        }
        if (textSize != 100) {
            val clockSize = context.resources.getDimensionPixelSize(R.dimen.widget_current_weather_icon_size).toFloat()
                .times(textSize)
                .div(100f)
            val clockAASize = context.resources.getDimensionPixelSize(R.dimen.widget_aa_text_size).toFloat()
                .times(textSize)
                .div(100f)
            val contentSize = context.resources.getDimensionPixelSize(R.dimen.widget_content_text_size).toFloat()
                .times(textSize)
                .div(100f)
            views.apply {
                setTextViewTextSize(R.id.widget_clock_day_clock_light, TypedValue.COMPLEX_UNIT_PX, clockSize)
                setTextViewTextSize(R.id.widget_clock_day_clock_normal, TypedValue.COMPLEX_UNIT_PX, clockSize)
                setTextViewTextSize(R.id.widget_clock_day_clock_black, TypedValue.COMPLEX_UNIT_PX, clockSize)
                setTextViewTextSize(R.id.widget_clock_day_clock_aa_light, TypedValue.COMPLEX_UNIT_PX, clockAASize)
                setTextViewTextSize(R.id.widget_clock_day_clock_aa_normal, TypedValue.COMPLEX_UNIT_PX, clockAASize)
                setTextViewTextSize(R.id.widget_clock_day_clock_aa_black, TypedValue.COMPLEX_UNIT_PX, clockAASize)
                setTextViewTextSize(R.id.widget_clock_day_title, TypedValue.COMPLEX_UNIT_PX, contentSize)
                setTextViewTextSize(R.id.widget_clock_day_alternate_calendar, TypedValue.COMPLEX_UNIT_PX, contentSize)
                setTextViewTextSize(R.id.widget_clock_day_subtitle, TypedValue.COMPLEX_UNIT_PX, contentSize)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setInt(R.id.widget_clock_day_subtitle, "setLineHeight", contentSize.roundToInt())
                }
            }
        }
        if (color.showCard) {
            views.setImageViewResource(R.id.widget_clock_day_card, getCardBackgroundId(color))
            views.setInt(R.id.widget_clock_day_card, "setImageAlpha", (cardAlpha / 100.0 * 255).toInt())
        }
        when (clockFont) {
            "normal" -> {
                views.apply {
                    setViewVisibility(R.id.widget_clock_day_clock_lightContainer, View.GONE)
                    setViewVisibility(R.id.widget_clock_day_clock_normalContainer, View.VISIBLE)
                    setViewVisibility(R.id.widget_clock_day_clock_blackContainer, View.GONE)
                }
            }
            "black" -> {
                views.apply {
                    setViewVisibility(R.id.widget_clock_day_clock_lightContainer, View.GONE)
                    setViewVisibility(R.id.widget_clock_day_clock_normalContainer, View.GONE)
                    setViewVisibility(R.id.widget_clock_day_clock_blackContainer, View.VISIBLE)
                }
            }
            else -> {
                views.apply {
                    setViewVisibility(R.id.widget_clock_day_clock_lightContainer, View.VISIBLE)
                    setViewVisibility(R.id.widget_clock_day_clock_normalContainer, View.GONE)
                    setViewVisibility(R.id.widget_clock_day_clock_blackContainer, View.GONE)
                }
            }
        }

        // Reuse HourlyTrendWidgetIMP's drawable view (clock-less hourly strip) and bake it into
        // a bitmap, the same way it bakes its own standalone widget.
        val hourlyColor = WidgetColor(context, cardStyle, "auto", location.isDaylight)
        HourlyTrendWidgetIMP.getDrawableView(context, location, hourlyColor)?.let { drawableView ->
            val items: Array<WidgetItemView> = arrayOf(
                drawableView.findViewById(R.id.widget_trend_hourly_item_1),
                drawableView.findViewById(R.id.widget_trend_hourly_item_2),
                drawableView.findViewById(R.id.widget_trend_hourly_item_3),
                drawableView.findViewById(R.id.widget_trend_hourly_item_4),
                drawableView.findViewById(R.id.widget_trend_hourly_item_5)
            )
            items.forEach { it.setSize(width / 5f) }
            drawableView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            drawableView.layout(0, 0, drawableView.measuredWidth, drawableView.measuredHeight)
            val cache = createBitmap(drawableView.measuredWidth, drawableView.measuredHeight)
            drawableView.draw(Canvas(cache))
            views.setImageViewBitmap(R.id.widget_clock_day_hourly_strip, cache)
        }

        setOnClickPendingIntent(context, views, location)
        return views
    }

    fun isInUse(context: Context): Boolean {
        val widgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, WidgetClockDayHourlyProvider::class.java))
        return widgetIds != null && widgetIds.isNotEmpty()
    }

    private fun setOnClickPendingIntent(context: Context, views: RemoteViews, location: Location) {
        views.setOnClickPendingIntent(
            R.id.widget_clock_day_weather,
            getWeatherPendingIntent(context, location, Widgets.CLOCK_DAY_HOURLY_PENDING_INTENT_CODE_WEATHER)
        )
        views.setOnClickPendingIntent(
            R.id.widget_clock_day_clock_light,
            getAlarmPendingIntent(context, Widgets.CLOCK_DAY_HOURLY_PENDING_INTENT_CODE_CLOCK_LIGHT)
        )
        views.setOnClickPendingIntent(
            R.id.widget_clock_day_clock_normal,
            getAlarmPendingIntent(context, Widgets.CLOCK_DAY_HOURLY_PENDING_INTENT_CODE_CLOCK_NORMAL)
        )
        views.setOnClickPendingIntent(
            R.id.widget_clock_day_clock_black,
            getAlarmPendingIntent(context, Widgets.CLOCK_DAY_HOURLY_PENDING_INTENT_CODE_CLOCK_BLACK)
        )
        views.setOnClickPendingIntent(
            R.id.widget_clock_day_title,
            getCalendarPendingIntent(context, Widgets.CLOCK_DAY_HOURLY_PENDING_INTENT_CODE_CALENDAR)
        )
    }
}
