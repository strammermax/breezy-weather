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

package com.liveweatherwallpaperapp

import android.app.Application
import android.app.UiModeManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import com.google.firebase.messaging.FirebaseMessaging
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.uiModeManager
import com.liveweatherwallpaperapp.common.extensions.workManager
import com.liveweatherwallpaperapp.common.utils.AndroidSignatureFinder
import com.liveweatherwallpaperapp.common.utils.helpers.LogHelper
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.remoteviews.Notifications
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import livewallpaperweather.data.location.LocationRepository
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import javax.inject.Inject

@HiltAndroidApp
class BreezyWeather : Application(), Configuration.Provider {

    companion object {

        lateinit var instance: BreezyWeather
            private set

        fun getProcessName() = try {
            val file = File("/proc/" + Process.myPid() + "/" + "cmdline")
            val mBufferedReader = BufferedReader(FileReader(file))
            val processName = mBufferedReader.readLine().trim {
                it <= ' '
            }
            mBufferedReader.close()

            processName
        } catch (e: Exception) {
            e.printStackTrace()

            null
        }
    }

    private val activitySet: MutableSet<BreezyActivity> by lazy {
        HashSet()
    }
    var topActivity: BreezyActivity? = null
        private set

    val debugMode: Boolean by lazy {
        applicationInfo != null && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository

    @Inject
    lateinit var locationRepository: LocationRepository

    override fun onCreate() {
        super.onCreate()

        instance = this

        setupNotificationChannels()

        if (getProcessName().equals(packageName)) {
            // Sets and persists the night mode setting for this app. This allows the system to know
            // if the app wants to be displayed in dark mode before it launches so that the splash
            // screen can be displayed accordingly.
            setDayNightMode()
        }

        /*
         * We don’t use the return value, but querying the work manager might help bringing back
         * scheduled workers after the app has been killed/shutdown on some devices
         */
        this.workManager.getWorkInfosLiveData(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))

        registerFcmToken()
        reconcileRemovalsOnStartup()
    }

    /**
     * Startup-only check for curator deletions/disables missed while the app was closed
     * (FCM only reaches a running app) -- see [WallpaperRepository.reconcileRemovals] and
     * docs/UpdateFLow.md flow 5. Scoped to the active location only, matching how
     * [WallpaperPhotoRefreshWorker] treats "the location driving the active wallpaper" as
     * the one that matters most; the periodic since/changed poll remains the broader
     * fallback for every tracked location.
     */
    private fun reconcileRemovalsOnStartup() {
        CoroutineScope(Dispatchers.IO).launch {
            if (!com.liveweatherwallpaperapp.wallpaper.photo.WallpaperImageStore(
                    this@BreezyWeather
                ).photoBackgroundEnabled
            ) {
                return@launch
            }
            val location = locationRepository.getFirstLocation(withParameters = false) ?: return@launch
            wallpaperRepository.reconcileRemovals(
                location.latitude,
                location.longitude,
                location
            )
        }
    }

    /**
     * Sends this device's current FCM token to RemoveSky on every start (a plain idempotent
     * upsert server-side, see app/dao/fcm_dao.py) so curator deletions/disables reach it via
     * push instead of waiting for the next poll -- see
     * com.liveweatherwallpaperapp.wallpaper.photo.RemoveSkyMessagingService and
     * docs/UpdateFLow.md flow 5. [RemoveSkyMessagingService.onNewToken] covers later
     * rotations of the same token.
     */
    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = if (task.isSuccessful) task.result else null
            android.util.Log.d(
                "RemoveSkyMessaging",
                "fetched token: ${token?.take(12)}... success=${task.isSuccessful}"
            )
            if (token.isNullOrBlank()) return@addOnCompleteListener
            CoroutineScope(Dispatchers.IO).launch {
                val ok = wallpaperRepository.registerFcmToken(token)
                android.util.Log.d("RemoveSkyMessaging", "registerFcmToken on start result: $ok")
            }
        }
    }

    fun addActivity(a: BreezyActivity) {
        activitySet.add(a)
    }

    fun removeActivity(a: BreezyActivity) {
        activitySet.remove(a)
    }

    fun setTopActivity(a: BreezyActivity) {
        topActivity = a
    }

    fun checkToCleanTopActivity(a: BreezyActivity) {
        if (topActivity === a) {
            topActivity = null
        }
    }

    fun recreateAllActivities() {
        val topA = topActivity
        for (a in activitySet) {
            if (a != topA) a.recreate()
        }
        // ensure that top activity stays on top by recreating it last
        topA?.recreate()
    }

    private fun setDayNightMode() {
        updateDayNightMode(SettingsManager.getInstance(this).darkMode.value)
    }

    fun updateDayNightMode(dayNightMode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            uiModeManager?.setApplicationNightMode(
                when (dayNightMode) {
                    AppCompatDelegate.MODE_NIGHT_NO -> UiModeManager.MODE_NIGHT_NO
                    AppCompatDelegate.MODE_NIGHT_YES -> UiModeManager.MODE_NIGHT_YES
                    else -> UiModeManager.MODE_NIGHT_AUTO
                }
            )
        } else {
            AppCompatDelegate.setDefaultNightMode(dayNightMode)
        }
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            LogHelper.log(msg = "Failed to setup notification channels")
        }
    }

    /*
     * /!\ Changing the below logic to impersonate Breezy Weather is a violation of the LGPL license that was granted
     * to you.
     * You're allowed to make a fork, but you're NOT allowed to impersonate the "Breezy Weather" app.
     * Use your own app name. See instructions in the README file, License section.
     */
    val isSignedByBreezy: Boolean
        get() {
            return AndroidSignatureFinder.getAndroidSignatures(packageName, packageManager).any {
                it == "29:D4:35:F7:0A:A9:AE:C3:C1:FA:FF:7F:7F:FA:6E:15:78:50:88:D8:7F:06:EC:FC:AB:9C:3C:C6:2D:C2:69:D8"
            }
        }

    val isImpersonatingBreezyWeather: Boolean
        get() {
            return (
                getString(R.string.brand_name).contains("breezy", ignoreCase = true) ||
                    BuildConfig.APPLICATION_ID.contains("breezy", ignoreCase = true)
                ) &&
                !isSignedByBreezy &&
                !debugMode
        }

    /*
     * Returns a User-Agent sources can use
     */
    val userAgent: String
        get() {
            return if (!getString(R.string.brand_name).contains("breezy", ignoreCase = true) ||
                isSignedByBreezy ||
                debugMode
            ) {
                "${getString(R.string.brand_name)}/${BuildConfig.VERSION_NAME} ${BuildConfig.REPORT_ISSUE}"
            } else {
                // Do not return anything if someone is trying to impersonate Breezy Weather
                // or we would be made responsible for their app calls
                ""
            }
        }

    override val workManagerConfiguration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
