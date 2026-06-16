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

package org.breezyweather.wallpaper

import android.graphics.Bitmap

/**
 * Shared snapshot of the live wallpaper's rendered frame (sky + photo layers, at 1/4 resolution).
 * Written by [MaterialLiveWallpaperService] every ~30 frames on the wallpaper handler thread;
 * read by the frosted-glass card backgrounds on the UI thread.
 *
 * We never recycle the old bitmap to avoid a race condition where the UI thread still holds a
 * reference to it. The bitmaps are small (~650 KB at 1080p) and GC handles them quickly.
 */
object WallpaperSnapshot {
    @Volatile var bitmap: Bitmap? = null
}
