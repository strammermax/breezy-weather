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

package com.liveweatherwallpaperapp.common.utils

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * On-device hardware stats (free storage, RAM usage, CPU temperature) for the "+ systeem"
 * widget row. All of this is best-effort and permission-free: free storage and RAM come from
 * standard platform APIs, CPU temperature is read from the thermal sysfs nodes exposed by the
 * kernel (no two devices agree on which zone is the CPU, so the first plausible reading wins).
 */
object DeviceStatsHelper {

    data class Stats(
        val freeStorageGb: Double?,
        val usedRamGb: Double?,
        val totalRamGb: Double?,
        val cpuTemperatureCelsius: Double?,
    )

    fun read(context: Context): Stats {
        return Stats(
            freeStorageGb = freeStorageGb(),
            usedRamGb = usedRamGb(context),
            totalRamGb = totalRamGb(context),
            cpuTemperatureCelsius = cpuTemperatureCelsius(),
        )
    }

    private fun freeStorageGb(): Double? = try {
        val stat = StatFs(File("/data").path)
        stat.availableBytes.toDouble() / GIGABYTE
    } catch (e: Exception) {
        null
    }

    private fun usedRamGb(context: Context): Double? = totalRamGb(context)?.let { total ->
        availableRamGb(context)?.let { available -> (total - available).coerceAtLeast(0.0) }
    }

    private fun totalRamGb(context: Context): Double? = try {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        info.totalMem.toDouble() / GIGABYTE
    } catch (e: Exception) {
        null
    }

    private fun availableRamGb(context: Context): Double? = try {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        info.availMem.toDouble() / GIGABYTE
    } catch (e: Exception) {
        null
    }

    // Most devices expose one thermal zone per sensor with no standard naming; "cpu"/"soc" in
    // the zone's type file is the closest thing to a convention, otherwise zone0 is usually the
    // SoC. A reading is only trusted if it falls in a physically plausible range.
    private fun cpuTemperatureCelsius(): Double? {
        val thermalDir = File("/sys/class/thermal")
        val zones = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        val preferred = zones.firstOrNull { zone ->
            File(zone, "type").runCatching { readText().trim().lowercase() }
                .getOrDefault("")
                .let { it.contains("cpu") || it.contains("soc") }
        }
        (preferred?.let { listOf(it) } ?: zones.toList()).forEach { zone ->
            val raw = File(zone, "temp").runCatching { readText().trim().toDouble() }.getOrNull()
            if (raw != null) {
                val celsius = if (raw > 1000) raw / 1000.0 else raw
                if (celsius in 0.0..120.0) return celsius
            }
        }
        return null
    }

    private const val GIGABYTE = 1024.0 * 1024.0 * 1024.0
}
