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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.liveweatherwallpaperapp.remoteviews.presenters.MaterialYouForecastWidgetIMP
import com.liveweatherwallpaperapp.wallpaper.photo.WeetjeStore
import com.liveweatherwallpaperapp.wallpaper.photo.toWallpaperPlaceQuery
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import livewallpaperweather.data.location.LocationRepository
import javax.inject.Inject

/**
 * Fired by [WidgetFactAlarmScheduler] at two exact times a day: SHOW pulls a cached weetje
 * (see [WeetjeStore], the same location fun-fact cache used for dwell notifications) for the
 * current location and pushes it onto the 4x2 widget; HIDE clears it again -- and re-arms
 * tomorrow's pair, since [WidgetFactAlarmScheduler.scheduleNext] must not be called right after
 * SHOW (it would overwrite the still-pending HIDE).
 */
@AndroidEntryPoint
class WidgetFactAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val show = intent.getBooleanExtra(EXTRA_SHOW, false)

        if (!show) {
            MaterialYouForecastWidgetIMP.updateFactVisibility(context, show = false, factText = null)
            WidgetFactAlarmScheduler.scheduleNext(context)
            return
        }

        if (!MaterialYouForecastWidgetIMP.isEnabled(context)) return
        GlobalScope.launch(Dispatchers.IO) {
            val location = locationRepository.getFirstLocation(withParameters = false)
            val locationKey = location?.toWallpaperPlaceQuery()?.cacheFileName()?.substringBeforeLast('.')
            val fact = locationKey?.let { WeetjeStore(context).weetjesFor(it).randomOrNull()?.weetje }
            MaterialYouForecastWidgetIMP.updateFactVisibility(context, show = true, factText = fact)
        }
    }

    companion object {
        const val EXTRA_SHOW = "show_fact"
    }
}
