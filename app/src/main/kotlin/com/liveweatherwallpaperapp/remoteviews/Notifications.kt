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

package com.liveweatherwallpaperapp.remoteviews

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_MIN
import androidx.core.content.ContextCompat
import androidx.core.text.parseAsHtml
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.buildNotificationChannel
import com.liveweatherwallpaperapp.common.extensions.buildNotificationChannelGroup
import com.liveweatherwallpaperapp.common.extensions.notificationBuilder
import com.liveweatherwallpaperapp.common.extensions.notify
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.domain.location.model.getPlace
import com.liveweatherwallpaperapp.domain.location.model.isDaylight
import com.liveweatherwallpaperapp.domain.settings.ConfigStore
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.domain.weather.model.getMinutelyDescription
import com.liveweatherwallpaperapp.domain.weather.model.getMinutelyTitle
import com.liveweatherwallpaperapp.domain.weather.model.hasMinutelyPrecipitation
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.weather.model.Alert
import livewallpaperweather.domain.weather.model.Weather
import livewallpaperweather.domain.weather.reference.AlertSeverity
import java.text.DateFormat

object Notifications {

    // We only have one group as we don’t have many channels
    // We name it “Breezy Weather” as LeakCanary also has its own group
    private const val GROUP_BREEZY_WEATHER = "group_breezy_weather"

    private const val CHANNEL_ALERT = "alert"
    private const val ID_ALERT_MIN = 1000
    private const val ID_ALERT_MAX = 1999
    private const val ID_ALERT_GROUP = 2000
    private const val ID_PRECIPITATION = 3000

    const val CHANNEL_FORECAST = "forecast"
    const val ID_TODAY_FORECAST = 2
    const val ID_TOMORROW_FORECAST = 3
    const val ID_UPDATING_TODAY_FORECAST = 7
    const val ID_UPDATING_TOMORROW_FORECAST = 8

    const val CHANNEL_WIDGET = "widget"
    const val ID_WIDGET = 1
    const val ID_UPDATING_WIDGET = 6

    const val CHANNEL_BACKGROUND = "background"
    const val ID_RUNNING_IN_BACKGROUND = 5
    const val ID_UPDATING_AWAKE = 9
    const val ID_WEATHER_PROGRESS = -101
    const val ID_WEATHER_ERROR = -102

    /**
     * Notification channel used for crash log file sharing.
     */
    const val CHANNEL_CRASH_LOGS = "crash_logs"
    const val ID_CRASH_LOGS = -201

    /**
     * Notification channel used for the "new app version available" push (see
     * RemoveSkyMessagingService / app/services/push.py send_update_notification).
     */
    const val CHANNEL_APP_UPDATE = "app_update"
    const val ID_APP_UPDATE = -301

    private const val ALERT_GROUP_KEY = "breezy_weather_alert_notification_group"
    private const val PREFERENCE_NOTIFICATION = "NOTIFICATION_PREFERENCE"
    private const val KEY_NOTIFICATION_ID = "NOTIFICATION_ID"
    // private const val PREFERENCE_SHORT_TERM_PRECIPITATION_ALERT = "SHORT_TERM_PRECIPITATION_ALERT_PREFERENCE"
    // private const val KEY_PRECIPITATION_LOCATION_KEY = "PRECIPITATION_LOCATION_KEY"
    // private const val KEY_PRECIPITATION_DATE = "PRECIPITATION_DATE"

    private val deprecatedChannels = listOf(
        "normally"
    )

    /**
     * Initialize channels so that the user can disable them, even if didn’t receive a notification yet
     */
    fun createChannels(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)

        // Delete old notification channels
        deprecatedChannels.forEach(notificationManager::deleteNotificationChannel)

        notificationManager.createNotificationChannelGroupsCompat(
            listOf(
                buildNotificationChannelGroup(GROUP_BREEZY_WEATHER) {
                    setName(context.getString(R.string.brand_name))
                }
            )
        )

        notificationManager.createNotificationChannelsCompat(
            listOf(
                buildNotificationChannel(CHANNEL_ALERT, IMPORTANCE_HIGH) {
                    setName(context.getString(R.string.notification_channel_alerts))
                    setGroup(GROUP_BREEZY_WEATHER)
                },
                buildNotificationChannel(CHANNEL_FORECAST, IMPORTANCE_DEFAULT) {
                    setName(context.getString(R.string.notification_channel_forecast))
                    setGroup(GROUP_BREEZY_WEATHER)
                },
                buildNotificationChannel(CHANNEL_WIDGET, IMPORTANCE_DEFAULT) {
                    setName(context.getString(R.string.notification_channel_widget))
                    setGroup(GROUP_BREEZY_WEATHER)
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_BACKGROUND, IMPORTANCE_MIN) {
                    setName(context.getString(R.string.notification_channel_background_services))
                    setGroup(GROUP_BREEZY_WEATHER)
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_CRASH_LOGS, IMPORTANCE_HIGH) {
                    setName(context.getString(R.string.notification_channel_crash_logs))
                },
                buildNotificationChannel(CHANNEL_APP_UPDATE, IMPORTANCE_DEFAULT) {
                    setName(context.getString(R.string.notification_channel_app_update))
                    setGroup(GROUP_BREEZY_WEATHER)
                }
            )
        )
    }

    /**
     * Shows a visible "new app version available" notification, pushed via FCM by
     * RemoveSky when a new build is published (see RemoveSkyMessagingService).
     */
    fun sendAppUpdateNotification(context: Context, message: String) {
        if (!SettingsManager.getInstance(context).isAppUpdatePushEnabled) return
        context.notify(
            ID_APP_UPDATE,
            context.notificationBuilder(CHANNEL_APP_UPDATE).apply {
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setSmallIcon(R.drawable.ic_alert)
                setContentTitle(context.getString(R.string.brand_name))
                setContentText(message)
                setStyle(NotificationCompat.BigTextStyle().bigText(message))
                setAutoCancel(true)
                setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        ID_APP_UPDATE,
                        IntentHelper.buildMainActivityIntent(),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }.build()
        )
    }

    private fun getNotificationBuilder(
        context: Context,
        @DrawableRes iconId: Int,
        title: String,
        subtitle: String,
        content: CharSequence?,
        intent: PendingIntent,
    ): NotificationCompat.Builder {
        return context.notificationBuilder(CHANNEL_ALERT).apply {
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setSmallIcon(iconId)
            setContentTitle(title)
            setSubText(subtitle)
            setContentText(content)
            setAutoCancel(true)
            setOnlyAlertOnce(true)
            setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            setContentIntent(intent)
        }
    }

    fun checkAndSendAlert(
        context: Context,
        location: Location,
        oldResult: Weather?,
    ) {
        val weather = location.weather
        if (weather == null || !SettingsManager.getInstance(context).isAlertPushEnabled) return

        val alertList = mutableListOf<Alert>()
        if (oldResult == null) {
            alertList.addAll(weather.alertList)
        } else {
            val idSet = mutableSetOf<String>()
            val desSet = mutableSetOf<String>()
            for (alert in oldResult.alertList.filter {
                it.severity != AlertSeverity.MINOR
            }) {
                idSet.add(alert.alertId)
                desSet.add(
                    alert.headline?.ifEmpty {
                        context.getString(R.string.alert)
                    } ?: context.getString(R.string.alert)
                )
            }
            for (alert in weather.alertList.filter {
                it.severity != AlertSeverity.MINOR
            }) {
                if (!idSet.contains(alert.alertId) &&
                    !desSet.contains(
                        alert.headline?.ifEmpty {
                            context.getString(R.string.alert)
                        } ?: context.getString(R.string.alert)
                    )
                ) {
                    alertList.add(alert)
                }
            }
        }
        alertList.forEach { alert ->
            sendAlertNotification(context, location, alert, alertList.size > 1)
        }
    }

    private fun sendAlertNotification(
        context: Context,
        location: Location,
        alert: Alert,
        inGroup: Boolean,
    ) {
        val notificationId = getAlertNotificationId(context)
        context.notify(
            notificationId,
            buildSingleAlertNotification(context, location, alert, inGroup, notificationId)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && inGroup) {
            context.notify(
                ID_ALERT_GROUP,
                buildAlertGroupSummaryNotification(context, location, alert, notificationId)
            )
        }
    }

    @SuppressLint("InlinedApi")
    private fun buildSingleAlertNotification(
        context: Context,
        location: Location,
        alert: Alert,
        inGroup: Boolean,
        notificationId: Int,
    ): Notification {
        val time = alert.startDate?.let {
            // FIXME: Timezone
            DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.DEFAULT).format(it)
        }
        val description = alert.description?.replace("\n", "<br />")?.parseAsHtml() ?: ""
        val builder = getNotificationBuilder(
            context,
            R.drawable.ic_alert,
            alert.headline?.ifEmpty {
                context.getString(R.string.alert)
            } ?: context.getString(R.string.alert),
            time ?: "",
            description,
            PendingIntent.getActivity(
                context,
                notificationId,
                IntentHelper.buildMainActivityShowAlertsIntent(location, alert.alertId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle(
                    alert.headline?.ifEmpty {
                        context.getString(R.string.alert)
                    } ?: context.getString(R.string.alert)
                )
                .setSummaryText(time)
                .bigText(description)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && inGroup) {
            builder.setGroup(ALERT_GROUP_KEY)
        }
        return builder.build()
    }

    @SuppressLint("InlinedApi")
    private fun buildAlertGroupSummaryNotification(
        context: Context,
        location: Location,
        alert: Alert,
        notificationId: Int,
    ): Notification {
        return context.notificationBuilder(CHANNEL_ALERT).apply {
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setSmallIcon(R.drawable.ic_alert)
            setContentTitle(
                alert.headline?.ifEmpty {
                    context.getString(R.string.alert)
                } ?: context.getString(R.string.alert)
            )
            setGroup(ALERT_GROUP_KEY)
            color = getColor(context, location)
            setGroupSummary(true)
            setOnlyAlertOnce(true)
            setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    IntentHelper.buildMainActivityShowAlertsIntent(location, alert.alertId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }.build()
    }

    private fun getAlertNotificationId(context: Context): Int {
        val config = ConfigStore(context, PREFERENCE_NOTIFICATION)
        var id = config.getInt(KEY_NOTIFICATION_ID, ID_ALERT_MIN) + 1
        if (id > ID_ALERT_MAX) {
            id = ID_ALERT_MIN
        }
        config.edit()
            .putInt(KEY_NOTIFICATION_ID, id)
            .apply()
        return id
    }

    // precipitation.
    fun checkAndSendPrecipitation(context: Context, location: Location) {
        if (!SettingsManager.getInstance(context).isPrecipitationPushEnabled ||
            location.weather?.minutelyForecast.isNullOrEmpty()
        ) {
            return
        }
        // val config = ConfigStore(context, PREFERENCE_SHORT_TERM_PRECIPITATION_ALERT)
        // val timestamp = config.getLong(KEY_PRECIPITATION_DATE, 0)

        if (location.weather!!.hasMinutelyPrecipitation) {
            context.notify(
                ID_PRECIPITATION,
                getNotificationBuilder(
                    context,
                    R.drawable.ic_precipitation,
                    location.weather!!.getMinutelyTitle(context),
                    location.getPlace(context),
                    location.weather!!.getMinutelyDescription(context, location),
                    PendingIntent.getActivity(
                        context,
                        ID_PRECIPITATION,
                        IntentHelper.buildMainActivityIntent(location),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )
            /*config.edit()
                .putString(KEY_PRECIPITATION_LOCATION_KEY, location.formattedId)
                .putLong(KEY_PRECIPITATION_DATE, System.currentTimeMillis())
                .apply()*/
        }
    }

    @ColorInt
    private fun getColor(context: Context, location: Location): Int {
        return ContextCompat.getColor(
            context,
            if (location.isDaylight) R.color.lightPrimary_5 else R.color.darkPrimary_5
        )
    }
}
