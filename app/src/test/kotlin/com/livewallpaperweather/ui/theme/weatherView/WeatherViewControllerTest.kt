package com.livewallpaperweather.ui.theme.weatherView

import livewallpaperweather.domain.weather.reference.WeatherCode
import io.kotest.matchers.shouldBe
import com.livewallpaperweather.R
import org.junit.jupiter.api.Test

class WeatherViewControllerTest {

    @Test
    fun `weather texts use semantic resources instead of wallpaper preset positions`() {
        val expectedResources = mapOf(
            WeatherCode.CLEAR to R.string.weather_kind_clear,
            WeatherCode.PARTLY_CLOUDY to R.string.weather_kind_partly_cloudy,
            WeatherCode.CLOUDY to R.string.weather_kind_cloudy,
            WeatherCode.RAIN to R.string.weather_kind_rain,
            WeatherCode.SNOW to R.string.weather_kind_snow,
            WeatherCode.SLEET to R.string.weather_kind_sleet,
            WeatherCode.HAIL to R.string.weather_kind_hail,
            WeatherCode.FOG to R.string.weather_kind_fog,
            WeatherCode.HAZE to R.string.weather_kind_haze,
            WeatherCode.THUNDER to R.string.weather_kind_thunder,
            WeatherCode.THUNDERSTORM to R.string.weather_kind_thunderstorm,
            WeatherCode.WIND to R.string.weather_kind_wind,
        )

        expectedResources.forEach { (weatherCode, resource) ->
            WeatherViewController.getWeatherTextResource(weatherCode) shouldBe resource
        }
    }
}
