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

package com.liveweatherwallpaperapp.ui.theme.weatherView

import com.liveweatherwallpaperapp.BreezyWeather
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.domain.location.model.isDaylight
import com.liveweatherwallpaperapp.ui.theme.weatherView.WeatherView.WeatherKindRule
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.weather.reference.WeatherCode

object WeatherViewController {

    fun getWeatherCode(
        @WeatherKindRule weatherKind: Int,
    ): WeatherCode = when (weatherKind) {
        WeatherView.WEATHER_KIND_CLOUDY -> WeatherCode.CLOUDY
        WeatherView.WEATHER_KIND_CLOUD -> WeatherCode.PARTLY_CLOUDY
        WeatherView.WEATHER_KIND_FOG -> WeatherCode.FOG
        WeatherView.WEATHER_KIND_HAIL -> WeatherCode.HAIL
        WeatherView.WEATHER_KIND_HAZE -> WeatherCode.HAZE
        WeatherView.WEATHER_KIND_RAINY -> WeatherCode.RAIN
        WeatherView.WEATHER_KIND_SLEET -> WeatherCode.SLEET
        WeatherView.WEATHER_KIND_SNOW -> WeatherCode.SNOW
        WeatherView.WEATHER_KIND_THUNDERSTORM -> WeatherCode.THUNDERSTORM
        WeatherView.WEATHER_KIND_THUNDER -> WeatherCode.THUNDER
        WeatherView.WEATHER_KIND_WIND -> WeatherCode.WIND
        else -> WeatherCode.CLEAR
    }

    @WeatherKindRule
    fun getWeatherKind(location: Location?): Int = getWeatherKind(location?.weather?.current?.weatherCode)

    fun isDaylight(location: Location?): Boolean = location?.isDaylight ?: true

    @WeatherKindRule
    fun getWeatherKind(weatherCode: WeatherCode?): Int = when (weatherCode) {
        WeatherCode.CLEAR -> WeatherView.WEATHER_KIND_CLEAR
        WeatherCode.PARTLY_CLOUDY -> WeatherView.WEATHER_KIND_CLOUD
        WeatherCode.CLOUDY -> WeatherView.WEATHER_KIND_CLOUDY
        WeatherCode.RAIN -> WeatherView.WEATHER_KIND_RAINY
        WeatherCode.SNOW -> WeatherView.WEATHER_KIND_SNOW
        WeatherCode.WIND -> WeatherView.WEATHER_KIND_WIND
        WeatherCode.FOG -> WeatherView.WEATHER_KIND_FOG
        WeatherCode.HAZE -> WeatherView.WEATHER_KIND_HAZE
        WeatherCode.SLEET -> WeatherView.WEATHER_KIND_SLEET
        WeatherCode.HAIL -> WeatherView.WEATHER_KIND_HAIL
        WeatherCode.THUNDER -> WeatherView.WEATHER_KIND_THUNDER
        WeatherCode.THUNDERSTORM -> WeatherView.WEATHER_KIND_THUNDERSTORM
        else -> WeatherView.WEATHER_KIND_NULL
    }

    fun getWeatherText(weatherCode: WeatherCode): String {
        return BreezyWeather.instance.getString(getWeatherTextResource(weatherCode))
    }

    /**
     * Weather data must never be translated through the live-wallpaper preset array. That
     * array also contains visual-only modes (codes 200 and 201), so its positions are not
     * weather-code positions and can change independently.
     */
    internal fun getWeatherTextResource(weatherCode: WeatherCode): Int = when (weatherCode) {
        WeatherCode.CLEAR -> R.string.weather_kind_clear
        WeatherCode.PARTLY_CLOUDY -> R.string.weather_kind_partly_cloudy
        WeatherCode.CLOUDY -> R.string.weather_kind_cloudy
        WeatherCode.RAIN -> R.string.weather_kind_rain
        WeatherCode.SNOW -> R.string.weather_kind_snow
        WeatherCode.SLEET -> R.string.weather_kind_sleet
        WeatherCode.HAIL -> R.string.weather_kind_hail
        WeatherCode.FOG -> R.string.weather_kind_fog
        WeatherCode.HAZE -> R.string.weather_kind_haze
        WeatherCode.THUNDER -> R.string.weather_kind_thunder
        WeatherCode.THUNDERSTORM -> R.string.weather_kind_thunderstorm
        WeatherCode.WIND -> R.string.weather_kind_wind
    }
}
