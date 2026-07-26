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

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.bus.EventBus
import com.liveweatherwallpaperapp.remoteviews.Notifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives RemoveSky's "purge this photo now" push (see `app/services/push.py` and
 * docs/UpdateFLow.md flow 5): a curator soft-delete/disable in the Beheren tab reaches
 * every active device within seconds instead of waiting for the next
 * [WallpaperRepository.pruneDisabledPhotos]/[WallpaperRepository.checkForNewPhotos] poll
 * -- the reason this exists is child-safety (an inappropriate photo must not linger).
 *
 * The since/changed polling (flow 1/4) remains the fallback for devices offline when the
 * push was sent, or if FCM delivery is delayed/dropped.
 */
@AndroidEntryPoint
class RemoveSkyMessagingService : FirebaseMessagingService() {

    @Inject lateinit var wallpaperRepository: WallpaperRepository

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "onMessageReceived: from=${message.from} data=${message.data}")
        when (message.data["action"]) {
            "purge" -> handlePurge(message)
            "update_available" -> handleUpdateAvailable(message)
            "upload_progress" -> handleUploadProgress(message)
            "upload_result" -> handleUploadResult(message)
        }
    }

    private fun handleUploadProgress(message: RemoteMessage) {
        val recordId = message.data["record_id"]?.toIntOrNull() ?: return
        val stage = message.data["stage"] ?: return
        Log.d(TAG, "upload_progress: record_id=$recordId stage=$stage")
        EventBus.instance.with(UploadProcessingProgressMessage::class.java).postValue(
            UploadProcessingProgressMessage(recordId, stage)
        )
    }

    /** Outcome of the async camera-upload pass (see processing.finish_upload_processing /
     * push.send_to_token) for a photo this device uploaded -- the /upload response itself
     * came back as "pending" with no verdict yet (see RemoveSkyUploadResult.pending), so
     * this push is the only place "done"/"rejected"/"failed" ever reaches the user. */
    private fun handleUploadResult(message: RemoteMessage) {
        val result = message.data["result"] ?: return
        val reason = message.data["reason"]
        val recordId = message.data["record_id"]?.toIntOrNull()
        Log.d(TAG, "upload_result: record_id=$recordId result=$result reason=$reason")
        if (recordId != null) {
            fun flag(key: String) = message.data[key]?.toBooleanStrictOrNull()
            val checks = if (result == "done" && message.data.containsKey("has_sky_top")) {
                RemoveSkyCheckResult(
                    ok = true,
                    reason = null,
                    skyFraction = null,
                    checks = RemoveSkyChecks(
                        hasSkyTop = flag("has_sky_top"),
                        isOutdoor = flag("is_outdoor"),
                        isCity = flag("is_city"),
                        hasColor = flag("has_color"),
                        hasGps = flag("has_gps"),
                        hasDate = flag("has_date"),
                        isNightVisual = message.data["day_period"]?.let { it == "night" },
                        seasonVisual = message.data["season"]?.takeIf { it.isNotBlank() },
                    ),
                )
            } else null
            EventBus.instance.with(UploadProcessingResultMessage::class.java).postValue(
                UploadProcessingResultMessage(recordId, result, reason, message.data["processed_url"], checks)
            )
        }
        val text = when (result) {
            "done" -> getString(R.string.camera_upload_result_done)
            "rejected" -> getString(R.string.camera_upload_result_rejected, reason ?: "")
            else -> getString(R.string.camera_upload_result_failed)
        }
        Notifications.sendUploadResultNotification(applicationContext, text)
    }

    private fun handlePurge(message: RemoteMessage) {
        // Normalize scheme/host/port to match this device's configured RemoveSky base --
        // the server may not know its own public scheme (e.g. plain HTTP behind a
        // TLS-terminating proxy), and an http/https mismatch must not break the exact
        // string match against a cached photo's sourceUrl.
        val urls = message.data["urls"]?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.map { wallpaperRepository.normalizeServiceUrl(it) }
            .orEmpty()
        if (urls.isEmpty()) return
        Log.d(TAG, "purging ${urls.size} url(s): $urls")
        scope.launch {
            wallpaperRepository.purgeUrls(urls)
        }
    }

    private fun handleUpdateAvailable(message: RemoteMessage) {
        val text = message.data["message"] ?: return
        Log.d(TAG, "update available: version=${message.data["version"]} message=$text")
        Notifications.sendAppUpdateNotification(applicationContext, text)
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "onNewToken: ${token.take(12)}...")
        scope.launch {
            val ok = wallpaperRepository.registerFcmToken(token)
            Log.d(TAG, "registerFcmToken result: $ok")
        }
    }

    companion object {
        private const val TAG = "RemoveSkyMessaging"
    }
}

/** In-process counterpart of the upload-result notification. A visible CameraActivity uses
 * this to replace its pending card as soon as the targeted FCM push arrives. */
data class UploadProcessingResultMessage(
    val recordId: Int,
    val result: String,
    val reason: String?,
    val processedUrl: String?,
    val checkResult: RemoveSkyCheckResult? = null,
)

data class UploadProcessingProgressMessage(
    val recordId: Int,
    val stage: String,
)
