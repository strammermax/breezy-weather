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
import com.liveweatherwallpaperapp.domain.settings.ConfigStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Settings and local cache for the weetjes (location fun-fact) notification feature.
 *
 * Mirrors [WallpaperImageStore]'s SharedPreferences+JSON pattern rather than introducing Room
 * for what's a small, per-device, non-relational cache. Weetjes are fetched in bulk per
 * location ([RemoveSkyProvider.fetchNearbyWeetjes]) and cached here; the app itself decides
 * which cached weetje to show next and tracks what's already been shown, so a location doesn't
 * need a network round-trip on every dwell check and the user never sees the same fact twice
 * (until the cache for that place is exhausted).
 */
class WeetjeStore(context: Context) {

    private val config = ConfigStore(context, SP_NAME)

    /** Master switch for the dwell-notification feature. */
    var notificationsEnabled: Boolean
        get() = config.getBoolean(KEY_ENABLED, true)
        set(value) {
            config.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** How long the user must stay near a location before a weetje notification can fire. */
    var dwellThresholdMinutes: Int
        get() = config.getInt(KEY_DWELL_THRESHOLD_MINUTES, DEFAULT_DWELL_THRESHOLD_MINUTES)
            .coerceIn(MIN_DWELL_THRESHOLD_MINUTES, MAX_DWELL_THRESHOLD_MINUTES)
        set(value) {
            config.edit().putInt(
                KEY_DWELL_THRESHOLD_MINUTES,
                value.coerceIn(MIN_DWELL_THRESHOLD_MINUTES, MAX_DWELL_THRESHOLD_MINUTES)
            ).apply()
        }

    /** Maximum number of weetje notifications shown per calendar day (device-local date). */
    var maxNotificationsPerDay: Int
        get() = config.getInt(KEY_MAX_PER_DAY, DEFAULT_MAX_PER_DAY).coerceIn(MIN_MAX_PER_DAY, MAX_MAX_PER_DAY)
        set(value) {
            config.edit().putInt(KEY_MAX_PER_DAY, value.coerceIn(MIN_MAX_PER_DAY, MAX_MAX_PER_DAY)).apply()
        }

    // --- Dwell tracking -----------------------------------------------------------------
    //
    // Single active slot, not a per-location map: only one location is ever "where the user
    // currently is" at a time (the worker's activating location, refreshed with a live GPS
    // fix each tick). A map keyed by location would let a stale timer from a visit days ago
    // resume instantly on a later revisit instead of requiring a fresh full dwell period --
    // storing just the one current (key, startedAt) pair and resetting whenever the key
    // changes avoids that entirely.

    /** The location key the dwell timer is currently tracking, or null if none. */
    var dwellLocationKey: String?
        get() = config.getString(KEY_DWELL_LOCATION_KEY, null)
        set(value) {
            config.edit().putString(KEY_DWELL_LOCATION_KEY, value).apply()
        }

    /** Epoch millis when the current dwell session (for [dwellLocationKey]) started. */
    var dwellStartedAtMillis: Long?
        get() = config.getLong(KEY_DWELL_STARTED_AT, -1L).takeIf { it >= 0 }
        set(value) {
            if (value == null) {
                config.edit().remove(KEY_DWELL_STARTED_AT).apply()
            } else {
                config.edit().putLong(KEY_DWELL_STARTED_AT, value).apply()
            }
        }

    /** Starts (or restarts) the dwell session for [locationKey] at [timestampMillis]. */
    fun startDwell(locationKey: String, timestampMillis: Long) {
        dwellLocationKey = locationKey
        dwellStartedAtMillis = timestampMillis
    }

    /** Clears the active dwell session, e.g. right after a notification fires for it, so a
     * future visit needs a fresh full dwell period before notifying again. */
    fun clearDwell() {
        dwellLocationKey = null
        dwellStartedAtMillis = null
    }

    // --- Daily notification cap ---------------------------------------------------------

    /** How many weetje notifications have already been shown today (device-local date). */
    fun notificationsShownToday(): Int {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        return if (config.getString(KEY_SHOWN_DATE, null) == today) {
            config.getInt(KEY_SHOWN_COUNT, 0)
        } else {
            0
        }
    }

    /** Records that a weetje notification was just shown at [timestampMillis], resetting the
     * daily counter if the device-local date has rolled over since the last one. Also updates
     * [lastNotificationShownAtMillis], which is what actually spaces out notifications evenly
     * across the day (see [WeetjeDwellChecker.canSendNext]) -- the daily counter is only a
     * backstop cap, not the primary spacing mechanism. */
    fun recordNotificationShown(timestampMillis: Long) {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val count = if (config.getString(KEY_SHOWN_DATE, null) == today) {
            config.getInt(KEY_SHOWN_COUNT, 0) + 1
        } else {
            1
        }
        config.edit()
            .putString(KEY_SHOWN_DATE, today)
            .putInt(KEY_SHOWN_COUNT, count)
            .putLong(KEY_LAST_SHOWN_AT, timestampMillis)
            .apply()
    }

    /** Epoch millis the most recent weetje notification (any location) was shown at, or null
     * if none has ever been shown on this device. */
    val lastNotificationShownAtMillis: Long?
        get() = config.getLong(KEY_LAST_SHOWN_AT, -1L).takeIf { it >= 0 }

    // --- Per-location weetje cache --------------------------------------------------------

    /** Cached weetjes for [locationKey], in whatever order they were last stored. */
    fun weetjesFor(locationKey: String): List<CachedWeetje> = try {
        val array = weetjeMap().optJSONArray(locationKey) ?: return emptyList()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optInt("id", -1)
                val weetje = item.optString("weetje").takeIf { it.isNotBlank() }
                if (id < 0 || weetje == null) continue
                add(
                    CachedWeetje(
                        id = id,
                        weetje = weetje,
                        omschrijving = item.optString("omschrijving").ifBlank { null },
                        url = item.optString("url").ifBlank { null },
                        shownAt = item.optLong("shownAt", -1L).takeIf { it >= 0 }
                    )
                )
            }
        }
    } catch (e: Throwable) {
        emptyList()
    }

    /**
     * Merges freshly-fetched [weetjes] into [locationKey]'s cache: new ids are added, ids
     * already present keep their existing `shownAt` (a re-fetch must never "un-show" a
     * weetje the user has already seen). Ids no longer present upstream (e.g. a curator
     * disabled/deleted one) are dropped.
     */
    fun mergeWeetjes(locationKey: String, weetjes: List<RemoveSkyWeetje>) {
        val existingById = weetjesFor(locationKey).associateBy { it.id }
        val merged = weetjes.map { fresh ->
            CachedWeetje(
                id = fresh.id,
                weetje = fresh.weetje,
                omschrijving = fresh.omschrijving,
                url = fresh.linkDetail,
                shownAt = existingById[fresh.id]?.shownAt
            )
        }
        saveWeetjes(locationKey, merged)
    }

    /** Marks [id] as shown right now, for [locationKey]. */
    fun markShown(locationKey: String, id: Int, timestampMillis: Long) {
        val updated = weetjesFor(locationKey).map {
            if (it.id == id) it.copy(shownAt = timestampMillis) else it
        }
        saveWeetjes(locationKey, updated)
    }

    private fun saveWeetjes(locationKey: String, weetjes: List<CachedWeetje>) {
        val map = weetjeMap()
        val array = JSONArray()
        weetjes.forEach { w ->
            array.put(
                JSONObject().apply {
                    put("id", w.id)
                    put("weetje", w.weetje)
                    put("omschrijving", w.omschrijving ?: JSONObject.NULL)
                    put("url", w.url ?: JSONObject.NULL)
                    if (w.shownAt != null) put("shownAt", w.shownAt)
                }
            )
        }
        map.put(locationKey, array)
        config.edit().putString(KEY_WEETJES, map.toString()).apply()
    }

    private fun weetjeMap(): JSONObject = try {
        config.getString(KEY_WEETJES, null)?.let(::JSONObject) ?: JSONObject()
    } catch (e: Throwable) {
        JSONObject()
    }

    companion object {
        private const val SP_NAME = "live_wallpaper_weetjes"
        private const val KEY_ENABLED = "weetje_notifications_enabled"
        private const val KEY_DWELL_THRESHOLD_MINUTES = "weetje_dwell_threshold_minutes"
        private const val KEY_MAX_PER_DAY = "weetje_max_per_day"
        private const val KEY_DWELL_LOCATION_KEY = "weetje_dwell_location_key"
        private const val KEY_DWELL_STARTED_AT = "weetje_dwell_started_at"
        private const val KEY_SHOWN_DATE = "weetje_shown_date"
        private const val KEY_SHOWN_COUNT = "weetje_shown_count"
        private const val KEY_LAST_SHOWN_AT = "weetje_last_shown_at"
        private const val KEY_WEETJES = "weetje_cache"

        const val DEFAULT_DWELL_THRESHOLD_MINUTES = 60
        const val MIN_DWELL_THRESHOLD_MINUTES = 15
        const val MAX_DWELL_THRESHOLD_MINUTES = 240

        const val DEFAULT_MAX_PER_DAY = 2
        const val MIN_MAX_PER_DAY = 1
        const val MAX_MAX_PER_DAY = 10
    }
}

/** A weetje cached locally for one location. [shownAt] is null until the notification for it
 * has actually been shown once. */
data class CachedWeetje(
    val id: Int,
    val weetje: String,
    val omschrijving: String?,
    val url: String?,
    val shownAt: Long?,
)
