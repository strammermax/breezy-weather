package com.wolkentypes.app.clouds

import android.content.Context

private const val PREFS_NAME = "cloud_presets"

/**
 * Per-weertype opgeslagen "Wolken instellen"-aanpassingen (lagen, dichtheid, wind, diepte).
 * Puur SharedPreferences met handmatige encodering, zodat we geen serialisatie-library
 * hoeven toe te voegen voor deze kleine dataset.
 */
data class PersistedPreset(
    val layers: Map<CloudLayer, LayerCloudConfig>,
    val density: Float,
    val wind: Float,
    val depth: Float
)

fun savePreset(
    context: Context,
    weatherId: String,
    layers: Map<CloudLayer, LayerCloudConfig>,
    density: Float,
    wind: Float,
    depth: Float
) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().apply {
        putFloat("$weatherId.density", density)
        putFloat("$weatherId.wind", wind)
        putFloat("$weatherId.depth", depth)
        CloudLayer.entries.forEach { layer ->
            val config = layers[layer] ?: LayerCloudConfig()
            putString("$weatherId.${layer.name}", encodeLayerConfig(config))
        }
        apply()
    }
}

fun loadPreset(context: Context, weatherId: String): PersistedPreset? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.contains("$weatherId.density")) return null
    val density = prefs.getFloat("$weatherId.density", 1f)
    val wind = prefs.getFloat("$weatherId.wind", 1f)
    val depth = prefs.getFloat("$weatherId.depth", 1f)
    val layers = CloudLayer.entries.associateWith { layer ->
        prefs.getString("$weatherId.${layer.name}", null)
            ?.let { decodeLayerConfig(it) }
            ?: LayerCloudConfig()
    }
    return PersistedPreset(layers, density, wind, depth)
}

private fun encodeLayerConfig(config: LayerCloudConfig): String {
    val types = config.types.joinToString(",") { it.name }
    return listOf(
        types,
        config.amount.name,
        config.sizeMultiplier,
        config.heightOffset,
        config.verticalSpread,
        config.speedMultiplier,
        config.alphaMultiplier,
        config.horizontalSpread
    ).joinToString(";")
}

private fun decodeLayerConfig(raw: String): LayerCloudConfig = runCatching {
    val parts = raw.split(";")
    val types = parts[0].takeIf { it.isNotEmpty() }
        ?.split(",")
        ?.mapNotNull { name -> CloudTextureType.entries.find { it.name == name } }
        ?.toSet()
        ?: emptySet()
    LayerCloudConfig(
        types = types,
        amount = CloudAmount.entries.find { it.name == parts[1] } ?: CloudAmount.NONE,
        sizeMultiplier = parts[2].toFloat(),
        heightOffset = parts[3].toFloat(),
        verticalSpread = parts[4].toFloat(),
        speedMultiplier = parts[5].toFloat(),
        alphaMultiplier = parts[6].toFloat(),
        horizontalSpread = parts[7].toFloat()
    )
}.getOrElse { LayerCloudConfig() }
