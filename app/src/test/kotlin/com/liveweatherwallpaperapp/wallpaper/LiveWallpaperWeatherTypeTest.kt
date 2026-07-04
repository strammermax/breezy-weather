package com.liveweatherwallpaperapp.wallpaper

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LiveWallpaperWeatherTypeTest {

    @Test
    fun `visual-only legacy values migrate to codes outside the WMO range`() {
        normalizeWallpaperWeatherType("rotating") shouldBe "200"
        normalizeWallpaperWeatherType("HOLLANDSE_LUCHT") shouldBe "201"
    }

    @Test
    fun `official and automatic values remain unchanged`() {
        normalizeWallpaperWeatherType("auto") shouldBe "auto"
        normalizeWallpaperWeatherType("CLEAR") shouldBe "CLEAR"
        normalizeWallpaperWeatherType("RAIN") shouldBe "RAIN"
    }
}
