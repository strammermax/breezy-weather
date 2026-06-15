/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WallpaperSeasonGradingTest {

    @Test
    fun `december january and february are winter on the northern hemisphere`() {
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 12, 15), latitude = 52.0) shouldBe WallpaperSeason.WINTER
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 1, 15), latitude = 52.0) shouldBe WallpaperSeason.WINTER
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 2, 15), latitude = 52.0) shouldBe WallpaperSeason.WINTER
    }

    @Test
    fun `june july and august are summer on the northern hemisphere`() {
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 6, 15), latitude = 52.0) shouldBe WallpaperSeason.SUMMER
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 7, 15), latitude = 52.0) shouldBe WallpaperSeason.SUMMER
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 8, 15), latitude = 52.0) shouldBe WallpaperSeason.SUMMER
    }

    @Test
    fun `the same months yield the opposite season for negative latitude`() {
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 1, 15), latitude = -33.0) shouldBe WallpaperSeason.SUMMER
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 7, 15), latitude = -33.0) shouldBe WallpaperSeason.WINTER
    }

    @Test
    fun `unknown latitude falls back to the northern hemisphere`() {
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 1, 15), latitude = null) shouldBe WallpaperSeason.WINTER
        WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 7, 15), latitude = null) shouldBe WallpaperSeason.SUMMER
    }

    @Test
    fun `every month yields exactly one valid season`() {
        for (month in 1..12) {
            val season = WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, month, 10), latitude = 52.0)
            WallpaperSeason.entries.contains(season) shouldBe true
        }
    }

    @Test
    fun `the grading mapping stays within the allowed range for every season`() {
        WallpaperSeason.entries.forEach { season ->
            val grading = WallpaperSeasonGrading.baseGradingFor(season)
            grading.warmthShift shouldBeGreaterThanOrEqual -1f
            grading.warmthShift shouldBeLessThanOrEqual 1f
            grading.brightnessShift shouldBeGreaterThanOrEqual -1f
            grading.brightnessShift shouldBeLessThanOrEqual 1f
            grading.saturationShift shouldBeGreaterThanOrEqual -1f
            grading.saturationShift shouldBeLessThanOrEqual 1f
            grading.tintStrength shouldBeGreaterThanOrEqual 0f
            grading.tintStrength shouldBeLessThanOrEqual 1f
        }
    }

    @Test
    fun `the effective strength is bounded by MAX_SEASON_GRADING_STRENGTH`() {
        WallpaperSeasonGrading.effectiveStrength(1f) shouldBe WallpaperSeasonGrading.MAX_SEASON_GRADING_STRENGTH
        WallpaperSeasonGrading.effectiveStrength(2f) shouldBe WallpaperSeasonGrading.MAX_SEASON_GRADING_STRENGTH
        WallpaperSeasonGrading.effectiveStrength(0.5f) shouldBeLessThan WallpaperSeasonGrading.MAX_SEASON_GRADING_STRENGTH
    }

    @Test
    fun `a strength of zero produces a neutral grading that does not change colour`() {
        val base = WallpaperSeasonGrading.baseGradingFor(WallpaperSeason.WINTER)
        val effective = WallpaperSeasonGrading.effectiveGrading(base, strength = 0f)

        effective.warmthShift shouldBe 0f
        effective.brightnessShift shouldBe 0f
        effective.saturationShift shouldBe 0f
        effective.tintStrength shouldBe 0f
    }

    @Test
    fun `the disabled flag yields a grading alpha of zero`() {
        WallpaperSeasonGrading.gradingAlpha(experimentEnabled = false, daylight = 1f) shouldBe 0f
        WallpaperSeasonGrading.gradingAlpha(experimentEnabled = false, daylight = 0f) shouldBe 0f
    }

    @Test
    fun `the daylight factor lowers the grading alpha at night`() {
        val day = WallpaperSeasonGrading.gradingAlpha(experimentEnabled = true, daylight = 1f)
        val night = WallpaperSeasonGrading.gradingAlpha(experimentEnabled = true, daylight = 0f)

        night shouldBeLessThan day
        night shouldBeGreaterThan 0f
    }

    @Test
    fun `a season change produces a continuous transition and no jump`() {
        val winter = WallpaperSeasonGrading.baseGradingFor(WallpaperSeason.WINTER)
        val summer = WallpaperSeasonGrading.baseGradingFor(WallpaperSeason.SUMMER)

        val atStart = WallpaperSeasonGrading.lerpGrading(winter, summer, progress = 0f)
        val atEnd = WallpaperSeasonGrading.lerpGrading(winter, summer, progress = 1f)
        val atMid = WallpaperSeasonGrading.lerpGrading(winter, summer, progress = 0.5f)

        atStart shouldBe winter
        atEnd shouldBe summer
        atMid.warmthShift shouldBe ((winter.warmthShift + summer.warmthShift) / 2f).plusOrMinus(0.0001f)
    }

    @Test
    fun `season determination is deterministic for a given date and latitude`() {
        val first = WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 3, 21), latitude = 52.0)
        val second = WallpaperSeasonGrading.seasonFor(LocalDate.of(2024, 3, 21), latitude = 52.0)

        first shouldBe second
    }

    @Test
    fun `lerping at progress zero or one returns the exact endpoints`() {
        val from = WallpaperSeasonGrading.baseGradingFor(WallpaperSeason.AUTUMN)
        val to = WallpaperSeasonGrading.baseGradingFor(WallpaperSeason.SPRING)

        WallpaperSeasonGrading.lerpGrading(from, to, progress = 0f) shouldBe from
        WallpaperSeasonGrading.lerpGrading(from, to, progress = 1f) shouldBe to
    }
}
