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
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import org.breezyweather.ui.common.images.MoonDrawable
import kotlin.math.min

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
            if (sceneState.usesGreyscalePhoto) {
                val greyscalePaint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(
                        ColorMatrix().apply { setSaturation(1f - sceneState.photoGreyscaleAmount) },
                    )
                }
                canvas.saveLayer(null, greyscalePaint)
                drawPhotoForeground(canvas, width, height, photo)
                canvas.restore()
            } else {
                drawPhotoForeground(canvas, width, height, photo)
            }
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
        val profiledColors = when {
            sceneState.richSky -> SkyColors.applyRichSkyTint(colors, sceneState.daytime)
            sceneState.condition.sky == WallpaperSkyCondition.FAIR ->
                SkyColors.applyFairSkyTint(colors, sceneState.daytime)
            else -> colors
        }
        val overcastColors = SkyColors.applyOvercastTint(
            profiledColors,
            sceneState.cloudDarkness,
            sceneState.daytime,
        )
        return SkyColors.applyFogSkyTint(overcastColors, sceneState.fogIntensity, sceneState.daytime)
    }

    private fun drawCelestialBody(canvas: Canvas, width: Int, height: Int, sceneState: WallpaperSceneState) {
        val now = System.currentTimeMillis()
        val shortestSide = min(width, height).toFloat()
        val fogVisibility = 1f - (sceneState.fogIntensity * 0.72f).coerceIn(0f, 1f)
        val sunAlpha = CelestialTiming.sunVisibility(
            now,
            sceneState.sunriseMillis,
            sceneState.sunsetMillis,
            sceneState.daytime,
        ) * fogVisibility
        val moonAlpha = 1f - sunAlpha
        val positionTime = now / 60_000L * 60_000L

        if (sunAlpha > 0.01f) {
            val sunProgress = CelestialTiming.celestialProgress(positionTime, sceneState.sunriseMillis, sceneState.sunsetMillis)
            val sunX = CelestialTiming.celestialX(width, sunProgress)
            val sunY = CelestialTiming.celestialY(height, sunProgress)
            drawSun(canvas, sunX, sunY, shortestSide, sunAlpha)
        }
        if (moonAlpha > 0.01f) {
            val moonProgress = CelestialTiming.celestialProgress(positionTime, sceneState.moonriseMillis, sceneState.moonsetMillis)
            val moonX = CelestialTiming.celestialX(width, moonProgress)
            val moonY = CelestialTiming.celestialY(height, moonProgress)
            val size = (shortestSide * CelestialTiming.CELESTIAL_SIZE_FRACTION).toInt()
            val halfSize = size / 2
            val moonDrawable = MoonDrawable()
            moonDrawable.setPhaseAngle(sceneState.moonPhaseAngle)
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

    private fun drawPhotoForeground(canvas: Canvas, width: Int, height: Int, photo: Bitmap) {
        WallpaperPhotoLayout.drawOnCanvas(canvas, width, height, photo)
    }
}
