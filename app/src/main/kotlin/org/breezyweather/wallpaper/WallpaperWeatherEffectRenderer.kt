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
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import org.breezyweather.ui.theme.weatherView.WeatherView
import java.util.Random
import kotlin.math.min
import kotlin.math.roundToInt

private const val CLOUD_LAYER_COUNT = 5

private fun isCloudLayerEnabled(index: Int, budget: Int): Boolean = when {
    budget >= CLOUD_LAYER_COUNT -> true
    budget == 4 -> index != 2
    budget == 3 -> index == 0 || index == 2 || index == 4
    budget == 2 -> index == 0 || index == 4
    budget == 1 -> index == 4
    else -> false
}

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
    private val precipitationIntensity: Float = 0f,
    /**
     * Signed horizontal shear (dx/dy) applied to falling rain/snow/hail, from
     * [WallpaperSceneState.precipitationTiltSlope]. 0 = straight down.
     */
    private val precipitationTiltSlope: Float = 0f,
    private val qualityProfile: WallpaperQualityProfile = WallpaperQualityProfile.BALANCED,
    /** Seeds the Canvas-fallback particle/drop randomness for reproducible visual regression tests (ACT-008). */
    private val randomSeed: Long? = null,
) {
    private var daylight = daylight.coerceIn(0f, 1f)
    private val daytime: Boolean
        get() = daylight >= 0.5f
    private var shader: RuntimeShader? = null
    private var shaderPaint: Paint? = null
    private var glassShader: RuntimeShader? = null
    private var glassShaderPaint: Paint? = null
    private var canvasRenderer: CanvasRenderer? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val s = RuntimeShader(SHADER_SOURCE)
                shader = s
                shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = s as Shader
                }
                if (glassRainIntensity > 0f) {
                    val glass = RuntimeShader(GLASS_SHADER_SOURCE)
                    glassShader = glass
                    glassShaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glass }
                }
            } catch (error: Throwable) {
                Log.w(LOG_TAG, "Weather RuntimeShader unavailable; using Canvas fallback", error)
                canvasRenderer = CanvasRenderer(weatherKind, this.daylight, cloudSpeedFactor, cloudField, fogField, starField, glassRainIntensity, precipitationTiltSlope, randomSeed)
            }
        } else {
            canvasRenderer = CanvasRenderer(weatherKind, this.daylight, cloudSpeedFactor, cloudField, fogField, starField, glassRainIntensity, precipitationTiltSlope, randomSeed)
        }
    }

    private var elapsedSeconds = 0f
    private var precipitationLayerCount = (
        MIN_PRECIPITATION_LAYERS +
            (MAX_PRECIPITATION_LAYERS - MIN_PRECIPITATION_LAYERS) * precipitationIntensity.coerceIn(0f, 1f)
        )
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

    /** Effective quality profile after temporary degradation (ACT-009 telemetry). */
    val effectiveQualityProfile: WallpaperQualityProfile
        get() = degradationTracker.effectiveProfile

    /** Current degradation level, 0 = no degradation (ACT-009 telemetry). */
    val qualityDegradationLevel: Int
        get() = degradationTracker.degradationLevel

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

    fun drawGlassRainDrops(canvas: Canvas, alpha: Float = 1f, sceneTexture: Shader? = null) {
        if (canvas.width <= 0 || canvas.height <= 0 || alpha <= 0f || glassRainIntensity <= 0f) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && sceneTexture != null) {
            val glass = glassShader ?: return
            val paint = glassShaderPaint ?: return
            glass.setFloatUniform("resolution", canvas.width.toFloat(), canvas.height.toFloat())
            glass.setFloatUniform("time", elapsedSeconds)
            glass.setFloatUniform("rainAmount", glassRainIntensity)
            glass.setFloatUniform("transitionAlpha", alpha.coerceIn(0f, 1f))
            glass.setInputShader("sceneTexture", sceneTexture)
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
        }
    }

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
            var activeCloudLayerCount = 0
            cloudField.layers.forEachIndexed { index, layer ->
                if (layer.alpha > 0f && isCloudLayerEnabled(index, qualityBudget.cloudLayers)) {
                    activeCloudLayerCount++
                }
            }
            s.setFloatUniform(
                "layerCount",
                activeCloudLayerCount.toFloat(),
            )
            s.setFloatUniform("layerScale", FloatArray(5) { cloudField.layers[it].scale })
            s.setFloatUniform("layerSpeed", FloatArray(5) { cloudField.layers[it].speedFactor })
            s.setFloatUniform("layerAlpha", FloatArray(5) {
                if (isCloudLayerEnabled(it, qualityBudget.cloudLayers)) cloudField.layers[it].alpha else 0f
            })
            s.setFloatUniform("layerDarkness", FloatArray(5) { cloudField.layers[it].darkness })
            s.setFloatUniform("layerVerticalOffset", FloatArray(5) { cloudField.layers[it].verticalOffset })
            s.setFloatUniform("windDirection", cloudField.directionDegrees)
            s.setFloatUniform("fogBandCount", fogField.bands.count { it.baseAlpha > 0f }.toFloat().coerceAtMost(qualityBudget.fogBands.toFloat()))
            s.setFloatUniform("fogVerticalCenter", FloatArray(4) { fogField.bands[it].verticalCenter })
            s.setFloatUniform("fogHeight", FloatArray(4) { fogField.bands[it].height })
            s.setFloatUniform("fogBandAlpha", FloatArray(4) { if (it < qualityBudget.fogBands) fogField.bands[it].baseAlpha * qualityBudget.blurStrength else 0f })
            s.setFloatUniform("fogSpeed", FloatArray(4) { fogField.bands[it].speedFactor })
            s.setFloatUniform("fogColor", FogFieldFactory.fogColor(fogField.isHaze, daylight))
            s.setFloatUniform("fogGlobalAlpha", fogField.globalAlpha * qualityBudget.blurStrength)
            s.setFloatUniform("starVisibility", StarFieldFactory.starVisibility(daylight))
            s.setFloatUniform("starSeed", starField.seed)
            s.setFloatUniform("effectSeed", ((randomSeed ?: 0L) % 100_000L).toFloat())
            s.setFloatUniform("glassRainIntensity", glassRainIntensity)
            s.setFloatUniform("glassTrailLength", glassRainProfile.trailLength)
            s.setFloatUniform("glassHighlightStrength", glassRainProfile.highlightStrength)
            s.setFloatUniform("glassRefractionStrength", glassRainProfile.refractionStrength)
            s.setFloatUniform("precipTilt", precipitationTiltSlope)
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
        val precipitationTiltSlope: Float = 0f,
        val randomSeed: Long? = null,
    ) {
        val daytime: Boolean
            get() = daylight >= 0.5f
        private val random = if (randomSeed != null) Random(randomSeed) else Random()
        private val particles = mutableListOf<Particle>()
        private val clouds = mutableListOf<CloudParticle>()
        private val screenDrops = mutableListOf<ScreenDrop>()
        private var qualityBudget = WallpaperQualityProfileFactory.budgetFor(WallpaperQualityProfile.BALANCED)
        private var glassRainProfile = GlassRainFieldFactory.BALANCED
        private var lightningAlpha = 0f
        private var lightningBoltPath: Path? = null
        private var lightningBranchPath: Path? = null
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
                cloudField.layers.forEachIndexed { index, layer ->
                    if (!isCloudLayerEnabled(index, qualityBudget.cloudLayers)) return@forEachIndexed
                    if (layer.alpha <= 0f) return@forEachIndexed
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
                    precipitationTiltSlope,
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
                    generateLightningBolt()
                }
            }
        }

        /**
         * Builds a jagged lightning bolt (with one branch) from the top of the screen
         * down to a randomly chosen point, for the Canvas fallback's lightning flash.
         */
        private fun generateLightningBolt() {
            if (lastWidth <= 0 || lastHeight <= 0) return
            val startX = lastWidth * (0.18f + random.nextFloat() * 0.64f)
            val endY = lastHeight * (0.40f + random.nextFloat() * 0.45f)
            lightningBoltPath = jaggedLightningPath(startX, 0f, endY)

            val branchStartY = endY * (0.30f + random.nextFloat() * 0.30f)
            val branchEndY = branchStartY + lastHeight * (0.10f + random.nextFloat() * 0.15f)
            lightningBranchPath = jaggedLightningPath(startX, branchStartY, branchEndY)
        }

        private fun jaggedLightningPath(startX: Float, startY: Float, endY: Float): Path {
            val path = Path()
            path.moveTo(startX, startY)
            var x = startX
            val steps = 12
            val stepHeight = (endY - startY) / steps
            for (i in 1..steps) {
                x += (random.nextFloat() - 0.5f) * lastWidth * 0.06f
                path.lineTo(x, startY + stepHeight * i)
            }
            return path
        }

        private fun lerpInt(from: Int, to: Int, t: Float): Int =
            (from + (to - from) * t.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)

        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

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

            // Dense scenes get a soft, spatially varied ceiling instead of one flat fill.
            val maxLayerAlpha = cloudField.layers.maxOf { it.alpha }
            val ceilingStrength = (smoothstep(0.58f, 0.92f, maxLayerAlpha) * maxLayerAlpha).coerceIn(0f, 1f)
            if (ceilingStrength > 0f) {
                val floorDarkness = cloudField.layers.map { it.darkness }.average().toFloat()
                val light = if (daytime) Triple(255, 255, 255) else Triple(196, 202, 214)
                val dark = if (daytime) Triple(110, 128, 150) else Triple(58, 70, 92)
                val ceilingColor = Color.rgb(
                    lerpInt(light.first, dark.first, floorDarkness),
                    lerpInt(light.second, dark.second, floorDarkness),
                    lerpInt(light.third, dark.third, floorDarkness),
                )
                paint.color = ceilingColor
                paint.alpha = (ceilingStrength * 178 * contribution).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                repeat(6) { index ->
                    val tone = if (index % 2 == 0) 0.88f else 1.08f
                    paint.color = Color.rgb(
                        (Color.red(ceilingColor) * tone).toInt().coerceIn(0, 255),
                        (Color.green(ceilingColor) * tone).toInt().coerceIn(0, 255),
                        (Color.blue(ceilingColor) * tone).toInt().coerceIn(0, 255),
                    )
                    paint.alpha = (ceilingStrength * 38 * contribution).toInt().coerceIn(0, 255)
                    val bandWidth = canvas.width * (0.58f + index % 3 * 0.11f)
                    val centerX = canvas.width * ((index * 0.31f + 0.08f) % 1f)
                    val centerY = canvas.height * (0.08f + index % 4 * 0.16f)
                    canvas.drawOval(
                        centerX - bandWidth,
                        centerY - canvas.height * 0.14f,
                        centerX + bandWidth,
                        centerY + canvas.height * 0.14f,
                        paint,
                    )
                }
            }

            // Layered cloud masses (ACT-003): each layer's own alpha/darkness drives a
            // light-to-dark blend so Cloud/Cloudy/Rain/Thunderstorm/Wind read differently.
            for (c in clouds) {
                val layer = c.layer
                val layerIndex = (layer.depth * (CLOUD_LAYER_COUNT - 1)).roundToInt()
                if (!isCloudLayerEnabled(layerIndex, qualityBudget.cloudLayers)) continue
                val light = if (daytime) Triple(255, 255, 255) else Triple(196, 202, 214)
                val dark = if (daytime) Triple(110, 128, 150) else Triple(58, 70, 92)
                val darkness = layer.darkness
                val topColor = Color.rgb(
                    lerpInt(light.first, dark.first, darkness),
                    lerpInt(light.second, dark.second, darkness),
                    lerpInt(light.third, dark.third, darkness),
                )
                // Volumetric look: shade the lower body/base of the cloud darker than the
                // puffy top, mirroring the AGSL shader's vertical shade gradient.
                val baseColor = Color.rgb(
                    (Color.red(topColor) * 0.78f).toInt().coerceIn(0, 255),
                    (Color.green(topColor) * 0.78f).toInt().coerceIn(0, 255),
                    (Color.blue(topColor) * 0.78f).toInt().coerceIn(0, 255),
                )
                val alpha = (layer.alpha * 255 * contribution).toInt().coerceIn(0, 255)

                paint.color = topColor
                paint.alpha = alpha
                when (weatherKind) {
                    WeatherView.WEATHER_KIND_THUNDER,
                    WeatherView.WEATHER_KIND_THUNDERSTORM,
                    -> {
                        canvas.drawCircle(c.x - c.radius * 0.15f, c.y - c.radius * 0.75f, c.radius * 0.43f, paint)
                        canvas.drawCircle(c.x, c.y - c.radius * 0.30f, c.radius * 0.62f, paint)
                        canvas.drawOval(
                            c.x - c.radius * 1.15f,
                            c.y - c.radius,
                            c.x + c.radius * 1.20f,
                            c.y - c.radius * 0.48f,
                            paint,
                        )
                    }
                    WeatherView.WEATHER_KIND_RAINY,
                    WeatherView.WEATHER_KIND_SLEET,
                    WeatherView.WEATHER_KIND_SNOW,
                    WeatherView.WEATHER_KIND_HAIL,
                    WeatherView.WEATHER_KIND_CLOUDY,
                    -> canvas.drawOval(
                        c.x - c.radius * 1.35f,
                        c.y - c.radius * 0.42f,
                        c.x + c.radius * 1.35f,
                        c.y + c.radius * 0.42f,
                        paint,
                    )
                    else -> {
                        canvas.drawCircle(c.x - c.radius * 0.52f, c.y + c.radius * 0.08f, c.radius * 0.48f, paint)
                        canvas.drawCircle(c.x - c.radius * 0.12f, c.y - c.radius * 0.18f, c.radius * 0.62f, paint)
                        canvas.drawCircle(c.x + c.radius * 0.38f, c.y - c.radius * 0.06f, c.radius * 0.52f, paint)
                    }
                }

                paint.color = baseColor
                paint.alpha = alpha
                canvas.drawOval(c.x - c.radius, c.y, c.x + c.radius, c.y + c.radius * 0.58f, paint)
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

        /**
         * Draws fog/haze as a flat tint over the whole screen (covering the photo
         * foreground, since this runs in the foreground pass) plus denser
         * near-horizon bands, mirroring the AGSL renderer's fogHazeBands.
         */
        private fun drawFog(canvas: Canvas, contribution: Float) {
            val rgb = FogFieldFactory.fogColor(fogField.isHaze, daylight)
            paint.color = Color.rgb(
                (rgb[0] * 255f).toInt().coerceIn(0, 255),
                (rgb[1] * 255f).toInt().coerceIn(0, 255),
                (rgb[2] * 255f).toInt().coerceIn(0, 255),
            )

            if (fogField.globalAlpha > 0f) {
                paint.alpha = (fogField.globalAlpha * qualityBudget.blurStrength * 255f * contribution).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, 0f, lastWidth.toFloat(), lastHeight.toFloat(), paint)
            }

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
        }

        fun drawForegroundEffects(canvas: Canvas, contribution: Float = 1f) {
            lastWidth = canvas.width
            lastHeight = canvas.height

            if (fogField.globalAlpha > 0f || fogField.bands.any { it.baseAlpha > 0f }) {
                drawFog(canvas, contribution)
            }

            // Draw lightning flash and bolt
            if (lightningAlpha > 0) {
                paint.color = Color.WHITE
                paint.alpha = (lightningAlpha * 255 * contribution * 0.55f).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, 0f, lastWidth.toFloat(), lastHeight.toFloat(), paint)

                val bolt = lightningBoltPath
                if (bolt != null) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND

                    // Soft outer glow.
                    paint.color = Color.rgb(220, 235, 255)
                    paint.strokeWidth = lastWidth * 0.018f
                    paint.alpha = (lightningAlpha * 255 * contribution * 0.5f).toInt().coerceIn(0, 255)
                    canvas.drawPath(bolt, paint)
                    lightningBranchPath?.let { canvas.drawPath(it, paint) }

                    // Bright core.
                    paint.color = Color.WHITE
                    paint.strokeWidth = lastWidth * 0.004f
                    paint.alpha = (lightningAlpha * 255 * contribution).toInt().coerceIn(0, 255)
                    canvas.drawPath(bolt, paint)
                    lightningBranchPath?.let { canvas.drawPath(it, paint) }

                    paint.style = Paint.Style.FILL
                    paint.strokeCap = Paint.Cap.BUTT
                }
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
                else -> 20f + speedY * precipitationTiltSlope // Slant for rain, wind-driven
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

        // AGSL port of D:/Project/raindrops/rain-threejs/rain-threejs/shaders/rain.frag.
        // The original texture2D(u_tex0, UV + n) becomes sceneTexture.eval(sampleCoord).
        private const val GLASS_SHADER_SOURCE = """
            uniform float2 resolution;
            uniform float time;
            uniform float rainAmount;
            uniform float transitionAlpha;
            uniform shader sceneTexture;

            float hash(float n) {
                return fract(sin(n * 12345.564) * 7658.76);
            }

            float3 hash3(float p) {
                float3 p3 = fract(float3(p) * float3(.1031, .11369, .13787));
                p3 += dot(p3, p3.yzx + 19.19);
                return fract(float3(
                    (p3.x + p3.y) * p3.z,
                    (p3.x + p3.z) * p3.y,
                    (p3.y + p3.z) * p3.x
                ));
            }

            float saw(float b, float t) {
                return smoothstep(0.0, b, t) * smoothstep(1.0, b, t);
            }

            float2 dropLayer(float2 uv, float t) {
                float2 baseUv = uv;
                uv.y += t * 0.75;
                float2 aspect = float2(6.0, 1.0);
                float2 grid = aspect * 2.0;
                float2 id = floor(uv * grid);
                uv.y += hash(id.x);
                id = floor(uv * grid);
                float3 random = hash3(id.x * 35.2 + id.y * 2376.1);
                float2 cell = fract(uv * grid) - float2(0.5, 0.0);

                float x = random.x - 0.5;
                float y = baseUv.y * 20.0;
                float wiggle = sin(y + sin(y));
                x += wiggle * (0.5 - abs(x)) * (random.z - 0.5);
                x *= 0.7;
                float ti = fract(t + random.z);
                y = (saw(0.85, ti) - 0.5) * 0.9 + 0.5;

                float distanceToDrop = length((cell - float2(x, y)) * aspect.yx);
                float mainDrop = smoothstep(0.4, 0.0, distanceToDrop);
                float radius = sqrt(smoothstep(1.0, y, cell.y));
                float columnDistance = abs(cell.x - x);
                float trail = smoothstep(0.23 * radius, 0.15 * radius * radius, columnDistance);
                float trailFront = smoothstep(-0.02, 0.02, cell.y - y);
                trail *= trailFront * radius * radius;

                y = baseUv.y;
                float trail2 = smoothstep(0.2 * radius, 0.0, columnDistance);
                float droplets = max(0.0, sin(y * (1.0 - y) * 120.0) - cell.y)
                    * trail2 * trailFront * random.z;
                y = fract(y * 10.0) + (cell.y - 0.5);
                float smallDistance = length(cell - float2(x, y));
                droplets = smoothstep(0.3, 0.0, smallDistance);
                return float2(mainDrop + droplets * radius * trailFront, trail);
            }

            float staticDrops(float2 uv, float t) {
                uv *= 40.0;
                float2 id = floor(uv);
                uv = fract(uv) - 0.5;
                float3 random = hash3(id.x * 107.45 + id.y * 3543.654);
                float2 position = (random.xy - 0.5) * 0.7;
                float fade = saw(0.025, fract(t + random.z));
                return smoothstep(0.3, 0.0, length(uv - position))
                    * fract(random.z * 10.0) * fade;
            }

            float2 drops(float2 uv, float t, float l0, float l1, float l2) {
                float stationary = staticDrops(uv, t) * l0;
                float2 moving1 = dropLayer(uv, t) * l1;
                float2 moving2 = dropLayer(uv * 1.85, t) * l2;
                float heavyLayer = max(0.0, l2 - 1.0);
                float2 moving3 = dropLayer(uv * 2.70, t + 0.37) * heavyLayer;
                float mask = smoothstep(0.3, 1.0, stationary + moving1.x + moving2.x + moving3.x);
                return float2(mask, max(max(moving1.y * l0, moving2.y * l1), moving3.y));
            }

            half4 main(float2 fragCoord) {
                float2 uv = (fragCoord - 0.5 * resolution) / resolution.y;
                uv.y = -uv.y;
                float t = time * 0.1;
                float stationary = 0.45;
                float layer1 = 0.0;
                float layer2 = 0.0;
                if (rainAmount >= 0.5 && rainAmount < 0.85) {
                    stationary = 1.80;
                    layer1 = 0.97;
                    layer2 = 1.0;
                } else if (rainAmount >= 0.85) {
                    stationary = 2.0;
                    layer1 = 1.0;
                    layer2 = 2.0;
                }
                float2 drop = drops(uv, t, stationary, layer1, layer2);

                float2 epsilon = float2(0.0015, 0.0);
                float maskX = drops(uv + epsilon, t, stationary, layer1, layer2).x;
                float maskY = drops(uv + epsilon.yx, t, stationary, layer1, layer2).x;
                float2 normal = float2(maskX - drop.x, maskY - drop.x);
                float2 offset = float2(normal.x * resolution.x, -normal.y * resolution.y);
                float2 sampleCoord = clamp(fragCoord + offset, float2(0.0), resolution - 1.0);

                half3 color = sceneTexture.eval(sampleCoord).rgb;
                if (rainAmount >= 0.85) {
                    float radius = 12.0;
                    color += sceneTexture.eval(clamp(sampleCoord + float2(radius, 0.0), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord - float2(radius, 0.0), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord + float2(0.0, radius), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord - float2(0.0, radius), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord + float2(8.5, 8.5), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord + float2(-8.5, 8.5), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord + float2(8.5, -8.5), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord - float2(8.5, 8.5), float2(0.0), resolution - 1.0)).rgb;
                    color *= half(1.0 / 9.0);
                    color = mix(color * 0.68, half3(0.32, 0.38, 0.44), half(0.28));
                } else if (rainAmount >= 0.5) {
                    float radius = 4.0;
                    color += sceneTexture.eval(clamp(sampleCoord + float2(radius, 0.0), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord - float2(radius, 0.0), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord + float2(0.0, radius), float2(0.0), resolution - 1.0)).rgb;
                    color += sceneTexture.eval(clamp(sampleCoord - float2(0.0, radius), float2(0.0), resolution - 1.0)).rgb;
                    color *= half(0.20);
                    color *= 0.86;
                } else {
                    color *= 0.97;
                }
                float2 normalizedUv = fragCoord / resolution;
                float2 centeredUv = normalizedUv - 0.5;
                color *= half(1.0 - dot(centeredUv, centeredUv) * 0.8);
                color *= mix(half3(1.0), half3(0.8, 0.9, 1.3), half(0.3));
                color *= 1.1;
                float alpha = transitionAlpha;
                return half4(color * half(alpha), half(alpha));
            }
        """

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
            uniform float layerScale[5];
            uniform float layerSpeed[5];
            uniform float layerAlpha[5];
            uniform float layerDarkness[5];
            uniform float layerVerticalOffset[5];
            uniform float windDirection;
            uniform float fogBandCount;
            uniform float fogVerticalCenter[4];
            uniform float fogHeight[4];
            uniform float fogBandAlpha[4];
            uniform float fogSpeed[4];
            uniform float fogColor[3];
            uniform float fogGlobalAlpha;
            uniform float starVisibility;
            uniform float starSeed;
            uniform float effectSeed;
            uniform float glassRainIntensity;
            uniform float glassTrailLength;
            uniform float glassHighlightStrength;
            uniform float glassRefractionStrength;
            uniform float precipTilt;

            // Enhanced hash functions from demo for better randomness
            float hash(float n) {
                return fract(sin(n) * 43758.5453123);
            }

            float hash21(float2 p) {
                p += effectSeed * float2(0.017, 0.031);
                p = fract(p * float2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float hash2(float2 p) {
                return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
            }

            float3 hash3(float p) {
                float3 p3 = fract(float3(p) * float3(.1031, .11369, .13787));
                p3 += dot(p3, p3.yzx + 19.19);
                return fract(float3((p3.x + p3.y) * p3.z, (p3.x + p3.z) * p3.y, (p3.y + p3.z) * p3.x));
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
                p.x += p.y * (0.16 + precipTilt);
                float2 cell = floor(p);
                float2 local = fract(p) - 0.5;
                float random = hash21(cell + seed);
                local.y = fract(local.y - time * speed + random) - 0.5;
                local.x += (random - 0.5) * 0.65;
                float streak = smoothstep(0.045, 0.0, abs(local.x))
                    * smoothstep(0.48, 0.08, abs(local.y));
                return streak * smoothstep(0.28, 0.96, random);
            }

            // Sawtooth wave: 0→1 over [0, edge], then instant reset.
            // edge=0.85 → drop falls in 85% of cycle, snaps back in 15%.
            // Technique from rocksdanister/rain (rain.frag Saw function).
            float sawWave(float edge, float t) {
                return smoothstep(0.0, edge, t) * smoothstep(1.0, edge, t);
            }

            // Dense static micro-drop field with Saw-based random appear/disappear.
            // Adapted from rocksdanister/rain StaticDrops: 40× grid, phase-based fade.
            float staticRainDrops(float2 uv, float seed) {
                float2 p = uv * 40.0;
                float2 id = floor(p);
                float2 local = fract(p) - 0.5;
                float rA = hash21(id + seed);
                float rB = hash21(id + seed + float2(5.7,  11.3));
                float rC = hash21(id + seed + float2(13.1,  7.9));
                float2 center = (float2(rA, rB) - 0.5) * 0.70;
                float d = length(local - center);
                float fade = sawWave(0.025, fract(time * 0.10 + rC));
                return smoothstep(0.30, 0.0, d) * rC * fade;
            }

            // Improved rain-drop-on-glass layer with enhanced physics.
            // Based on demo shader with better wiggle, cascade droplets, and refraction.
            //
            // Key improvements:
            //   1. Enhanced lateral wiggle for more natural drift
            //   2. Improved cascade droplets in trail wake
            //   3. Better trail fade-out physics
            //   4. More realistic refraction effects
            //
            // colScale  = number of drop columns across the aspectUv.x range [0, aspect].
            // speed     = sawtooth cycles per second (0.18 ≈ one fall every 5–6 s).
            // trailLen  = 0 = no trail/wiggle, 1 = full trail + cascade droplets.
            // refStr    = interior lens-brightening strength.
            // Returns float3(highlight, shadow, refraction).
            float3 rainDropLayer(float2 uv, float colScale, float speed, float seed,
                                 float trailLen, float refStr) {
                float2 UV = uv;

                // Elongated 6:1 grid for vertical drop lanes
                float rowScale = colScale / 6.0;
                float2 p = float2(UV.x * colScale, UV.y * rowScale);

                float2 cellId = floor(p);
                float2 st = float2(fract(p.x) - 0.5, fract(p.y));

                float rA = hash21(cellId + seed);
                float rB = hash21(cellId + seed + float2(11.3, 7.1));
                float rC = hash21(cellId + seed + float2(23.7, 17.4));

                // Enhanced lateral wiggle with sin(y+sin(y)) for naturalistic S-curve
                float x = (rA - 0.5) * 0.70;
                float wFreq = UV.y * rowScale * 18.0;
                float wiggle = sin(wFreq + sin(wFreq));
                // Increased wiggle strength for more visible movement
                x += wiggle * (0.5 - abs(x)) * (rC - 0.5) * 0.50 * trailLen;

                // Sawtooth fall with proper instant reset
                float dropSpd = speed * mix(0.75, 1.25, rB);
                float ti = fract(time * dropSpd + rC);
                // Use sawWave edge at 0.85 for 85% fall, 15% hold at bottom
                float dropY = smoothstep(0.0, 0.85, ti);

                // Compensate for 6:1 aspect ratio
                float2 delta = st - float2(x, dropY);
                float dist = length(delta * float2(1.0, 6.0));

                float dropSize = 0.42;
                float body = smoothstep(dropSize, dropSize * 0.72, dist);

                // Dome surface normal for physically-based lighting
                float2 nXY = (delta * float2(1.0, 6.0)) / max(dist, 0.001);
                float nZ = sqrt(max(0.0, 1.0 - dist * dist / (dropSize * dropSize)));
                float3 N = normalize(float3(-nXY * body, nZ));

                // Blinn-Phong specular with light from upper-left
                float3 L = normalize(float3(-0.35, -0.55, 0.80));
                float3 H = normalize(L + float3(0.0, 0.0, 1.0));
                float spec = pow(max(dot(N, H), 0.0), 52.0) * body;

                // Surface tension rim
                float rim = smoothstep(dropSize + 0.04, dropSize, dist)
                          - smoothstep(dropSize, dropSize * 0.88, dist);

                // Gravity shadow at bottom
                float2 shadowPos = float2(x, dropY + dropSize * 0.52);
                float bottomShadow = smoothstep(dropSize * 0.36, 0.0,
                    length((st - shadowPos) * float2(1.0, 6.0))) * body;

                // Improved trail with better fade physics
                float r = sqrt(max(0.0, smoothstep(1.0, 0.2, dropY)));
                float cd = abs(st.x - x);
                float trail = smoothstep(0.23 * r, 0.15 * r * r, cd)
                            * smoothstep(-0.02, 0.02, dropY - st.y)
                            * r * r * trailLen;

                // Enhanced cascade droplets with sin wave formula from demo
                float y2 = UV.y * rowScale * 3.0;
                float trail2 = smoothstep(0.20 * r, 0.0, cd);
                // Improved cascade formula: sin(y * (1.0 - y) * 120.0)
                float droplets = max(0.0,
                    sin(y2 * (1.0 - fract(y2)) * 120.0) - (dropY - st.y)
                ) * trail2 * smoothstep(-0.02, 0.02, dropY - st.y) * rC * trailLen;

                // Visibility factor (~25% of cells active)
                float visible = step(0.75, rB);

                // Enhanced highlight contributions
                float highlight = (
                    spec     * 1.80 +  // Increased specular
                    rim      * 0.90 +  // Stronger rim light
                    trail    * 0.12 +
                    droplets * 0.28    // More visible cascade droplets
                ) * visible;

                float shadow = (
                    bottomShadow * 0.40 +
                    trail        * 0.16
                ) * visible;

                // Enhanced refraction strength
                return float3(highlight, shadow, body * visible * refStr * 1.2);
            }

            // Hail sits visually between snow (slow, round, drifting flakes) and rain (fast,
            // long streaks): falls noticeably faster than snow and is shaped as small,
            // vertically-elongated pellets rather than round flakes or full streaks.
            float hailLayer(float2 uv, float scale, float speed, float seed) {
                float2 p = uv * scale;
                p.x += p.y * precipTilt;
                p.x -= time * 0.08 * windFactor;
                p.y -= time * speed * 1.9;
                float2 cell = floor(p);
                float2 local = fract(p) - 0.5;
                float random = hash21(cell + seed);
                local.x += (random - 0.5) * 0.50;
                float radius = mix(0.028, 0.060, random);
                float2 pellet = local * float2(1.15, 0.62);
                return smoothstep(radius, radius * 0.55, length(pellet)) * smoothstep(0.42, 0.96, random);
            }

            float snowLayer(float2 uv, float scale, float speed, float drift, float seed) {
                float2 p = uv * scale;
                p.x += p.y * precipTilt;
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
                // Flat tint across the whole scene (including the photo foreground,
                // drawn before this pass), so fog/haze reads as a uniform atmosphere
                // rather than only a low-lying band near the horizon.
                float globalNoise = noise21(float2(aspectUv.x * 1.6 + time * 0.01, uv.y * 2.4));
                result += fogGlobalAlpha * (0.8 + globalNoise * 0.2);
                return clamp(result, 0.0, 1.0);
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

            // Wandering horizontal displacement for a lightning bolt, built from a few
            // octaves of value noise so the channel zigzags down the screen instead of
            // following a straight line.
            float boltOffset(float y, float seed) {
                float o = 0.0;
                o += (noise21(float2(y * 7.0, seed)) - 0.5) * 0.16;
                o += (noise21(float2(y * 17.0, seed + 5.0)) - 0.5) * 0.07;
                o += (noise21(float2(y * 41.0, seed + 11.0)) - 0.5) * 0.03;
                return o;
            }

            // A single jagged lightning stroke from yStart to yEnd, centered on x0 with
            // wandering offsets from boltOffset. Returns a bright core plus a soft glow,
            // faded in/out at both ends so the stroke doesn't end abruptly.
            float lightningStroke(float2 uv, float x0, float seed, float yStart, float yEnd, float width) {
                if (uv.y < yStart || uv.y > yEnd) return 0.0;
                float x = x0 + boltOffset(uv.y, seed) * smoothstep(yStart, yStart + 0.04, uv.y);
                float dist = abs(uv.x - x);
                float core = smoothstep(width, width * 0.15, dist);
                float glow = smoothstep(width * 6.0, 0.0, dist) * 0.35;
                float fade = smoothstep(yStart, yStart + 0.02, uv.y) * smoothstep(yEnd, yEnd - 0.12, uv.y);
                return (core + glow) * fade;
            }

            // Procedural lightning bolt with one branch, repositioned each time the
            // lightning flash cycle restarts (driven by cycleSeed).
            float lightningBolt(float2 uv, float cycleSeed) {
                float x0 = mix(0.18, 0.82, hash21(float2(cycleSeed, 2.0)));
                float yEnd = mix(0.40, 0.85, hash21(float2(cycleSeed, 4.0)));
                float bolt = lightningStroke(uv, x0, cycleSeed, 0.0, yEnd, 0.005);

                float yBranch = yEnd * mix(0.30, 0.60, hash21(float2(cycleSeed, 6.0)));
                float branchX = x0 + boltOffset(yBranch, cycleSeed);
                float branchEnd = yBranch + mix(0.10, 0.25, hash21(float2(cycleSeed, 8.0)));
                bolt += lightningStroke(uv, branchX, cycleSeed + 31.0, yBranch, branchEnd, 0.0035) * 0.8;

                return clamp(bolt, 0.0, 1.0);
            }

            float cloudCircle(float2 point, float2 center, float radius, float softness) {
                return smoothstep(radius + softness, radius - softness, length(point - center));
            }

            float cloudEllipse(float2 point, float2 center, float2 radius, float softness) {
                float distanceFromCenter = length((point - center) / radius);
                return smoothstep(1.0 + softness, 1.0 - softness, distanceFromCenter);
            }

            float cloudFbm(float2 p) {
                float value = 0.0;
                value += noise21(p) * 0.571;
                p = p * 2.03 + float2(3.1, -1.7);
                value += noise21(p) * 0.286;
                p = p * 2.11 + float2(-2.4, 4.3);
                value += noise21(p) * 0.143;
                return value;
            }

            float dualCloudDensity(float2 p, float seed) {
                float2 driftA = float2(time * 0.004, time * 0.0015);
                float2 driftB = float2(-time * 0.0023, time * 0.0011);
                float fieldA = cloudFbm(p * 2.15 + float2(seed * 1.7, seed * 0.4) + driftA);
                float fieldB = cloudFbm(p * 3.70 + float2(seed * 2.9, seed * 1.3) + driftB);
                return clamp(fieldA * 0.68 + fieldB * 0.32, 0.0, 1.0);
            }

            float cumulusSilhouette(float2 p, float seed) {
                float h1 = hash21(float2(seed, 11.0));
                float h2 = hash21(float2(seed, 23.0));
                float h3 = hash21(float2(seed, 37.0));
                float cloud = cloudCircle(p, float2(-0.72, mix(0.02, 0.20, h1)), mix(0.34, 0.46, h2), 0.14);
                cloud = max(cloud, cloudCircle(p, float2(-0.34, mix(-0.34, -0.12, h2)), mix(0.44, 0.59, h3), 0.14));
                cloud = max(cloud, cloudCircle(p, float2(mix(-0.10, 0.10, h1), mix(-0.48, -0.24, h3)), mix(0.48, 0.64, h2), 0.15));
                cloud = max(cloud, cloudCircle(p, float2(0.38, mix(-0.31, -0.08, h1)), mix(0.43, 0.59, h3), 0.14));
                cloud = max(cloud, cloudCircle(p, float2(0.76, mix(0.01, 0.22, h2)), mix(0.32, 0.46, h1), 0.13));
                float base = smoothstep(0.39, 0.12, abs(p.y - 0.20)) * smoothstep(1.18, 0.76, abs(p.x));
                return max(cloud, base * smoothstep(0.12, 0.56, cloud));
            }

            float stratusSilhouette(float2 p, float seed) {
                float waviness = (noise21(float2(p.x * 1.25 + seed, seed * 2.1)) - 0.5) * 0.42;
                float band = smoothstep(0.52, 0.14, abs(p.y + waviness));
                float ends = smoothstep(1.65, 1.12, abs(p.x));
                float pockets = noise21(p * float2(1.25, 2.8) + float2(seed * 2.0, 4.7));
                return band * ends * smoothstep(0.22, 0.58, pockets + band * 0.48);
            }

            float cumulonimbusSilhouette(float2 p, float seed) {
                float tower = cloudEllipse(p, float2(-0.12, -0.30), float2(0.58, 1.04), 0.15);
                tower = max(tower, cloudCircle(p, float2(-0.24, -0.98), 0.43, 0.14));
                tower = max(tower, cloudCircle(p, float2(0.18, -0.76), 0.50, 0.14));
                tower = max(tower, cloudCircle(p, float2(0.35, -0.28), 0.56, 0.15));
                float anvil = cloudEllipse(p, float2(0.18, -1.04), float2(1.12, 0.28), 0.16);
                float base = smoothstep(0.36, 0.11, abs(p.y - 0.36)) * smoothstep(1.14, 0.82, abs(p.x));
                return max(max(tower, anvil), base);
            }

            // Returns (density, shade). Every morphology gets the same dual moving density
            // treatment, so open pockets and soft edges evolve independently of its outline.
            float2 cloudShape(float2 uv, float2 center, float size, float seed) {
                float2 p = (uv - center) / size;
                float silhouette = mode == 4.0
                    ? cumulonimbusSilhouette(p, seed)
                    : ((mode == 1.0 || mode == 2.0 || mode == 3.0 || mode == 5.0 ||
                        mode == 7.0 || mode == 9.0 || mode == 10.0)
                        ? stratusSilhouette(p, seed)
                        : cumulusSilhouette(p, seed));
                float densityField = dualCloudDensity(p, seed);
                float density = smoothstep(0.14, 0.82, silhouette + (densityField - 0.50) * 0.72);
                density *= smoothstep(0.02, 0.30, silhouette);
                float verticalShade = smoothstep(-0.72, 0.88, p.y);
                float internalShade = 1.0 - densityField;
                float shade = clamp(verticalShade * 0.62 + internalShade * 0.38, 0.0, 1.0);
                return float2(density, shade);
            }

            float2 driftingCloud(float2 uv, float y, float size, float speed, float seed) {
                float travel = resolution.x / resolution.y + size * 2.7;
                float x = fract(seed + time * speed) * travel - size * 1.35;
                return cloudShape(uv, float2(x, y), size, seed);
            }

            // Screen-filling nimbostratus/altostratus field. Two independently drifting
            // FBM domains replace the old flat coverage floor while preserving full cover.
            float2 overcastField(float2 uv, float seed) {
                float2 p = uv * float2(1.15, 2.35);
                float fieldA = cloudFbm(p + float2(time * 0.0030, seed * 2.7));
                float fieldB = cloudFbm(p * 1.83 + float2(-time * 0.0018, seed * 4.1));
                float density = clamp(fieldA * 0.64 + fieldB * 0.36, 0.0, 1.0);
                float shade = clamp((1.0 - density) * 0.60 + fieldB * 0.40, 0.0, 1.0);
                return float2(density, shade);
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

                if (weatherPass == 2.0 && glassRainIntensity > 0.0) {
                    // Three-layer approach from rocksdanister/rain:
                    //   staticDrops — 40× grid micro-drops, Saw-fade appear/disappear
                    //   drop1       — main animated layer: sawtooth fall + wiggle + cascade
                    //   drop2       — secondary layer at 1.85× UV (smaller/denser drops)
                    //
                    // colScale=50 with aspectUv.x≈0.46 → ~23 columns → ~20px radius drops.
                    // colScale=50 on UV×1.85 → ~42 columns → ~11px radius drops.
                    float layer1Int = smoothstep(0.25, 0.75, glassRainIntensity);
                    float layer2Int = smoothstep(0.0,  0.5,  glassRainIntensity);

                    float3 drop1 = rainDropLayer(aspectUv,        50.0, 0.18, 31.0,
                                                 glassTrailLength,       glassRefractionStrength);
                    float3 drop2 = rainDropLayer(aspectUv * 1.85, 50.0, 0.22, 73.0,
                                                 glassTrailLength * 0.75, glassRefractionStrength * 0.80);

                    glassHighlight = clamp((
                        drop1.x * layer1Int * 0.24 +
                        drop2.x * layer2Int * 0.18
                    ) * glassHighlightStrength, 0.0, 0.32);

                    glassShadow = clamp(
                        drop1.y * layer1Int * 1.00 +
                        drop2.y * layer2Int * 0.85,
                        0.0, 0.60);

                    glassRefraction = clamp(
                        drop1.z * layer1Int * 1.00 +
                        drop2.z * layer2Int * 0.75,
                        0.0, 0.60);

                    glassHighlight  *= glassRainIntensity;
                    glassShadow     *= glassRainIntensity;
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
                if (weatherPass == 1.0 && (fogBandCount > 0.0 || fogGlobalAlpha > 0.0)) {
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

                // Layered cloud mass: five back-to-front layers with their own scale, speed,
                // alpha and darkness (ACT-003). A layer with alpha 0 contributes nothing,
                // so transitions between weather families never pop a layer in or out.
                if (weatherPass == 0.0) {
                    float clouds = 0.0;
                    float3 cloudColor = color;
                    if (layerCount > 0.0) {
                        float dirSign = cos(radians(windDirection)) >= 0.0 ? 1.0 : -1.0;
                        float darknessSum = 0.0;
                        float shadeSum = 0.0;
                        float alphaSum = 0.0;
                        for (int i = 0; i < 5; i++) {
                            if (layerAlpha[i] <= 0.0) continue;
                            float y = 0.15 + float(i) * 0.085 + layerVerticalOffset[i];
                            float size = 0.16 + layerScale[i] * 0.14;
                            float speed = 0.005 * layerSpeed[i] * dirSign;
                            float seed = 0.08 + float(i) * 0.41;

                            // Density-driven coverage: at low layerAlpha only one cloud mass
                            // drifts through (half bewolkt). As density rises, extra masses
                            // fade in and overlap, merging into larger clusters (meer
                            // bewolkt) and, at the highest densities, a near-continuous
                            // ceiling (vol bewolkt) — all from the existing layerAlpha,
                            // with no new uniforms.
                            float2 shape = driftingCloud(aspectUv, y, size, speed, seed) * layerAlpha[i];
                            float shapeMask = shape.x;
                            float shadeAcc = shape.x * shape.y;
                            float weightAcc = shape.x;

                            bool stratiform = mode == 1.0 || mode == 2.0 || mode == 3.0 ||
                                mode == 5.0 || mode == 7.0 || mode == 9.0 || mode == 10.0;
                            if (!stratiform && layerAlpha[i] > 0.45) {
                                float2 extra1 = driftingCloud(aspectUv, y + 0.05, size * 0.92, speed * 1.18, seed + 0.27) * layerAlpha[i];
                                shapeMask = max(shapeMask, extra1.x);
                                shadeAcc += extra1.x * extra1.y;
                                weightAcc += extra1.x;
                            }
                            if (!stratiform && layerAlpha[i] > 0.75) {
                                float2 extra2 = driftingCloud(aspectUv, y - 0.06, size * 1.08, speed * 0.84, seed + 0.59) * layerAlpha[i];
                                shapeMask = max(shapeMask, extra2.x);
                                shadeAcc += extra2.x * extra2.y;
                                weightAcc += extra2.x;
                            }

                            clouds = max(clouds, shapeMask);
                            darknessSum += layerDarkness[i] * shapeMask;
                            shadeSum += (weightAcc > 0.0 ? shadeAcc / weightAcc : 0.0) * shapeMask;
                            alphaSum += shapeMask;
                        }
                        float weightedDarkness = alphaSum > 0.0 ? darknessSum / alphaSum : 0.0;
                        float weightedShade = alphaSum > 0.0 ? shadeSum / alphaSum : 0.0;

                        // Dense scenes use their own moving nimbostratus/altostratus field.
                        // It fills open sky with textured density instead of a flat alpha floor.
                        float maxLayerAlpha = max(
                            max(max(layerAlpha[0], layerAlpha[1]), max(layerAlpha[2], layerAlpha[3])),
                            layerAlpha[4]
                        );
                        // Precipitation must retain an overcast ceiling even when adaptive
                        // quality keeps only the faintest, most distant parallax layer.
                        float precipitationCeiling = (mode == 1.0 || mode == 2.0 || mode == 4.0 ||
                            mode == 5.0 || mode == 9.0) ? 0.90 : 0.0;
                        float ceilingStrength = max(
                            smoothstep(0.58, 0.92, maxLayerAlpha) * maxLayerAlpha,
                            precipitationCeiling
                        );
                        if (ceilingStrength > 0.0) {
                            float2 ceiling = overcastField(aspectUv, effectSeed * 0.013 + 0.7);
                            float ceilingMask = ceilingStrength * mix(0.86, 1.0, ceiling.x);
                            float floorDarkness = (
                                layerDarkness[0] + layerDarkness[1] + layerDarkness[2] +
                                layerDarkness[3] + layerDarkness[4]
                            ) / 5.0;
                            float ceilingDominance = max(ceilingMask - clouds, 0.0);
                            weightedDarkness = mix(weightedDarkness, floorDarkness, ceilingDominance);
                            weightedShade = mix(weightedShade, ceiling.y, ceilingDominance);
                            clouds = max(clouds, ceilingMask);
                        }

                        float3 fairColor = daylight > 0.5 ? float3(0.94, 0.97, 1.0) : float3(0.48, 0.54, 0.64);
                        // Storm color is near-black at full darkness, matching the dramatic
                        // deep blue-grey of real cumulonimbus / rain cloud undersides.
                        float3 stormColor = daylight > 0.5 ? float3(0.20, 0.22, 0.28) : float3(0.09, 0.11, 0.15);
                        float greyness = pow(weightedDarkness, 0.8);
                        cloudColor = mix(fairColor, stormColor, greyness);
                        // Shade: darker clouds get a stronger top→base gradient so storm
                        // cloud bases look almost black while tops stay lighter (top-lit).
                        float shadeStrength = mix(0.80, 0.46, smoothstep(0.30, 0.85, weightedDarkness));
                        cloudColor *= mix(1.0, shadeStrength, weightedShade);
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
                    float cycleLength = 8.7;
                    float cycle = fract(time / cycleLength);
                    float lightning = smoothstep(0.032, 0.0, abs(cycle - 0.018));
                    lightning += smoothstep(0.018, 0.0, abs(cycle - 0.056)) * 0.45;
                    color = mix(color, float3(0.78, 0.84, 1.0), lightning);
                    alpha = min(0.82, alpha + lightning * 0.48);

                    // Visible lightning bolt: a jagged channel from the cloud base down
                    // through the sky, flashing in sync with the screen flash above. The
                    // shape is re-derived each cycle from cycleSeed, so each flash strikes
                    // a different, randomly placed bolt.
                    float cycleSeed = floor(time / cycleLength) + effectSeed * 0.137;
                    float bolt = lightningBolt(uv, cycleSeed) * clamp(lightning, 0.0, 1.0);
                    color = mix(color, float3(0.92, 0.96, 1.0), bolt);
                    alpha = max(alpha, min(0.95, alpha + bolt));
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
                    + float3(0.62, 0.74, 0.88) * glassHighlight;
                a = a + glassHighlight * 0.35 * (1.0 - a);
                // ACT-006 Phase 1: lens brightening — inside a water drop the converging
                // lens focuses ambient sky light, making the interior appear slightly
                // brighter and sky-tinted rather than replacing with a fixed blue colour.
                float refractionMix = clamp(glassRefraction, 0.0, 1.0) * 0.82;
                // Brighten + very subtle sky tint (water is mostly transparent)
                float3 brightened = min(premultiplied * 1.30 + float3(0.03, 0.05, 0.07), float3(1.0));
                float3 skyHint = float3(0.68, 0.84, 0.94);
                premultiplied = premultiplied * (1.0 - refractionMix)
                    + mix(brightened, skyHint, 0.28) * refractionMix;
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
