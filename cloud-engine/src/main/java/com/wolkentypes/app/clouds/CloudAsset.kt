package com.wolkentypes.app.clouds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal data class CloudAsset(
    val fileName: String,
    val targetLayer: CloudLayer?,
    val bitmap: Bitmap,
    val aspectRatio: Float,
    val type: CloudTextureType,
    val contentOffsetX: Int,
    val contentOffsetY: Int,
    val contentWidth: Int,
    val contentHeight: Int,
)

private const val SHARED_FOLDER = "clouds/generated"

/**
 * De transparante voorbeeldwolken uit assets worden eenmaal gedecodeerd en daarna door
 * alle instanties hergebruikt. Grote bronbestanden worden tijdens het laden verkleind om
 * onnodig bitmapgeheugen op telefoons te voorkomen.
 *
 * Elk weertype kan zijn eigen set wolken hebben in `clouds/generated/<weatherId>/` (zelfde
 * bestandsnaam-contract: prefixen white-, dark-, veil-, horizon-bank, overhead-bank). Als
 * die map niet bestaat of leeg is, valt dit terug op de gedeelde pool in
 * `clouds/generated/`, zodat weertypes zonder eigen set gewoon blijven werken.
 */
internal fun loadCloudAssets(context: Context, weatherId: String? = null): List<CloudAsset> {
    // Motregen, sneeuw en sneeuwbuien delen het zachte stratusmateriaal met egaal grijs.
    // De compositie en neerslag zijn wel eigen aan elk weertype, maar de wolkenplaten
    // horen bij dezelfde familie.
    val assetWeatherId = when (weatherId) {
        "drizzle", "rain", "snow", "snow_showers" -> "overcast"
        else -> weatherId
    }
    val perWeatherFolder = assetWeatherId?.let { "$SHARED_FOLDER/$it" }
    if (weatherId in setOf("rain", "showers")) {
        val baseFolder = if (weatherId == "rain") "$SHARED_FOLDER/overcast" else SHARED_FOLDER
        val rainFolder = "$SHARED_FOLDER/rain"
        return loadCloudAssetsFromFolder(context, baseFolder) +
            loadCloudAssetsFromFolder(context, rainFolder)
    }
    val folder = perWeatherFolder?.takeIf { hasWebpFiles(context, it) } ?: SHARED_FOLDER
    return loadCloudAssetsFromFolder(context, folder)
}

private fun hasWebpFiles(context: Context, folder: String): Boolean =
    context.assets.list(folder).orEmpty().any { it.endsWith(".webp", ignoreCase = true) }

private fun loadCloudAssetsFromFolder(context: Context, folder: String): List<CloudAsset> {
    // Bestandsnamen zijn het contract: hierdoor worden nieuwe, zelf gegenereerde assets
    // automatisch gevonden zonder de Kotlin-code opnieuw aan te passen. Assets zijn lossy WebP
    // (niet PNG) om de APK-grootte te beperken -- 34 bestanden gingen van 23MB naar 1,5MB.
    val paths = context.assets.list(folder).orEmpty()
        .filter { it.endsWith(".webp", ignoreCase = true) }
        .sorted()
        .mapNotNull { name ->
            val type = when {
                name.startsWith("white-") -> CloudTextureType.WHITE
                name.startsWith("dark-") -> CloudTextureType.DARK
                name.startsWith("veil-") -> CloudTextureType.SMOKE
                name.startsWith("horizon-bank") -> CloudTextureType.HORIZON_BANK
                name.startsWith("overhead-bank") -> CloudTextureType.OVERHEAD_BANK
                else -> null
            }
            type?.let { "$folder/$name" to it }
        }

    return paths.mapNotNull { (path, type) ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@mapNotNull null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1200 || bounds.outHeight / sampleSize > 1200) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = context.assets.open(path).use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@mapNotNull null

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width
        var top = bitmap.height
        var right = 0
        var bottom = 0
        pixels.forEachIndexed { index, color ->
            if ((color ushr 24) > 8) {
                val x = index % bitmap.width
                val y = index / bitmap.width
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        val contentWidth = (right - left + 1).coerceAtLeast(1)
        val contentHeight = (bottom - top + 1).coerceAtLeast(1)
        CloudAsset(
            fileName = path.substringAfterLast('/'),
            targetLayer = targetLayerFor(path.substringAfterLast('/')),
            bitmap = bitmap,
            aspectRatio = contentWidth.toFloat() / contentHeight,
            type = type,
            contentOffsetX = left.coerceAtMost(bitmap.width - 1),
            contentOffsetY = top.coerceAtMost(bitmap.height - 1),
            contentWidth = contentWidth,
            contentHeight = contentHeight
        )
    }
}

/** Optionele laagbinding via de bestandsnaam; assets zonder hint blijven algemeen inzetbaar. */
private fun targetLayerFor(name: String): CloudLayer? = when {
    name.startsWith("horizon-bank") -> CloudLayer.HORIZON
    name.startsWith("overhead-bank") -> CloudLayer.OVERHEAD
    "-near-" in name -> CloudLayer.NEAR
    "-midfield-" in name -> CloudLayer.MIDFIELD
    "-distant-" in name -> CloudLayer.DISTANT
    else -> null
}
