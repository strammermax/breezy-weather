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

package com.liveweatherwallpaperapp.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.liveweatherwallpaperapp"

/**
 * Macrobenchmarks for the parts of the app this project's battery/performance push (see
 * MaterialLiveWallpaperService's 30fps cap, WallpaperEffectView's 60fps cap, and
 * WallpaperRepository's bitmap cache/downsampling/RGB_565) is meant to protect. Run on a real
 * device via `./gradlew :benchmark:connectedFreenetBenchmarkAndroidTest` -- macrobenchmarks
 * cannot run on the emulator (no reliable frame-timing/compilation data there).
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** Cold-start time to the first drawn frame of the main screen (which hosts the frosted
     *  [com.liveweatherwallpaperapp.wallpaper.WallpaperEffectView] background). */
    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT
    ) {
        pressHome()
        startActivityAndWait()
    }

    /** Frame-timing (jank/frame-duration) while the main screen is idling with its frosted
     *  background animating -- the scenario the 60fps Choreographer cap targets directly. */
    @Test
    fun mainScreenFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)
        // Let the frosted background's Choreographer loop run for a representative window.
        Thread.sleep(3_000)
    }
}
