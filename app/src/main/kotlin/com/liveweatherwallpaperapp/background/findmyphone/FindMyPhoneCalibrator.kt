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

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records a short sample of the user's own whistle (right after they enable "Find my phone")
 * and estimates its pitch, so the feature can recognize *this* person's whistle instead of a
 * fixed band tuned for nobody in particular -- everyone whistles at a different pitch.
 */
object FindMyPhoneCalibrator {

    private const val SAMPLE_RATE = 11025
    private const val BUFFER_SIZE_SAMPLES = 512

    // Deliberately wider than the production detection band: this is a one-off search for
    // "wherever this person's whistle actually is," covering the full plausible human range,
    // not a narrow band tuned to a specific person yet.
    private const val SEARCH_MIN_HZ = 400.0
    private const val SEARCH_MAX_HZ = 4000.0
    private const val CONFIDENCE_MIN = 0.6

    /**
     * Records for [durationMs] and returns the median pitch (Hz) across all confidently-detected
     * whistle buffers, or null if nothing confident enough was heard (e.g. silence, or a noisy
     * environment). Runs on [Dispatchers.IO]; safe to call from a Composable's coroutine scope.
     */
    suspend fun calibrate(context: Context, durationMs: Long = 3000L): Float? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext null
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) return@withContext null
        val bufferSize = maxOf(minBufferSize, BUFFER_SIZE_SAMPLES * 2)

        @Suppress("MissingPermission") // checked above
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext null
        }

        val detectedHz = mutableListOf<Double>()
        try {
            record.startRecording()
            val buffer = ShortArray(BUFFER_SIZE_SAMPLES)
            val endAt = System.currentTimeMillis() + durationMs
            while (System.currentTimeMillis() < endAt) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    WhistlePitchEstimator.estimateHz(
                        buffer,
                        read,
                        SAMPLE_RATE,
                        SEARCH_MIN_HZ,
                        SEARCH_MAX_HZ,
                        CONFIDENCE_MIN
                    )?.let { detectedHz.add(it) }
                }
            }
        } finally {
            try {
                record.stop()
            } catch (e: IllegalStateException) {
                // never successfully started -- nothing to stop
            }
            record.release()
        }

        if (detectedHz.isEmpty()) return@withContext null
        detectedHz.sort()
        detectedHz[detectedHz.size / 2].toFloat()
    }
}
