/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperRepository
import com.wolkentypes.app.clouds.CloudAmount
import com.wolkentypes.app.clouds.CloudLayer
import com.wolkentypes.app.clouds.CloudSurfaceView
import com.wolkentypes.app.clouds.CloudTextureType
import com.wolkentypes.app.clouds.LayerCloudConfig
import com.wolkentypes.app.clouds.cloudProfileFor
import com.wolkentypes.app.clouds.loadPreset
import com.wolkentypes.app.clouds.savePreset
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Debug-build-only per-layer tuning screen for the `:cloud-engine` renderer (wolke-refactor-plan
 * Stap 6), ported from wolkentypes' own `MainActivity`/`LayerControlPanel`. Presets are saved via
 * `com.wolkentypes.app.clouds.savePreset` (per weatherId, same SharedPreferences format the
 * wolkentypes prototype uses) and picked up live by [WallpaperWeatherEffectRenderer] whenever
 * "New clouds" is enabled -- tuning a weather type here changes what the live wallpaper renders
 * for it, it isn't a separate preview-only sandbox.
 *
 * Only reachable from a `BuildConfig.DEBUG`-gated button in [LiveWallpaperConfigActivity]; never
 * linked from anywhere else, so it cannot be reached in a release build.
 */
@AndroidEntryPoint
class CloudTuningActivity : BreezyActivity() {

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(Unit) {
                backgroundBitmap = withContext(Dispatchers.IO) { wallpaperRepository.loadCachedBitmap() }
            }
            BreezyWeatherTheme {
                Surface(Modifier.fillMaxSize()) {
                    CloudTuningScreen(onBack = { finish() }, backgroundBitmap = backgroundBitmap)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudTuningScreen(onBack: () -> Unit, backgroundBitmap: Bitmap?) {
    val context = LocalContext.current
    var weatherMenuExpanded by remember { mutableStateOf(false) }
    var controlsExpanded by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(CLOUD_TUNING_WEATHER_TYPES.first()) }
    val baseProfile = cloudProfileFor(selected.id)
    val savedPreset = remember(selected.id) { loadPreset(context, selected.id) }
    var layerConfigs by remember(selected.id) { mutableStateOf(savedPreset?.layers ?: baseProfile.layers) }
    var density by remember(selected.id) { mutableFloatStateOf(savedPreset?.density ?: baseProfile.density) }
    var wind by remember(selected.id) { mutableFloatStateOf(savedPreset?.wind ?: baseProfile.speed) }
    var depth by remember(selected.id) { mutableFloatStateOf(savedPreset?.depth ?: 1f) }
    var cloudView by remember { mutableStateOf<CloudSurfaceView?>(null) }
    val liveProfile = baseProfile.copy(layers = layerConfigs, density = density, speed = baseProfile.speed)

    LaunchedEffect(selected.id, layerConfigs, density, wind, depth) {
        savePreset(context, selected.id, layerConfigs, density, wind, depth)
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(selected.skyTop, selected.skyBottom)))
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> CloudSurfaceView(ctx) },
            update = { view ->
                cloudView = view
                view.profile = liveProfile
                view.weatherId = selected.id
                view.windSpeedMultiplier = wind
                view.densityMultiplier = 1f
                view.layerDepthMultiplier = depth
            }
        )
        // Same composition as the real live wallpaper: sky+clouds fill the screen, the
        // cached location photo (if any) sits in the foreground third so the tuning preview
        // matches what actually ends up on the home screen instead of a flat gradient.
        if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.35f)
            )
        }
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)
                .background(Color(0xB3303A50), RoundedCornerShape(12.dp)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Box(Modifier.width(8.dp))
            ExposedDropdownMenuBox(
                expanded = weatherMenuExpanded,
                onExpandedChange = { weatherMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    readOnly = true,
                    value = selected.label,
                    onValueChange = {},
                    label = { Text("Weather type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(weatherMenuExpanded) },
                    colors = cloudWeatherTypeFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(weatherMenuExpanded, { weatherMenuExpanded = false }) {
                    CLOUD_TUNING_WEATHER_TYPES.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                selected = type
                                weatherMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        val controlsHeight = if (controlsExpanded) Modifier.fillMaxHeight(.82f) else Modifier
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(controlsHeight)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (controlsExpanded) {
                CloudLayerControlPanel(
                    configs = layerConfigs,
                    onConfigChange = { layer, config -> layerConfigs = layerConfigs + (layer to config) },
                    density = density,
                    onDensityChange = { density = it },
                    depth = depth,
                    onDepthChange = { depth = it },
                    wind = wind,
                    onWindChange = { wind = it },
                    onRandomizeAssets = {
                        cloudView?.randomizeAssets()
                    },
                    easterEggAssetCount = cloudView?.easterEggAssetCount ?: 15,
                    onTriggerEasterEgg = { assetIndex, alpha, speed ->
                        cloudView?.triggerEasterEgg(assetIndex, alpha, speed)
                    },
                    modifier = Modifier.weight(1f).padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = { controlsExpanded = !controlsExpanded },
                colors = cloudActionButtonColors(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    if (controlsExpanded) "Hide cloud settings  ↓" else "Show cloud settings  ↑",
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CloudLayerControlPanel(
    configs: Map<CloudLayer, LayerCloudConfig>,
    onConfigChange: (CloudLayer, LayerCloudConfig) -> Unit,
    density: Float,
    onDensityChange: (Float) -> Unit,
    depth: Float,
    onDepthChange: (Float) -> Unit,
    wind: Float,
    onWindChange: (Float) -> Unit,
    onRandomizeAssets: () -> Unit,
    easterEggAssetCount: Int,
    onTriggerEasterEgg: (assetIndex: Int, alpha: Float, speedMultiplier: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedLayers by remember { mutableStateOf(setOf<CloudLayer>()) }
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color(0x66F8FAFF),
        shadowElevation = 10.dp
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Cloud layers",
                color = CLOUD_PANEL_TEXT_COLOR,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Pick type, amount, position and movement per layer",
                color = Color(0xFF53627A),
                fontSize = 13.sp
            )
            Button(
                onClick = onRandomizeAssets,
                modifier = Modifier.fillMaxWidth(),
                colors = cloudActionButtonColors(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Randomize all cloud assets", fontSize = 11.sp)
            }
            CloudLayer.entries.reversed().forEach { layer ->
                val isExpanded = layer in expandedLayers
                Row(
                    Modifier.fillMaxWidth().clickable {
                        expandedLayers = if (isExpanded) expandedLayers - layer else expandedLayers + layer
                    }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(layer.label, fontWeight = FontWeight.Bold, color = Color(0xFF23324A))
                    Text(if (isExpanded) "⌄" else "›", fontSize = 24.sp, color = Color(0xFF6C4EB4))
                }
                if (isExpanded) {
                    CloudLayerSection(
                        layer,
                        configs[layer] ?: LayerCloudConfig(),
                        onChange = { onConfigChange(layer, it) }
                    )
                }
                HorizontalDivider(color = Color(0xFFD8DEEA))
            }
            CloudTuningSlider("Overall amount", "${(density * 100).roundToInt()}%", density, 0f..2f, onDensityChange)
            CloudTuningSlider("Stacking / depth", "${(depth * 100).roundToInt()}%", depth, .35f..1.8f, onDepthChange)
            val windText = if (abs(wind) < .1f) "calm" else "%+.1f m/s".format(wind)
            CloudTuningSlider("Wind", windText, wind, -4f..4f, onWindChange)

            if (BuildConfig.DEBUG) {
                HorizontalDivider(color = Color(0xFFD8DEEA))
                EasterEggDebugSection(easterEggAssetCount, onTriggerEasterEgg)
            }
        }
    }
}

/**
 * Debug-only: the real easter eggs cycle automatically every few minutes and pick their own
 * asset/count/pace, so testers need a way to summon a specific one on demand with adjustable
 * transparency/speed instead of waiting and hoping. Speed/transparency here are debug-session-
 * only (not persisted) -- they just tune the next forced trigger.
 */
@Composable
private fun EasterEggDebugSection(
    easterEggAssetCount: Int,
    onTrigger: (assetIndex: Int, alpha: Float, speedMultiplier: Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🥚 Easter egg (debug)", fontWeight = FontWeight.Bold, color = Color(0xFF23324A))
        Text(if (expanded) "⌄" else "›", fontSize = 24.sp, color = Color(0xFF6C4EB4))
    }
    if (expanded) {
        var assetIndex by remember { mutableStateOf(0) }
        var alpha by remember { mutableFloatStateOf(0.55f) }
        var speed by remember { mutableFloatStateOf(1f) }
        var dropdownExpanded by remember { mutableStateOf(false) }
        val eggCount = easterEggAssetCount.coerceAtLeast(1)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    readOnly = true,
                    value = "Egg ${assetIndex + 1} / $eggCount",
                    onValueChange = {},
                    label = { Text("Which cloud") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                    colors = cloudWeatherTypeFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(dropdownExpanded, { dropdownExpanded = false }) {
                    (0 until eggCount).forEach { index ->
                        DropdownMenuItem(
                            text = { Text("Egg ${index + 1}") },
                            onClick = {
                                assetIndex = index
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
            CloudTuningSlider(
                "Transparency",
                "${(alpha * 100).roundToInt()}%",
                alpha,
                0.1f..1f
            ) { alpha = it }
            CloudTuningSlider(
                "Speed",
                "%.1fx".format(speed),
                speed,
                0.2f..3f
            ) { speed = it }
            Button(
                onClick = { onTrigger(assetIndex, alpha, speed) },
                modifier = Modifier.fillMaxWidth(),
                colors = cloudActionButtonColors(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Force easter egg", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CloudLayerSection(
    layer: CloudLayer,
    config: LayerCloudConfig,
    onChange: (LayerCloudConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CloudTypeChip("White", CloudTextureType.WHITE, config, onChange, Modifier.weight(1f))
            CloudTypeChip("Dark", CloudTextureType.DARK, config, onChange, Modifier.weight(1f))
            CloudTypeChip(
                if (layer == CloudLayer.OVERHEAD) "High veil" else "Veil",
                CloudTextureType.SMOKE,
                config,
                onChange,
                Modifier.weight(1f)
            )
        }
        if (layer == CloudLayer.HORIZON || layer == CloudLayer.OVERHEAD) {
            val special = if (layer ==
                CloudLayer.HORIZON
            ) {
                CloudTextureType.HORIZON_BANK
            } else {
                CloudTextureType.OVERHEAD_BANK
            }
            CloudTypeChip(
                if (layer ==
                    CloudLayer.HORIZON
                ) {
                    "Horizon bank"
                } else {
                    "Overhead deck"
                },
                special,
                config,
                onChange
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val labels = listOf("None", "A few", "Some", "A lot")
            CloudAmount.entries.forEachIndexed { index, amount ->
                FilterChip(
                    selected = config.amount == amount,
                    onClick = { onChange(config.copy(amount = amount)) },
                    label = { Text(labels[index], fontSize = 10.sp) },
                    colors = cloudFilterChipColors(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        CloudTuningSlider(
            "Size",
            "${(config.sizeMultiplier * 100).roundToInt()}%",
            config.sizeMultiplier,
            .35f..2.5f,
            { onChange(config.copy(sizeMultiplier = it)) }
        )
        CloudTuningSlider(
            "Height",
            "%+.0f%%".format(config.heightOffset * 100f),
            config.heightOffset,
            (-.22f)..(.22f),
            { onChange(config.copy(heightOffset = it)) }
        )
        CloudTuningSlider(
            "Vertical spread",
            "${(config.verticalSpread * 100).roundToInt()}%",
            config.verticalSpread,
            .15f..2f,
            { onChange(config.copy(verticalSpread = it)) }
        )
        CloudTuningSlider(
            "Horizontal spread",
            "${(config.horizontalSpread * 100).roundToInt()}%",
            config.horizontalSpread,
            .4f..2f,
            { onChange(config.copy(horizontalSpread = it)) }
        )
        CloudTuningSlider(
            "Layer speed",
            "${(config.speedMultiplier * 100).roundToInt()}%",
            config.speedMultiplier,
            0f..3f,
            { onChange(config.copy(speedMultiplier = it)) }
        )
        CloudTuningSlider(
            "Opacity",
            "${(config.alphaMultiplier * 100).roundToInt()}%",
            config.alphaMultiplier,
            0f..1f,
            { onChange(config.copy(alphaMultiplier = it)) }
        )
    }
}

@Composable
private fun CloudTypeChip(
    label: String,
    type: CloudTextureType,
    config: LayerCloudConfig,
    onChange: (LayerCloudConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = type in config.types,
        onClick = {
            val types = if (type in config.types) config.types - type else config.types + type
            onChange(config.copy(types = types))
        },
        label = { Text(label, fontSize = 11.sp) },
        colors = cloudFilterChipColors(),
        modifier = modifier
    )
}

@Composable
private fun cloudFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color(0x99303A50),
    labelColor = Color.White,
    selectedContainerColor = Color(0xDD303A50),
    selectedLabelColor = Color.White
)

@Composable
private fun cloudActionButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color(0xDD303A50),
    contentColor = Color.White
)

@Composable
private fun cloudWeatherTypeFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
    focusedIndicatorColor = Color.White,
    unfocusedIndicatorColor = Color.White.copy(alpha = 0.7f),
    focusedTrailingIconColor = Color.White,
    unfocusedTrailingIconColor = Color.White
)

@Composable
private fun CloudTuningSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = CLOUD_PANEL_TEXT_COLOR, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = CLOUD_PANEL_TEXT_COLOR, fontWeight = FontWeight.Bold)
        }
        Slider(
            value,
            onChange,
            modifier = Modifier.graphicsLayer(scaleY = 0.6f),
            valueRange = range
        )
    }
}

private val CLOUD_PANEL_TEXT_COLOR = Color(0xFF23324A)
