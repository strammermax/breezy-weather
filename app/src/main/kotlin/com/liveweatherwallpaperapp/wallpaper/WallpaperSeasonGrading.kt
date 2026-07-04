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

package com.liveweatherwallpaperapp.wallpaper

import java.time.LocalDate

/** The four meteorological seasons used for the experimental scene grading (ACT-012). */
enum class WallpaperSeason {
    WINTER,
    SPRING,
    SUMMER,
    AUTUMN,
}

/**
 * A small, normalized description of a seasonal colour/light shift applied as the very last
 * layer of the scene. All values are deliberately tiny: [tint] is only ever blended in with
 * [tintStrength], and every shift is further scaled by the experiment's effective strength
 * (see [WallpaperSeasonGrading.effectiveGrading]).
 */
data class SeasonGrading(
    /** -1f cooler .. +1f warmer. */
    val warmthShift: Float,
    /** Subtle ARGB overlay colour. */
    val tint: Int,
    /** 0f..1f, how strongly [tint] is blended in. */
    val tintStrength: Float,
    /** -1f darker .. +1f lighter. */
    val brightnessShift: Float,
    /** -1f less saturated .. +1f more saturated. */
    val saturationShift: Float,
) {
    companion object {
        /** Fully neutral grading: applying this never changes the rendered colour. */
        val NEUTRAL = SeasonGrading(
            warmthShift = 0f,
            tint = 0x00000000,
            tintStrength = 0f,
            brightnessShift = 0f,
            saturationShift = 0f,
        )
    }
}

/**
 * Pure season detection and grading model for the experimental seasonal colour grading
 * (ACT-012). Contains no Android dependency so it can be unit tested on a plain JVM.
 */
object WallpaperSeasonGrading {

    /**
     * The seasonal grading is always scaled down so the effect stays subtle, regardless of the
     * user-chosen strength.
     */
    const val MAX_SEASON_GRADING_STRENGTH = 0.35f

    /** Cool, slightly desaturated blue. */
    private const val WINTER_TINT = 0xFF6E8FBF.toInt()

    /** Fresh, light green. */
    private const val SPRING_TINT = 0xFF9FD08A.toInt()

    /** Warm, bright yellow. */
    private const val SUMMER_TINT = 0xFFFFD27A.toInt()

    /** Warm amber. */
    private const val AUTUMN_TINT = 0xFFD9904D.toInt()

    private val WINTER_GRADING = SeasonGrading(
        warmthShift = -0.25f,
        tint = WINTER_TINT,
        tintStrength = 0.12f,
        brightnessShift = -0.05f,
        saturationShift = -0.10f,
    )
    private val SPRING_GRADING = SeasonGrading(
        warmthShift = 0.05f,
        tint = SPRING_TINT,
        tintStrength = 0.08f,
        brightnessShift = 0.05f,
        saturationShift = 0.10f,
    )
    private val SUMMER_GRADING = SeasonGrading(
        warmthShift = 0.20f,
        tint = SUMMER_TINT,
        tintStrength = 0.10f,
        brightnessShift = 0.05f,
        saturationShift = 0.10f,
    )
    private val AUTUMN_GRADING = SeasonGrading(
        warmthShift = 0.15f,
        tint = AUTUMN_TINT,
        tintStrength = 0.10f,
        brightnessShift = -0.05f,
        saturationShift = 0.05f,
    )

    /**
     * Determines the season for [date], using calendar month only (never temperature, to avoid
     * jumps caused by weather extremes). [latitude] flips the result for the southern hemisphere;
     * a missing/unknown latitude defaults to the northern hemisphere.
     */
    fun seasonFor(date: LocalDate, latitude: Double?): WallpaperSeason {
        val northern = when (date.monthValue) {
            12, 1, 2 -> WallpaperSeason.WINTER
            3, 4, 5 -> WallpaperSeason.SPRING
            6, 7, 8 -> WallpaperSeason.SUMMER
            else -> WallpaperSeason.AUTUMN
        }
        return if (latitude != null && latitude < 0.0) flip(northern) else northern
    }

    private fun flip(season: WallpaperSeason): WallpaperSeason = when (season) {
        WallpaperSeason.WINTER -> WallpaperSeason.SUMMER
        WallpaperSeason.SUMMER -> WallpaperSeason.WINTER
        WallpaperSeason.SPRING -> WallpaperSeason.AUTUMN
        WallpaperSeason.AUTUMN -> WallpaperSeason.SPRING
    }

    /** The recommended, un-scaled grading description for [season]. */
    fun baseGradingFor(season: WallpaperSeason): SeasonGrading = when (season) {
        WallpaperSeason.WINTER -> WINTER_GRADING
        WallpaperSeason.SPRING -> SPRING_GRADING
        WallpaperSeason.SUMMER -> SUMMER_GRADING
        WallpaperSeason.AUTUMN -> AUTUMN_GRADING
    }

    /**
     * The effective strength multiplier for a user-chosen [strength] in `0f..1f`, bounded by
     * [MAX_SEASON_GRADING_STRENGTH].
     */
    fun effectiveStrength(strength: Float): Float = strength.coerceIn(0f, 1f) * MAX_SEASON_GRADING_STRENGTH

    /**
     * Scales [base] by the effective strength derived from [strength] (0f..1f). A strength of 0
     * yields [SeasonGrading.NEUTRAL]-equivalent shifts (tint strength 0, all shifts 0), so the
     * colour is left unchanged.
     */
    fun effectiveGrading(base: SeasonGrading, strength: Float): SeasonGrading {
        val factor = effectiveStrength(strength)
        return SeasonGrading(
            warmthShift = base.warmthShift * factor,
            tint = base.tint,
            tintStrength = base.tintStrength * factor,
            brightnessShift = base.brightnessShift * factor,
            saturationShift = base.saturationShift * factor,
        )
    }

    /**
     * The overall opacity (`0f..1f`) of the seasonal grading layer. Zero when the experiment is
     * disabled. Otherwise restrained at night and slightly stronger by day, so the existing
     * night colouring stays in charge and a warm summer grading never turns a night scene
     * unnaturally orange.
     */
    fun gradingAlpha(experimentEnabled: Boolean, daylight: Float): Float {
        if (!experimentEnabled) return 0f
        val safeDaylight = daylight.coerceIn(0f, 1f)
        return lerp(NIGHT_ALPHA, DAY_ALPHA, safeDaylight)
    }

    /** Linear interpolation between two [SeasonGrading]s, e.g. across a season change. */
    fun lerpGrading(from: SeasonGrading, to: SeasonGrading, progress: Float): SeasonGrading {
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return from
        if (p >= 1f) return to
        return SeasonGrading(
            warmthShift = lerp(from.warmthShift, to.warmthShift, p),
            tint = lerpColor(from.tint, to.tint, p),
            tintStrength = lerp(from.tintStrength, to.tintStrength, p),
            brightnessShift = lerp(from.brightnessShift, to.brightnessShift, p),
            saturationShift = lerp(from.saturationShift, to.saturationShift, p),
        )
    }

    private const val NIGHT_ALPHA = 0.4f
    private const val DAY_ALPHA = 1.0f
}
