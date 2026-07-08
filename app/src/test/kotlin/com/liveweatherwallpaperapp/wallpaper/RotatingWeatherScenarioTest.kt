/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RotatingWeatherScenarioTest {
    private val states = RotatingWeatherScenarios.ALL.map { scenario ->
        WallpaperSceneStateFactory.create(
            weatherKind = scenario.weatherKind,
            daylight = 1f,
            precipitationMillimetersPerHour = scenario.precipitationMillimetersPerHour,
            cloudCoverPercent = scenario.cloudCoverPercent,
            visibilityMeters = scenario.visibilityMeters
        )
    }

    @Test
    fun `scenario labels are unique`() {
        RotatingWeatherScenarios.ALL.map { it.labelRes }.distinct().size shouldBe RotatingWeatherScenarios.ALL.size
    }

    @Test
    fun `rotating test covers every normalized sky condition`() {
        states.map { it.condition.sky }.toSet() shouldContainAll WallpaperSkyCondition.entries
    }

    @Test
    fun `rotating test covers every precipitation type and intensity`() {
        states.map { it.condition.precipitation }.toSet() shouldContainAll WallpaperPrecipitationCondition.entries
        states.map { it.condition.precipitationIntensity }.toSet() shouldContainAll WallpaperEffectIntensity.entries
    }

    @Test
    fun `rotating test covers every visibility condition plus thunder and wind`() {
        states.map { it.condition.visibility }.toSet() shouldContainAll WallpaperVisibilityCondition.entries
        states.any { it.condition.thunderIntensity in 0.01f..<1f } shouldBe true
        states.any { it.condition.thunderIntensity == 1f } shouldBe true
        states.any { it.condition.windy } shouldBe true
    }
}
