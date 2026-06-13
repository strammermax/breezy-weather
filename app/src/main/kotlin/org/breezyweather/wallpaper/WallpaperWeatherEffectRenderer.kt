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
    private val daytime: Boolean,
    private val cloudSpeedFactor: Float = 1f,
) {
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
                canvasRenderer = CanvasRenderer(weatherKind, daytime, cloudSpeedFactor)
            }
        } else {
            canvasRenderer = CanvasRenderer(weatherKind, daytime, cloudSpeedFactor)
        }
    }

    private var elapsedSeconds = 0f
    private var precipitationLayerCount = DEFAULT_PRECIPITATION_LAYERS
    private var averageFrameMillis = TARGET_FRAME_MILLIS
    private var qualityEvaluationMillis = 0L
    private var stableFrameMillis = 0L

    fun update(intervalMillis: Long, animate: Boolean) {
        if (animate) {
            val delta = min(intervalMillis, MAX_FRAME_INTERVAL_MILLIS).coerceAtLeast(0L)
            elapsedSeconds += delta / 1000f
            canvasRenderer?.update(delta)
            updateAdaptivePrecipitationQuality(intervalMillis)
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
                precipitationLayerCount = (precipitationLayerCount + 1f).coerceAtMost(MAX_PRECIPITATION_LAYERS)
                stableFrameMillis = 0L
            }
            qualityEvaluationMillis = 0L
        }
    }

    fun drawBackgroundWeatherPass(canvas: Canvas) = draw(canvas, WEATHER_PASS_BACKGROUND)

    fun drawForegroundWeatherPass(canvas: Canvas) = draw(canvas, WEATHER_PASS_FOREGROUND)

    fun drawGlassRainDrops(canvas: Canvas) = draw(canvas, WEATHER_PASS_GLASS)

    private fun draw(canvas: Canvas, pass: Float) {
        if (canvas.width <= 0 || canvas.height <= 0) return

        val s = shader
        val sp = shaderPaint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && s != null && sp != null) {
            s.setFloatUniform("resolution", canvas.width.toFloat(), canvas.height.toFloat())
            s.setFloatUniform("time", elapsedSeconds)
            s.setFloatUniform("mode", shaderMode(weatherKind))
            s.setFloatUniform("daylight", if (daytime) 1f else 0f)
            s.setFloatUniform("windFactor", cloudSpeedFactor)
            s.setFloatUniform("weatherPass", pass)
            s.setFloatUniform("precipitationLayers", precipitationLayerCount)
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), sp)
        } else {
            if (pass == WEATHER_PASS_BACKGROUND) {
                canvasRenderer?.drawClouds(canvas)
            } else if (pass == WEATHER_PASS_FOREGROUND) {
                canvasRenderer?.drawForegroundEffects(canvas)
            } else {
                canvasRenderer?.drawGlassRainDrops(canvas)
            }
        }
    }

    private class CanvasRenderer(
        val weatherKind: Int,
        val daytime: Boolean,
        val cloudSpeedFactor: Float,
    ) {
        private val random = Random()
        private val particles = mutableListOf<Particle>()
        private val clouds = mutableListOf<CloudParticle>()
        private val screenDrops = mutableListOf<ScreenDrop>()
        private var lightningAlpha = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var lastWidth = 0
        private var lastHeight = 0

        fun update(deltaMillis: Long) {
            val deltaSec = deltaMillis / 1000f

            if (lastWidth <= 0 || lastHeight <= 0) return

            // Initial clouds/fog if needed
            if (clouds.isEmpty() && (
                weatherKind == WeatherView.WEATHER_KIND_FOG ||
                weatherKind == WeatherView.WEATHER_KIND_HAZE ||
                weatherKind == WeatherView.WEATHER_KIND_CLOUD ||
                weatherKind == WeatherView.WEATHER_KIND_CLOUDY ||
                weatherKind == WeatherView.WEATHER_KIND_RAINY ||
                weatherKind == WeatherView.WEATHER_KIND_SNOW ||
                weatherKind == WeatherView.WEATHER_KIND_SLEET ||
                weatherKind == WeatherView.WEATHER_KIND_HAIL ||
                weatherKind == WeatherView.WEATHER_KIND_WIND ||
                weatherKind == WeatherView.WEATHER_KIND_THUNDER ||
                weatherKind == WeatherView.WEATHER_KIND_THUNDERSTORM
            )) {
                val count = when (weatherKind) {
                    WeatherView.WEATHER_KIND_CLOUD -> 3
                    WeatherView.WEATHER_KIND_WIND -> 5
                    WeatherView.WEATHER_KIND_CLOUDY -> 7
                    else -> 6
                }
                for (i in 0 until count) {
                    clouds.add(CloudParticle(random, lastWidth, lastHeight))
                }
            }

            // Update particles (rain/snow)
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

            // Update clouds
            for (c in clouds) {
                c.x += c.speedX * cloudSpeedFactor * deltaSec
                if (c.x > lastWidth + c.radius) {
                    c.x = -c.radius
                    c.y = random.nextFloat() * lastHeight * 0.5f // Keep clouds in upper half
                }
            }

            for (drop in screenDrops) {
                drop.update(deltaSec, lastHeight)
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

        fun drawClouds(canvas: Canvas) {
            lastWidth = canvas.width
            lastHeight = canvas.height

            if (weatherKind == WeatherView.WEATHER_KIND_FOG || weatherKind == WeatherView.WEATHER_KIND_HAZE) {
                paint.color = if (weatherKind == WeatherView.WEATHER_KIND_FOG) {
                    if (daytime) Color.rgb(205, 220, 225) else Color.rgb(92, 106, 124)
                } else {
                    if (daytime) Color.rgb(218, 198, 164) else Color.rgb(104, 96, 96)
                }
                val baseAlpha = if (weatherKind == WeatherView.WEATHER_KIND_FOG) 42 else 28
                repeat(3) { layer ->
                    paint.alpha = baseAlpha + layer * 12
                    val top = lastHeight * (0.33f + layer * 0.13f)
                    canvas.drawOval(
                        -lastWidth * 0.18f,
                        top,
                        lastWidth * 1.18f,
                        top + lastHeight * (0.18f + layer * 0.05f),
                        paint,
                    )
                }
                return
            }

            for (c in clouds) {
                val stormy = weatherKind == WeatherView.WEATHER_KIND_RAINY ||
                    weatherKind == WeatherView.WEATHER_KIND_SNOW ||
                    weatherKind == WeatherView.WEATHER_KIND_SLEET ||
                    weatherKind == WeatherView.WEATHER_KIND_HAIL ||
                    weatherKind == WeatherView.WEATHER_KIND_THUNDER ||
                    weatherKind == WeatherView.WEATHER_KIND_THUNDERSTORM
                paint.color = when {
                    stormy && daytime -> Color.rgb(118, 136, 158)
                    stormy -> Color.rgb(61, 73, 94)
                    daytime -> Color.WHITE
                    else -> Color.LTGRAY
                }
                val alpha = if (weatherKind == WeatherView.WEATHER_KIND_FOG || weatherKind == WeatherView.WEATHER_KIND_HAZE) 0.3f else 0.5f
                paint.alpha = (alpha * 255 * c.alphaMod).toInt()
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

        fun drawForegroundEffects(canvas: Canvas) {
            lastWidth = canvas.width
            lastHeight = canvas.height

            // Draw lightning flash
            if (lightningAlpha > 0) {
                paint.color = Color.WHITE
                paint.alpha = (lightningAlpha * 255).toInt()
                canvas.drawRect(0f, 0f, lastWidth.toFloat(), lastHeight.toFloat(), paint)
            }

            // Draw particles
            for (p in particles) {
                paint.color = if (weatherKind == WeatherView.WEATHER_KIND_RAINY ||
                    weatherKind == WeatherView.WEATHER_KIND_THUNDERSTORM ||
                    weatherKind == WeatherView.WEATHER_KIND_SLEET
                ) {
                    if (daytime) 0xAAFFFFFF.toInt() else 0x77FFFFFF.toInt()
                } else {
                    Color.WHITE
                }
                paint.alpha = (p.alpha * 255).toInt()
                if (weatherKind == WeatherView.WEATHER_KIND_SNOW || weatherKind == WeatherView.WEATHER_KIND_HAIL) {
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                } else {
                    // Rain streaks
                    paint.strokeWidth = p.size
                    canvas.drawLine(p.x, p.y, p.x + p.speedX * 0.02f, p.y + p.speedY * 0.02f, paint)
                }
            }

        }

        fun drawGlassRainDrops(canvas: Canvas) {
            lastWidth = canvas.width
            lastHeight = canvas.height
            if (weatherKind == WeatherView.WEATHER_KIND_RAINY ||
                weatherKind == WeatherView.WEATHER_KIND_THUNDER ||
                weatherKind == WeatherView.WEATHER_KIND_THUNDERSTORM ||
                weatherKind == WeatherView.WEATHER_KIND_SLEET
            ) {
                if (screenDrops.isEmpty()) {
                    repeat(34) { screenDrops.add(ScreenDrop(random, lastWidth, lastHeight)) }
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                for (drop in screenDrops) {
                    paint.color = Color.rgb(24, 38, 58)
                    paint.alpha = 78
                    canvas.drawArc(drop.bounds, 12f, 156f, false, paint)
                    paint.color = Color.WHITE
                    paint.alpha = 46
                    canvas.drawOval(drop.bounds, paint)
                    paint.alpha = 115
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

        private class CloudParticle(r: Random, w: Int, h: Int) {
            var x = r.nextFloat() * w
            var y = r.nextFloat() * h * 0.48f
            val radius = w * 0.10f + r.nextFloat() * w * 0.12f
            val speedX = 10f + r.nextFloat() * 20f
            val alphaMod = 0.55f + r.nextFloat() * 0.30f
        }

        private class ScreenDrop(r: Random, w: Int, h: Int) {
            private val radius = 5f + r.nextFloat() * 13f
            private val x = radius + r.nextFloat() * (w - radius * 2f).coerceAtLeast(1f)
            private var y = radius + r.nextFloat() * (h - radius * 2f).coerceAtLeast(1f)
            private val speedY = if (r.nextFloat() < 0.22f) 7f + r.nextFloat() * 16f else 0f
            val bounds = RectF()
            val highlight = RectF()

            init {
                updateBounds()
            }

            fun update(deltaSeconds: Float, height: Int) {
                if (speedY == 0f) return
                y += speedY * deltaSeconds
                if (y - radius > height) y = -radius
                updateBounds()
            }

            private fun updateBounds() {
                bounds.set(x - radius * 0.72f, y - radius, x + radius * 0.72f, y + radius)
                highlight.set(x - radius * 0.48f, y - radius * 0.72f, x + radius * 0.48f, y + radius * 0.50f)
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

            float2 glassDropLayer(float2 uv, float scale, float speed, float seed) {
                float2 p = uv * float2(scale, scale * 1.18);
                float2 cell = floor(p);
                float random = hash21(cell + seed);
                float2 local = fract(p) - 0.5;
                local.y = fract(local.y - time * speed * mix(0.18, 1.0, random)) - 0.5;
                local.x += (random - 0.5) * 0.46 + sin(local.y * 9.0 + seed) * 0.018;

                float size = mix(0.045, 0.13, hash21(cell + seed + 17.0));
                float2 dropPoint = local;
                dropPoint.y *= mix(0.72, 0.46, random);
                dropPoint.x *= 1.12;
                float distanceFromDrop = length(dropPoint);
                float body = smoothstep(size, size * 0.72, distanceFromDrop);
                float rim = smoothstep(size * 1.07, size * 0.82, distanceFromDrop)
                    - smoothstep(size * 0.76, size * 0.48, distanceFromDrop);

                float trailWidth = mix(0.012, 0.034, random);
                float trail = smoothstep(trailWidth, 0.0, abs(local.x))
                    * smoothstep(0.42, size * 0.38, local.y)
                    * smoothstep(-0.50, -0.18, local.y);
                float topLight = smoothstep(size * 0.42, 0.0,
                    length(dropPoint - float2(-size * 0.30, -size * 0.32)));
                float bottomShade = smoothstep(size * 0.34, 0.0,
                    length(dropPoint - float2(size * 0.16, size * 0.42)));
                float visible = smoothstep(0.70, 0.98, random);
                float highlight = (rim * 0.30 + topLight * 0.42 + trail * 0.10) * visible;
                float shadow = (body * 0.08 + bottomShade * 0.30 + trail * 0.12) * visible;
                return float2(highlight, shadow);
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

            float hazeLayer(float2 uv) {
                float wave = noise21(float2(uv.x * 2.0 + time * 0.010, uv.y * 4.2));
                float horizon = smoothstep(0.10, 0.48, uv.y) * (1.0 - smoothstep(0.82, 1.16, uv.y));
                return (0.36 + wave * 0.26) * horizon;
            }

            float hazeDust(float2 uv, float scale, float seed) {
                float2 p = uv * scale;
                p.x += time * 0.008;
                float2 cell = floor(p);
                float2 local = fract(p) - 0.5;
                float random = hash21(cell + seed);
                local += (float2(random, hash21(cell + seed + 9.0)) - 0.5) * 0.62;
                return smoothstep(0.045, 0.0, length(local)) * smoothstep(0.72, 0.98, random);
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

                if (weatherPass == 1.0 && (mode == 1.0 || mode == 4.0 || mode == 5.0)) {
                    float rain = rainLayer(aspectUv, 18.0, 1.65, 1.0) * 0.24;
                    rain += rainLayer(aspectUv, 27.0, 2.15, 8.0) * 0.38;
                    rain += rainLayer(aspectUv, 38.0, 2.75, 19.0) * 0.52;
                    alpha += rain;
                }

                if (weatherPass == 2.0 && (mode == 1.0 || mode == 4.0 || mode == 5.0)) {
                    float2 largeDrops = glassDropLayer(aspectUv, 5.5, 0.026, 31.0);
                    float2 mediumDrops = glassDropLayer(aspectUv, 10.0, 0.016, 73.0);
                    float2 smallDrops = glassDropLayer(aspectUv, 18.0, 0.007, 117.0);
                    glassHighlight = clamp(largeDrops.x + mediumDrops.x * 0.78 + smallDrops.x * 0.46, 0.0, 0.72);
                    glassShadow = clamp(largeDrops.y + mediumDrops.y * 0.82 + smallDrops.y * 0.50, 0.0, 0.58);
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

                if (weatherPass == 1.0 && (mode == 3.0 || mode == 4.0)) {
                    float fog = fogLayer(aspectUv);
                    alpha += fog * (mode == 4.0 ? 0.16 : 0.58);
                    color = mix(color, daylight > 0.5 ? float3(0.82, 0.89, 0.91) : float3(0.42, 0.50, 0.62), 0.58);
                }

                if (weatherPass == 1.0 && mode == 10.0) {
                    float haze = hazeLayer(uv);
                    float dust = hazeDust(aspectUv, 31.0, 17.0) * 0.16;
                    dust += hazeDust(aspectUv, 47.0, 43.0) * 0.10;
                    alpha += haze * 0.40 + dust;
                    color = daylight > 0.5 ? float3(0.86, 0.76, 0.58) : float3(0.48, 0.43, 0.43);
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

                if (weatherPass == 0.0 && (mode == 1.0 || mode == 2.0 || mode == 4.0 || mode == 5.0 ||
                    mode == 6.0 || mode == 7.0 || mode == 8.0 || mode == 9.0)) {
                    float clouds = driftingCloud(aspectUv, 0.19, 0.22, 0.006 * windFactor, 0.08) * 0.42;
                    clouds = max(clouds, driftingCloud(aspectUv, 0.34, 0.31, 0.004 * windFactor, 0.53) * 0.50);
                    clouds = max(clouds, driftingCloud(aspectUv, 0.48, 0.18, 0.008 * windFactor, 0.79) * 0.34);
                    if (mode == 7.0) {
                        clouds = max(clouds, driftingCloud(aspectUv, 0.26, 0.38, 0.003, 0.31) * 0.58);
                        clouds = max(clouds, driftingCloud(aspectUv, 0.55, 0.28, 0.005, 0.93) * 0.46);
                    }
                    if (mode == 1.0 || mode == 2.0 || mode == 4.0 || mode == 5.0) {
                        clouds = max(clouds, driftingCloud(aspectUv, 0.28, 0.40, 0.004 * windFactor, 0.31) * 0.62);
                        clouds = max(clouds, driftingCloud(aspectUv, 0.47, 0.34, 0.005 * windFactor, 0.66) * 0.54);
                    }
                    if (mode == 8.0) {
                        clouds = driftingCloud(aspectUv, 0.18, 0.13, 0.022 * windFactor, 0.08) * 0.34;
                        clouds = max(clouds, driftingCloud(aspectUv, 0.34, 0.16, 0.030 * windFactor, 0.53) * 0.40);
                        clouds = max(clouds, driftingCloud(aspectUv, 0.50, 0.11, 0.039 * windFactor, 0.93) * 0.30);
                    }
                    alpha += clouds;
                    float stormy = (mode == 1.0 || mode == 2.0 || mode == 4.0 || mode == 5.0 || mode == 9.0) ? 1.0 : 0.0;
                    float3 fairColor = daylight > 0.5 ? float3(0.94, 0.97, 1.0) : float3(0.48, 0.54, 0.64);
                    float3 stormColor = daylight > 0.5 ? float3(0.48, 0.55, 0.64) : float3(0.24, 0.29, 0.38);
                    color = mix(fairColor, stormColor, stormy);
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
                return half4(half3(premultiplied), half(a));
            }
        """
    }
}
