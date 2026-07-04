package com.livewallpaperweather.common.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Opt-in diagnostics. May contain photo references, precise locations and server responses. */
object DiagnosticLogger {
    private const val PREFS = "diagnostic_logging"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_STARTED_AT = "started_at"
    private const val DIRECTORY = "debug_logs"
    private val MAX_AGE_MS = TimeUnit.DAYS.toMillis(3)
    private const val LOG_TAG = "BreezyDiagnostics"
    private val lock = Any()

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return false
        val started = prefs.getLong(KEY_STARTED_AT, 0L)
        if (started == 0L || System.currentTimeMillis() - started >= MAX_AGE_MS) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            archiveDailyLogs(context, includeToday = true)
            cleanup(context)
            return false
        }
        archiveDailyLogs(context, includeToday = false)
        cleanup(context)
        return true
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_ENABLED, enabled)
            if (enabled) putLong(KEY_STARTED_AT, System.currentTimeMillis()) else remove(KEY_STARTED_AT)
        }.apply()
        if (enabled) log(context, "Diagnostics", "Debug logging enabled for at most 3 days")
    }

    fun log(context: Context, area: String, message: String, error: Throwable? = null) {
        if (!isEnabled(context)) return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())
        val line = buildString {
            append(timestamp).append(" [").append(Thread.currentThread().name).append("] ")
            append(area).append(": ").append(message).append('\n')
            error?.let { append(Log.getStackTraceString(it)).append('\n') }
        }
        synchronized(lock) {
            runCatching {
                val file = File(root(context), "${dayStamp()}.log")
                file.appendText(line)
            }.onFailure { Log.e(LOG_TAG, "Could not write diagnostic log", it) }
        }
        Log.d(LOG_TAG, "$area: $message", error)
    }

    fun files(context: Context): List<File> = synchronized(lock) {
        cleanup(context)
        root(context).walkTopDown().filter(File::isFile).toList()
    }

    fun deleteAll(context: Context) = synchronized(lock) {
        root(context).deleteRecursively()
    }

    private fun cleanup(context: Context) = synchronized(lock) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        root(context).walkBottomUp().forEach { file ->
            if (file != root(context) && file.lastModified() < cutoff) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }

    private fun archiveDailyLogs(context: Context, includeToday: Boolean) = synchronized(lock) {
        val today = dayStamp()
        root(context).listFiles { file -> file.isFile && file.extension == "log" }
            ?.filter { includeToday || it.nameWithoutExtension != today }
            ?.forEach { logFile ->
                runCatching {
                    val archive = File(root(context), "${logFile.nameWithoutExtension}.zip")
                    ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry(logFile.name))
                        logFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    archive.setLastModified(logFile.lastModified())
                    logFile.delete()
                }.onFailure { Log.e(LOG_TAG, "Could not archive ${logFile.name}", it) }
            }
        }

    private fun root(context: Context) = File(context.filesDir, DIRECTORY).apply { mkdirs() }
    private fun dayStamp() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
}
