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

package com.livewallpaperweather.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Opens the system UI to apply the LiveWallpaperWeather live wallpaper.
 *
 * Tries the direct preview of [MaterialLiveWallpaperService] first (ACTION_CHANGE_LIVE_WALLPAPER),
 * then the generic live-wallpaper chooser (ACTION_LIVE_WALLPAPER_CHOOSER). Some OEMs — notably
 * Samsung/OneUI — don't support the direct preview and throw various exceptions, so every attempt
 * catches any [Exception]. The launch flags must be combined with `or`; `and` of two disjoint
 * bits is 0 (no flags), which is what previously broke the launch on Samsung.
 *
 * @return true when one of the intents launched, false when none could be started.
 */
fun launchLiveWallpaperPicker(context: Context): Boolean {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
    val previewIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, MaterialLiveWallpaperService::class.java)
        )
        .addFlags(flags)
    val chooserIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        .addFlags(flags)
    return listOf(previewIntent, chooserIntent).any { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
