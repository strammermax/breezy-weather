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

package com.liveweatherwallpaperapp.common

import android.content.Context
import com.liveweatherwallpaperapp.domain.settings.ConfigStore
import org.json.JSONObject

enum class AppMessageKind {
    WARNING,
    WEETJE,
}

data class AppMessage(
    val kind: AppMessageKind,
    /** Stable id used for dedupe/clear, e.g. "weather_update_failed", "photo_refresh_failed". */
    val key: String,
    val title: String,
    val body: String? = null,
    /** Only meaningful for [AppMessageKind.WEETJE] (link to the source page). */
    val url: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/**
 * Persists at most one active message per [AppMessageKind], surfaced as a card on the main
 * screen (see AppMessageViewHolder) so warnings/weetjes aren't only visible through a
 * dismissible, easy-to-miss system notification.
 */
class AppMessageStore(context: Context) {

    private val config = ConfigStore(context, SP_NAME)

    /** Records/replaces the active message for this [AppMessage.kind]. */
    fun setMessage(message: AppMessage) {
        config.edit().putString(messageKey(message.kind), toJson(message)).apply()
    }

    /** Clears the active message for [key] if it's still the one currently stored (source resolved itself). */
    fun clear(key: String) {
        for (kind in AppMessageKind.entries) {
            val current = readMessage(kind) ?: continue
            if (current.key == key) {
                config.edit().remove(messageKey(kind)).apply()
            }
        }
    }

    /** User tapped the X: hide this kind's current message until a newer one replaces it. */
    fun dismiss(kind: AppMessageKind) {
        val current = readMessage(kind) ?: return
        config.edit().putLong(dismissedAtKey(kind), current.timestampMillis).apply()
    }

    /** Active, non-dismissed, non-expired messages -- warning first, then weetje. */
    fun activeMessages(): List<AppMessage> {
        val now = System.currentTimeMillis()
        return AppMessageKind.entries.mapNotNull { kind ->
            val message = readMessage(kind) ?: return@mapNotNull null
            val dismissedAt = config.getLong(dismissedAtKey(kind), 0L)
            if (message.timestampMillis <= dismissedAt) return@mapNotNull null
            if (kind == AppMessageKind.WARNING && now - message.timestampMillis > WARNING_EXPIRY_MILLIS) {
                return@mapNotNull null
            }
            message
        }
    }

    private fun readMessage(kind: AppMessageKind): AppMessage? {
        val json = config.getString(messageKey(kind), null) ?: return null
        return try {
            fromJson(kind, json)
        } catch (e: Throwable) {
            null
        }
    }

    private fun messageKey(kind: AppMessageKind) = "message_${kind.name}"
    private fun dismissedAtKey(kind: AppMessageKind) = "dismissed_at_${kind.name}"

    private fun toJson(message: AppMessage): String {
        return JSONObject().apply {
            put("key", message.key)
            put("title", message.title)
            put("body", message.body)
            put("url", message.url)
            put("timestampMillis", message.timestampMillis)
        }.toString()
    }

    private fun fromJson(kind: AppMessageKind, json: String): AppMessage {
        val obj = JSONObject(json)
        return AppMessage(
            kind = kind,
            key = obj.getString("key"),
            title = obj.getString("title"),
            body = obj.optString("body").takeIf { it.isNotEmpty() && !obj.isNull("body") },
            url = obj.optString("url").takeIf { it.isNotEmpty() && !obj.isNull("url") },
            timestampMillis = obj.getLong("timestampMillis")
        )
    }

    companion object {
        private const val SP_NAME = "app_message"
        private const val WARNING_EXPIRY_MILLIS = 24L * 60 * 60 * 1000
    }
}
