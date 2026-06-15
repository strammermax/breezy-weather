/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import org.breezyweather.ui.common.images.MoonDrawable
import kotlin.math.min
import kotlin.math.sin

/**
 * ACT-014: renders a single static frame of the live wallpaper scene (sky gradient,
 * sun/moon, weather background pass, location photo) for use as the home screen's
 * background behind the ACT-013 glass cards.
 *
 * This intentionally mirrors (rather than reuses) the private rendering helpers of
 * [MaterialLiveWallpaperService], which are tightly coupled to its animated
 * [android.service.wallpaper.WallpaperService.Engine] instance state. This snapshot
 * is a one-shot, non-animated render and does not touch the live wallpaper service.
 */
internal object WallpaperSceneSnapshot {

    private const val PHOTO_HEIGHT_FRACTION = 0.52f
    private const val CELESTIAL_SIZE_FRACTION = 0.14f

    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        photo: Bitmap?,
        sceneState: WallpaperSceneState,
    ) {
        if (width <= 0 || height <= 0) return

        drawSkyBackground(canvas, width, height, sceneState)
        drawCelestialBody(canvas, width, height, sceneState)

        // Note: the cloud/weather background pass (WallpaperWeatherEffectRenderer) relies on
        // RuntimeShader, which Android refuses to draw onto a software-backed Canvas/Bitmap
        // (IllegalArgumentException: "Software rendering doesn't support RuntimeShader").
        // Since this snapshot is rendered onto a software bitmap for use as a static
        // background drawable, that pass is intentionally skipped here.

        if (photo != null) {
            drawPhotoForeground(canvas, width, height, photo)
        }
    }

    private fun drawSkyBackground(canvas: Canvas, width: Int, height: Int, sceneState: WallpaperSceneState) {
        val colors = skyColors(sceneState)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                colors[0],
                colors[1],
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun skyColors(sceneState: WallpaperSceneState): IntArray {
        val night = SkyColors.NIGHT
        val dawn = SkyColors.DAWN
        val day = SkyColors.DAY
        val dusk = SkyColors.DUSK

        val now = System.currentTimeMillis()
        val sunrise = sceneState.sunriseMillis
        val sunset = sceneState.sunsetMillis
        val transition = 45L * 60L * 1000L

        val colors = if (sunrise == null || sunset == null) {
            if (sceneState.daytime) day else night
        } else {
            when {
                now < sunrise - transition -> night
                now < sunrise -> SkyColors.blendSky(night, dawn, SkyColors.fraction(now, sunrise - transition, sunrise))
                now < sunrise + transition -> SkyColors.blendSky(dawn, day, SkyColors.fraction(now, sunrise, sunrise + transition))
                now < sunset - transition -> day
                now < sunset -> SkyColors.blendSky(day, dusk, SkyColors.fraction(now, sunset - transition, sunset))
                now < sunset + transition -> SkyColors.blendSky(dusk, night, SkyColors.fraction(now, sunset, sunset + transition))
                else -> night
            }
        }
        return SkyColors.applyOvercastTint(colors, sceneState.cloudDarkness, sceneState.daytime)
    }

    private fun drawCelestialBody(canvas: Canvas, width: Int, height: Int, sceneState: WallpaperSceneState) {
        val now = System.currentTimeMillis()
        val horizonY = height * 0.48f
        val peakY = height * 0.12f
        val shortestSide = min(width, height).toFloat()
        val sunAlpha = sunVisibility(now, sceneState)
        val moonAlpha = 1f - sunAlpha
        val positionTime = now / 60_000L * 60_000L

        if (sunAlpha > 0.01f) {
            val sunProgress = celestialProgress(positionTime, sceneState.sunriseMillis, sceneState.sunsetMillis)
            val sunX = width * (0.12f + 0.76f * sunProgress)
            val sunY = horizonY - sin(Math.PI * sunProgress).toFloat() * (horizonY - peakY)
            drawSun(canvas, sunX, sunY, shortestSide, sunAlpha)
        }
        if (moonAlpha > 0.01f) {
            val moonProgress = celestialProgress(positionTime, sceneState.moonriseMillis, sceneState.moonsetMillis)
            val moonX = width * (0.12f + 0.76f * moonProgress)
            val moonY = horizonY - sin(Math.PI * moonProgress).toFloat() * (horizonY - peakY)
            val size = (shortestSide * CELESTIAL_SIZE_FRACTION).toInt()
            val halfSize = size / 2
            val moonDrawable = MoonDrawable()
            moonDrawable.alpha = (moonAlpha * 255).toInt()
            moonDrawable.setBounds(
                (moonX - halfSize).toInt(),
                (moonY - halfSize).toInt(),
                (moonX + halfSize).toInt(),
                (moonY + halfSize).toInt(),
            )
            moonDrawable.draw(canvas)
        }
    }

    private fun drawSun(canvas: Canvas, centerX: Float, centerY: Float, shortestSide: Float, visibility: Float) {
        val glowRadius = shortestSide * CelestialGlow.GLOW_RADIUS_FRACTION
        val coreRadius = shortestSide * CelestialGlow.CORE_RADIUS_FRACTION
        val alpha = (visibility * 255).toInt()

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = CelestialGlow.glowShader(centerX, centerY, glowRadius)
        }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            CelestialGlow.configureCorePaint(this)
        }
        CelestialGlow.draw(canvas, centerX, centerY, glowRadius, coreRadius, alpha, glowPaint, corePaint)
    }

    private fun sunVisibility(now: Long, sceneState: WallpaperSceneState): Float {
        val sunrise = sceneState.sunriseMillis ?: return if (sceneState.daytime) 1f else 0f
        val sunset = sceneState.sunsetMillis ?: return if (sceneState.daytime) 1f else 0f
        val crossFade = 25L * 60L * 1000L
        return when {
            now < sunrise - crossFade -> 0f
            now < sunrise + crossFade -> SkyColors.fraction(now, sunrise - crossFade, sunrise + crossFade)
            now < sunset - crossFade -> 1f
            now < sunset + crossFade -> 1f - SkyColors.fraction(now, sunset - crossFade, sunset + crossFade)
            else -> 0f
        }
    }

    private fun celestialProgress(now: Long, preferredStart: Long?, preferredEnd: Long?): Float {
        val start = preferredStart ?: return 0.5f
        val end = preferredEnd ?: return 0.5f
        if (end <= start) return 0.5f
        return ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    private fun drawPhotoForeground(canvas: Canvas, width: Int, height: Int, photo: Bitmap) {
        val photoHeight = height * PHOTO_HEIGHT_FRACTION
        val scale = photoHeight / photo.height
        val photoWidth = photo.width * scale
        val left = (width - photoWidth) / 2f
        canvas.drawBitmap(
            photo,
            null,
            RectF(left, height - photoHeight, left + photoWidth, height.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
    }
}
