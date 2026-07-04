package com.liveweatherwallpaperapp.sources.openmeteo

import livewallpaperweather.domain.weather.reference.WeatherCode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OpenMeteoWeatherCodeTest {

    @Test
    fun `official WMO codes map to matching semantic weather types`() {
        assertCodes(WeatherCode.CLEAR, 0, 1)
        assertCodes(WeatherCode.PARTLY_CLOUDY, 2)
        assertCodes(WeatherCode.CLOUDY, 3)
        assertCodes(WeatherCode.FOG, 45, 48)
        assertCodes(WeatherCode.RAIN, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
        assertCodes(WeatherCode.SNOW, 71, 73, 75, 77, 85, 86)
        assertCodes(WeatherCode.THUNDERSTORM, 95, 96, 99)
    }

    @Test
    fun `visual-only and unknown codes are not accepted as fetched weather`() {
        getOpenMeteoWeatherCode(null) shouldBe null
        getOpenMeteoWeatherCode(200) shouldBe null
        getOpenMeteoWeatherCode(201) shouldBe null
    }

    private fun assertCodes(expected: WeatherCode, vararg codes: Int) {
        codes.forEach { code ->
            getOpenMeteoWeatherCode(code) shouldBe expected
        }
    }
}
