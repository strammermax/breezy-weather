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

package com.liveweatherwallpaperapp.ui.settings.compose

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.background.findmyphone.FindMyPhoneCalibrator
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface CalibrationStep {
    data object Prompt : CalibrationStep
    data class CountingDown(val secondsLeft: Int) : CalibrationStep
    data object Recording : CalibrationStep
    data class Done(val hz: Float?) : CalibrationStep
}

private const val COUNTDOWN_SECONDS = 10
private const val RECORDING_DURATION_MS = 5000L

/**
 * Shown when the user turns whistle detection on (either directly, or by turning on "Find my
 * phone" while whistle detection is already enabled): records the user's own whistle and saves
 * its pitch via [onCalibrated], so detection is tuned to this person instead of a fixed band
 * tuned for nobody in particular -- everyone whistles differently.
 *
 * Flow: a prompt explaining what's about to happen -> (if accepted) a 10s countdown giving the
 * user time to step back to a realistic distance -> a start "ping" -> 5s of recording -> an end
 * "ping-pong" -> a confirmation screen. [onDeclined] is called if the user opts out from the
 * prompt, so the caller can turn whistle detection back off.
 */
@Composable
fun FindMyPhoneCalibrationDialog(
    context: Context,
    onCalibrated: (Float?) -> Unit,
    onDeclined: () -> Unit,
) {
    var step by remember { mutableStateOf<CalibrationStep>(CalibrationStep.Prompt) }
    val scope = rememberCoroutineScope()
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }
    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }

    fun startCalibration() {
        scope.launch {
            for (secondsLeft in COUNTDOWN_SECONDS downTo 1) {
                step = CalibrationStep.CountingDown(secondsLeft)
                delay(1000)
            }
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            delay(200)

            step = CalibrationStep.Recording
            val hz = FindMyPhoneCalibrator.calibrate(context, RECORDING_DURATION_MS)

            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            delay(250)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
            delay(150)

            step = CalibrationStep.Done(hz)
        }
    }

    AlertDialog(
        onDismissRequest = { /* only dismissible via the explicit buttons below */ },
        title = { Text(stringResource(R.string.find_my_phone_calibration_title)) },
        text = {
            when (val s = step) {
                is CalibrationStep.Prompt -> {
                    Text(stringResource(R.string.find_my_phone_calibration_prompt))
                }
                is CalibrationStep.CountingDown -> {
                    Column {
                        Text(
                            stringResource(R.string.find_my_phone_calibration_countdown, s.secondsLeft)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator()
                    }
                }
                is CalibrationStep.Recording -> {
                    Column {
                        Text(stringResource(R.string.find_my_phone_calibration_recording))
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator()
                    }
                }
                is CalibrationStep.Done -> {
                    Text(stringResource(R.string.find_my_phone_calibration_done))
                }
            }
        },
        confirmButton = {
            when (val s = step) {
                is CalibrationStep.Prompt -> {
                    TextButton(onClick = { startCalibration() }) {
                        Text(stringResource(R.string.find_my_phone_calibration_start))
                    }
                }
                is CalibrationStep.Done -> {
                    TextButton(onClick = { onCalibrated(s.hz) }) {
                        Text(stringResource(R.string.find_my_phone_calibration_ok))
                    }
                }
                else -> Unit
            }
        },
        dismissButton = {
            if (step is CalibrationStep.Prompt) {
                TextButton(onClick = onDeclined) {
                    Text(stringResource(R.string.find_my_phone_calibration_not_now))
                }
            }
        }
    )
}
