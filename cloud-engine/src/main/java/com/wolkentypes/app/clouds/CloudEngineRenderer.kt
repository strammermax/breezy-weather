package com.wolkentypes.app.clouds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import java.util.Calendar
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
            rebuildAssetPools()
            rebuildInstances()
        }
    var weatherId: String? = null
        set(value) {
            field = value
            reloadAssets()
        }
    var windSpeedMultiplier: Float = 1f
    var easterEggsEnabled: Boolean = true
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
    private val easterEggAssets: List<CloudAsset> = loadEasterEggAssets(context)
    private var assetsByLayer: Map<CloudLayer, List<CloudAsset>> = emptyMap()
    private var instances: List<CloudInstance> = emptyList()
    private val forcedAssetIndex = mutableMapOf<CloudLayer, Int>()

    // Debug-triggered easter egg (see triggerEasterEggNow()): 0L means "none pending".
    private var forcedEasterEggStartMillis: Long = 0L
    private var forcedEasterEggAssetIndex: Int = 0
    private var forcedEasterEggAlpha: Float = 0.55f
    private var forcedEasterEggSpeedMultiplier: Float = 1f

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
        rebuildAssetPools()
        rebuildInstances()
    }

    private fun rebuildAssetPools() {
        assetsByLayer = CloudLayer.entries.associateWith { layer ->
            val selected = profile.layers[layer]?.types.orEmpty().intersect(spriteTypes).let {
                if (layer == CloudLayer.OVERHEAD) it - CloudTextureType.SMOKE else it
            }
            cloudAssets.filter { it.type in selected && (it.targetLayer == null || it.targetLayer == layer) }
        }
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
                        // Wide enough that the Height/Vertical spread sliders (up to +-.22 offset,
                        // up to 2x spread) never get silently clipped before reaching the screen
                        // edges, e.g. OVERHEAD's band center sits near 0 so a negative Height
                        // offset combined with max spread can reach well below the old -.2 floor.
                        yFraction = (
                            bandCenter +
                                (rnd.nextFloat() * 2f - 1f) * bandHalfHeight * config.verticalSpread +
                                config.heightOffset
                            ).coerceIn(-.5f, 1.15f),
                        scale = layer.scaleRange.start + rnd.nextFloat() *
                            (layer.scaleRange.endInclusive - layer.scaleRange.start),
                        assetIndex = forcedAssetIndex[layer]?.let { Math.floorMod(it, pool.size) }
                            ?: rnd.nextInt(pool.size),
                        baseWidthDp = 230f + rnd.nextFloat() * 160f,
                        rotationDegrees = -2f + rnd.nextFloat() * 4f,
                        alpha = (layer.alpha * config.alphaMultiplier).coerceIn(0f, 1f)
                    )
                }
            }
        }
    }

    fun assetNames(layer: CloudLayer): List<String> = assetsByLayer[layer].orEmpty().map { it.fileName }

    fun selectedAssetName(layer: CloudLayer): String? {
        val pool = assetsByLayer[layer].orEmpty()
        if (pool.isEmpty()) return null
        val index = forcedAssetIndex[layer]?.let { Math.floorMod(it, pool.size) }
            ?: instances.firstOrNull { it.layer == layer }?.assetIndex
            ?: 0
        return pool.getOrNull(index)?.fileName
    }

    private fun selectedLayerAsset(layer: CloudLayer, seedOffset: Long = 0L): CloudAsset? {
        val pool = assetsByLayer[layer].orEmpty()
        if (pool.isEmpty()) return null
        val index = forcedAssetIndex[layer]?.let { Math.floorMod(it, pool.size) }
            ?: Math.floorMod(randomSeed + seedOffset, pool.size.toLong()).toInt()
        return pool[index]
    }

    fun cycleAsset(layer: CloudLayer, delta: Int): String? {
        val pool = assetsByLayer[layer].orEmpty()
        if (pool.isEmpty()) return null
        val current = forcedAssetIndex[layer]
            ?: instances.firstOrNull { it.layer == layer }?.assetIndex
            ?: 0
        forcedAssetIndex[layer] = Math.floorMod(current + delta, pool.size)
        rebuildInstances()
        return selectedAssetName(layer)
    }

    fun useAutomaticAsset(layer: CloudLayer): String? {
        forcedAssetIndex.remove(layer)
        rebuildInstances()
        return selectedAssetName(layer)
    }

    fun randomizeAssets() {
        forcedAssetIndex.clear()
        randomSeed += 1L
    }

    /** Number of easter-egg cloud assets available, for a debug UI to build an asset picker. */
    val easterEggAssetCount: Int get() = easterEggAssets.size

    /**
     * Debug-only: immediately shows an easter-egg cloud crossing the screen, bypassing the
     * normal once/twice-a-day time gate in [drawDailyEasterEgg]. Calling it again while one is
     * still traveling replaces it instead of queueing another -- fast taps swap, they don't
     * stack. [assetIndex] picks a specific asset (null cycles to the next one automatically).
     * [alpha] and [speedMultiplier] are debug-only overrides, not persisted anywhere.
     */
    fun triggerEasterEggNow(assetIndex: Int?, alpha: Float, speedMultiplier: Float) {
        if (easterEggAssets.isEmpty()) return
        forcedEasterEggAssetIndex = assetIndex?.let { Math.floorMod(it, easterEggAssets.size) }
            ?: (forcedEasterEggAssetIndex + 1) % easterEggAssets.size
        forcedEasterEggAlpha = alpha.coerceIn(0f, 1f)
        forcedEasterEggSpeedMultiplier = speedMultiplier.coerceIn(0.1f, 5f)
        forcedEasterEggStartMillis = System.currentTimeMillis()
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
        drawDailyEasterEgg(canvas, screenWidth, screenHeight, pxPerDp)

        if (shaderPaint != null) drawHazeShader(canvas, screenWidth, screenHeight, time)
    }

    /**
     * Laat maximaal twee gedeelde easter eggs per lokale kalenderdag door het volledige beeld
     * reizen. De dag bepaalt de twee tijdstippen, zodat hertekenen geen extra egg spawnt.
     */
    private fun drawDailyEasterEgg(
        canvas: Canvas,
        screenWidth: Float,
        screenHeight: Float,
        pxPerDp: Float,
    ) {
        if (easterEggAssets.isEmpty()) return

        if (forcedEasterEggStartMillis != 0L) {
            val travelSeconds = FORCED_EASTER_EGG_TRAVEL_SECONDS / forcedEasterEggSpeedMultiplier.coerceAtLeast(.05f)
            val forcedElapsedSeconds = (System.currentTimeMillis() - forcedEasterEggStartMillis) / 1000f
            if (forcedElapsedSeconds in 0f..travelSeconds) {
                val asset = easterEggAssets[forcedEasterEggAssetIndex % easterEggAssets.size]
                val progress = forcedElapsedSeconds / travelSeconds
                // Smaller than the real daily egg (screenWidth * ~.42-.63) -- at that size it
                // dwarfed the regular clouds around it.
                val width = screenWidth * FORCED_EASTER_EGG_SIZE_FRACTION
                val height = width / asset.aspectRatio
                val padding = maxOf(screenWidth * .08f, 24f * pxPerDp)
                val left = -width - padding + progress * (screenWidth + width + padding * 2f)
                val top = screenHeight * .30f
                val fade = minOf((progress / .1f).coerceAtMost(1f), ((1f - progress) / .1f).coerceAtMost(1f))
                drawAsset(canvas, asset, left, top, width, height, forcedEasterEggAlpha * fade)
                return
            }
            forcedEasterEggStartMillis = 0L
        }

        if (!easterEggsEnabled || weatherId == "clear") return

        // Repeats every EASTER_EGG_INTERVAL_MINUTES (one cycle = one crossing window), instead
        // of the old fixed twice-a-day windows. Travel speed matches the clouds' own pace
        // (windSpeedMultiplier) so a windy scene sweeps the egg across faster, same as the
        // cloud layers around it -- not a fixed duration independent of the weather.
        val nowMillis = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val daySeed = calendar.get(Calendar.YEAR) * 400L + calendar.get(Calendar.DAY_OF_YEAR)
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val secondFraction = calendar.get(Calendar.SECOND) / 60f + calendar.get(Calendar.MILLISECOND) / 60_000f
        val minuteOfDayF = minuteOfDay + secondFraction

        val cycleIndex = (minuteOfDayF / EASTER_EGG_INTERVAL_MINUTES).toLong()
        val cycleStart = cycleIndex * EASTER_EGG_INTERVAL_MINUTES
        val travelMinutes = EASTER_EGG_BASE_TRAVEL_MINUTES / windSpeedMultiplier.coerceAtLeast(.1f)
        val progress = (minuteOfDayF - cycleStart) / travelMinutes
        if (progress !in 0f..1f) return

        val cycleSeed = daySeed * 2_654_435_761L + cycleIndex * 40_503L + randomSeed
        // Random 1 or 2 eggs per cycle (roughly a quarter of cycles get a second one), picked
        // from the full asset pool -- no more reserving the last asset for a separate weekly-
        // special path, everything's just part of the same random draw now.
        val eggCount = if (Math.floorMod(cycleSeed, 4L) == 0L) 2 else 1
        val fade = minOf((progress / .1f).coerceAtMost(1f), ((1f - progress) / .1f).coerceAtMost(1f))

        for (egg in 0 until eggCount) {
            val eggSeed = cycleSeed * 97L + egg * 7_919L
            val asset = easterEggAssets[Math.floorMod(eggSeed, easterEggAssets.size.toLong()).toInt()]
            val sizeVariation = Math.floorMod(eggSeed shr 8, 21L).toFloat() / 100f
            val width = screenWidth * (.32f + sizeVariation)
            val height = width / asset.aspectRatio
            val padding = maxOf(screenWidth * .08f, 24f * pxPerDp)
            // Second egg trails slightly behind and offset in height, instead of both eggs
            // moving in perfect lockstep across the exact same line.
            val eggProgress = if (egg == 0) progress else (progress - .08f).coerceIn(0f, 1f)
            val left = -width - padding + eggProgress * (screenWidth + width + padding * 2f)
            val verticalRange = (screenHeight * .62f - height).coerceAtLeast(screenHeight * .12f)
            val topSeed = Math.floorMod(eggSeed shr 16, 10_000L).toFloat() / 10_000f
            val top = screenHeight * .10f + verticalRange * topSeed
            drawAsset(canvas, asset, left, top, width, height, EASTER_EGG_ALPHA * fade)
        }
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
            (
                horizonConfig.amount.weight / CloudAmount.A_LOT.weight.toFloat() *
                    .82f * horizonConfig.alphaMultiplier
                ).coerceIn(0f, 1f)
        )
    }

    // Gesloten dekken bestaan uit schermbrede stratusplaten, niet uit losse billboards. De
    // platen overlappen royaal zodat er ook tijdens beweging geen open sleuf ontstaat.
    private fun drawStratusPlates(canvas: Canvas, screenWidth: Float, screenHeight: Float, time: Float) {
        fun plate(layer: CloudLayer, centerFraction: Float, widthFactor: Float, phase: Float) {
            val config = profile.layers[layer] ?: return
            if (config.amount == CloudAmount.NONE) return
            val bank = selectedLayerAsset(layer) ?: return
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
                    (
                        overheadConfig.amount.weight / CloudAmount.A_LOT.weight.toFloat() *
                            .48f * overheadConfig.alphaMultiplier
                        ).coerceIn(0f, 1f)
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
                (
                    overheadConfig.amount.weight / CloudAmount.A_LOT.weight.toFloat() *
                        .9f * overheadConfig.alphaMultiplier
                    ).coerceIn(0f, 1f)
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
            // De bitmap begint volledig buiten de linker schermrand en wordt pas hergebruikt
            // nadat ook zijn achterrand rechts buiten beeld is. Een kleine extra veiligheidszone
            // voorkomt afrondings-/rotatiepops, zonder de lange onzichtbare halve-schermmarge die
            // eerder gaten in een wolkenlaag veroorzaakte.
            val offscreenPadding = maxOf(screenWidth * .08f, 24f * pxPerDp)
            val cycleStart = -width - offscreenPadding
            val travel = screenWidth + width + offscreenPadding * 2f
            val phase = cloud.laneOffsetFraction * travel + time * speed
            val wrappedPhase = ((phase % travel) + travel) % travel
            val x = cycleStart + wrappedPhase
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
        /** How often a new easter-egg crossing cycle starts. */
        const val EASTER_EGG_INTERVAL_MINUTES = 5f

        /** Crossing duration at windSpeedMultiplier=1 -- scaled by the clouds' own wind speed
         *  so the egg always keeps pace with them instead of a fixed independent duration. */
        const val EASTER_EGG_BASE_TRAVEL_MINUTES = 1f

        const val EASTER_EGG_ALPHA = 0.5f

        // Debug-triggered eggs cross the screen much faster than the real daily ones, so testers
        // tapping the button repeatedly actually see each one instead of waiting minutes.
        const val FORCED_EASTER_EGG_TRAVEL_SECONDS = 3f
        const val FORCED_EASTER_EGG_SIZE_FRACTION = 0.28f

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
