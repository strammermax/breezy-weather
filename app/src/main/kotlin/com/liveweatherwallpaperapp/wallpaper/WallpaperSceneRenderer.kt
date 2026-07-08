/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.Shader
import android.media.ImageReader
import android.os.Build
import java.nio.ByteBuffer

/**
 * Offscreen render path for ACT-008 visual regression tests.
 *
 * Reuses [WallpaperWeatherEffectRenderer] (AGSL on API 33+, Canvas fallback below) without
 * invoking the [android.service.wallpaper.WallpaperService.Engine] lifecycle. The sky gradient
 * and sun/moon are intentionally simplified placeholders (not a copy of
 * [MaterialLiveWallpaperService]'s production sky/photo composition) so this stays a small,
 * additive testability hook rather than a refactor of the service.
 */
internal object WallpaperSceneRenderer {

    /** ~30 FPS step used to settle particle/cloud/drop positions deterministically. */
    private const val FIXED_FRAME_INTERVAL_MILLIS = 33L

    /** Number of fixed steps applied before drawing, roughly 1 second at 30 FPS. */
    private const val SETTLE_FRAME_COUNT = 30

    /**
     * Renders a single scene to a bitmap with a fixed seed, size and quality profile.
     * Deterministic for identical inputs.
     */
    fun renderEffectsToBitmap(
        state: WallpaperSceneState,
        width: Int,
        height: Int,
        seed: Long,
        transitionProgress: Float = 1f,
        qualityProfile: WallpaperQualityProfile = WallpaperQualityProfile.BALANCED,
    ): Bitmap {
        val renderer = buildRenderer(state, seed, qualityProfile)
        return renderToBitmap(width, height) { canvas ->
            drawScene(canvas, renderer, state, width, height, transitionProgress)
        }
    }

    /**
     * Renders a transition between two scenes at a fixed [progress] (0f..1f), mimicking the
     * composited old/new renderer crossfade used by ACT-002.
     */
    fun renderTransitionToBitmap(
        fromState: WallpaperSceneState,
        toState: WallpaperSceneState,
        width: Int,
        height: Int,
        seed: Long,
        progress: Float,
        qualityProfile: WallpaperQualityProfile = WallpaperQualityProfile.BALANCED,
    ): Bitmap {
        val fromRenderer = buildRenderer(fromState, seed, qualityProfile)
        val toRenderer = buildRenderer(toState, seed, qualityProfile)
        val clampedProgress = progress.coerceIn(0f, 1f)
        return renderToBitmap(width, height) { canvas ->
            drawSky(canvas, toState, width, height)
            drawCelestialBody(canvas, toState, width, height)
            fromRenderer.drawBackgroundWeatherPass(canvas, 1f - clampedProgress)
            toRenderer.drawBackgroundWeatherPass(canvas, clampedProgress)
            fromRenderer.drawForegroundWeatherPass(canvas, 1f - clampedProgress)
            toRenderer.drawForegroundWeatherPass(canvas, clampedProgress)
            fromRenderer.drawGlassRainDrops(canvas, 1f - clampedProgress)
            toRenderer.drawGlassRainDrops(canvas, clampedProgress)
        }
    }

    private fun buildRenderer(
        state: WallpaperSceneState,
        seed: Long,
        qualityProfile: WallpaperQualityProfile,
    ): WallpaperWeatherEffectRenderer {
        val renderer = WallpaperWeatherEffectRenderer(
            weatherKind = state.weatherKind,
            daylight = state.daylight,
            cloudSpeedFactor = state.windFactor,
            cloudField = CloudFieldFactory.cloudFieldParams(
                family = state.weatherFamily,
                cloudDensity = state.cloudDensity,
                cloudDarkness = state.cloudDarkness,
                windFactor = state.windFactor,
                windDirectionDegrees = state.windDirectionDegrees
            ),
            fogField = FogFieldFactory.fogFieldParams(
                fogIntensity = state.fogIntensity,
                hazeIntensity = state.hazeIntensity,
                windFactor = state.windFactor,
                windDirectionDegrees = state.windDirectionDegrees
            ),
            starField = StarFieldFactory.starFieldParams(locationSeed = seed),
            glassRainIntensity = state.glassRainIntensity,
            precipitationIntensity = state.precipitationIntensity,
            precipitationTiltSlope = state.precipitationTiltSlope,
            qualityProfile = qualityProfile,
            randomSeed = seed,
            fairSky = state.condition.sky == WallpaperSkyCondition.FAIR
        )
        repeat(SETTLE_FRAME_COUNT) {
            renderer.update(FIXED_FRAME_INTERVAL_MILLIS, animate = true)
        }
        return renderer
    }

    private fun drawScene(
        canvas: Canvas,
        renderer: WallpaperWeatherEffectRenderer,
        state: WallpaperSceneState,
        width: Int,
        height: Int,
        transitionProgress: Float,
    ) {
        drawSky(canvas, state, width, height)
        drawCelestialBody(canvas, state, width, height)
        renderer.drawBackgroundWeatherPass(canvas, transitionProgress)
        renderer.drawForegroundWeatherPass(canvas, transitionProgress)
        renderer.drawGlassRainDrops(canvas, transitionProgress)
    }

    private fun drawSky(canvas: Canvas, state: WallpaperSceneState, width: Int, height: Int) {
        val baseColors = if (state.daytime) {
            val colors = if (state.condition.sky == WallpaperSkyCondition.FAIR) {
                SkyColors.applyFairSkyTint(SkyColors.DAY, true)
            } else {
                intArrayOf(Color.rgb(90, 150, 225), Color.rgb(195, 222, 255))
            }
            colors
        } else {
            intArrayOf(Color.rgb(8, 14, 38), Color.rgb(28, 34, 68))
        }
        val colors = SkyColors.applyFogSkyTint(baseColors, state.fogIntensity, state.daytime)
        val (top, bottom) = colors[0] to colors[1]
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawCelestialBody(canvas: Canvas, state: WallpaperSceneState, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (state.daytime) Color.rgb(255, 248, 220) else Color.rgb(225, 228, 240)
        }
        canvas.drawCircle(width * 0.5f, height * 0.18f, width * 0.06f, paint)
    }

    private fun renderToBitmap(width: Int, height: Int, draw: (Canvas) -> Unit): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderWithHardwareRenderer(width, height, draw)
        } else {
            renderWithSoftwareCanvas(width, height, draw)
        }
    }

    private fun renderWithSoftwareCanvas(width: Int, height: Int, draw: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        return bitmap
    }

    /** Used on API 29+ so AGSL `RuntimeShader` passes (Android 13+) render correctly. */
    private fun renderWithHardwareRenderer(width: Int, height: Int, draw: (Canvas) -> Unit): Bitmap {
        val renderer = HardwareRenderer()
        val node = RenderNode("WallpaperSceneRenderer")
        node.setPosition(0, 0, width, height)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            val canvas = node.beginRecording(width, height)
            draw(canvas)
            node.endRecording()
            renderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw()

            val image = reader.acquireNextImage() ?: error("WallpaperSceneRenderer: no image produced")
            try {
                val plane = image.planes[0]
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                copyPlaneToBitmap(plane.buffer, plane.rowStride, plane.pixelStride, width, height, bitmap)
                return bitmap
            } finally {
                image.close()
            }
        } finally {
            reader.close()
            renderer.destroy()
        }
    }

    private fun copyPlaneToBitmap(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        bitmap: Bitmap,
    ) {
        val rowPadding = rowStride - pixelStride * width
        if (rowPadding == 0) {
            bitmap.copyPixelsFromBuffer(buffer)
            return
        }
        val rowBytes = ByteArray(pixelStride * width)
        val pixels = ByteArray(pixelStride * width * height)
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(rowBytes)
            System.arraycopy(rowBytes, 0, pixels, row * rowBytes.size, rowBytes.size)
        }
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
    }
}
