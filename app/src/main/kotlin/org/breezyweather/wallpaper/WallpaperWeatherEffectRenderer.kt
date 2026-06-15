/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import org.breezyweather.ui.theme.weatherView.WeatherView
import java.util.Random
import kotlin.math.min

/**
 * Weather overlay for Live Wallpaper.
 * Uses AGSL on Android 13+ and a custom Canvas fallback on older versions.
 * The shader is original and intentionally independent of Lively Weather shaders.
 */
internal class WallpaperWeatherEffectRenderer(
    private val weatherKind: Int,
    daylight: Float,
    private val cloudSpeedFactor: Float = 1f,
    private val cloudField: CloudFieldParams = CloudFieldFactory.cloudFieldParams(
        family = WallpaperSceneStateFactory.weatherFamily(weatherKind),
        cloudDensity = 0f,
        cloudDarkness = 0f,
        windFactor = cloudSpeedFactor,
        windDirectionDegrees = null,
    ),
    private val fogField: FogFieldParams = FogFieldFactory.fogFieldParams(
        fogIntensity = 0f,
        hazeIntensity = 0f,
        windFactor = cloudSpeedFactor,
        windDirectionDegrees = null,
    ),
    private val starField: StarFieldParams = StarFieldFactory.starFieldParams(locationSeed = 0L),
    private val glassRainIntensity: Float = 0f,
    private val qualityProfile: WallpaperQualityProfile = WallpaperQualityProfile.BALANCED,
) {
    private var daylight = daylight.coerceIn(0f, 1f)
    private val daytime: Boolean
        get() = daylight >= 0.5f
    private var shader: RuntimeShader? = null
    private var shaderPaint: Paint? = null
    private var canvasRenderer: CanvasRenderer? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val s = RuntimeShader(SHADER_SOURCE)
                shader = s
                shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = s as Shader
                }
            } catch (error: Throwable) {
                Log.w(LOG_TAG, "Weather RuntimeShader unavailable; using Canvas fallback", error)
                canvasRenderer = CanvasRenderer(weatherKind, this.daylight, cloudSpeedFactor, cloudField, fogField, starField, glassRainIntensity)
            }
        } else {
            canvasRenderer = CanvasRenderer(weatherKind, this.daylight, cloudSpeedFactor, cloudField, fogField, starField, glassRainIntensity)
        }
    }

    private var elapsedSeconds = 0f
    private var precipitationLayerCount = DEFAULT_PRECIPITATION_LAYERS
    private val degradationTracker = QualityDegradationTracker(qualityProfile)
    private var qualityBudget = WallpaperQualityProfileFactory.budgetFor(qualityProfile)
    private val precipitationLayerCap: Float
        get() = when (qualityBudget.maxSnowParticles) {
            WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.BATTERY_SAVER).maxSnowParticles -> 14f
            WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.HIGH).maxSnowParticles -> MAX_PRECIPITATION_LAYERS
            else -> 17f
        }
    private val glassRainProfile: GlassRainProfile
        get() {
            val profile = GlassRainFieldFactory.profileFor(precipitationLayerCount)
            return if (profile.maxDrops > qualityBudget.maxGlassDrops) {
                profile.copy(maxDrops = qualityBudget.maxGlassDrops)
            } else {
                profile
            }
        }
    private var averageFrameMillis = TARGET_FRAME_MILLIS
    private var qualityEvaluationMillis = 0L
    private var stableFrameMillis = 0L
    private var canvasUpdateAccumulatorMillis = 0L

    fun setDaylight(daylight: Float) {
        this.daylight = daylight.coerceIn(0f, 1f)
        canvasRenderer?.daylight = this.daylight
    }

    /** Resets temporary quality degradation, e.g. when the wallpaper becomes visible again. */
    fun resetQualityDegradation() {
        degradationTracker.reset()
        qualityBudget = WallpaperQualityProfileFactory.budgetFor(degradationTracker.effectiveProfile)
    }

    fun update(intervalMillis: Long, animate: Boolean) {
        if (!animate) return
        val delta = min(intervalMillis, MAX_FRAME_INTERVAL_MILLIS).coerceAtLeast(0L)
        elapsedSeconds += delta / 1000f
        qualityBudget = WallpaperQualityProfileFactory.budgetFor(degradationTracker.recordFrame(delta.toFloat()))
        updateAdaptivePrecipitationQuality(intervalMillis)

        canvasUpdateAccumulatorMillis += delta
        val updateIntervalMillis = 1000L / qualityBudget.effectUpdateHz
        if (canvasUpdateAccumulatorMillis >= updateIntervalMillis) {
            canvasRenderer?.update(canvasUpdateAccumulatorMillis, precipitationLayerCount, qualityBudget)
            canvasUpdateAccumulatorMillis = 0L
        }
    }

    private fun updateAdaptivePrecipitationQuality(frameMillis: Long) {
        if (weatherKind != WeatherView.WEATHER_KIND_SNOW && weatherKind != WeatherView.WEATHER_KIND_HAIL) return
        if (frameMillis !in 1..MAX_FRAME_INTERVAL_MILLIS) return
        averageFrameMillis += (frameMillis - averageFrameMillis) * 0.08f
        qualityEvaluationMillis += frameMillis
        stableFrameMillis = if (averageFrameMillis <= STABLE_FRAME_MILLIS) {
            stableFrameMillis + frameMillis
        } else {
            0L
        }
        if (qualityEvaluationMillis >= QUALITY_EVALUATION_MILLIS) {
            if (averageFrameMillis >= SLOW_FRAME_MILLIS) {
                precipitationLayerCount = (precipitationLayerCount - 2f).coerceAtLeast(MIN_PRECIPITATION_LAYERS)
                stableFrameMillis = 0L
            } else if (stableFrameMillis >= QUALITY_INCREASE_MILLIS) {
                precipitationLayerCount = (precipitationLayerCount + 1f)
                    .coerceAtMost(MAX_PRECIPITATION_LAYERS)
                    .coerceAtMost(precipitationLayerCap)
                stableFrameMillis = 0L
            }
            qualityEvaluationMillis = 0L
        }
        precipitationLayerCount = precipitationLayerCount.coerceAtMost(precipitationLayerCap)
    }

    fun drawBackgroundWeatherPass(canvas: Canvas, alpha: Float = 1f) = draw(canvas, WEATHER_PASS_BACKGROUND, alpha)

    fun drawForegroundWeatherPass(canvas: Canvas, alpha: Float = 1f) = draw(canvas, WEATHER_PASS_FOREGROUND, alpha)

    fun drawGlassRainDrops(canvas: Canvas, alpha: Float = 1f) = draw(canvas, WEATHER_PASS_GLASS, alpha)

    private fun draw(canvas: Canvas, pass: Float, alpha: Float = 1f) {
        if (canvas.width <= 0 || canvas.height <= 0) return
        if (alpha <= 0f) return

        val s = shader
        val sp = shaderPaint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && s != null && sp != null) {
            s.setFloatUniform("resolution", canvas.width.toFloat(), canvas.height.toFloat())
            s.setFloatUniform("time", elapsedSeconds)
            s.setFloatUniform("mode", shaderMode(weatherKind))
            s.setFloatUniform("daylight", daylight)
            s.setFloatUniform("windFactor", cloudSpeedFactor)
            s.setFloatUniform("weatherPass", pass)
            s.setFloatUniform("precipitationLayers", precipitationLayerCount)
            s.setFloatUniform("transitionAlpha", alpha.coerceIn(0f, 1f))
            // ACT-007: layers/bands at or beyond the active quality budget contribute
            // zero alpha, so they fall out of the cloud/fog blends below without any
            // other shader changes.
            s.setFloatUniform("layerCount", cloudField.layers.count { it.alpha > 0f }.toFloat().coerceAtMost(qualityBudget.cloudLayers.toFloat()))
            s.setFloatUniform("layerScale", FloatArray(3) { cloudField.layers[it].scale })
            s.setFloatUniform("layerSpeed", FloatArray(3) { cloudField.layers[it].speedFactor })
            s.setFloatUniform("layerAlpha", FloatArray(3) { if (it < qualityBudget.cloudLayers) cloudField.layers[it].alpha else 0f })
            s.setFloatUniform("layerDarkness", FloatArray(3) { cloudField.layers[it].darkness })
            s.setFloatUniform("layerVerticalOffset", FloatArray(3) { cloudField.layers[it].verticalOffset })
            s.setFloatUniform("windDirection", cloudField.directionDegrees)
            s.setFloatUniform("fogBandCount", fogField.bands.count { it.baseAlpha > 0f }.toFloat().coerceAtMost(qualityBudget.fogBands.toFloat()))
            s.setFloatUniform("fogVerticalCenter", FloatArray(4) { fogField.bands[it].verticalCenter })
            s.setFloatUniform("fogHeight", FloatArray(4) { fogField.bands[it].height })
            s.setFloatUniform("fogBandAlpha", FloatArray(4) { if (it < qualityBudget.fogBands) fogField.bands[it].baseAlpha * qualityBudget.blurStrength else 0f })
            s.setFloatUniform("fogSpeed", FloatArray(4) { fogField.bands[it].speedFactor })
            s.setFloatUniform("fogColor", FogFieldFactory.fogColor(fogField.isHaze, daylight))
            s.setFloatUniform("starVisibility", StarFieldFactory.starVisibility(daylight))
            s.setFloatUniform("starSeed", starField.seed)
            s.setFloatUniform("glassRainIntensity", glassRainIntensity)
            s.setFloatUniform("glassTrailLength", glassRainProfile.trailLength)
            s.setFloatUniform("glassHighlightStrength", glassRainProfile.highlightStrength)
            s.setFloatUniform("glassRefractionStrength", glassRainProfile.refractionStrength)
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), sp)
        } else {
            if (pass == WEATHER_PASS_BACKGROUND) {
                canvasRenderer?.drawClouds(canvas, alpha)
            } else if (pass == WEATHER_PASS_FOREGROUND) {
                canvasRenderer?.drawForegroundEffects(canvas, alpha)
            } else {
                canvasRenderer?.drawGlassRainDrops(canvas, alpha)
            }
        }
    }

    private class CanvasRenderer(
        val weatherKind: Int,
        var daylight: Float,
        val cloudSpeedFactor: Float,
        val cloudField: CloudFieldParams,
        val fogField: FogFieldParams,
        val starField: StarFieldParams,
        val glassRainIntensity: Float,
    ) {
        val daytime: Boolean
            get() = daylight >= 0.5f
        private val random = Random()
        private val particles = mutableListOf<Particle>()
        private val clouds = mutableListOf<CloudParticle>()
        private val screenDrops = mutableListOf<ScreenDrop>()
        private var qualityBudget = WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.BALANCED)
        private var glassRainProfile = GlassRainFieldFactory.BALANCED
        private var lightningAlpha = 0f
        private var fogElapsedSeconds = 0f
        private var starElapsedSeconds = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var lastWidth = 0
        private var lastHeight = 0
        private val snowHailPool: WallpaperParticlePool? = when (weatherKind) {
            WeatherView.WEATHER_KIND_SNOW -> WallpaperParticlePool(WallpaperParticleKind.SNOW)
            WeatherView.WEATHER_KIND_HAIL -> WallpaperParticlePool(WallpaperParticleKind.HAIL)
            else -> null
        }

        fun update(deltaMillis: Long, effectiveLayers: Float, budget: QualityBudget) {
            qualityBudget = budget
            val deltaSec = deltaMillis / 1000f
            fogElapsedSeconds += deltaSec
            starElapsedSeconds += deltaSec

            if (lastWidth <= 0 || lastHeight <= 0) return

            // Initial cloud mass layers (ACT-003): a few blobs per visible layer, sized and
            // positioned from that layer's CloudFieldParams. Capped to qualityBudget.cloudLayers
            // (ACT-007); the remaining layers are simply never added.
            if (clouds.isEmpty()) {
                cloudField.layers.take(qualityBudget.cloudLayers).forEach { layer ->
                    if (layer.alpha <= 0f) return@forEach
                    val count = 2 + (layer.depth * 2f).toInt()
                    repeat(count) {
                        clouds.add(CloudParticle(random, lastWidth, lastHeight, layer))
                    }
                }
            }

            // Snow and hail use the pre-allocated particle pool (ACT-004); rain/sleet keep the
            // lightweight streak particles below.
            val pool = snowHailPool
            if (pool != null) {
                pool.configure(
                    lastWidth,
                    lastHeight,
                    cloudField.directionDegrees,
                    cloudSpeedFactor,
                    effectiveLayers,
                )
                pool.update(deltaSec)
            } else {
                if (particles.isEmpty()) {
                    spawnInitialParticles()
                }
                val it = particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    p.y += p.speedY * deltaSec
                    p.x += p.speedX * deltaSec
                    if (p.y > lastHeight + 100) {
                        p.y = -100f
                        p.x = random.nextFloat() * lastWidth
                    }
                    if (p.x > lastWidth + 100f) p.x = -100f
                    if (p.x < -100f) p.x = lastWidth + 100f
                }
            }

            // Update cloud mass layers: deeper (back) layers move slower for parallax.
            for (c in clouds) {
                c.x += c.speedX * deltaSec
                if (c.x > lastWidth + c.radius) {
                    c.x = -c.radius
                } else if (c.x < -c.radius) {
                    c.x = lastWidth + c.radius
                }
            }

            glassRainProfile = GlassRainFieldFactory.profileFor(effectiveLayers)
            for (drop in screenDrops) {
                drop.update(deltaSec, lastHeight, glassRainProfile.trailLength)
            }

            // Update lightning
            if (weatherKind == WeatherView.WEATHER_KIND_THUNDER || weatherKind == WeatherView.WEATHER_KIND_THUNDERSTORM) {
                if (lightningAlpha > 0) {
                    lightningAlpha -= deltaSec * 2f
                } else if (random.nextFloat() < 0.005f) {
                    lightningAlpha = 0.8f
                }
            }
        }

        private fun lerpInt(from: Int, to: Int, t: Float): Int =
            (from + (to - from) * t.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)

        private fun spawnInitialParticles() {
            val count = when (weatherKind) {
                WeatherView.WEATHER_KIND_RAINY, WeatherView.WEATHER_KIND_THUNDERSTORM -> 80
                WeatherView.WEATHER_KIND_SLEET -> 60
                WeatherView.WEATHER_KIND_SNOW -> 100
                WeatherView.WEATHER_KIND_HAIL -> 55
                else -> 0
            }
            for (i in 0 until count) {
                particles.add(Particle(random, lastWidth, lastHeight, true))
            }
        }

        fun drawClouds(canvas: Canvas, contribution: Float = 1f) {
            lastWidth = canvas.width
            lastHeight = canvas.height

            drawStars(canvas, contribution)

            if (weatherKind == WeatherView.WEATHER_KIND_FOG || weatherKind == WeatherView.WEATHER_KIND_HAZE) {
                val rgb = FogFieldFactory.fogColor(fogField.isHaze, daylight)
                paint.color = Color.rgb(
                    (rgb[0] * 255f).toInt().coerceIn(0, 255),
                    (rgb[1] * 255f).toInt().coerceIn(0, 255),
                    (rgb[2] * 255f).toInt().coerceIn(0, 255),
                )
                val dirSign = if (kotlin.math.cos(Math.toRadians(fogField.directionDegrees.toDouble())) >= 0) 1f else -1f
                // ACT-007: cap visible bands at qualityBudget.fogBands and scale their
                // opacity by blurStrength (lower in Battery saver).
                fogField.bands.take(qualityBudget.fogBands).forEach { band ->
                    if (band.baseAlpha <= 0f) return@forEach
                    paint.alpha = (band.baseAlpha * qualityBudget.blurStrength * 255f * contribution).toInt().coerceIn(0, 255)
                    val centerY = band.verticalCenter * lastHeight
                    val halfHeight = band.height * lastHeight * 0.5f
                    val driftX = (fogElapsedSeconds * band.speedFactor * cloudSpeedFactor * dirSign * lastWidth * 0.01f) %
                        (lastWidth * 0.18f)
                    canvas.drawOval(
                        -lastWidth * 0.18f + driftX,
                        centerY - halfHeight,
                        lastWidth * 1.18f + driftX,
                        centerY + halfHeight,
                        paint,
                    )
                }
                return
            }

            // Layered cloud masses (ACT-003): each layer's own alpha/darkness drives a
            // light-to-dark blend so Cloud/Cloudy/Rain/Thunderstorm/Wind read differently.
            for (c in clouds) {
                val layer = c.layer
                val light = if (daytime) Triple(255, 255, 255) else Triple(196, 202, 214)
                val dark = if (daytime) Triple(110, 128, 150) else Triple(58, 70, 92)
                val darkness = layer.darkness
                paint.color = Color.rgb(
                    lerpInt(light.first, dark.first, darkness),
                    lerpInt(light.second, dark.second, darkness),
                    lerpInt(light.third, dark.third, darkness),
                )
                paint.alpha = (layer.alpha * 255 * contribution).toInt().coerceIn(0, 255)
                canvas.drawCircle(c.x - c.radius * 0.52f, c.y + c.radius * 0.08f, c.radius * 0.48f, paint)
                canvas.drawCircle(c.x - c.radius * 0.12f, c.y - c.radius * 0.18f, c.radius * 0.62f, paint)
                canvas.drawCircle(c.x + c.radius * 0.38f, c.y - c.radius * 0.06f, c.radius * 0.52f, paint)
                canvas.drawOval(
                    c.x - c.radius,
                    c.y,
                    c.x + c.radius,
                    c.y + c.radius * 0.58f,
                    paint,
                )
            }
        }

        private fun drawStars(canvas: Canvas, contribution: Float) {
            val visibility = StarFieldFactory.starVisibility(daylight)
            if (visibility <= 0f) return
            for (star in starField.stars) {
                val twinkle = 0.65f + 0.35f * kotlin.math.sin(starElapsedSeconds * star.twinkleSpeed + star.twinklePhase)
                val alpha = star.brightness * twinkle * visibility * contribution
                if (alpha <= 0f) continue
                paint.color = Color.WHITE
                paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(star.x * lastWidth, star.y * lastHeight, star.size * (lastWidth / 400f), paint)
            }
        }

        fun drawForegroundEffects(canvas: Canvas, contribution: Float = 1f) {
            lastWidth = canvas.width
            lastHeight = canvas.height

            // Draw lightning flash
            if (lightningAlpha > 0) {
                paint.color = Color.WHITE
                paint.alpha = (lightningAlpha * 255 * contribution).toInt()
                canvas.drawRect(0f, 0f, lastWidth.toFloat(), lastHeight.toFloat(), paint)
            }

            // Snow and hail come from the pre-allocated pool; rain/sleet stay as streaks.
            val pool = snowHailPool
            if (pool != null) {
                pool.draw(canvas, paint, contribution)
            } else {
                for (p in particles) {
                    paint.color = if (daytime) 0xAAFFFFFF.toInt() else 0x77FFFFFF.toInt()
                    paint.alpha = (p.alpha * 255 * contribution).toInt()
                    paint.strokeWidth = p.size
                    canvas.drawLine(p.x, p.y, p.x + p.speedX * 0.02f, p.y + p.speedY * 0.02f, paint)
                }
            }

        }

        fun drawGlassRainDrops(canvas: Canvas, contribution: Float = 1f) {
            lastWidth = canvas.width
            lastHeight = canvas.height
            if (glassRainIntensity <= 0f) return
            if (weatherKind == WeatherView.WEATHER_KIND_RAINY ||
                weatherKind == WeatherView.WEATHER_KIND_THUNDER ||
                weatherKind == WeatherView.WEATHER_KIND_THUNDERSTORM ||
                weatherKind == WeatherView.WEATHER_KIND_SLEET
            ) {
                if (screenDrops.isEmpty()) {
                    val staticRatio = GlassRainFieldFactory.staticRatio(glassRainIntensity)
                    // ACT-007: never exceed the active quality budget's drop count.
                    val dropCount = glassRainProfile.maxDrops.coerceAtMost(qualityBudget.maxGlassDrops)
                    repeat(dropCount) { index ->
                        val isSliding = index >= dropCount * staticRatio
                        screenDrops.add(ScreenDrop(random, lastWidth, lastHeight, isSliding))
                    }
                }
                val effectiveAlpha = glassRainIntensity * contribution
                val highlightStrength = glassRainProfile.highlightStrength
                val refractionStrength = glassRainProfile.refractionStrength
                paint.strokeWidth = 2f
                for (drop in screenDrops) {
                    if (drop.isSliding && drop.trailLength > 1f) {
                        paint.style = Paint.Style.STROKE
                        paint.color = Color.rgb(220, 236, 255)
                        paint.alpha = (70 * highlightStrength * effectiveAlpha).toInt().coerceIn(0, 255)
                        canvas.drawLine(drop.x, drop.y - drop.radius, drop.x, drop.y - drop.radius - drop.trailLength, paint)
                    }
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(24, 38, 58)
                    paint.alpha = (78 * effectiveAlpha).toInt().coerceIn(0, 255)
                    canvas.drawArc(drop.bounds, 12f, 156f, false, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.rgb(154, 200, 232)
                    paint.alpha = (60 * refractionStrength * effectiveAlpha).toInt().coerceIn(0, 255)
                    canvas.drawOval(drop.lensRing, paint)
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.WHITE
                    paint.alpha = (46 * effectiveAlpha).toInt().coerceIn(0, 255)
                    canvas.drawOval(drop.bounds, paint)
                    paint.alpha = (115 * highlightStrength * effectiveAlpha).toInt().coerceIn(0, 255)
                    canvas.drawArc(drop.highlight, 195f, 82f, false, paint)
                }
                paint.style = Paint.Style.FILL
            }
        }

        private inner class Particle(r: Random, w: Int, h: Int, randomizeY: Boolean) {
            var x = r.nextFloat() * w
            var y = if (randomizeY) r.nextFloat() * h else -10f
            val layer = if (weatherKind == WeatherView.WEATHER_KIND_SNOW) r.nextInt(4) else r.nextInt(3)
            val alpha = when (layer) {
                0 -> 0.22f
                1 -> 0.42f
                2 -> 0.68f
                else -> 0.92f
            }
            val size = when (weatherKind) {
                WeatherView.WEATHER_KIND_SNOW -> (1.5f + layer * 2.4f)
                WeatherView.WEATHER_KIND_HAIL -> (2.5f + layer * 2f)
                else -> (1f + layer * 1f) // Rain thickness
            }
            val speedY = when (weatherKind) {
                WeatherView.WEATHER_KIND_SNOW -> (28f + layer * 42f)
                WeatherView.WEATHER_KIND_HAIL -> (260f + layer * 130f)
                else -> (800f + layer * 400f) // Rain speed
            }
            val speedX = when (weatherKind) {
                WeatherView.WEATHER_KIND_SNOW ->
                    (r.nextFloat() - 0.5f) * (18f + layer * 8f) +
                        cloudSpeedFactor * (10f + layer * 8f)
                WeatherView.WEATHER_KIND_HAIL -> cloudSpeedFactor * (20f + layer * 12f)
                else -> 20f // Slight slant for rain
            }
        }

        private class CloudParticle(r: Random, w: Int, h: Int, val layer: CloudLayer) {
            var x = r.nextFloat() * w
            var y = (0.18f + layer.depth * 0.10f + layer.verticalOffset + r.nextFloat() * 0.20f) * h
            val radius = w * (0.06f * layer.scale) + r.nextFloat() * w * (0.07f * layer.scale)
            val speedX = (8f + r.nextFloat() * 16f) * layer.speedFactor
        }

        /**
         * ACT-006: static drops jitter slightly in place; sliding drops fall, accelerate
         * and grow a trail above them that is cleared on recycle.
         */
        private class ScreenDrop(r: Random, w: Int, h: Int, val isSliding: Boolean) {
            val radius = 5f + r.nextFloat() * 13f
            var x = radius + r.nextFloat() * (w - radius * 2f).coerceAtLeast(1f)
            var y = radius + r.nextFloat() * (h - radius * 2f).coerceAtLeast(1f)
            private var speedY = if (isSliding) 7f + r.nextFloat() * 16f else 0f
            private val jitterPhase = r.nextFloat() * (2f * Math.PI).toFloat()
            private val jitterSpeed = 0.4f + r.nextFloat() * 0.6f
            private val baseX = x
            private var elapsedSeconds = 0f
            var trailLength = 0f
            val bounds = RectF()
            val highlight = RectF()
            val lensRing = RectF()

            init {
                updateBounds()
            }

            fun update(deltaSeconds: Float, height: Int, trailLengthFactor: Float) {
                elapsedSeconds += deltaSeconds
                if (isSliding) {
                    speedY += deltaSeconds * 6f
                    y += speedY * deltaSeconds
                    trailLength = (trailLength + speedY * deltaSeconds)
                        .coerceAtMost(radius * 3f * trailLengthFactor)
                    if (y - radius - trailLength > height) {
                        y = -radius
                        speedY = 7f
                        trailLength = 0f
                    }
                } else {
                    x = baseX + kotlin.math.sin(elapsedSeconds * jitterSpeed + jitterPhase) * radius * 0.06f
                }
                updateBounds()
            }

            private fun updateBounds() {
                bounds.set(x - radius * 0.72f, y - radius, x + radius * 0.72f, y + radius)
                highlight.set(x - radius * 0.48f, y - radius * 0.72f, x + radius * 0.48f, y + radius * 0.50f)
                lensRing.set(x - radius * 0.40f, y - radius * 0.40f, x + radius * 0.40f, y + radius * 0.40f)
            }
        }
    }

    companion object {
        private const val MAX_FRAME_INTERVAL_MILLIS = 100L
        private const val TARGET_FRAME_MILLIS = 1000f / 30f
        private const val STABLE_FRAME_MILLIS = 34.5f
        private const val SLOW_FRAME_MILLIS = 37.5f
        private const val QUALITY_EVALUATION_MILLIS = 3_000L
        private const val QUALITY_INCREASE_MILLIS = 10_000L
        private const val MIN_PRECIPITATION_LAYERS = 10f
        private const val DEFAULT_PRECIPITATION_LAYERS = 16f
        private const val MAX_PRECIPITATION_LAYERS = 20f
        private const val LOG_TAG = "LWW"
        private const val WEATHER_PASS_BACKGROUND = 0f
        private const val WEATHER_PASS_FOREGROUND = 1f
        private const val WEATHER_PASS_GLASS = 2f

        fun supports(weatherKind: Int): Boolean = weatherKind in setOf(
            WeatherView.WEATHER_KIND_CLEAR,
            WeatherView.WEATHER_KIND_RAINY,
            WeatherView.WEATHER_KIND_SLEET,
            WeatherView.WEATHER_KIND_SNOW,
            WeatherView.WEATHER_KIND_FOG,
            WeatherView.WEATHER_KIND_HAZE,
            WeatherView.WEATHER_KIND_THUNDER,
            WeatherView.WEATHER_KIND_THUNDERSTORM,
            WeatherView.WEATHER_KIND_HAIL,
            WeatherView.WEATHER_KIND_CLOUD,
            WeatherView.WEATHER_KIND_CLOUDY,
            WeatherView.WEATHER_KIND_WIND,
        )

        private fun shaderMode(weatherKind: Int): Float = when (weatherKind) {
            WeatherView.WEATHER_KIND_RAINY -> 1f
            WeatherView.WEATHER_KIND_SNOW -> 2f
            WeatherView.WEATHER_KIND_FOG -> 3f
            WeatherView.WEATHER_KIND_THUNDER, WeatherView.WEATHER_KIND_THUNDERSTORM -> 4f
            WeatherView.WEATHER_KIND_SLEET -> 5f
            WeatherView.WEATHER_KIND_CLOUD -> 6f
            WeatherView.WEATHER_KIND_CLOUDY -> 7f
            WeatherView.WEATHER_KIND_WIND -> 8f
            WeatherView.WEATHER_KIND_HAIL -> 9f
            WeatherView.WEATHER_KIND_HAZE -> 10f
            else -> 0f
        }

        private const val SHADER_SOURCE = """
            uniform float2 resolution;
            uniform float time;
            uniform float mode;
            uniform float daylight;
            uniform float windFactor;
            uniform float weatherPass;
            uniform float precipitationLayers;
            uniform float transitionAlpha;
            uniform float layerCount;
            uniform float layerScale[3];
            uniform float layerSpeed[3];
            uniform float layerAlpha[3];
            uniform float layerDarkness[3];
            uniform float layerVerticalOffset[3];
            uniform float windDirection;
            uniform float fogBandCount;
            uniform float fogVerticalCenter[4];
            uniform float fogHeight[4];
            uniform float fogBandAlpha[4];
            uniform float fogSpeed[4];
            uniform float fogColor[3];
            uniform float starVisibility;
            uniform float starSeed;
            uniform float glassRainIntensity;
            uniform float glassTrailLength;
            uniform float glassHighlightStrength;
            uniform float glassRefractionStrength;

            float hash21(float2 p) {
                p = fract(p * float2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float noise21(float2 p) {
                float2 i = floor(p);
                float2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(
                    mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x),
                    mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), f.x),
                    f.y
                );
            }

            float rainLayer(float2 uv, float scale, float speed, float seed) {
                float2 p = uv * float2(scale, scale * 0.72);
                p.x += p.y * 0.16;
                float2 cell = floor(p);
                float2 local = fract(p) - 0.5;
                float random = hash21(cell + seed);
                local.y = fract(local.y - time * speed + random) - 0.5;
                local.x += (random - 0.5) * 0.65;
                float streak = smoothstep(0.045, 0.0, abs(local.x))
                    * smoothstep(0.48, 0.08, abs(local.y));
                return streak * smoothstep(0.28, 0.96, random);
            }

            // ACT-006: returns (highlight, shadow, refraction). trailLength scales how far
            // the drop's trail reaches (0 = static, no trail), refractionStrength scales a
            // lensing ring that approximates background light bending through the drop.
            float3 glassDropLayer(float2 uv, float scale, float speed, float seed, float trailLength, float refractionStrength) {
                float2 p = uv * float2(scale, scale * 1.18);
                float2 cell = floor(p);
                float random = hash21(cell + seed);
                float2 local = fract(p) - 0.5;
                local.y = fract(local.y - time * speed * mix(0.18, 1.0, random)) - 0.5;
                float windSkew = sin(radians(windDirection)) * 0.10 * windFactor;
                local.x += (random - 0.5) * 0.46 + sin(local.y * 9.0 + seed) * 0.018 + windSkew * local.y;

                float size = mix(0.045, 0.13, hash21(cell + seed + 17.0));
                float2 dropPoint = local;
                dropPoint.y *= mix(0.72, 0.46, random);
                dropPoint.x *= 1.12;
                float distanceFromDrop = length(dropPoint);
                float body = smoothstep(size, size * 0.72, distanceFromDrop);
                float rim = smoothstep(size * 1.07, size * 0.82, distanceFromDrop)
                    - smoothstep(size * 0.76, size * 0.48, distanceFromDrop);

                float trailWidth = mix(0.012, 0.034, random);
                float trailReach = mix(size * 0.42, 0.42, trailLength);
                float trail = smoothstep(trailWidth, 0.0, abs(local.x))
                    * smoothstep(trailReach, size * 0.38, local.y)
                    * smoothstep(-0.50, -0.18, local.y)
                    * trailLength;
                float topLight = smoothstep(size * 0.42, 0.0,
                    length(dropPoint - float2(-size * 0.30, -size * 0.32)));
                float bottomShade = smoothstep(size * 0.34, 0.0,
                    length(dropPoint - float2(size * 0.16, size * 0.42)));
                float visible = smoothstep(0.70, 0.98, random);
                float highlight = (rim * 0.30 + topLight * 0.42 + trail * 0.10) * visible;
                float shadow = (body * 0.08 + bottomShade * 0.30 + trail * 0.12) * visible;
                // Bright lensing ring inside the drop body, approximating refraction of the
                // scene behind the glass.
                float lensRing = smoothstep(size * 0.92, size * 0.60, distanceFromDrop)
                    - smoothstep(size * 0.55, size * 0.30, distanceFromDrop);
                float refraction = lensRing * body * visible * refractionStrength;
                return float3(highlight, shadow, refraction);
            }

            float hailLayer(float2 uv, float scale, float speed, float seed) {
                float2 p = uv * scale;
                p.x -= time * 0.08 * windFactor;
                p.y -= time * speed;
                float2 cell = floor(p);
                float2 local = fract(p) - 0.5;
                float random = hash21(cell + seed);
                local.x += (random - 0.5) * 0.58;
                float radius = mix(0.035, 0.080, random);
                return smoothstep(radius, radius * 0.68, length(local)) * smoothstep(0.42, 0.96, random);
            }

            float snowLayer(float2 uv, float scale, float speed, float drift, float seed) {
                float2 p = uv * scale;
                p.x -= time * drift * windFactor;
                p.x += sin(time * mix(0.35, 0.72, drift) + p.y * 0.8 + seed) * (0.06 + drift * 0.06);
                p.y -= time * speed;
                float2 cell = floor(p);
                float2 local = fract(p) - 0.5;
                float random = hash21(cell + seed);
                local.x += (random - 0.5) * 0.72;
                local.y += (hash21(cell + seed + 19.0) - 0.5) * 0.36;
                float radius = mix(0.035, 0.095, random);
                float flake = smoothstep(radius, radius * 0.18, length(local));
                return flake * smoothstep(0.16, 0.92, random);
            }

            float fogLayer(float2 uv) {
                float nearBank = noise21(float2(uv.x * 1.8 + time * 0.020, uv.y * 5.0)) * 0.62;
                nearBank += noise21(float2(uv.x * 4.1 - time * 0.012, uv.y * 8.0) + 7.3) * 0.38;
                float midBank = noise21(float2(uv.x * 2.6 - time * 0.014, uv.y * 6.5) - 3.7);
                float nearMask = smoothstep(0.26, 0.50, uv.y) * (1.0 - smoothstep(0.72, 1.08, uv.y));
                float midMask = smoothstep(0.12, 0.36, uv.y) * (1.0 - smoothstep(0.56, 0.88, uv.y));
                return smoothstep(0.30, 0.76, nearBank) * nearMask +
                    smoothstep(0.38, 0.78, midBank) * midMask * 0.64;
            }


            // ACT-005: mist/haze as horizontal depth bands. Lower bands (closer to
            // verticalCenter = 1, the horizon) are denser, taller and slower; higher
            // bands are thinner, lighter and drift slightly faster.
            float fogHazeBands(float2 uv, float2 aspectUv) {
                float dirSign = cos(radians(windDirection)) >= 0.0 ? 1.0 : -1.0;
                float result = 0.0;
                for (int i = 0; i < 4; i++) {
                    if (fogBandAlpha[i] <= 0.0) continue;
                    float drift = aspectUv.x + time * fogSpeed[i] * windFactor * dirSign * 0.02;
                    float n = noise21(float2(drift * 2.2, uv.y * 4.0 + float(i) * 13.0));
                    float halfHeight = fogHeight[i] * 0.5;
                    float band = smoothstep(fogVerticalCenter[i] - halfHeight, fogVerticalCenter[i] - halfHeight * 0.3, uv.y)
                        * (1.0 - smoothstep(fogVerticalCenter[i] + halfHeight * 0.3, fogVerticalCenter[i] + halfHeight, uv.y));
                    result += band * (0.55 + n * 0.45) * fogBandAlpha[i];
                }
                return result;
            }

            // ACT-014: procedural (not astronomically-accurate) star field, three layers of
            // grid-based point stars with independent density/size/twinkle.
            float starLayer(float2 uv, float density, float threshold, float seed) {
                float2 p = uv * density;
                float2 cell = floor(p);
                float2 local = fract(p) - 0.5;
                float h = hash21(cell + seed);
                if (h < threshold) return 0.0;
                float size = mix(0.05, 0.16, hash21(cell + seed + 31.0));
                float star = smoothstep(size, 0.0, length(local));
                float twinklePhase = hash21(cell + seed + 53.0) * 6.2831853;
                float twinkleSpeed = mix(0.5, 2.0, hash21(cell + seed + 71.0));
                float twinkle = 0.6 + 0.4 * sin(time * twinkleSpeed + twinklePhase);
                float brightness = mix(0.4, 1.0, hash21(cell + seed + 97.0));
                return star * brightness * twinkle;
            }

            float starField(float2 auv, float seed) {
                float stars = 0.0;
                stars += starLayer(auv, 60.0, 0.985, seed + 1.0) * 1.0;
                stars += starLayer(auv, 35.0, 0.97, seed + 17.0) * 0.7;
                stars += starLayer(auv, 18.0, 0.95, seed + 41.0) * 0.45;
                return clamp(stars, 0.0, 1.0);
            }

            float cloudCircle(float2 point, float2 center, float radius, float softness) {
                return smoothstep(radius + softness, radius - softness, length(point - center));
            }

            float cloudShape(float2 uv, float2 center, float size) {
                float2 p = (uv - center) / size;
                float cloud = cloudCircle(p, float2(-0.58, 0.08), 0.42, 0.12);
                cloud = max(cloud, cloudCircle(p, float2(-0.20, -0.18), 0.56, 0.13));
                cloud = max(cloud, cloudCircle(p, float2(0.25, -0.08), 0.68, 0.14));
                cloud = max(cloud, cloudCircle(p, float2(0.68, 0.12), 0.40, 0.12));
                cloud = max(cloud, smoothstep(0.38, 0.18, abs(p.y - 0.18))
                    * smoothstep(1.10, 0.72, abs(p.x)));
                return cloud;
            }

            float driftingCloud(float2 uv, float y, float size, float speed, float seed) {
                float travel = resolution.x / resolution.y + size * 2.7;
                float x = fract(seed + time * speed) * travel - size * 1.35;
                return cloudShape(uv, float2(x, y), size);
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;
                float aspect = resolution.x / resolution.y;
                float2 aspectUv = uv;
                aspectUv.x *= aspect;

                float alpha = 0.0;
                float3 color = float3(0.82, 0.91, 1.0);
                float glassHighlight = 0.0;
                float glassShadow = 0.0;
                float glassRefraction = 0.0;

                if (weatherPass == 1.0 && (mode == 1.0 || mode == 4.0 || mode == 5.0)) {
                    float rain = rainLayer(aspectUv, 18.0, 1.65, 1.0) * 0.24;
                    rain += rainLayer(aspectUv, 27.0, 2.15, 8.0) * 0.38;
                    rain += rainLayer(aspectUv, 38.0, 2.75, 19.0) * 0.52;
                    alpha += rain;
                }

                if (weatherPass == 2.0 && (mode == 1.0 || mode == 4.0 || mode == 5.0) && glassRainIntensity > 0.0) {
                    float3 largeDrops = glassDropLayer(aspectUv, 5.5, 0.026, 31.0, glassTrailLength, glassRefractionStrength);
                    float3 mediumDrops = glassDropLayer(aspectUv, 10.0, 0.016, 73.0, glassTrailLength, glassRefractionStrength);
                    float3 smallDrops = glassDropLayer(aspectUv, 18.0, 0.007, 117.0, glassTrailLength, glassRefractionStrength);
                    glassHighlight = clamp((largeDrops.x + mediumDrops.x * 0.78 + smallDrops.x * 0.46) * glassHighlightStrength, 0.0, 0.85);
                    glassShadow = clamp(largeDrops.y + mediumDrops.y * 0.82 + smallDrops.y * 0.50, 0.0, 0.58);
                    glassRefraction = clamp(largeDrops.z + mediumDrops.z * 0.78 + smallDrops.z * 0.46, 0.0, 0.5);
                    glassHighlight *= glassRainIntensity;
                    glassShadow *= glassRainIntensity;
                    glassRefraction *= glassRainIntensity;
                }

                if (weatherPass == 1.0 && (mode == 2.0 || mode == 5.0)) {
                    float snow = snowLayer(aspectUv, 48.0, 0.11, 0.045, 2.0) * 0.18;
                    snow += snowLayer(aspectUv, 42.0, 0.13, 0.055, 7.0) * 0.20;
                    snow += snowLayer(aspectUv, 36.0, 0.15, 0.070, 13.0) * 0.23;
                    snow += snowLayer(aspectUv, 30.0, 0.18, 0.090, 21.0) * 0.27;
                    snow += snowLayer(aspectUv, 25.0, 0.21, 0.115, 29.0) * 0.32;
                    snow += snowLayer(aspectUv, 20.0, 0.25, 0.145, 39.0) * 0.38;
                    snow += snowLayer(aspectUv, 16.0, 0.30, 0.185, 51.0) * 0.45;
                    snow += snowLayer(aspectUv, 12.0, 0.36, 0.235, 67.0) * 0.53;
                    snow += snowLayer(aspectUv, 8.5, 0.43, 0.300, 83.0) * 0.62;
                    snow += snowLayer(aspectUv, 6.0, 0.52, 0.390, 103.0) * 0.72;
                    if (precipitationLayers > 10.0) snow += snowLayer(aspectUv, 45.0, 0.12, 0.050, 113.0) * 0.18;
                    if (precipitationLayers > 11.0) snow += snowLayer(aspectUv, 39.0, 0.14, 0.062, 127.0) * 0.21;
                    if (precipitationLayers > 12.0) snow += snowLayer(aspectUv, 33.0, 0.17, 0.080, 139.0) * 0.24;
                    if (precipitationLayers > 13.0) snow += snowLayer(aspectUv, 28.0, 0.20, 0.102, 151.0) * 0.28;
                    if (precipitationLayers > 14.0) snow += snowLayer(aspectUv, 23.0, 0.23, 0.130, 167.0) * 0.33;
                    if (precipitationLayers > 15.0) snow += snowLayer(aspectUv, 18.0, 0.28, 0.165, 181.0) * 0.39;
                    if (precipitationLayers > 16.0) snow += snowLayer(aspectUv, 14.0, 0.33, 0.210, 197.0) * 0.46;
                    if (precipitationLayers > 17.0) snow += snowLayer(aspectUv, 10.5, 0.39, 0.265, 211.0) * 0.54;
                    if (precipitationLayers > 18.0) snow += snowLayer(aspectUv, 7.5, 0.46, 0.330, 227.0) * 0.63;
                    if (precipitationLayers > 19.0) snow += snowLayer(aspectUv, 5.3, 0.56, 0.420, 241.0) * 0.73;
                    alpha += snow;
                    color = float3(0.96, 0.98, 1.0);
                }

                if (weatherPass == 1.0 && mode == 4.0) {
                    float fog = fogLayer(aspectUv);
                    alpha += fog * 0.16;
                    color = mix(color, daylight > 0.5 ? float3(0.82, 0.89, 0.91) : float3(0.42, 0.50, 0.62), 0.58);
                }

                // ACT-005: fog and haze as horizontal depth bands, fading near the
                // horizon. Each band's alpha already accounts for fog/haze intensity,
                // so a fully clear scene (fogBandCount == 0) draws nothing here.
                if (weatherPass == 1.0 && (mode == 3.0 || mode == 10.0) && fogBandCount > 0.0) {
                    float bands = fogHazeBands(uv, aspectUv);
                    alpha += bands;
                    color = float3(fogColor[0], fogColor[1], fogColor[2]);
                }

                if (weatherPass == 1.0 && mode == 9.0) {
                    float hail = hailLayer(aspectUv, 42.0, 0.72, 9.0) * 0.20;
                    hail += hailLayer(aspectUv, 36.0, 0.86, 17.0) * 0.23;
                    hail += hailLayer(aspectUv, 31.0, 1.00, 29.0) * 0.27;
                    hail += hailLayer(aspectUv, 26.0, 1.18, 41.0) * 0.31;
                    hail += hailLayer(aspectUv, 22.0, 1.36, 53.0) * 0.36;
                    hail += hailLayer(aspectUv, 18.0, 1.58, 67.0) * 0.42;
                    hail += hailLayer(aspectUv, 15.0, 1.82, 79.0) * 0.49;
                    hail += hailLayer(aspectUv, 12.0, 2.08, 97.0) * 0.57;
                    hail += hailLayer(aspectUv, 9.5, 2.38, 109.0) * 0.66;
                    hail += hailLayer(aspectUv, 7.5, 2.72, 127.0) * 0.74;
                    if (precipitationLayers > 10.0) hail += hailLayer(aspectUv, 39.0, 0.79, 139.0) * 0.21;
                    if (precipitationLayers > 11.0) hail += hailLayer(aspectUv, 34.0, 0.93, 151.0) * 0.24;
                    if (precipitationLayers > 12.0) hail += hailLayer(aspectUv, 29.0, 1.09, 163.0) * 0.28;
                    if (precipitationLayers > 13.0) hail += hailLayer(aspectUv, 24.0, 1.27, 179.0) * 0.33;
                    if (precipitationLayers > 14.0) hail += hailLayer(aspectUv, 20.0, 1.47, 193.0) * 0.38;
                    if (precipitationLayers > 15.0) hail += hailLayer(aspectUv, 17.0, 1.70, 211.0) * 0.44;
                    if (precipitationLayers > 16.0) hail += hailLayer(aspectUv, 14.0, 1.96, 227.0) * 0.51;
                    if (precipitationLayers > 17.0) hail += hailLayer(aspectUv, 11.0, 2.24, 241.0) * 0.59;
                    if (precipitationLayers > 18.0) hail += hailLayer(aspectUv, 8.5, 2.56, 257.0) * 0.67;
                    if (precipitationLayers > 19.0) hail += hailLayer(aspectUv, 6.5, 2.92, 271.0) * 0.75;
                    alpha += hail;
                    color = float3(0.88, 0.94, 1.0);
                }

                // Layered cloud mass: back/mid/front layers with their own scale, speed,
                // alpha and darkness (ACT-003). A layer with alpha 0 contributes nothing,
                // so transitions between weather families never pop a layer in or out.
                if (weatherPass == 0.0) {
                    float clouds = 0.0;
                    float3 cloudColor = color;
                    if (layerCount > 0.0) {
                        float dirSign = cos(radians(windDirection)) >= 0.0 ? 1.0 : -1.0;
                        float darknessSum = 0.0;
                        float alphaSum = 0.0;
                        for (int i = 0; i < 3; i++) {
                            if (layerAlpha[i] <= 0.0) continue;
                            float y = 0.16 + float(i) * 0.13 + layerVerticalOffset[i];
                            float size = 0.16 + layerScale[i] * 0.14;
                            float speed = 0.005 * layerSpeed[i] * dirSign;
                            float seed = 0.08 + float(i) * 0.41;
                            float shape = driftingCloud(aspectUv, y, size, speed, seed) * layerAlpha[i];
                            clouds = max(clouds, shape);
                            darknessSum += layerDarkness[i] * shape;
                            alphaSum += shape;
                        }
                        float weightedDarkness = alphaSum > 0.0 ? darknessSum / alphaSum : 0.0;
                        float3 fairColor = daylight > 0.5 ? float3(0.94, 0.97, 1.0) : float3(0.48, 0.54, 0.64);
                        float3 stormColor = daylight > 0.5 ? float3(0.48, 0.55, 0.64) : float3(0.24, 0.29, 0.38);
                        cloudColor = mix(fairColor, stormColor, weightedDarkness);
                    }

                    // ACT-014: stars fade in at night and are occluded by clouds.
                    float starAlpha = 0.0;
                    if (starVisibility > 0.0) {
                        float horizonFade = smoothstep(0.66, 0.38, uv.y);
                        starAlpha = starField(aspectUv, starSeed) * starVisibility * horizonFade * (1.0 - clouds);
                    }

                    float total = clouds + starAlpha;
                    if (total > 0.0) {
                        color = mix(float3(1.0, 0.98, 0.92), cloudColor, clouds / total);
                    }
                    alpha += total;
                }

                if (weatherPass == 1.0 && mode == 4.0) {
                    float cycle = fract(time / 8.7);
                    float lightning = smoothstep(0.032, 0.0, abs(cycle - 0.018));
                    lightning += smoothstep(0.018, 0.0, abs(cycle - 0.056)) * 0.45;
                    color = mix(color, float3(0.78, 0.84, 1.0), lightning);
                    alpha = min(0.82, alpha + lightning * 0.48);
                }

                // Skia composites shader output as PREMULTIPLIED alpha. Returning straight
                // alpha adds `color` to every pixel even where alpha is 0, washing the whole
                // wallpaper out (white haze). Premultiply so transparent pixels stay invisible.
                float a = clamp(alpha, 0.0, 0.86);
                float3 premultiplied = color * a;
                premultiplied = premultiplied * (1.0 - glassShadow)
                    + float3(0.05, 0.08, 0.13) * glassShadow;
                a = a + glassShadow * (1.0 - a);
                premultiplied = premultiplied * (1.0 - glassHighlight)
                    + float3(0.86, 0.93, 1.0) * glassHighlight;
                a = a + glassHighlight * (1.0 - a);
                // ACT-006: lensing ring approximating refraction of the scene behind each drop.
                float refractionMix = clamp(glassRefraction, 0.0, 1.0) * 0.9;
                premultiplied = premultiplied * (1.0 - refractionMix)
                    + float3(0.66, 0.85, 1.0) * refractionMix;
                a = a + refractionMix * (1.0 - a);
                // Global crossfade contribution. Output is premultiplied, so scale both the
                // premultiplied color and alpha to keep transparent pixels invisible.
                premultiplied = premultiplied * transitionAlpha;
                a = a * transitionAlpha;
                return half4(half3(premultiplied), half(a));
            }
        """
    }
}
