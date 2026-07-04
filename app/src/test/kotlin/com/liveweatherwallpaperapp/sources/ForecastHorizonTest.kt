package com.liveweatherwallpaperapp.sources

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.time.Duration.Companion.days

class ForecastHorizonTest {

    private val today = Date(1_750_000_000_000)

    @Test
    fun `five future days satisfy minimum horizon`() {
        val forecast = forecastWithDays(5)

        forecast.forecastDaysFrom(today) { it } shouldBe 5
        forecast.hasMinimumForecastDays(today) { it } shouldBe true
    }

    @Test
    fun `four future days do not satisfy minimum horizon`() {
        forecastWithDays(4).hasMinimumForecastDays(today) { it } shouldBe false
    }

    @Test
    fun `past days are excluded from future horizon`() {
        val forecast = listOf(Date(today.time - 1.days.inWholeMilliseconds)) + forecastWithDays(4)

        forecast.forecastDaysFrom(today) { it } shouldBe 4
        forecast.hasMinimumForecastDays(today) { it } shouldBe false
    }

    @Test
    fun `null and empty forecasts are incomplete`() {
        val missing: List<Date>? = null

        missing.hasMinimumForecastDays(today) { it } shouldBe false
        emptyList<Date>().hasMinimumForecastDays(today) { it } shouldBe false
    }

    private fun forecastWithDays(count: Int): List<Date> =
        List(count) { index -> Date(today.time + index.days.inWholeMilliseconds) }
}
