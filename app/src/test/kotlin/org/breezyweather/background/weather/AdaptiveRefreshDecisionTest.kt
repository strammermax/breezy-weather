package org.breezyweather.background.weather

import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.model.Current
import breezyweather.domain.weather.model.Hourly
import breezyweather.domain.weather.model.Weather
import breezyweather.domain.weather.reference.WeatherCode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.time.Duration.Companion.minutes

class AdaptiveRefreshDecisionTest {

    private fun hourly(offsetMinutes: Long, weatherCode: WeatherCode?) = Hourly(
        date = Date(System.currentTimeMillis() + offsetMinutes.minutes.inWholeMilliseconds),
        weatherCode = weatherCode,
    )

    @Test
    fun `null weather never needs an adaptive refresh`() {
        needsAdaptiveRefresh(null) shouldBe false
    }

    @Test
    fun `an active alert always needs an adaptive refresh`() {
        val weather = Weather(
            current = Current(weatherCode = WeatherCode.CLEAR),
            alertList = listOf(Alert(alertId = "1", color = 0)),
        )

        needsAdaptiveRefresh(weather) shouldBe true
    }

    @Test
    fun `an expired alert does not count`() {
        val weather = Weather(
            current = Current(weatherCode = WeatherCode.CLEAR),
            alertList = listOf(
                Alert(alertId = "1", endDate = Date(System.currentTimeMillis() - 60_000), color = 0)
            ),
        )

        needsAdaptiveRefresh(weather) shouldBe false
    }

    @Test
    fun `stable clear weather with no alert does not need an adaptive refresh`() {
        val weather = Weather(
            current = Current(weatherCode = WeatherCode.CLEAR),
            hourlyForecast = listOf(
                hourly(0, WeatherCode.CLEAR),
                hourly(60, WeatherCode.CLEAR),
                hourly(120, WeatherCode.PARTLY_CLOUDY),
            ),
        )

        needsAdaptiveRefresh(weather) shouldBe false
    }

    @Test
    fun `rain starting within the next two hours needs an adaptive refresh`() {
        val weather = Weather(
            current = Current(weatherCode = WeatherCode.CLEAR),
            hourlyForecast = listOf(
                hourly(0, WeatherCode.CLEAR),
                hourly(60, WeatherCode.RAIN),
            ),
        )

        needsAdaptiveRefresh(weather) shouldBe true
    }

    @Test
    fun `rain stopping within the next two hours needs an adaptive refresh`() {
        val weather = Weather(
            current = Current(weatherCode = WeatherCode.RAIN),
            hourlyForecast = listOf(
                hourly(0, WeatherCode.RAIN),
                hourly(60, WeatherCode.CLOUDY),
            ),
        )

        needsAdaptiveRefresh(weather) shouldBe true
    }

    @Test
    fun `a change beyond the next two hours does not count`() {
        val weather = Weather(
            current = Current(weatherCode = WeatherCode.CLEAR),
            hourlyForecast = listOf(
                hourly(0, WeatherCode.CLEAR),
                hourly(60, WeatherCode.CLEAR),
                hourly(120, WeatherCode.CLEAR),
                hourly(180, WeatherCode.RAIN),
            ),
        )

        needsAdaptiveRefresh(weather) shouldBe false
    }

    @Test
    fun `plain thunder is not treated as precipitation`() {
        // THUNDER carries no rain (see the live wallpaper's rain-mode handling); a switch
        // between CLEAR and THUNDER alone should not trigger an adaptive refresh.
        val weather = Weather(
            current = Current(weatherCode = WeatherCode.CLEAR),
            hourlyForecast = listOf(
                hourly(0, WeatherCode.CLEAR),
                hourly(60, WeatherCode.THUNDER),
            ),
        )

        needsAdaptiveRefresh(weather) shouldBe false
    }

    @Test
    fun `a thunderstorm starting within the next two hours needs an adaptive refresh`() {
        val weather = Weather(
            current = Current(weatherCode = WeatherCode.CLEAR),
            hourlyForecast = listOf(
                hourly(0, WeatherCode.CLEAR),
                hourly(60, WeatherCode.THUNDERSTORM),
            ),
        )

        needsAdaptiveRefresh(weather) shouldBe true
    }

    @Test
    fun `no current weather code and no alert means no adaptive refresh`() {
        val weather = Weather(current = Current(weatherCode = null))

        needsAdaptiveRefresh(weather) shouldBe false
    }
}
