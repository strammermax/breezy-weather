/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Shared sun rendering: a bright core with a large, soft radiating glow, used by both the
 * live wallpaper renderer (MaterialLiveWallpaperService) and the static detail-screen
 * snapshot (WallpaperSceneSnapshot).
 */
object CelestialGlow {
    /** Glow radius as a fraction of the screen's shortest side. */
    const val GLOW_RADIUS_FRACTION = 0.62f

    /** Core (disc) radius as a fraction of the screen's shortest side. */
    const val CORE_RADIUS_FRACTION = 0.06f

    private val GLOW_COLORS = intArrayOf(
        Color.argb(255, 255, 255, 246),
        Color.argb(220, 255, 253, 232),
        Color.argb(150, 255, 248, 214),
        Color.argb(90, 255, 240, 190),
        Color.argb(46, 255, 230, 170),
        Color.argb(16, 255, 220, 160),
        Color.TRANSPARENT
    )

    private val GLOW_STOPS = floatArrayOf(0f, 0.08f, 0.22f, 0.40f, 0.62f, 0.84f, 1f)

    private val CORE_COLOR = Color.rgb(255, 255, 248)

    /** Builds the radial-gradient shader for the sun's glow, centered at [centerX]/[centerY]. */
    fun glowShader(centerX: Float, centerY: Float, glowRadius: Float): Shader = RadialGradient(
        centerX,
        centerY,
        glowRadius,
        GLOW_COLORS,
        GLOW_STOPS,
        Shader.TileMode.CLAMP
    )

    /** Draws the sun (glow + core) using the given paints, which should already have their shader/color set. */
    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        glowRadius: Float,
        coreRadius: Float,
        alpha: Int,
        glowPaint: Paint,
        corePaint: Paint,
    ) {
        glowPaint.alpha = alpha
        corePaint.alpha = alpha
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)
        canvas.drawCircle(centerX, centerY, coreRadius, corePaint)
    }

    /** Configures [corePaint] with the sun's core color (call once at setup). */
    fun configureCorePaint(corePaint: Paint) {
        corePaint.color = CORE_COLOR
    }
}
