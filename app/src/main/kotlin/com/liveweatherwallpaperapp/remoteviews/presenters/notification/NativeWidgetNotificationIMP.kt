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

package com.liveweatherwallpaperapp.remoteviews.presenters.notification

import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.common.extensions.getFormattedMediumDayAndMonthInAdditionalCalendar
import com.liveweatherwallpaperapp.common.extensions.getFormattedTime
import com.liveweatherwallpaperapp.common.extensions.is12Hour
import com.liveweatherwallpaperapp.common.extensions.notificationBuilder
import com.liveweatherwallpaperapp.common.extensions.notify
import com.liveweatherwallpaperapp.common.extensions.toBitmap
import com.liveweatherwallpaperapp.common.options.appearance.CalendarHelper
import com.liveweatherwallpaperapp.domain.location.model.getPlace
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.domain.weather.model.getName
import com.liveweatherwallpaperapp.domain.weather.model.getStrength
import com.liveweatherwallpaperapp.remoteviews.Notifications
import com.liveweatherwallpaperapp.remoteviews.presenters.AbstractRemoteViewsPresenter
import com.liveweatherwallpaperapp.ui.theme.resource.ResourceHelper
import com.liveweatherwallpaperapp.ui.theme.resource.ResourcesProviderFactory
import livewallpaperweather.domain.location.model.Location
import java.util.Date

object NativeWidgetNotificationIMP : AbstractRemoteViewsPresenter() {
    fun buildNotificationAndSendIt(
        context: Context,
        location: Location,
        daytime: Boolean,
        tempIcon: Boolean,
        persistent: Boolean,
    ) {
        val current = location.weather?.current ?: return
        val provider = ResourcesProviderFactory.newInstance
        val settings = SettingsManager.getInstance(context)
        val temperatureUnit = settings.getTemperatureUnit(context)

        val tempFeelsLikeOrAir = if (settings.isWidgetNotificationUsingFeelsLike) {
            current.temperature?.feelsLikeTemperature ?: current.temperature?.temperature
        } else {
            current.temperature?.temperature
        }
        val temperature = if (tempIcon) tempFeelsLikeOrAir else null

        val subtitle = StringBuilder()
        subtitle.append(location.getPlace(context))
        if (CalendarHelper.getAlternateCalendarSetting(context) != null) {
            subtitle.append(context.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
                .append(Date().getFormattedMediumDayAndMonthInAdditionalCalendar(location, context))
        } else {
            location.weather!!.base.refreshTime?.let {
                subtitle.append(context.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
                    .append(context.getString(R.string.notification_refreshed_at))
                    .append(" ")
                    .append(it.getFormattedTime(location, context, context.is12Hour))
            }
        }

        val contentTitle = StringBuilder()
        if (!tempIcon && tempFeelsLikeOrAir != null) {
            contentTitle.append(tempFeelsLikeOrAir.formatMeasure(context, temperatureUnit))
        }
        if (!current.weatherText.isNullOrEmpty()) {
            if (contentTitle.toString().isNotEmpty()) contentTitle.append(" – ")
            contentTitle.append(current.weatherText)
        }

        val notification = context.notificationBuilder(Notifications.CHANNEL_WIDGET).apply {
            priority = NotificationCompat.PRIORITY_MAX
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            if (temperature != null) {
                setSmallIcon(
                    IconCompat.createWithBitmap(
                        ResourceHelper.createTempBitmap(context, temperature, temperatureUnit)
                    )
                )
            } else {
                setSmallIcon(
                    ResourceHelper.getDefaultMinimalXmlIconId(current.weatherCode, daytime)
                )
            }
            current.weatherCode?.let { weatherCode ->
                setLargeIcon(
                    ResourceHelper.getWidgetNotificationIcon(
                        provider,
                        weatherCode,
                        daytime,
                        minimal = false,
                        darkText = false
                    ).toBitmap()
                )
            }
            setSubText(subtitle.toString())
            setContentTitle(contentTitle.toString())
            if (current.airQuality?.isIndexValid == true) {
                setContentText(context.getString(R.string.air_quality) + " – " + current.airQuality!!.getName(context))
            } else {
                current.wind?.getStrength(context)?.let { strength ->
                    setContentText(context.getString(R.string.wind) + " – " + strength)
                }
            }
            setOngoing(persistent)
            setOnlyAlertOnce(true)
            setContentIntent(getWeatherPendingIntent(context, null, Notifications.ID_WIDGET))
        }.build()

        if (!tempIcon) {
            current.weatherCode?.let { weatherCode ->
                try {
                    notification.javaClass
                        .getMethod("setSmallIcon", Icon::class.java)
                        .invoke(
                            notification,
                            ResourceHelper.getMinimalIcon(provider, weatherCode, daytime)
                        )
                } catch (ignore: Exception) {
                    // do nothing.
                }
            }
        }

        context.notify(Notifications.ID_WIDGET, notification)
    }
}
