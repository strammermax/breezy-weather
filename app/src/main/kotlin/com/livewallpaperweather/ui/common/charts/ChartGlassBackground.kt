package com.livewallpaperweather.ui.common.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import com.livewallpaperweather.R
import com.livewallpaperweather.domain.settings.SettingsManager

internal data class GlassChartStyle(
    val background: Color,
    val content: Color,
    val outline: Color,
)

/** Uses the same black/white card and text preferences as the cards on the main screen. */
@Composable
internal fun rememberGlassChartStyle(): GlassChartStyle {
    val context = LocalContext.current
    val settings = SettingsManager.getInstance(context)
    val alpha = settings.tileCardAlpha.coerceIn(0, 100) / 100f
    val background = when (settings.tileCardStyle) {
        "light" -> Color.White.copy(alpha = alpha)
        "dark" -> Color.Black.copy(alpha = alpha)
        "auto" -> Color(context.getColor(R.color.colorGlassCardBackground)).copy(alpha = alpha)
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when (settings.tileTextColor) {
        "light" -> Color.White
        "dark" -> Color.Black
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    return GlassChartStyle(
        background = background,
        content = content,
        outline = content.copy(alpha = 0.45f),
    )
}

/** Keeps chart labels readable over the animated sky and location-photo backgrounds. */
@Composable
internal fun Modifier.withGlassChartBackground(background: Color): Modifier = this
    .clip(MaterialTheme.shapes.extraLarge)
    .background(background)
    .padding(
        horizontal = dimensionResource(R.dimen.normal_margin),
        vertical = dimensionResource(R.dimen.small_margin),
    )
