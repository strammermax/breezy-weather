/*
 * This file is part of Breezy Weather.
 */

package com.liveweatherwallpaperapp.wallpaper

import android.graphics.Bitmap

/**
 * Creates a deliberately low-detail version of a scene bitmap for the frosted
 * app background. Scaling down before filtering back up is inexpensive, works
 * on every supported Android version, and keeps the original bitmap untouched.
 */
internal fun Bitmap.toFrostedBackground(strength: Int = 2): Bitmap {
    if (width < 2 || height < 2) return this
    val downsample = when (strength.coerceIn(1, 3)) {
        1 -> 14
        3 -> 34
        else -> 24
    }
    val sampleWidth = (width / downsample).coerceAtLeast(1)
    val sampleHeight = (height / downsample).coerceAtLeast(1)
    val sample = Bitmap.createScaledBitmap(this, sampleWidth, sampleHeight, true)
    return Bitmap.createScaledBitmap(sample, width, height, true).also {
        if (sample !== this && sample !== it) sample.recycle()
    }
}
