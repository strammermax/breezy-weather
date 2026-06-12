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
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
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
            } catch (_: Throwable) {
                // Fallback to canvas if shader fails to compile
                canvasRenderer = CanvasRenderer(weatherKind, daytime)
            }
        } else {
            canvasRenderer = CanvasRenderer(weatherKind, daytime)
        }
    }

    private var elapsedSeconds = 0f

    fun update(intervalMillis: Long, animate: Boolean) {
        if (animate) {
            val delta = min(intervalMillis, MAX_FRAME_INTERVAL_MILLIS).coerceAtLeast(0L)
            elapsedSeconds += delta / 1000f
            canvasRenderer?.update(delta)
        }
    }

    fun draw(canvas: Canvas) {
        if (canvas.width <= 0 || canvas.height <= 0) return

        val s = shader
        val sp = shaderPaint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && s != null && sp != null) {
            s.setFloatUniform("resolution", canvas.width.toFloat(), canvas.height.toFloat())
            s.setFloatUniform("time", elapsedSeconds)
            s.setFloatUniform("mode", shaderMode(weatherKind))
            s.setFloatUniform("daylight", if (daytime) 1f else 0f)
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), sp)
        } else {
            canvasRenderer?.draw(canvas)
        }
    }

    private class CanvasRenderer(val weatherKind: Int, val daytime: Boolean) {
        private val random = Random()
        private val particles = mutableListOf<Particle>()
        private val clouds = mutableListOf<CloudParticle>()
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
                weatherKind == WeatherView.WEATHER_KIND_THUNDER ||
                weatherKind == WeatherView.WEATHER_KIND_THUNDERSTORM
            )) {
                val count = if (weatherKind == WeatherView.WEATHER_KIND_CLOUD) 5 else 10
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
            }

            // Update clouds
            for (c in clouds) {
                c.x += c.speedX * deltaSec
                if (c.x > lastWidth + c.radius) {
                    c.x = -c.radius
                    c.y = random.nextFloat() * lastHeight * 0.5f // Keep clouds in upper half
                }
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
                else -> 0
            }
            for (i in 0 until count) {
                particles.add(Particle(random, lastWidth, lastHeight, true))
            }
        }

        fun draw(canvas: Canvas) {
            lastWidth = canvas.width
            lastHeight = canvas.height

            // Draw clouds/fog first (behind particles)
            for (c in clouds) {
                paint.color = if (daytime) Color.WHITE else Color.LTGRAY
                val alpha = if (weatherKind == WeatherView.WEATHER_KIND_FOG || weatherKind == WeatherView.WEATHER_KIND_HAZE) 0.3f else 0.5f
                paint.alpha = (alpha * 255 * c.alphaMod).toInt()
                canvas.drawCircle(c.x, c.y, c.radius, paint)
            }

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
                if (weatherKind == WeatherView.WEATHER_KIND_SNOW) {
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                } else {
                    // Rain streaks
                    paint.strokeWidth = p.size
                    canvas.drawLine(p.x, p.y, p.x + p.speedX * 0.02f, p.y + p.speedY * 0.02f, paint)
                }
            }
        }

        private inner class Particle(r: Random, w: Int, h: Int, randomizeY: Boolean) {
            var x = r.nextFloat() * w
            var y = if (randomizeY) r.nextFloat() * h else -10f
            val layer = r.nextInt(3) // 0: back, 1: mid, 2: front
            val alpha = when (layer) {
                0 -> 0.3f
                1 -> 0.6f
                else -> 0.9f
            }
            val size = when (weatherKind) {
                WeatherView.WEATHER_KIND_SNOW -> (2f + layer * 3f)
                else -> (1f + layer * 1f) // Rain thickness
            }
            val speedY = when (weatherKind) {
                WeatherView.WEATHER_KIND_SNOW -> (50f + layer * 50f)
                else -> (800f + layer * 400f) // Rain speed
            }
            val speedX = when (weatherKind) {
                WeatherView.WEATHER_KIND_SNOW -> (r.nextFloat() - 0.5f) * 30f
                else -> 20f // Slight slant for rain
            }
        }

        private class CloudParticle(r: Random, w: Int, h: Int) {
            var x = r.nextFloat() * w
            var y = r.nextFloat() * h * 0.7f
            val radius = w * 0.2f + r.nextFloat() * w * 0.3f
            val speedX = 10f + r.nextFloat() * 20f
            val alphaMod = 0.2f + r.nextFloat() * 0.3f
        }
    }

    companion object {
        private const val MAX_FRAME_INTERVAL_MILLIS = 100L

        fun supports(weatherKind: Int): Boolean = weatherKind in setOf(
            WeatherView.WEATHER_KIND_RAINY,
            WeatherView.WEATHER_KIND_SLEET,
            WeatherView.WEATHER_KIND_SNOW,
            WeatherView.WEATHER_KIND_FOG,
            WeatherView.WEATHER_KIND_HAZE,
            WeatherView.WEATHER_KIND_THUNDER,
            WeatherView.WEATHER_KIND_THUNDERSTORM,
            WeatherView.WEATHER_KIND_CLOUD,
            WeatherView.WEATHER_KIND_CLOUDY,
        )

        private fun shaderMode(weatherKind: Int): Float = when (weatherKind) {
            WeatherView.WEATHER_KIND_RAINY -> 1f
            WeatherView.WEATHER_KIND_SNOW -> 2f
            WeatherView.WEATHER_KIND_FOG, WeatherView.WEATHER_KIND_HAZE -> 3f
            WeatherView.WEATHER_KIND_THUNDER, WeatherView.WEATHER_KIND_THUNDERSTORM -> 4f
            WeatherView.WEATHER_KIND_SLEET -> 5f
            WeatherView.WEATHER_KIND_CLOUD, WeatherView.WEATHER_KIND_CLOUDY -> 6f
            else -> 0f
        }

        private const val SHADER_SOURCE = """
            uniform float2 resolution;
            uniform float time;
            uniform float mode;
            uniform float daylight;

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
                local.y = fract(local.y + time * speed + random) - 0.5;
                local.x += (random - 0.5) * 0.65;
                float streak = smoothstep(0.045, 0.0, abs(local.x))
                    * smoothstep(0.48, 0.08, abs(local.y));
                return streak * smoothstep(0.28, 0.96, random);
            }

            float snowLayer(float2 uv, float scale, float speed, float seed) {
                float2 p = uv * scale;
                p.x += sin(time * 0.55 + p.y * 0.8 + seed) * 0.18;
                p.y += time * speed;
                float2 cell = floor(p);
                float2 local = fract(p) - 0.5;
                float random = hash21(cell + seed);
                local.x += (random - 0.5) * 0.65;
                float radius = mix(0.055, 0.14, random);
                return smoothstep(radius, radius * 0.25, length(local));
            }

            float fogLayer(float2 uv) {
                float2 p = uv * float2(2.2, 4.0);
                p.x += time * 0.035;
                float broad = noise21(p) * 0.58 + noise21(p * 2.05 + 7.3) * 0.28;
                broad += noise21(p * 4.1 - 3.7) * 0.14;
                float horizon = smoothstep(0.03, 0.55, uv.y) * smoothstep(1.12, 0.38, uv.y);
                return smoothstep(0.36, 0.82, broad) * horizon;
            }

            float cloudLayer(float2 uv, float scale, float speed, float seed) {
                float2 p = uv * scale;
                p.x += time * speed;
                float n = noise21(p + seed);
                n = smoothstep(0.4, 0.8, n);
                return n * smoothstep(0.1, 0.4, uv.y) * smoothstep(0.9, 0.6, uv.y);
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;
                float aspect = resolution.x / resolution.y;
                float2 aspectUv = uv;
                aspectUv.x *= aspect;

                float alpha = 0.0;
                float3 color = float3(0.82, 0.91, 1.0);

                if (mode == 1.0 || mode == 4.0 || mode == 5.0) {
                    float rain = rainLayer(aspectUv, 18.0, 1.65, 1.0) * 0.24;
                    rain += rainLayer(aspectUv, 27.0, 2.15, 8.0) * 0.38;
                    rain += rainLayer(aspectUv, 38.0, 2.75, 19.0) * 0.52;
                    alpha += rain;

                    float2 dropUv = aspectUv * float2(7.0, 11.0);
                    float2 dropCell = floor(dropUv);
                    float2 dropLocal = fract(dropUv) - 0.5;
                    float dropRandom = hash21(dropCell + 31.0);
                    dropLocal.y += sin(time * 0.2 + dropRandom * 6.283) * 0.025;
                    float ring = smoothstep(0.19, 0.14, length(dropLocal))
                        - smoothstep(0.13, 0.08, length(dropLocal));
                    alpha += ring * smoothstep(0.78, 0.98, dropRandom) * 0.34;
                }

                if (mode == 2.0 || mode == 5.0) {
                    float snow = snowLayer(aspectUv, 7.0, 0.22, 2.0) * 0.42;
                    snow += snowLayer(aspectUv, 11.0, 0.34, 13.0) * 0.62;
                    snow += snowLayer(aspectUv, 17.0, 0.50, 29.0) * 0.82;
                    alpha += snow;
                    color = float3(0.96, 0.98, 1.0);
                }

                if (mode == 3.0 || mode == 4.0) {
                    float fog = fogLayer(aspectUv);
                    alpha += fog * (mode == 4.0 ? 0.16 : 0.34);
                    color = mix(color, daylight > 0.5 ? float3(0.82, 0.89, 0.91) : float3(0.42, 0.50, 0.62), 0.58);
                }

                if (mode == 6.0) {
                    float clouds = cloudLayer(aspectUv, 3.0, 0.02, 1.0) * 0.3;
                    clouds += cloudLayer(aspectUv, 5.0, 0.035, 10.0) * 0.2;
                    alpha += clouds;
                    color = daylight > 0.5 ? float3(1.0, 1.0, 1.0) : float3(0.6, 0.65, 0.7);
                }

                if (mode == 4.0) {
                    float cycle = fract(time / 8.7);
                    float lightning = smoothstep(0.032, 0.0, abs(cycle - 0.018));
                    lightning += smoothstep(0.018, 0.0, abs(cycle - 0.056)) * 0.45;
                    color = mix(color, float3(0.78, 0.84, 1.0), lightning);
                    alpha = min(0.82, alpha + lightning * 0.48);
                }

                return half4(half3(color), half(clamp(alpha, 0.0, 0.86)));
            }
        """
    }
}
