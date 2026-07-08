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

package com.liveweatherwallpaperapp.sources.openweather

import android.content.Context
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.capitalize
import com.liveweatherwallpaperapp.common.extensions.code
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.getIsoFormattedDate
import com.liveweatherwallpaperapp.common.extensions.toDate
import com.liveweatherwallpaperapp.common.extensions.toDateNoHour
import com.liveweatherwallpaperapp.common.preference.EditTextPreference
import com.liveweatherwallpaperapp.common.preference.Preference
import com.liveweatherwallpaperapp.domain.settings.SourceConfigStore
import com.liveweatherwallpaperapp.sources.openweather.json.OpenWeatherAirPollution
import com.liveweatherwallpaperapp.sources.openweather.json.OpenWeatherAirPollutionResult
import com.liveweatherwallpaperapp.sources.openweather.json.OpenWeatherForecast
import com.liveweatherwallpaperapp.sources.openweather.json.OpenWeatherForecastResult
import com.liveweatherwallpaperapp.unit.distance.Distance.Companion.meters
import com.liveweatherwallpaperapp.unit.pollutant.PollutantConcentration.Companion.microgramsPerCubicMeter
import com.liveweatherwallpaperapp.unit.precipitation.Precipitation.Companion.millimeters
import com.liveweatherwallpaperapp.unit.pressure.Pressure.Companion.hectopascals
import com.liveweatherwallpaperapp.unit.ratio.Ratio.Companion.fraction
import com.liveweatherwallpaperapp.unit.ratio.Ratio.Companion.percent
import com.liveweatherwallpaperapp.unit.speed.Speed.Companion.metersPerSecond
import com.liveweatherwallpaperapp.unit.temperature.Temperature.Companion.celsius
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceFeature
import livewallpaperweather.domain.weather.model.AirQuality
import livewallpaperweather.domain.weather.model.Precipitation
import livewallpaperweather.domain.weather.model.PrecipitationProbability
import livewallpaperweather.domain.weather.model.Wind
import livewallpaperweather.domain.weather.reference.WeatherCode
import livewallpaperweather.domain.weather.wrappers.AirQualityWrapper
import livewallpaperweather.domain.weather.wrappers.CurrentWrapper
import livewallpaperweather.domain.weather.wrappers.DailyWrapper
import livewallpaperweather.domain.weather.wrappers.HourlyWrapper
import livewallpaperweather.domain.weather.wrappers.TemperatureWrapper
import livewallpaperweather.domain.weather.wrappers.WeatherWrapper
import retrofit2.Retrofit
import java.util.Date
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.Duration.Companion.seconds

/**
 * OpenWeatherMap
 * No longer based on OneCall API as it is now billing-only
 */
class OpenWeatherService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") client: Retrofit.Builder,
) : OpenWeatherServiceStub() {

    override val privacyPolicyUrl = "https://openweather.co.uk/privacy-policy"

    private val mApi by lazy {
        client
            .baseUrl(OPEN_WEATHER_BASE_URL)
            .build()
            .create(OpenWeatherApi::class.java)
    }

    override val attributionLinks = mapOf(
        name to "https://openweather.co.uk/"
    )

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val apiKey = getApiKeyOrDefault()
        val languageCode = context.currentLocale.code
        val failedFeatures = mutableMapOf<SourceFeature, Throwable>()
        val forecast = if (SourceFeature.FORECAST in requestedFeatures) {
            mApi.getForecast(
                apiKey,
                location.latitude,
                location.longitude,
                "metric",
                languageCode
            ).onErrorResumeNext {
                failedFeatures[SourceFeature.FORECAST] = it
                Observable.just(OpenWeatherForecastResult())
            }
        } else {
            Observable.just(OpenWeatherForecastResult())
        }
        val current = if (SourceFeature.CURRENT in requestedFeatures) {
            mApi.getCurrent(
                apiKey,
                location.latitude,
                location.longitude,
                "metric",
                languageCode
            ).onErrorResumeNext {
                failedFeatures[SourceFeature.CURRENT] = it
                Observable.just(OpenWeatherForecast())
            }
        } else {
            Observable.just(OpenWeatherForecast())
        }
        val airPollution = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
            mApi.getAirPollution(
                apiKey,
                location.latitude,
                location.longitude
            ).onErrorResumeNext {
                failedFeatures[SourceFeature.AIR_QUALITY] = it
                Observable.just(OpenWeatherAirPollutionResult())
            }
        } else {
            Observable.just(OpenWeatherAirPollutionResult())
        }
        return Observable.zip(forecast, current, airPollution) {
                forecastResult: OpenWeatherForecastResult,
                currentResult: OpenWeatherForecast,
                airPollutionResult: OpenWeatherAirPollutionResult,
            ->
            WeatherWrapper(
                dailyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getDailyList(forecastResult.list, location)
                } else {
                    null
                },
                hourlyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getHourlyList(forecastResult.list)
                } else {
                    null
                },
                current = if (SourceFeature.CURRENT in requestedFeatures) {
                    getCurrent(currentResult)
                } else {
                    null
                },
                airQuality = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
                    AirQualityWrapper(hourlyForecast = getHourlyAirQuality(airPollutionResult.list))
                } else {
                    null
                },
                failedFeatures = failedFeatures
            )
        }
    }

    private fun getCurrent(currentResult: OpenWeatherForecast): CurrentWrapper? {
        if (currentResult.dt == null) return null

        return CurrentWrapper(
            weatherText = currentResult.weather?.getOrNull(0)?.description?.capitalize(),
            weatherCode = getWeatherCode(currentResult.weather?.getOrNull(0)?.id),
            temperature = TemperatureWrapper(
                temperature = currentResult.main?.temp?.celsius,
                feelsLike = currentResult.main?.feelsLike?.celsius
            ),
            wind = Wind(
                degree = currentResult.wind?.deg?.toDouble(),
                speed = currentResult.wind?.speed?.metersPerSecond,
                gusts = currentResult.wind?.gust?.metersPerSecond
            ),
            relativeHumidity = currentResult.main?.humidity?.percent,
            pressure = currentResult.main?.pressure?.hectopascals,
            cloudCover = currentResult.clouds?.all?.percent,
            visibility = currentResult.visibility?.meters
        )
    }

    private fun getDailyList(
        hourlyResult: List<OpenWeatherForecast>?,
        location: Location,
    ): List<DailyWrapper> {
        if (hourlyResult.isNullOrEmpty()) return emptyList()
        val dailyList = mutableListOf<DailyWrapper>()
        val hourlyListByDay = hourlyResult.groupBy {
            it.dt!!.seconds.inWholeMilliseconds.toDate().getIsoFormattedDate(location)
        }
        for (i in 0 until hourlyListByDay.entries.size - 1) {
            val dayDate = hourlyListByDay.keys.toTypedArray()[i].toDateNoHour(location.timeZone)
            if (dayDate != null) {
                dailyList.add(
                    DailyWrapper(
                        date = dayDate
                    )
                )
            }
        }
        return dailyList
    }

    private fun getHourlyList(
        hourlyResult: List<OpenWeatherForecast>?,
    ): List<HourlyWrapper>? {
        return hourlyResult?.map { result ->
            val theDate = result.dt!!.seconds.inWholeMilliseconds.toDate()
            HourlyWrapper(
                date = theDate,
                weatherText = result.weather?.getOrNull(0)?.main?.capitalize(),
                weatherCode = getWeatherCode(result.weather?.getOrNull(0)?.id),
                temperature = TemperatureWrapper(
                    temperature = result.main?.temp?.celsius,
                    feelsLike = result.main?.feelsLike?.celsius
                ),
                precipitation = Precipitation(
                    rain = result.rain?.cumul3h?.millimeters,
                    snow = result.snow?.cumul3h?.millimeters
                ),
                precipitationProbability = PrecipitationProbability(total = result.pop?.fraction),
                wind = Wind(
                    degree = result.wind?.deg?.toDouble(),
                    speed = result.wind?.speed?.metersPerSecond,
                    gusts = result.wind?.gust?.metersPerSecond
                ),
                relativeHumidity = result.main?.humidity?.percent,
                pressure = result.main?.pressure?.hectopascals,
                cloudCover = result.clouds?.all?.percent,
                visibility = result.visibility?.meters
            )
        }
    }

    private fun getWeatherCode(icon: Int?): WeatherCode? {
        return when (icon) {
            null -> null
            200, 201, 202 -> WeatherCode.THUNDERSTORM
            210, 211, 212 -> WeatherCode.THUNDER
            221, 230, 231, 232 -> WeatherCode.THUNDERSTORM
            300, 301, 302, 310, 311, 312, 313, 314, 321 -> WeatherCode.RAIN
            500, 501, 502, 503, 504 -> WeatherCode.RAIN
            511 -> WeatherCode.SLEET
            600, 601, 602 -> WeatherCode.SNOW
            611, 612, 613, 614, 615, 616 -> WeatherCode.SLEET
            620, 621, 622 -> WeatherCode.SNOW
            701, 711, 721, 731 -> WeatherCode.HAZE
            741 -> WeatherCode.FOG
            751, 761, 762 -> WeatherCode.HAZE
            771, 781 -> WeatherCode.WIND
            800 -> WeatherCode.CLEAR
            801, 802 -> WeatherCode.PARTLY_CLOUDY
            803, 804 -> WeatherCode.CLOUDY
            else -> null
        }
    }

    private fun getHourlyAirQuality(
        airPollutionResultList: List<OpenWeatherAirPollution>?,
    ): MutableMap<Date, AirQuality> {
        val airQualityHourly = mutableMapOf<Date, AirQuality>()
        airPollutionResultList?.forEach {
            airQualityHourly[it.dt.seconds.inWholeMilliseconds.toDate()] = AirQuality(
                pM25 = it.components?.pm25?.microgramsPerCubicMeter,
                pM10 = it.components?.pm10?.microgramsPerCubicMeter,
                sO2 = it.components?.so2?.microgramsPerCubicMeter,
                nO2 = it.components?.no2?.microgramsPerCubicMeter,
                o3 = it.components?.o3?.microgramsPerCubicMeter,
                cO = it.components?.co?.microgramsPerCubicMeter
            )
        }
        return airQualityHourly
    }

    // CONFIG
    private val config = SourceConfigStore(context, id)
    private var apikey: String
        set(value) {
            config.edit().putString("apikey", value).apply()
        }
        get() = config.getString("apikey", null) ?: ""

    private fun getApiKeyOrDefault(): String {
        return apikey.ifEmpty { BuildConfig.OPEN_WEATHER_KEY }
    }
    override val isConfigured
        get() = getApiKeyOrDefault().isNotEmpty()

    override val isRestricted
        get() = apikey.isEmpty()

    override fun getPreferences(context: Context): List<Preference> {
        return listOf(
            EditTextPreference(
                titleId = R.string.settings_weather_source_open_weather_api_key,
                summary = { c, content ->
                    content.ifEmpty {
                        c.getString(R.string.settings_source_default_value)
                    }
                },
                content = apikey,
                onValueChanged = {
                    apikey = it
                }
            )
        )
    }

    companion object {
        private const val OPEN_WEATHER_BASE_URL = "https://api.openweathermap.org/"
    }
}
