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

package com.liveweatherwallpaperapp.background.findmyphone

/**
 * Autocorrelation pitch estimator shared by [FindMyPhoneService] (narrow search within a
 * user's calibrated -- or the bundled default -- whistle band) and [FindMyPhoneCalibrator]
 * (wide search across the whole plausible human-whistle range, to find that band in the first
 * place). Adequate for isolating a single dominant tone without a full FFT/YIN implementation.
 */
object WhistlePitchEstimator {

    /**
     * Returns the estimated pitch in Hz within [minHz]..[maxHz], or null if no lag in that range
     * has a normalized correlation at or above [confidenceMin].
     *
     * The correlation is normalized against the buffer's zero-lag energy: a pure tone like a
     * whistle scores close to 1, while broadband noise (claps, taps, rustling, speech) scores
     * far lower, so [confidenceMin] rejects those without needing a separate noise classifier.
     */
    fun estimateHz(
        buffer: ShortArray,
        length: Int,
        sampleRate: Int,
        minHz: Double,
        maxHz: Double,
        confidenceMin: Double,
    ): Double? {
        val minLag = sampleRate / maxHz.toInt()
        val maxLag = sampleRate / minHz.toInt()
        if (maxLag >= length || minLag < 1) return null

        var zeroLagEnergy = 0.0
        for (i in 0 until length - minLag) zeroLagEnergy += buffer[i].toDouble() * buffer[i]
        if (zeroLagEnergy <= 0.0) return null

        var bestLag = -1
        var bestCorrelation = 0.0
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            for (i in 0 until length - lag) {
                correlation += buffer[i] * buffer[i + lag]
            }
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestCorrelation / zeroLagEnergy < confidenceMin) return null
        return sampleRate.toDouble() / bestLag
    }
}
