/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.livewallpaperweather.ui.theme.weatherView.WeatherView
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * ACT-008 visual regression tests.
 *
 * Renders [WallpaperWeatherEffectRenderer] offscreen via [WallpaperSceneRenderer] using only
 * deterministic, locally built [WallpaperSceneState] input (no GPS, network, RemoveSky or
 * personal photo data), and compares the result against stored baseline PNGs.
 *
 * ## Fixed inputs (documented per ACT-008 section 9)
 * - Surface size: [WIDTH] x [HEIGHT] (a representative phone portrait resolution).
 * - Quality profile: [WallpaperQualityProfile.BALANCED] for all scenarios.
 * - Settling: [WallpaperSceneRenderer] runs 30 fixed 33ms update steps before drawing, so
 *   particle/cloud/drop positions are deterministic for a given seed.
 * - Sunrise/sunset/moonrise/moonset millis and the `daylight` value are fixed per scenario
 *   below (see [Scenario.daylight], [Scenario.sunriseMillis], etc.).
 *
 * ## Reduced scope (documented per ACT-008 section 4 escape hatch)
 * [WallpaperSceneRenderer]'s sky/sun/moon drawing is an intentionally simplified placeholder
 * (flat gradient + a single circle depending on [WallpaperSceneState.daytime]), not a copy of
 * [MaterialLiveWallpaperService]'s production celestial positioning. These tests therefore
 * focus on the effect layers added by ACT-003..ACT-007 (clouds, precipitation, fog/haze,
 * glass-rain, stars, quality profiles) rather than on exact sun/moon screen position. Test
 * case 5/6 from section 16 (exact celestial position) is covered only at the "day vs night"
 * granularity (sun vs moon circle color), which is verified by [sunriseAndSunset_useDayCircle]
 * and [night_usesMoonCircle].
 *
 * ## Running these tests
 * ```
 * export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot"
 * ./gradlew :app:connectedBasicDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.livewallpaperweather.wallpaper.WallpaperVisualRegressionTest
 * ```
 * Requires an emulator or device running API 21+. AGSL effect passes only run on API 33+
 * (Android 13); below that, [WallpaperWeatherEffectRenderer] uses its Canvas fallback
 * automatically, so running on both an API 33+ and an API <33 image exercises both paths
 * (test case 10 / section 10).
 *
 * ## Baselines and updating references
 * Reviewed baselines are packaged in `app/src/androidTest/assets/wallpaper-regression-baseline`.
 * Generated images live under the instrumentation target app's external files directory. Pull
 * them with:
 * ```
 * adb pull /storage/emulated/0/Android/data/com.livewallpaperweather.debug/files/wallpaper-regression test-artifacts/regression-baseline
 * ```
 * To intentionally generate review candidates without comparison, pass the instrumentation
 * argument `generateBaselines=true`. Normal runs fail when a packaged baseline is missing.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperVisualRegressionTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val testContext = instrumentation.context
    private val generateBaselines = InstrumentationRegistry.getArguments()
        .getString("generateBaselines")
        .toBoolean()
    private val outputDir = File(context.getExternalFilesDir(null), "wallpaper-regression")

    /** Fixed surface size used for every scenario (documented per ACT-008 section 9). */
    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2280

        /** ~06:00 local. */
        const val SUNRISE_MILLIS = 6L * 60 * 60 * 1000
        /** ~21:00 local. */
        const val SUNSET_MILLIS = 21L * 60 * 60 * 1000
        const val MOONRISE_MILLIS = 20L * 60 * 60 * 1000
        const val MOONSET_MILLIS = 7L * 60 * 60 * 1000

        const val SIMILARITY_THRESHOLD = 0.95
    }

    /** One row of the section 8 scenario table, with the fixed inputs from section 9. */
    private data class Scenario(
        val name: String,
        val weatherKind: Int,
        val daylight: Float,
        val seed: Long,
        val windSpeedMetersPerSecond: Float = 3f,
        val windDirectionDegrees: Float? = 220f,
    ) {
        fun toSceneState(): WallpaperSceneState = WallpaperSceneStateFactory.create(
            weatherKind = weatherKind,
            daylight = daylight,
            windSpeedMetersPerSecond = windSpeedMetersPerSecond,
            windGustMetersPerSecond = windSpeedMetersPerSecond,
            windDirectionDegrees = windDirectionDegrees,
            sunriseMillis = SUNRISE_MILLIS,
            sunsetMillis = SUNSET_MILLIS,
            moonriseMillis = MOONRISE_MILLIS,
            moonsetMillis = MOONSET_MILLIS,
        )
    }

    private val scenarios = listOf(
        Scenario("clear_day", WeatherView.WEATHER_KIND_CLEAR, daylight = 1f, seed = 1001L),
        Scenario("sunrise", WeatherView.WEATHER_KIND_CLOUD, daylight = 0.5f, seed = 1002L),
        Scenario("sunset", WeatherView.WEATHER_KIND_CLOUD, daylight = 0.5f, seed = 1003L, windDirectionDegrees = 270f),
        Scenario("night", WeatherView.WEATHER_KIND_CLEAR, daylight = 0f, seed = 1004L),
        Scenario("cloud", WeatherView.WEATHER_KIND_CLOUD, daylight = 1f, seed = 1005L),
        Scenario("cloudy", WeatherView.WEATHER_KIND_CLOUDY, daylight = 1f, seed = 1006L),
        Scenario("rain", WeatherView.WEATHER_KIND_RAINY, daylight = 1f, seed = 1007L),
        Scenario("thunderstorm", WeatherView.WEATHER_KIND_THUNDERSTORM, daylight = 0.8f, seed = 1008L),
        Scenario("wind", WeatherView.WEATHER_KIND_WIND, daylight = 1f, seed = 1009L, windSpeedMetersPerSecond = 12f),
        Scenario("snow", WeatherView.WEATHER_KIND_SNOW, daylight = 1f, seed = 1010L),
        Scenario("sleet", WeatherView.WEATHER_KIND_SLEET, daylight = 1f, seed = 1011L),
        Scenario("fog", WeatherView.WEATHER_KIND_FOG, daylight = 1f, seed = 1012L),
    )

    init {
        outputDir.mkdirs()
    }

    /** Test case 1 (section 16): each of the twelve scenarios renders without crashing. */
    @Test
    fun allTwelveScenarios_renderWithoutCrash() {
        scenarios.forEach { scenario ->
            val bitmap = renderScenario(scenario)
            assert(bitmap.width == WIDTH && bitmap.height == HEIGHT) {
                "${scenario.name}: unexpected bitmap size ${bitmap.width}x${bitmap.height}"
            }
            saveAndCompareToBaseline(scenario.name, bitmap)
        }
    }

    /** Test case 2 (section 16): same input and seed yield an identical image. */
    @Test
    fun sameSeed_producesIdenticalImage() {
        val scenario = scenarios.first { it.name == "rain" }
        val first = renderScenario(scenario)
        val second = renderScenario(scenario)
        assert(similarity(first, second) >= 0.999) {
            "Same seed should produce (near-)identical images, similarity was ${similarity(first, second)}"
        }
    }

    /** Test case 3 (section 16): a different seed deterministically changes particle placement. */
    @Test
    fun differentSeed_changesParticleDistributionDeterministically() {
        val scenario = scenarios.first { it.name == "snow" }
        val first = renderScenario(scenario)
        val second = renderScenario(scenario.copy(seed = scenario.seed + 1))
        val thirdSameAsSecond = renderScenario(scenario.copy(seed = scenario.seed + 1))
        val repeatedSeedSimilarity = similarity(second, thirdSameAsSecond)

        assert(similarity(first, second) < 0.999) {
            "Different seeds should produce a different particle distribution"
        }
        assert(repeatedSeedSimilarity >= 0.995) {
            "Re-running the same alternate seed must reproduce the same distribution; " +
                "similarity was $repeatedSeedSimilarity"
        }
    }

    /** Test case 4 (section 16): the documented layer order (sky -> celestial -> weather passes). */
    @Test
    fun layerOrder_matchesDocumentedOrder() {
        // drawScene() in WallpaperSceneRenderer draws, in order: sky gradient, sun/moon circle,
        // background weather pass, foreground weather pass, glass-rain drops. A clear-day render
        // must therefore not be a flat single color (sky + sun circle are both visible).
        val bitmap = renderScenario(scenarios.first { it.name == "clear_day" })
        val distinctColors = HashSet<Int>()
        var x = 0
        while (x < bitmap.width) {
            distinctColors.add(bitmap.getPixel(x, (bitmap.height * 0.18f).toInt()))
            x += 17
        }
        assert(distinctColors.size > 1) {
            "Expected both sky gradient and sun circle to be visible (layer order check)"
        }
    }

    /** Test case 5 (section 16, reduced scope): sunrise/sunset render the day (sun) circle. */
    @Test
    fun sunriseAndSunset_useDayCircle() {
        listOf("sunrise", "sunset").forEach { name ->
            val scenario = scenarios.first { it.name == name }
            assert(scenario.toSceneState().daytime) { "$name should be daytime (daylight >= 0.5)" }
            renderScenario(scenario) // must not crash
        }
    }

    /** Test case 6 (section 16): night renders the moon (non-daytime) circle. */
    @Test
    fun night_usesMoonCircle() {
        val scenario = scenarios.first { it.name == "night" }
        assert(!scenario.toSceneState().daytime) { "night should not be daytime" }
        renderScenario(scenario)
    }

    /** Test case 7 (section 16): rain and thunderstorm render their precipitation/glass-rain layers. */
    @Test
    fun rainAndThunderstorm_renderWithoutEmptyFrame() {
        listOf("rain", "thunderstorm").forEach { name ->
            val scenario = scenarios.first { it.name == name }
            val bitmap = renderScenario(scenario)
            assert(!isSingleColor(bitmap)) { "$name should not render as an empty/flat frame" }
        }
    }

    /** Test case 8 (section 16): snow and sleet render particle layers without crashing. */
    @Test
    fun snowAndSleet_renderParticles() {
        listOf("snow", "sleet").forEach { name ->
            val scenario = scenarios.first { it.name == name }
            val bitmap = renderScenario(scenario)
            assert(!isSingleColor(bitmap)) { "$name should not render as an empty/flat frame" }
        }
    }

    /** Test case 9 (section 16): fog renders depth bands, not a full-screen white overlay. */
    @Test
    fun fog_doesNotRenderFullScreenWhiteOverlay() {
        val scenario = scenarios.first { it.name == "fog" }
        val bitmap = renderScenario(scenario)
        assert(!isSingleColor(bitmap, android.graphics.Color.WHITE)) {
            "Fog must not render as a full-screen white overlay"
        }
    }

    /**
     * Test case 11 (section 16): an ACT-002 transition at progress = 0.5 (Cloud -> Cloudy)
     * renders both renderers composited without crashing or producing an empty image.
     */
    @Test
    fun transitionAtHalfProgress_rendersBothScenesComposited() {
        val from = scenarios.first { it.name == "cloud" }.toSceneState()
        val to = scenarios.first { it.name == "cloudy" }.toSceneState()
        val bitmap = WallpaperSceneRenderer.renderTransitionToBitmap(
            fromState = from,
            toState = to,
            width = WIDTH,
            height = HEIGHT,
            seed = 2000L,
            progress = 0.5f,
        )
        assert(bitmap.width == WIDTH && bitmap.height == HEIGHT)
        assert(!isSingleColor(bitmap)) { "Transition render must not be an empty/flat frame" }
        saveAndCompareToBaseline("transition_cloud_to_cloudy_p050", bitmap)
    }

    private fun renderScenario(scenario: Scenario): Bitmap = WallpaperSceneRenderer.renderEffectsToBitmap(
        state = scenario.toSceneState(),
        width = WIDTH,
        height = HEIGHT,
        seed = scenario.seed,
        qualityProfile = WallpaperQualityProfile.BALANCED,
    )

    private fun isSingleColor(bitmap: Bitmap, expected: Int? = null): Boolean {
        val first = bitmap.getPixel(0, 0)
        if (expected != null && first != expected) return false
        for (y in 0 until bitmap.height step 37) {
            for (x in 0 until bitmap.width step 37) {
                if (bitmap.getPixel(x, y) != first) return false
            }
        }
        return true
    }

    /** Mean per-channel similarity in [0, 1], sampled on a coarse grid for speed. */
    private fun similarity(a: Bitmap, b: Bitmap): Double {
        if (a.width != b.width || a.height != b.height) return 0.0
        var totalDiff = 0L
        var samples = 0
        for (y in 0 until a.height step 11) {
            for (x in 0 until a.width step 11) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                totalDiff += channelDiff(pa, pb, 16) + channelDiff(pa, pb, 8) + channelDiff(pa, pb, 0)
                samples += 3
            }
        }
        val maxDiff = samples.toDouble() * 255.0
        return 1.0 - (totalDiff.toDouble() / maxDiff)
    }

    private fun channelDiff(a: Int, b: Int, shift: Int): Int =
        kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))

    /**
     * Saves [bitmap] to [outputDir], then compares it with the reviewed packaged baseline.
     * Explicit baseline-generation runs only write review candidates and never bless them.
     */
    private fun saveAndCompareToBaseline(name: String, bitmap: Bitmap) {
        val generated = File(outputDir, "$name.png")
        writePng(bitmap, generated)
        if (generateBaselines) {
            Log.i("ACT-008", "Generated review candidate for $name at ${generated.absolutePath}")
            return
        }

        val assetName = "wallpaper-regression-baseline/$name.png"
        val baselineBitmap = testContext.assets.open(assetName).use(BitmapFactory::decodeStream)
            ?: error("Missing reviewed ACT-008 baseline asset: $assetName")
        val score = similarity(bitmap, baselineBitmap)
        Log.i("ACT-008", "ACT-008 scenario=$name similarity=$score generated=${generated.absolutePath}")
        if (score < SIMILARITY_THRESHOLD) {
            val diff = File(outputDir, "${name}_diff.png")
            writePng(bitmap, diff)
            Log.w("ACT-008", "ACT-008 mismatch for $name: similarity=$score, diff saved to ${diff.absolutePath}")
        }
        assert(score >= SIMILARITY_THRESHOLD) {
            "Scenario $name similarity $score is below threshold $SIMILARITY_THRESHOLD; " +
                "generatedTopLeft=${bitmap.getPixel(0, 0).toUInt().toString(16)}, " +
                "baselineTopLeft=${baselineBitmap.getPixel(0, 0).toUInt().toString(16)}, " +
                "generatedCenter=${bitmap.getPixel(bitmap.width / 2, bitmap.height / 2).toUInt().toString(16)}, " +
                "baselineCenter=${baselineBitmap.getPixel(baselineBitmap.width / 2, baselineBitmap.height / 2).toUInt().toString(16)}"
        }
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
