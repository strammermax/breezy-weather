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

import android.content.Context
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.remoteviews.Notifications
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the weetjes (location fun-fact) dwell-notification feature: called once per
 * [WallpaperPhotoRefreshWorker] tick for the activating location (the one with a live GPS
 * fix), decides whether the user has dwelled there long enough, and shows a notification if
 * so. All actual timing/selection decisions live in the pure [WeetjeDwellChecker]; this class
 * only does I/O (store reads/writes, network calls, notification).
 *
 * Notification cadence: the *first* notification for a place needs a full
 * [WeetjeStore.dwellThresholdMinutes] of continuous dwelling. While the user keeps dwelling at
 * the same place after that, further notifications aren't gated on dwell time again -- they're
 * evenly spaced across the day instead (see [WeetjeDwellChecker.canSendNext]), e.g. maxPerDay=3
 * -> one every 4 hours, deferred to the next day if a slot would land in quiet hours. The dwell
 * session itself only resets when the location actually changes (see the location-key check
 * below), not after each notification.
 */
@Singleton
class WeetjeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wallpaperRepository: WallpaperRepository,
) {
    private val store = WeetjeStore(context)

    /**
     * @return true when the caller should schedule a sooner-than-usual retry
     * ([WallpaperPhotoRefreshWorker.WEETJE_RETRY_DELAY_MINUTES]) instead of waiting for the
     * next regular tick: the dwell threshold was met but no weetje was available yet,
     * typically because `/weetjes/request-more` just kicked off a fresh LLM generation on
     * the server (known to take at least ~1 minute) and hasn't finished -- mirrors
     * [WallpaperPhotoRefreshWorker]'s own empty-result retry for photos.
     */
    suspend fun checkAndNotify(latitude: Double, longitude: Double, place: PlaceQuery): Boolean {
        if (!store.notificationsEnabled) return false

        val locationKey = place.cacheFileName().substringBeforeLast('.')
        val now = System.currentTimeMillis()

        // A different location than the one the dwell timer was tracking (or no timer running
        // yet) -- (re)start it here. The very first tick at a genuinely new place therefore
        // always needs a full dwellThresholdMinutes before it can notify, same as any other.
        if (store.dwellLocationKey != locationKey) {
            store.startDwell(locationKey, now)
            return false
        }

        val dwellStartedAt = store.dwellStartedAtMillis ?: run {
            store.startDwell(locationKey, now)
            return false
        }
        if (!WeetjeDwellChecker.isDwellComplete(dwellStartedAt, now, store.dwellThresholdMinutes)) return false
        if (!WeetjeDwellChecker.canNotifyToday(store.notificationsShownToday(), store.maxNotificationsPerDay)) {
            return false
        }
        // Evenly spaces repeat notifications (while still dwelling at the same place) across
        // the day instead of firing again as soon as the dwell threshold alone would allow.
        if (!WeetjeDwellChecker.canSendNext(store.lastNotificationShownAtMillis, now, store.maxNotificationsPerDay)) {
            return false
        }
        // Hard rule, not user-configurable: never wake someone up for a fun fact. The dwell
        // session is deliberately left intact (not cleared) -- the weetje is still "due", just
        // deferred to the next tick once quiet hours end, not treated as missed.
        val hourOfDay = LocalTime.now(ZoneId.systemDefault()).hour
        if (WeetjeDwellChecker.isQuietHours(hourOfDay)) return false

        val taal = context.currentLocale.language
        var cached = store.weetjesFor(locationKey)
        if (cached.isEmpty()) {
            val fetched = wallpaperRepository.fetchNearbyWeetjes(latitude, longitude, taal)
            if (fetched.isEmpty()) {
                // Nothing indexed for this place at all -- not a "still generating" situation
                // (fetchNearbyWeetjes never triggers generation), so no point retrying soon;
                // the next regular tick is fine.
                return false
            }
            store.mergeWeetjes(locationKey, fetched)
            cached = store.weetjesFor(locationKey)
        }

        var pick = WeetjeDwellChecker.pickUnshown(cached)
        if (pick == null) {
            // Every cached weetje for this place has already been shown -- ask the server for
            // fresh ones (it rate-limits actual generation server-side, see
            // REQUEST_MORE_COOLDOWN_HOURS, so calling this liberally is safe).
            val land = place.country.orEmpty()
            val locatie = place.city ?: place.displayName
            val result = wallpaperRepository.requestMoreWeetjes(land, locatie, latitude, longitude, taal)
            if (result == null || result.weetjes.isEmpty()) {
                // Either the request failed, or the server just started generating and the
                // response won't have real content for at least ~1 minute -- try again soon
                // rather than waiting a full photoRefreshIntervalMinutes tick (up to 3 hours).
                return true
            }
            store.mergeWeetjes(locationKey, result.weetjes)
            pick = WeetjeDwellChecker.pickUnshown(store.weetjesFor(locationKey))
        }
        val weetje = pick ?: return false

        Notifications.sendWeetjeNotification(context, weetje.weetje, weetje.omschrijving, weetje.url)
        store.markShown(locationKey, weetje.id, now)
        store.recordNotificationShown(now)
        // Deliberately NOT clearing the dwell session: the user may still be at this same
        // place, and a further notification here is gated by canSendNext's even-spacing
        // check above, not by dwelling a fresh full threshold again. The dwell session only
        // resets when the location-key check at the top actually sees a different place.
        return false
    }
}
