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

package com.liveweatherwallpaperapp.wallpaper.photo

import java.util.concurrent.TimeUnit

/**
 * Pure decision logic for the weetje dwell-notification feature, mirroring how
 * [WallpaperPhotoRefreshPlanner] keeps timing/selection decisions separate from the I/O in
 * [WeetjeManager]/[WeetjeStore].
 */
object WeetjeDwellChecker {

    /** True once [dwellStartedAtMillis] is far enough in the past to satisfy [thresholdMinutes]. */
    fun isDwellComplete(dwellStartedAtMillis: Long, nowMillis: Long, thresholdMinutes: Int): Boolean =
        nowMillis - dwellStartedAtMillis >= TimeUnit.MINUTES.toMillis(thresholdMinutes.toLong())

    /** True while today's notification count hasn't reached the configured daily cap yet. */
    fun canNotifyToday(notificationsShownToday: Int, maxPerDay: Int): Boolean =
        notificationsShownToday < maxPerDay

    /**
     * True during the fixed quiet-hours window (20:00-08:00, device-local time) when a weetje
     * notification must never fire -- no one should be woken up for a fun fact. [hourOfDay] is
     * 0-23. Not user-configurable: unlike the dwell threshold/daily cap, this is a hard rule.
     */
    fun isQuietHours(hourOfDay: Int): Boolean = hourOfDay >= QUIET_HOURS_START || hourOfDay < QUIET_HOURS_END

    private const val QUIET_HOURS_START = 20
    private const val QUIET_HOURS_END = 8

    /** Length of the allowed (non-quiet-hours) window per day, in hours: 08:00-20:00. */
    const val ACTIVE_WINDOW_HOURS = QUIET_HOURS_START - QUIET_HOURS_END

    /**
     * True once enough time has passed since [lastNotificationShownAtMillis] to keep up to
     * [maxPerDay] notifications evenly spaced across the [ACTIVE_WINDOW_HOURS]-hour allowed
     * window, e.g. max=3 -> a notification every 4 hours. [lastNotificationShownAtMillis] null
     * (never shown one before) always passes.
     *
     * This -- not [canNotifyToday] -- is what actually spaces notifications out through the
     * day; the daily count is just a backstop cap. When the next evenly-spaced slot would land
     * during quiet hours, [isQuietHours] blocks it there and it fires at the first opportunity
     * after quiet hours end instead (naturally "pushed to the next day") -- no separate
     * next-day logic is needed here for that.
     */
    fun canSendNext(lastNotificationShownAtMillis: Long?, nowMillis: Long, maxPerDay: Int): Boolean {
        if (lastNotificationShownAtMillis == null) return true
        val intervalMillis = TimeUnit.HOURS.toMillis(ACTIVE_WINDOW_HOURS.toLong()) / maxPerDay.coerceAtLeast(1)
        return nowMillis - lastNotificationShownAtMillis >= intervalMillis
    }

    /** A random not-yet-shown weetje from [cached], or null if every cached one has already
     * been shown (caller should then try [WeetjeManager] fetching more). */
    fun pickUnshown(cached: List<CachedWeetje>): CachedWeetje? =
        cached.filter { it.shownAt == null }.randomOrNull()
}
