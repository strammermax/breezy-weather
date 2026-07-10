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
        if (message.data["action"] != "purge") return
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
