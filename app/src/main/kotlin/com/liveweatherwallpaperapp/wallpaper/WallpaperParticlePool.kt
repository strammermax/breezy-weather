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
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random

/**
 * Pre-allocated particle pool with a small sprite atlas for snow and hail (ACT-004).
 *
 * All per-particle state lives in fixed-size primitive arrays that are allocated exactly once. The
 * update and draw loops mutate those arrays in place and reuse a single [RectF] and the atlas
 * bitmaps, so no allocations happen per frame. Particles are distributed over
 * [WallpaperParticleTrajectory.DEPTH_BANDS] depth bands and their fall speed, radius, opacity and
 * drift come from [WallpaperParticleTrajectory], which makes hail faster and more compact than snow
 * and lets wind direction and speed bend the trajectories.
 *
 * The Canvas renderer keeps this as a fallback for pre-Android 13 devices; the AGSL path already
 * has its own layered snow/hail.
 */
internal class WallpaperParticlePool(
    private val kind: WallpaperParticleKind,
) {
    private val capacity = when (kind) {
        WallpaperParticleKind.SNOW -> SNOW_MAX_ACTIVE
        WallpaperParticleKind.HAIL -> HAIL_MAX_ACTIVE
    }
    private val minActive = when (kind) {
        WallpaperParticleKind.SNOW -> SNOW_MIN_ACTIVE
        WallpaperParticleKind.HAIL -> HAIL_MIN_ACTIVE
    }

    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val radius = FloatArray(capacity)
    private val alpha = FloatArray(capacity)
    private val swayAmplitude = FloatArray(capacity)
    private val swayPhase = FloatArray(capacity)
    private val spriteIndex = IntArray(capacity)

    private val random = Random()
    private val dst = RectF()

    private var width = 0
    private var height = 0
    private var initialized = false
    private var windComponent = 0f
    private var tiltSlope = 0f
    private var activeCount = 0
    private var elapsedSeconds = 0f

    private var spriteAtlas: Array<Bitmap>? = null

    /**
     * Configures the pool for the current surface and wind, (re)spawning the reserved buffer only
     * when the surface size changes or on first use. [effectiveLayers] is the adaptive quality
     * value and only changes how many of the reserved particles are drawn, never reallocating.
     */
    fun configure(
        width: Int,
        height: Int,
        windDirectionDegrees: Float,
        windFactor: Float,
        effectiveLayers: Float,
        tiltSlope: Float = 0f,
    ) {
        if (width <= 0 || height <= 0) return
        windComponent = WallpaperParticleTrajectory.horizontalWindComponent(windDirectionDegrees, windFactor)
        this.tiltSlope = if (tiltSlope.isFinite()) tiltSlope else 0f
        activeCount = WallpaperParticleTrajectory.activeParticleCount(kind, effectiveLayers, minActive, capacity)

        if (!initialized || width != this.width || height != this.height) {
            this.width = width
            this.height = height
            if (spriteAtlas == null) {
                spriteAtlas = buildSpriteAtlas(kind)
            }
            spawnAll()
            initialized = true
        } else {
            // Wind may have changed without a resize: refresh horizontal velocity in place.
            for (i in 0 until capacity) {
                val depth = depthFromRadius(radius[i])
                vx[i] = WallpaperParticleTrajectory.driftSpeed(kind, depth, windComponent) +
                    vy[i] * this.tiltSlope +
                    (random.nextFloat() - 0.5f) * HORIZONTAL_JITTER
            }
        }
    }

    fun update(deltaSeconds: Float) {
        if (!initialized || activeCount <= 0) return
        elapsedSeconds += deltaSeconds
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0 until activeCount) {
            val r = radius[i]
            y[i] += vy[i] * deltaSeconds
            val sway = if (swayAmplitude[i] != 0f) {
                kotlin.math.sin((elapsedSeconds + swayPhase[i]) * SWAY_FREQUENCY) * swayAmplitude[i]
            } else {
                0f
            }
            x[i] += (vx[i] + sway) * deltaSeconds

            if (y[i] - r > h) {
                y[i] = -r
                x[i] = random.nextFloat() * w
            }
            val span = w + r * 2f
            if (x[i] - r > w) {
                x[i] -= span
            } else if (x[i] + r < 0f) {
                x[i] += span
            }
        }
    }

    fun draw(canvas: Canvas, paint: Paint, contribution: Float) {
        val atlas = spriteAtlas ?: return
        if (!initialized || activeCount <= 0 || contribution <= 0f) return
        val previousColorFilter = paint.colorFilter
        paint.colorFilter = null
        paint.style = Paint.Style.FILL
        for (i in 0 until activeCount) {
            val r = radius[i]
            dst.set(x[i] - r, y[i] - r, x[i] + r, y[i] + r)
            paint.alpha = (alpha[i] * 255f * contribution).toInt().coerceIn(0, 255)
            canvas.drawBitmap(atlas[spriteIndex[i]], null, dst, paint)
        }
        paint.colorFilter = previousColorFilter
        paint.alpha = 255
    }

    private fun spawnAll() {
        val atlasSize = spriteAtlas?.size ?: 1
        for (i in 0 until capacity) {
            val band = random.nextInt(WallpaperParticleTrajectory.DEPTH_BANDS)
            val depth = WallpaperParticleTrajectory.depthForBand(band)
            x[i] = random.nextFloat() * width
            y[i] = random.nextFloat() * height
            vy[i] = WallpaperParticleTrajectory.fallSpeed(kind, depth) *
                (0.9f + random.nextFloat() * 0.2f)
            vx[i] = WallpaperParticleTrajectory.driftSpeed(kind, depth, windComponent) +
                vy[i] * tiltSlope +
                (random.nextFloat() - 0.5f) * HORIZONTAL_JITTER
            radius[i] = WallpaperParticleTrajectory.radius(kind, depth) *
                (0.85f + random.nextFloat() * 0.3f)
            alpha[i] = WallpaperParticleTrajectory.alpha(kind, depth)
            swayAmplitude[i] = WallpaperParticleTrajectory.swayAmplitude(kind, depth)
            swayPhase[i] = random.nextFloat() * 6.2832f
            spriteIndex[i] = random.nextInt(atlasSize)
        }
    }

    /** Recovers the approximate depth from a stored radius so wind refresh stays consistent. */
    private fun depthFromRadius(r: Float): Float {
        val base = WallpaperParticleTrajectory.radius(kind, 0f)
        val span = WallpaperParticleTrajectory.radius(kind, 1f) - base
        if (span == 0f) return 0f
        return ((r - base) / span).coerceIn(0f, 1f)
    }

    companion object {
        private const val SNOW_MIN_ACTIVE = 140
        private const val SNOW_MAX_ACTIVE = 240
        private const val HAIL_MIN_ACTIVE = 80
        private const val HAIL_MAX_ACTIVE = 160
        private const val HORIZONTAL_JITTER = 14f
        private const val SWAY_FREQUENCY = 1.4f
        private const val SPRITE_SIZE = 32

        /**
         * Builds the small per-kind sprite atlas once. Snow flakes are soft radial blobs in a few
         * variants; hail sprites are tighter, harder discs with a faint cold rim.
         */
        private fun buildSpriteAtlas(kind: WallpaperParticleKind): Array<Bitmap> {
            val variants = if (kind == WallpaperParticleKind.SNOW) 4 else 3
            return Array(variants) { variant ->
                val bitmap = Bitmap.createBitmap(SPRITE_SIZE, SPRITE_SIZE, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val center = SPRITE_SIZE / 2f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                when (kind) {
                    WallpaperParticleKind.SNOW -> {
                        val softness = 0.55f + variant * 0.12f
                        paint.shader = RadialGradient(
                            center,
                            center,
                            center,
                            intArrayOf(Color.WHITE, Color.argb(0, 255, 255, 255)),
                            floatArrayOf(softness.coerceIn(0.2f, 0.85f), 1f),
                            Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(center, center, center, paint)
                    }
                    WallpaperParticleKind.HAIL -> {
                        paint.color = Color.rgb(232, 244, 255)
                        canvas.drawCircle(center, center, center * 0.7f, paint)
                        paint.shader = null
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = SPRITE_SIZE * 0.06f
                        paint.color = Color.argb(150, 150, 180, 210)
                        canvas.drawCircle(center, center, center * 0.7f, paint)
                    }
                }
                bitmap
            }
        }
    }
}
