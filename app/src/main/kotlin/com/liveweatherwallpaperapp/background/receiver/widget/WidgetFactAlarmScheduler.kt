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

package com.liveweatherwallpaperapp.background.receiver.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.liveweatherwallpaperapp.domain.settings.AppDefaults
import java.util.Calendar

/**
 * Schedules the exact SHOW/HIDE pair for the widget fact banner (see
 * [WidgetFactAlarmReceiver]), timed from [AppDefaults.widgetFact]. Uses
 * [AlarmManager.setExactAndAllowWhileIdle] so both fire on time even in Doze -- a regular
 * `set()`/WorkManager periodic job can be delayed by minutes to hours under battery
 * optimization, which isn't acceptable for a 20-second-wide window.
 *
 * One-shot by design (`setExactAndAllowWhileIdle` doesn't repeat): re-arming for the next day
 * is [WidgetFactAlarmReceiver]'s job once the HIDE side fires, not this scheduler's -- calling
 * this again right after SHOW fires would overwrite today's still-pending HIDE with tomorrow's.
 */
object WidgetFactAlarmScheduler {

    private const val REQUEST_CODE_SHOW = 9101
    private const val REQUEST_CODE_HIDE = 9102

    /** No-ops if exact alarms aren't permitted (user hasn't granted the special "Alarms &
     * reminders" access on API 31+) -- the widget simply never gets the fact banner rather than
     * crashing or falling back to an inexact (and therefore pointless for a 20s window) alarm. */
    @SuppressLint("MissingPermission")
    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

        val config = AppDefaults.widgetFact
        val showTime = nextTriggerTime(config.hour, config.minute, config.second)
        val hideTime = showTime + config.durationSeconds * 1000L

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            showTime,
            pendingIntent(context, show = true, requestCode = REQUEST_CODE_SHOW)
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            hideTime,
            pendingIntent(context, show = false, requestCode = REQUEST_CODE_HIDE)
        )
    }

    private fun nextTriggerTime(hour: Int, minute: Int, second: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun pendingIntent(context: Context, show: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetFactAlarmReceiver::class.java)
            .putExtra(WidgetFactAlarmReceiver.EXTRA_SHOW, show)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
