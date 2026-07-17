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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.ui.main.MainActivity
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Foreground service backing "Find my phone". To keep it battery-friendly for something that
 * may sit running for hours, the microphone stays off until the screen has been continuously
 * off for [FindMyPhoneConfig.armDelayMinutes] -- normal screen-off/on cycles during regular use
 * never touch the mic at all. Once armed and listening, it reacts to two patterns:
 *  - 3 claps within [CLAP_WINDOW_MS] start the device's default ringtone (looping) + vibration.
 *  - 1 whistle in the [WHISTLE_MIN_HZ]..[WHISTLE_MAX_HZ] band starts the device's default
 *    notification sound (looping) + vibration, after [WHISTLE_REPLY_DELAY_MS].
 *
 * Both alerts -- and listening itself -- stop as soon as: the screen turns back on (pressing a
 * button, or touching the screen if the device has double-tap/lift-to-wake enabled), the device
 * is unlocked, the proximity sensor reports something close (picked up to look at or to an
 * ear), the motion sensor reports the phone being picked up or moved, or the app is opened; the
 * [FindMyPhoneConfig.armDelayMinutes] wait then starts over from the next screen-off.
 *
 * A reduced [SAMPLE_RATE] (vs. the usual 44100Hz) cuts the data volume to process by ~75%, and
 * buffers below the RMS gate skip onset/pitch analysis entirely so the CPU stays idle in
 * silence. A partial [PowerManager.WakeLock] keeps the CPU alive for this analysis while armed.
 *
 * Uses a hand-rolled RMS-onset detector (claps) and autocorrelation pitch estimator (whistle)
 * instead of a TarsosDSP dependency, to keep the feature self-contained.
 */
class FindMyPhoneService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var listening = false
    private var listenerThread: Thread? = null

    private val handler = Handler(Looper.getMainLooper())
    private val armRunnable = Runnable { startListening() }

    @Volatile
    private var alarmActive = false
    private var mediaPlayer: MediaPlayer? = null

    /** Snapshotted from [FindMyPhoneStore] / [FindMyPhoneConfig.current] each time listening
     * (re-)arms, so a settings change takes effect on the next arm cycle without needing to
     * restart the service. */
    private var rmsGateDb = FindMyPhoneConfig.current.rmsGateDb
    private var clapEnabled = true
    private var whistleEnabled = true
    private var whistleMinHz = WHISTLE_DEFAULT_MIN_HZ
    private var whistleMaxHz = WHISTLE_DEFAULT_MAX_HZ

    private var clapCount = 0
    private var lastClapAtMs = 0L
    private var lastClapOnsetAtMs = 0L
    private var lastLevel = 0.0

    /** Screen going off arms the [armRunnable] delay; screen coming back on (or unlocking)
     * cancels it and tears listening/any alarm back down. */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> arm()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> disarm()
            }
        }
    }

    // Both registered while actively listening (not just while an alarm is ringing), so a pickup
    // is caught immediately -- catching it only once the alarm has already started would mean
    // the ringtone/notification sound always gets at least one loop in before it can be silenced.
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // "Near" means something is close to the sensor (ear, pocket, hand picking it up).
            val maxRange = proximitySensor?.maximumRange ?: return
            if (event.values.isNotEmpty() && event.values[0] < maxRange) {
                disarm()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var significantMotionSensor: Sensor? = null
    private val significantMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            disarm()
            // A significant-motion sensor is one-shot: it must be re-armed after every trigger,
            // which only happens the next time listening (re-)starts via startListening().
        }
    }

    // Fallback pickup signal for devices without a significant-motion sensor: a simple
    // delta-based jolt detector on the accelerometer.
    private var accelerometerSensor: Sensor? = null
    private var lastAccelMagnitude = 0f
    private val accelerometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val magnitude = sqrt(
                event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
            )
            if (lastAccelMagnitude != 0f &&
                kotlin.math.abs(magnitude - lastAccelMagnitude) > ACCELEROMETER_JOLT_THRESHOLD
            ) {
                disarm()
            }
            lastAccelMagnitude = magnitude
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        significantMotionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        registerReceiver(
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        )

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FindMyPhone::WakeLock").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MS)
        }

        // The screen may already be off by the time the feature gets turned on (e.g. re-armed
        // after a config change); if so, start the arm delay immediately instead of waiting for
        // the next ACTION_SCREEN_OFF, which won't come until the screen is turned on and off again.
        if (!powerManager.isInteractive) {
            arm()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
        }
        return START_STICKY
    }

    private fun arm() {
        if (listening) return
        handler.removeCallbacks(armRunnable)
        // Priority: explicit tester-mode override > debug-build default (fast iteration without
        // needing to unlock tester mode) > the bundled production default.
        val armDelayMinutes = FindMyPhoneStore(this).testerArmDelayMinutesOverride
            ?: if (BuildConfig.DEBUG) DEBUG_ARM_DELAY_MINUTES else FindMyPhoneConfig.current.armDelayMinutes
        handler.postDelayed(armRunnable, armDelayMinutes * 60_000L)
    }

    private fun disarm() {
        handler.removeCallbacks(armRunnable)
        stopAlarm()
        stopListening()
    }

    @Suppress("MissingPermission") // RECORD_AUDIO is checked before this service is ever started
    private fun startListening() {
        if (listening) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val store = FindMyPhoneStore(this)
        rmsGateDb = store.testerRmsGateDbOverride ?: FindMyPhoneConfig.current.rmsGateDb
        clapEnabled = store.clapEnabled
        whistleEnabled = store.whistleEnabled
        // Everyone whistles at a different pitch: if the user has calibrated their own whistle,
        // search a band centered on it instead of the fixed bundled default.
        val center = store.whistleCenterHz
        if (center != null) {
            whistleMinHz = (center * WHISTLE_CALIBRATED_BAND_RATIO_LOW).toDouble()
            whistleMaxHz = (center * WHISTLE_CALIBRATED_BAND_RATIO_HIGH).toDouble()
        } else {
            whistleMinHz = WHISTLE_DEFAULT_MIN_HZ
            whistleMaxHz = WHISTLE_DEFAULT_MAX_HZ
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) return
        val bufferSize = maxOf(minBufferSize, BUFFER_SIZE_SAMPLES * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        audioRecord = record
        listening = true
        clapCount = 0
        record.startRecording()

        listenerThread = thread(name = "FindMyPhoneListener") {
            val buffer = ShortArray(BUFFER_SIZE_SAMPLES)
            while (listening) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    processBuffer(buffer, read)
                }
            }
        }
    }

    private fun stopListening() {
        if (!listening) return
        listening = false
        listenerThread?.join(500)
        listenerThread = null
        audioRecord?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // already stopped/released -- nothing to clean up
            }
            it.release()
        }
        audioRecord = null
    }

    private fun registerPickupSensors() {
        proximitySensor?.let {
            sensorManager.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        // Significant-motion is tuned to detect the device changing location (e.g. walking off
        // with it) and often does NOT fire for simply lifting the phone off a table -- register
        // it as a bonus signal, but always also run the accelerometer jolt detector, which is
        // what actually catches an ordinary pickup gesture.
        significantMotionSensor?.let { sensorManager.requestTriggerSensor(significantMotionListener, it) }
        lastAccelMagnitude = 0f
        accelerometerSensor?.let {
            sensorManager.registerListener(accelerometerListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun unregisterPickupSensors() {
        sensorManager.unregisterListener(proximityListener)
        sensorManager.unregisterListener(accelerometerListener)
        significantMotionSensor?.let { sensorManager.cancelTriggerSensor(significantMotionListener, it) }
    }

    private fun processBuffer(buffer: ShortArray, length: Int) {
        val level = rms(buffer, length)
        val db = 20 * log10(level.coerceAtLeast(1.0) / Short.MAX_VALUE)
        if (db < rmsGateDb) {
            lastLevel = level
            return
        }

        if (!alarmActive) {
            val now = System.currentTimeMillis()
            if (clapEnabled) detectClap(level, now)
            // A clap is loud, broadband noise that can briefly alias into the whistle band, so
            // skip whistle detection for a moment after any clap onset to avoid a false trigger.
            if (whistleEnabled && now - lastClapOnsetAtMs > WHISTLE_SUPPRESSION_AFTER_CLAP_MS) {
                detectWhistle(buffer, length)
            }
        }
        lastLevel = level
    }

    private fun rms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) sum += buffer[i].toDouble() * buffer[i]
        return sqrt(sum / length)
    }

    // A clap is a short, sharp rise in amplitude relative to the previous buffer, unlike a
    // sustained loud sound (speech, music), so a simple level-ratio is enough to isolate it.
    private fun detectClap(level: Double, now: Long) {
        if (level < lastLevel * CLAP_ONSET_RATIO) return
        lastClapOnsetAtMs = now

        if (now - lastClapAtMs > CLAP_WINDOW_MS) {
            clapCount = 0
        }
        if (now - lastClapAtMs > CLAP_DEBOUNCE_MS) {
            clapCount++
            lastClapAtMs = now
            if (clapCount >= 3) {
                clapCount = 0
                startAlert(RingtoneManager.TYPE_RINGTONE)
            }
        }
    }

    private fun detectWhistle(buffer: ShortArray, length: Int) {
        val pitch = WhistlePitchEstimator.estimateHz(
            buffer,
            length,
            SAMPLE_RATE,
            whistleMinHz,
            whistleMaxHz,
            CORRELATION_CONFIDENCE_MIN
        ) ?: return
        if (pitch in whistleMinHz..whistleMaxHz) {
            handler.postDelayed({
                startAlert(RingtoneManager.TYPE_NOTIFICATION)
            }, WHISTLE_REPLY_DELAY_MS)
        }
    }

    /** Starts a looping system sound (ringtone for claps, notification tone for whistles) plus
     * repeating vibration, both of which run until [stopAlarm] is called. Using the device's own
     * default sound (rather than a bundled asset) means there's exactly one sound source -- no
     * risk of it stacking with anything else. */
    private fun startAlert(ringtoneType: Int) {
        if (alarmActive) return
        alarmActive = true

        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, ringtoneType)
            ?: RingtoneManager.getDefaultUri(ringtoneType)
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (ringtoneType == RingtoneManager.TYPE_RINGTONE) {
                                AudioAttributes.USAGE_ALARM
                            } else {
                                AudioAttributes.USAGE_NOTIFICATION
                            }
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@FindMyPhoneService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: IOException) {
            // No default sound available on this device -- vibration alone still alerts.
        }

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 1)
        }

        // Pickup/motion sensors only need to run while there's actually something to dismiss --
        // registering them earlier (e.g. for the whole 15-minute listening window) would just
        // burn battery for no benefit, since nothing needs dismissing until an alert is ringing.
        registerPickupSensors()
    }

    private fun stopAlarm() {
        if (!alarmActive) return
        alarmActive = false
        handler.removeCallbacksAndMessages(null)
        unregisterPickupSensors()

        mediaPlayer?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // already stopped -- nothing to clean up
            }
            it.release()
        }
        mediaPlayer = null

        (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }

    private fun startForegroundNotification() {
        val channelId = "find_my_phone_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.find_my_phone_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(
                this,
                MainActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Android requires a persistent notification for as long as a foreground service holds
        // the microphone (privacy requirement since Android 8, cannot be suppressed entirely) --
        // kept to just the icon, with no title/text, so it's as unobtrusive as that allows.
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_about)
            .setContentIntent(contentIntent)
            .setSilent(true)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        stopAlarm()
        stopListening()
        wakeLock?.let { if (it.isHeld) it.release() }
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: IllegalArgumentException) {
            // never registered (e.g. crashed before onCreate finished) -- nothing to unregister
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 481_516
        private const val SAMPLE_RATE = 11025
        private const val BUFFER_SIZE_SAMPLES = 512
        private const val CLAP_ONSET_RATIO = 3.0
        private const val CLAP_WINDOW_MS = 3000L
        private const val CLAP_DEBOUNCE_MS = 300L
        private const val WHISTLE_DEFAULT_MIN_HZ = 800.0
        private const val WHISTLE_DEFAULT_MAX_HZ = 2000.0
        private const val WHISTLE_CALIBRATED_BAND_RATIO_LOW = 0.8f
        private const val WHISTLE_CALIBRATED_BAND_RATIO_HIGH = 1.25f
        private const val WHISTLE_REPLY_DELAY_MS = 2000L
        private const val WHISTLE_SUPPRESSION_AFTER_CLAP_MS = 1500L
        private const val CORRELATION_CONFIDENCE_MIN = 0.6
        private const val ACCELEROMETER_JOLT_THRESHOLD = 3f
        private const val DEBUG_ARM_DELAY_MINUTES = 1
        private const val MAX_WAKE_LOCK_MS = 10 * 60 * 60 * 1000L

        const val ACTION_STOP_ALARM = "com.liveweatherwallpaperapp.action.FIND_MY_PHONE_STOP_ALARM"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FindMyPhoneService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FindMyPhoneService::class.java))
        }

        /** Silences an in-progress alarm without stopping the background listening -- called
         * every time the app is opened, so finding the phone doesn't also disable the feature. */
        fun stopAlarm(context: Context) {
            context.startService(
                Intent(context, FindMyPhoneService::class.java).setAction(ACTION_STOP_ALARM)
            )
        }
    }
}
