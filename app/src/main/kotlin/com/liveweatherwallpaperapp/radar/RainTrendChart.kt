/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package com.liveweatherwallpaperapp.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liveweatherwallpaperapp.R
import kotlin.math.max

/**
 * Area chart of a precipitation nowcast (rain trend), mirroring the "2 uur
 * neerslagvoorspelling" card: time on the X axis, intensity (mm/h) on the Y axis.
 *
 * Self-contained Compose Canvas — no charting library — so it can be reused on the
 * live-wallpaper overlay later.
 */
@Composable
fun RainTrendChart(
    points: List<RainTrendPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    gridColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
) {
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "—",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    // Round the Y axis up to a sensible maximum so light drizzle is still visible.
    val dataMax = points.maxOf { it.intensityMmH }
    val axisMax = niceAxisMax(dataMax)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val w = size.width
                val h = size.height

                // Horizontal grid lines (0, ¼, ½, ¾, max).
                for (i in 0..4) {
                    val y = h - (h * i / 4f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                if (points.size == 1) {
                    val y = h - (points[0].intensityMmH / axisMax * h).toFloat()
                    drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 4f)
                    return@Canvas
                }

                val stepX = w / (points.size - 1)
                fun pointOffset(index: Int): Offset {
                    val x = stepX * index
                    val y = h - (points[index].intensityMmH / axisMax * h).toFloat().coerceIn(0f, h)
                    return Offset(x, y)
                }

                // Filled area under the curve.
                val fill = Path().apply {
                    moveTo(0f, h)
                    points.indices.forEach { i -> pointOffset(i).let { lineTo(it.x, it.y) } }
                    lineTo(w, h)
                    close()
                }
                drawPath(fill, color = fillColor)

                // The curve line itself.
                val line = Path().apply {
                    pointOffset(0).let { moveTo(it.x, it.y) }
                    for (i in 1 until points.size) pointOffset(i).let { lineTo(it.x, it.y) }
                }
                drawPath(line, color = lineColor, style = Stroke(width = 4f))
            }
        }

        // Axis labels: start time, end time, and the peak intensity.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                text = points.first().timeLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = stringResource(R.string.radar_rain_maximum, dataMax),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = points.last().timeLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Picks a clean Y-axis maximum (>= 1.0) just above the data's peak. */
private fun niceAxisMax(dataMax: Double): Double {
    val candidates = listOf(1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0)
    return candidates.firstOrNull { it >= dataMax } ?: max(dataMax, 1.0)
}
