/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import android.graphics.Color

/**
 * Shared helpers for the day/night sky gradient colors, used by both the live
 * wallpaper renderer (MaterialLiveWallpaperService) and the static detail-screen
 * snapshot (WallpaperSceneSnapshot).
 */
object SkyColors {
    val NIGHT = intArrayOf(Color.rgb(3, 12, 35), Color.rgb(31, 55, 94))
    val DAWN = intArrayOf(Color.rgb(78, 78, 126), Color.rgb(242, 145, 104))
    val DAY = intArrayOf(Color.rgb(42, 125, 196), Color.rgb(174, 221, 244))
    val DUSK = intArrayOf(Color.rgb(54, 61, 108), Color.rgb(230, 113, 76))

    // Overcast grey tones the sky blends towards as cloud cover/darkness increases
    // (e.g. for rain/thunder), so the background reads as a grey, heavy-cloud sky
    // rather than the usual clear-sky blue/dark colors.
    private val OVERCAST_DAY = intArrayOf(Color.rgb(92, 96, 104), Color.rgb(128, 132, 140))
    private val OVERCAST_NIGHT = intArrayOf(Color.rgb(24, 26, 32), Color.rgb(40, 43, 50))

    // Fog removes the remaining blue cast from the sky behind the photo. Daytime
    // stays light and diffuse; nighttime uses neutral charcoal greys.
    private val FOG_DAY = intArrayOf(Color.rgb(178, 178, 178), Color.rgb(214, 214, 214))
    private val FOG_NIGHT = intArrayOf(Color.rgb(48, 48, 48), Color.rgb(74, 74, 74))

    // "Holl. wolken": a richer, more saturated blue than the default DAY gradient,
    // modelled on a reference photo of a deep-blue Dutch sky with voluminous cumulus.
    private val RICH_SKY_DAY = intArrayOf(Color.rgb(6, 70, 152), Color.rgb(70, 148, 209))

    // Fair: plenty of clean blue sky, close to the supplied reference, but a little
    // softer than the deliberately dramatic "Holl. wolken" palette.
    private val FAIR_SKY_DAY = intArrayOf(Color.rgb(12, 86, 174), Color.rgb(82, 166, 222))

    fun blendSky(from: IntArray, to: IntArray, amount: Float): IntArray = intArrayOf(
        blendColor(from[0], to[0], amount),
        blendColor(from[1], to[1], amount),
    )

    fun blendColor(from: Int, to: Int, amount: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt(),
    )

    fun fraction(value: Long, start: Long, end: Long): Float =
        ((value - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    /**
     * Blends [colors] towards an overcast grey based on [cloudDarkness] (0 = clear,
     * 1 = heaviest cloud cover, see WallpaperSceneStateFactory.effectProfile), so rainy
     * or stormy weather renders a grey, heavy-cloud sky instead of the usual clear-sky
     * gradient.
     */
    fun applyOvercastTint(colors: IntArray, cloudDarkness: Float, daytime: Boolean): IntArray {
        if (cloudDarkness <= 0f) return colors
        val overcast = if (daytime) OVERCAST_DAY else OVERCAST_NIGHT
        val amount = (cloudDarkness * 0.85f).coerceIn(0f, 1f)
        return blendSky(colors, overcast, amount)
    }

    /** Replaces the blue sky with a neutral grey gradient once visible fog is present. */
    fun applyFogSkyTint(colors: IntArray, fogIntensity: Float, daytime: Boolean): IntArray {
        if (fogIntensity <= 0f) return colors
        val fog = if (daytime) FOG_DAY else FOG_NIGHT
        val amount = (fogIntensity / 0.60f).coerceIn(0f, 1f)
        return blendSky(colors, fog, amount)
    }

    /** Deepens a daytime clear-sky gradient towards [RICH_SKY_DAY] for "Holl. wolken". */
    fun applyRichSkyTint(colors: IntArray, daytime: Boolean): IntArray {
        if (!daytime) return colors
        return blendSky(colors, RICH_SKY_DAY, 0.96f)
    }

    /** Deepens the blue daytime gradient while preserving Fair's open-sky character. */
    fun applyFairSkyTint(colors: IntArray, daytime: Boolean): IntArray {
        if (!daytime) return colors
        return blendSky(colors, FAIR_SKY_DAY, 0.82f)
    }
}
