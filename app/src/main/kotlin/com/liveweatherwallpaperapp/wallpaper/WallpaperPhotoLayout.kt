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

package com.liveweatherwallpaperapp.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Shared photo-placement logic for the live wallpaper and its in-app snapshot/effect views.
 * The photo always anchors to the bottom of the target area, via [positionAtBottom] — bakes
 * into a new bitmap covering the full target width so there are no side gaps regardless of
 * aspect ratio. The positioned photo stays one foreground layer so animated clouds cannot drift
 * across opaque photographed objects.
 */
internal object WallpaperPhotoLayout {

    /** Fraction of screen height the photo occupies, measured from the bottom edge. */
    const val PHOTO_HEIGHT_FRACTION = 0.52f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Returns a new [targetWidth] × [targetHeight] bitmap with [source] placed at the
     * bottom edge, scaled to cover the full target width (or height fraction, whichever
     * is larger). Excess height above the photo area is transparent.
     *
     * Caller is responsible for recycling [source]; the returned bitmap is owned by the caller.
     */
    fun positionAtBottom(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val scale = maxOf(
            targetWidth.toFloat() / source.width,
            (targetHeight * PHOTO_HEIGHT_FRACTION) / source.height
        )
        val photoWidth = source.width * scale
        val photoHeight = source.height * scale
        val left = (targetWidth - photoWidth) / 2f
        canvas.drawBitmap(
            source,
            null,
            RectF(left, targetHeight - photoHeight, left + photoWidth, targetHeight.toFloat()),
            paint
        )
        return result
    }
}
