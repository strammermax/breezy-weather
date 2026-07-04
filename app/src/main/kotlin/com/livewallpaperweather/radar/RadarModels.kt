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

package com.livewallpaperweather.radar

/**
 * A single point of the precipitation nowcast (rain trend): the predicted rain intensity
 * at a given clock time. Produced by [BuienradarNowcastSource].
 */
data class RainTrendPoint(
    /** Clock label as returned by Buienradar, e.g. "14:30". */
    val timeLabel: String,
    /** Predicted precipitation intensity in millimetres per hour. */
    val intensityMmH: Double,
)

/**
 * A single radar map frame from RainViewer: a timestamp plus the path used to build tile URLs.
 * @see RainViewerRadarSource
 */
data class RadarFrame(
    val timeSeconds: Long,
    val path: String,
    val isForecast: Boolean,
)

/**
 * The set of radar frames available from RainViewer for animation, with the tile host.
 * Tile URL template: `$host$path/$size/{z}/{x}/{y}/$color/$options.png`.
 */
data class RadarFrames(
    val host: String,
    val frames: List<RadarFrame>,
) {
    fun tileUrl(
        frame: RadarFrame,
        z: Int,
        x: Int,
        y: Int,
        size: Int = 256,
        color: Int = 2,
        options: String = "1_1",
    ): String = "$host${frame.path}/$size/$z/$x/$y/$color/$options.png"
}
