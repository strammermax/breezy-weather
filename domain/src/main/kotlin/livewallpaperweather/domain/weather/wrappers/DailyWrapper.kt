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

package livewallpaperweather.domain.weather.wrappers

import livewallpaperweather.domain.weather.model.AirQuality
import livewallpaperweather.domain.weather.model.Daily
import livewallpaperweather.domain.weather.model.DailyCloudCover
import livewallpaperweather.domain.weather.model.DailyDewPoint
import livewallpaperweather.domain.weather.model.DailyPressure
import livewallpaperweather.domain.weather.model.DailyRelativeHumidity
import livewallpaperweather.domain.weather.model.DailyVisibility
import livewallpaperweather.domain.weather.model.DegreeDay
import livewallpaperweather.domain.weather.model.Pollen
import livewallpaperweather.domain.weather.model.UV
import java.util.Date
import kotlin.time.Duration

/**
 * Daily wrapper
 */
data class DailyWrapper(
    val date: Date,
    val day: HalfDayWrapper? = null,
    val night: HalfDayWrapper? = null,
    val degreeDay: DegreeDay? = null,
    val uV: UV? = null,
    val sunshineDuration: Duration? = null,
    val relativeHumidity: DailyRelativeHumidity? = null,
    val dewPoint: DailyDewPoint? = null,
    val pressure: DailyPressure? = null,
    val cloudCover: DailyCloudCover? = null,
    val visibility: DailyVisibility? = null,
) {
    fun toDaily(
        airQuality: AirQuality? = null,
        pollen: Pollen? = null,
    ) = Daily(
        date = this.date,
        day = this.day?.toHalfDay(),
        night = this.night?.toHalfDay(),
        degreeDay = this.degreeDay,
        airQuality = airQuality,
        pollen = pollen,
        uV = this.uV,
        sunshineDuration = this.sunshineDuration,
        relativeHumidity = this.relativeHumidity,
        dewPoint = this.dewPoint,
        pressure = this.pressure,
        cloudCover = this.cloudCover,
        visibility = this.visibility
    )
}
