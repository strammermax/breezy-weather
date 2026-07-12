package com.wolkentypes.app.clouds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Headless (no `View`/`Choreographer`) cloud renderer: the same model-/positiewiskunde and
 * Canvas/AGSL draw logic as [CloudSurfaceView], but driven externally frame-by-frame instead of
 * owning its own render loop. This is the shape a `WallpaperService`-style draw loop (which
 * already has its own Canvas and timing, e.g. LiveWeatherApp's `MaterialLiveWallpaperService`)
 * needs: call [update] then [draw] once per frame.
 */
class CloudEngineRenderer(private val context: Context) {

    var profile: CloudProfile = cloudProfileFor("partly_cloudy")
        set(value) {
            field = value
            rebuildInstances()
        }
    var weatherId: String? = null
        set(value) {
            field = value
            reloadAssets()
        }
    var windSpeedMultiplier: Float = 1f
    var densityMultiplier: Float = 1f
        set(value) {
            field = value
            rebuildInstances()
        }
    var layerDepthMultiplier: Float = 1f
    var randomSeed: Long = 42L
        set(value) {
            field = value
            rebuildInstances()
        }

    private var cloudAssets: List<CloudAsset> = emptyList()
    private var assetsByLayer: Map<CloudLayer, List<CloudAsset>> = emptyMap()
    private var instances: List<CloudInstance> = emptyList()

    private val spriteTypes = setOf(CloudTextureType.WHITE, CloudTextureType.DARK, CloudTextureType.SMOKE)
    private val overcastFamily = setOf("overcast", "drizzle", "rain", "snow", "snow_showers")

    private var elapsedSeconds = 0f
    private var globalAlpha = 1f

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = RectF()

    private val shader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(HAZE_SHADER_SOURCE) else null
    private val shaderPaint: Paint? = shader?.let { Paint().apply { this.shader = it } }

    init {
        reloadAssets()
    }

    /** Advances the animation clock. Call once per frame before [draw]. */
    fun update(elapsedSeconds: Float) {
        this.elapsedSeconds = elapsedSeconds
    }

    private fun reloadAssets() {
        cloudAssets = loadCloudAssets(context, weatherId)
        assetsByLayer = CloudLayer.entries.associateWith { layer ->
            val selected = profile.layers[layer]?.types.orEmpty().intersect(spriteTypes).let {
                if (layer == CloudLayer.OVERHEAD) it - CloudTextureType.SMOKE else it
            }
            cloudAssets.filter { it.type in selected && (it.targetLayer == null || it.targetLayer == layer) }
        }
        rebuildInstances()
    }

    private fun rebuildInstances() {
        val rnd = Random(randomSeed)
        instances = CloudLayer.entries.flatMap { layer ->
            val pool = assetsByLayer[layer].orEmpty()
            val config = profile.layers[layer] ?: LayerCloudConfig()
            val count = (config.amount.weight * densityMultiplier * profile.density).roundToInt()
            if (pool.isEmpty() || count <= 0) {
                emptyList()
            } else {
                List(count) { index ->
                    val bandCenter = (layer.heightBand.start + layer.heightBand.endInclusive) / 2f
                    val bandHalfHeight = (layer.heightBand.endInclusive - layer.heightBand.start) / 2f
                    val lane = (index + rnd.nextFloat() * 0.72f) / count
                    CloudInstance(
                        layer = layer,
                        laneOffsetFraction = .5f + (lane - .5f) * config.horizontalSpread,
                        yFraction = (
                            bandCenter +
                                (rnd.nextFloat() * 2f - 1f) * bandHalfHeight * config.verticalSpread +
                                config.heightOffset
                            ).coerceIn(-.2f, .9f),
                        scale = layer.scaleRange.start + rnd.nextFloat() *
                            (layer.scaleRange.endInclusive - layer.scaleRange.start),
                        assetIndex = rnd.nextInt(pool.size),
                        baseWidthDp = 230f + rnd.nextFloat() * 160f,
                        rotationDegrees = -2f + rnd.nextFloat() * 4f,
                        alpha = (layer.alpha * config.alphaMultiplier).coerceIn(0f, 1f)
                    )
                }
            }
        }
    }

    private fun bankVariant(type: CloudTextureType): CloudAsset? {
        val options = cloudAssets.filter { it.type == type }
        val variantSeed = profile.hashCode().toLong() xor randomSeed
        return options.getOrNull(Math.floorMod(variantSeed, options.size.coerceAtLeast(1).toLong()).toInt())
    }

    private fun drawAsset(
        canvas: Canvas,
        asset: CloudAsset,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        alpha: Float,
        rotationDegrees: Float = 0f,
    ) {
        srcRect.set(
            asset.contentOffsetX,
            asset.contentOffsetY,
            asset.contentOffsetX + asset.contentWidth,
            asset.contentOffsetY + asset.contentHeight
        )
        dstRect.set(left, top, left + width, top + height)
        bitmapPaint.alpha = (alpha.coerceIn(0f, 1f) * globalAlpha.coerceIn(0f, 1f) * 255).roundToInt()
        val save = canvas.save()
        if (rotationDegrees != 0f) canvas.rotate(rotationDegrees, dstRect.centerX(), dstRect.centerY())
        canvas.drawBitmap(asset.bitmap, srcRect, dstRect, bitmapPaint)
        canvas.restoreToCount(save)
    }

    /**
     * Draws the current frame. [alpha] is the cross-transition contribution (0f..1f), matching
     * the `contribution`/`alpha` convention used by callers like
     * `WallpaperWeatherEffectRenderer.drawBackgroundWeatherPass`.
     */
    fun draw(canvas: Canvas, alpha: Float = 1f) {
        val screenWidth = canvas.width.toFloat()
        val screenHeight = canvas.height.toFloat()
        if (screenWidth <= 0f || screenHeight <= 0f || alpha <= 0f) return
        globalAlpha = alpha
        val pxPerDp = context.resources.displayMetrics.density
        val time = elapsedSeconds
        val isOvercastFamily = weatherId in overcastFamily

        drawHorizonBank(canvas, screenWidth, screenHeight, pxPerDp, time, isOvercastFamily)
        if (isOvercastFamily) drawStratusPlates(canvas, screenWidth, screenHeight, time)
        drawOverheadVeilAndBank(canvas, screenWidth, screenHeight, pxPerDp, time, isOvercastFamily)
        if (weatherId in setOf("rain", "showers")) drawRainFront(canvas, screenWidth, screenHeight, pxPerDp, time)
        if (!isOvercastFamily) drawBillboardInstances(canvas, screenWidth, screenHeight, pxPerDp, time)

        if (shaderPaint != null) drawHazeShader(canvas, screenWidth, screenHeight, time)
    }

    private fun drawHorizonBank(
        canvas: Canvas,
        screenWidth: Float,
        screenHeight: Float,
        pxPerDp: Float,
        time: Float,
        isOvercastFamily: Boolean,
    ) {
        val horizonConfig = profile.layers[CloudLayer.HORIZON] ?: LayerCloudConfig()
        if (CloudTextureType.HORIZON_BANK !in horizonConfig.types || horizonConfig.amount == CloudAmount.NONE) return
        val bank = bankVariant(CloudTextureType.HORIZON_BANK) ?: return
        val width = screenWidth * 1.85f * horizonConfig.sizeMultiplier
        val height = width / bank.aspectRatio
        val travel = screenWidth + width
        val x = if (isOvercastFamily) {
            -(width - screenWidth) / 2f +
                sin(time * .08f * windSpeedMultiplier * horizonConfig.speedMultiplier) * screenWidth * .06f
        } else {
            -width + (
                (time * .7f * pxPerDp * windSpeedMultiplier * horizonConfig.speedMultiplier + travel * .3f) % travel
                )
        }
        val top = screenHeight *
            ((if (weatherId == "cloudy") .76f else .655f) + horizonConfig.heightOffset) - height / 2f
        drawAsset(
            canvas,
            bank,
            x,
            top,
            width,
            height,
            (horizonConfig.amount.weight / 4f * .82f * horizonConfig.alphaMultiplier).coerceIn(0f, 1f)
        )
    }

    // Gesloten dekken bestaan uit schermbrede stratusplaten, niet uit losse billboards. De
    // platen overlappen royaal zodat er ook tijdens beweging geen open sleuf ontstaat.
    private fun drawStratusPlates(canvas: Canvas, screenWidth: Float, screenHeight: Float, time: Float) {
        fun plate(layer: CloudLayer, centerFraction: Float, widthFactor: Float, phase: Float) {
            val config = profile.layers[layer] ?: return
            if (config.amount == CloudAmount.NONE) return
            val bank = cloudAssets.firstOrNull { it.targetLayer == layer } ?: return
            val width = screenWidth * widthFactor * config.sizeMultiplier
            val height = width / bank.aspectRatio
            val x = -(width - screenWidth) / 2f +
                sin(time * .075f * windSpeedMultiplier * config.speedMultiplier + phase) * screenWidth * .07f
            val top = screenHeight * (centerFraction + config.heightOffset) - height / 2f
            drawAsset(canvas, bank, x, top, width, height, config.alphaMultiplier.coerceIn(0f, 1f))
        }

        val isDrizzle = weatherId == "drizzle"
        val isRain = weatherId == "rain"
        plate(
            CloudLayer.DISTANT,
            if (isDrizzle) {
                .60f
            } else if (isRain) {
                .66f
            } else {
                .55f
            },
            1.95f,
            4.1f
        )
        plate(
            CloudLayer.MIDFIELD,
            if (isDrizzle) {
                .39f
            } else if (isRain) {
                .43f
            } else {
                .36f
            },
            1.85f,
            2.4f
        )
        plate(
            CloudLayer.NEAR,
            if (isDrizzle) {
                .16f
            } else if (isRain) {
                .20f
            } else {
                .18f
            },
            1.78f,
            .8f
        )
    }

    private fun drawOverheadVeilAndBank(
        canvas: Canvas,
        screenWidth: Float,
        screenHeight: Float,
        pxPerDp: Float,
        time: Float,
        isOvercastFamily: Boolean,
    ) {
        val overheadConfig = profile.layers[CloudLayer.OVERHEAD] ?: LayerCloudConfig()
        if (CloudTextureType.SMOKE in overheadConfig.types && overheadConfig.amount != CloudAmount.NONE) {
            cloudAssets.firstOrNull { it.type == CloudTextureType.SMOKE }?.let { veil ->
                val width = screenWidth * 1.75f * overheadConfig.sizeMultiplier
                val height = width / veil.aspectRatio
                val travel = screenWidth + width
                val phase = time * .22f * pxPerDp * windSpeedMultiplier * overheadConfig.speedMultiplier
                val x = -width + ((phase + travel * .55f) % travel)
                val top = screenHeight * (.17f + overheadConfig.heightOffset) - height / 2f
                drawAsset(
                    canvas,
                    veil,
                    x,
                    top,
                    width,
                    height,
                    (overheadConfig.amount.weight / 4f * .48f * overheadConfig.alphaMultiplier).coerceIn(0f, 1f)
                )
            }
        }
        if (CloudTextureType.OVERHEAD_BANK !in overheadConfig.types || overheadConfig.amount == CloudAmount.NONE) return

        bankVariant(CloudTextureType.OVERHEAD_BANK)?.let { bank ->
            val width = screenWidth * 1.65f * overheadConfig.sizeMultiplier
            val height = width / bank.aspectRatio
            val x = if (isOvercastFamily) {
                -(width - screenWidth) / 2f +
                    sin(time * .07f * windSpeedMultiplier * overheadConfig.speedMultiplier) * screenWidth * .06f
            } else {
                val phase = time * .45f * pxPerDp * windSpeedMultiplier * overheadConfig.speedMultiplier
                -screenWidth * .32f + phase % (screenWidth * .25f)
            }
            val top = screenHeight * (.06f + overheadConfig.heightOffset) - height / 2f
            drawAsset(
                canvas,
                bank,
                x,
                top,
                width,
                height,
                (overheadConfig.amount.weight / 4f * .9f * overheadConfig.alphaMultiplier).coerceIn(0f, 1f)
            )
        }

        // Bewolkt heeft een tweede brede wolkenplaat nodig: de referentie toont een gesloten
        // dek en geen blauwe opening tussen overhead- en middenlaag.
        if (weatherId != "cloudy") return
        val banks = cloudAssets.filter { it.type == CloudTextureType.OVERHEAD_BANK }
        banks.getOrNull(1)?.let { bank ->
            val width = screenWidth * 1.58f
            val height = width / bank.aspectRatio
            val x = -screenWidth * .18f - (time * .28f * pxPerDp * windSpeedMultiplier) % (screenWidth * .18f)
            val top = screenHeight * .30f - height / 2f
            drawAsset(canvas, bank, x, top, width, height, .96f)
        }

        val midfieldBanks = cloudAssets.filter { it.fileName.startsWith("dark-midfield-cloudy-") }
        val upperBank = midfieldBanks.getOrNull(
            Math.floorMod(randomSeed, midfieldBanks.size.coerceAtLeast(1).toLong()).toInt()
        )
        upperBank?.let { bank ->
            val width = screenWidth * 1.72f
            val height = width / bank.aspectRatio
            val travel = screenWidth + width
            val x = -width + (
                (time * .34f * pxPerDp * windSpeedMultiplier + travel * .46f) % travel
                )
            val top = screenHeight * .56f - height / 2f
            drawAsset(canvas, bank, x, top, width, height, .98f)

            // Onderste tussendek: overlapt zowel de brede middenplaat als de horizonbank, zodat
            // er tijdens het drijven geen grijze/blauwe sleuf tussen beide lagen kan ontstaan.
            val lowerBank = midfieldBanks.getOrNull(
                Math.floorMod(randomSeed + 1L, midfieldBanks.size.coerceAtLeast(1).toLong()).toInt()
            ) ?: bank
            val lowerWidth = screenWidth * 1.52f
            val lowerHeight = lowerWidth / lowerBank.aspectRatio
            val lowerTravel = screenWidth + lowerWidth
            val lowerX = -lowerWidth + (
                (time * .24f * pxPerDp * windSpeedMultiplier + lowerTravel * .76f) % lowerTravel
                )
            val lowerTop = screenHeight * .70f - lowerHeight / 2f
            drawAsset(canvas, lowerBank, lowerX, lowerTop, lowerWidth, lowerHeight, .97f)
        }
    }

    private fun drawRainFront(canvas: Canvas, screenWidth: Float, screenHeight: Float, pxPerDp: Float, time: Float) {
        val front = cloudAssets.firstOrNull { it.fileName == "dark-midfield-rain-front-01.webp" } ?: return
        val isShower = weatherId == "showers"
        val width = screenWidth * (if (isShower) 1.38f else 1.82f)
        val height = width / front.aspectRatio
        val x = if (isShower) {
            -width * .72f + (time * 1.5f * pxPerDp * windSpeedMultiplier) % (screenWidth + width)
        } else {
            -(width - screenWidth) / 2f + sin(time * .12f * windSpeedMultiplier) * screenWidth * .04f
        }
        val top = screenHeight * (if (isShower) .40f else .46f) - height / 2f
        drawAsset(canvas, front, x, top, width, height, if (isShower) .96f else 1f)
    }

    private fun drawBillboardInstances(
        canvas: Canvas,
        screenWidth: Float,
        screenHeight: Float,
        pxPerDp: Float,
        time: Float,
    ) {
        instances.forEach { cloud ->
            val pool = assetsByLayer[cloud.layer].orEmpty()
            val asset = pool.getOrNull(cloud.assetIndex) ?: return@forEach
            val depth = cloud.layer.cameraParallax
            val depthScale = (1f + (depth - .5f) * (layerDepthMultiplier - 1f) * .7f).coerceAtLeast(.55f)
            val layerSize = profile.layers[cloud.layer]?.sizeMultiplier ?: 1f
            val width = cloud.baseWidthDp * pxPerDp * cloud.scale * layerSize * depthScale
            val height = width / asset.aspectRatio
            val layerSpeed = profile.layers[cloud.layer]?.speedMultiplier ?: 1f
            val speed = cloud.layer.baseSpeedDpPerSec * pxPerDp * windSpeedMultiplier * profile.speed * layerSpeed
            // Marge ruim voorbij het scherm (i.p.v. exact de wolkbreedte): anders kan de
            // wrap-around net op de rand vallen, wat als een zichtbare "pop" oogt.
            val margin = width + screenWidth * .5f
            val travel = screenWidth + margin * 2f
            val startX = cloud.laneOffsetFraction * travel - margin
            val rawX = startX + time * speed
            val x = ((rawX % travel) + travel) % travel - margin
            val top = cloud.yFraction * screenHeight - height / 2f
            drawAsset(canvas, asset, x, top, width, height, cloud.alpha, cloud.rotationDegrees)
        }
    }

    /**
     * Zachte, langzaam bewegende diepte-haze bovenop de sprite-compositie — het AGSL-analoog
     * van LiveWeatherApp's procedurele shaderlaag, hier gebruikt als een op zichzelf staand
     * GPU-effect in plaats van een vervanging van de sprite-rendering.
     */
    private fun drawHazeShader(canvas: Canvas, screenWidth: Float, screenHeight: Float, time: Float) {
        val paint = shaderPaint ?: return
        val runtimeShader = shader ?: return
        runtimeShader.setFloatUniform("resolution", screenWidth, screenHeight)
        runtimeShader.setFloatUniform("time", time)
        paint.alpha = (globalAlpha.coerceIn(0f, 1f) * 255).roundToInt()
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)
    }

    private companion object {
        // Eenvoudige waarde-ruis (hash + bilineaire interpolatie) + fbm van twee octaven,
        // standaardtechniek uit shader-programmering (geen overgenomen broncode) — tekent een
        // zachte, langzaam driftende sluier met lage dekking boven de sprite-wolken.
        const val HAZE_SHADER_SOURCE = """
            uniform float2 resolution;
            uniform float time;

            float hash(float2 p) {
                float3 p3 = fract(float3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            float noise(float2 p) {
                float2 i = floor(p);
                float2 f = fract(p);
                float a = hash(i);
                float b = hash(i + float2(1.0, 0.0));
                float c = hash(i + float2(0.0, 1.0));
                float d = hash(i + float2(1.0, 1.0));
                float2 u = f * f * (3.0 - 2.0 * f);
                return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
            }

            float fbm(float2 p) {
                float value = 0.0;
                float amplitude = 0.55;
                for (int i = 0; i < 2; i++) {
                    value += amplitude * noise(p);
                    p *= 2.0;
                    amplitude *= 0.5;
                }
                return value;
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;
                float2 p = uv * float2(3.0, 2.0) + float2(time * 0.015, time * 0.004);
                float n = fbm(p);
                float coverage = smoothstep(0.55, 0.85, n) * 0.10;
                return half4(1.0, 1.0, 1.0, 1.0) * coverage;
            }
        """
    }
}
