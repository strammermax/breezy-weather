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

package com.liveweatherwallpaperapp.sources.smg

import io.reactivex.rxjava3.core.Observable
import com.liveweatherwallpaperapp.sources.smg.json.SmgBulletinResult
import com.liveweatherwallpaperapp.sources.smg.json.SmgCurrentResult
import com.liveweatherwallpaperapp.sources.smg.json.SmgForecastResult
import com.liveweatherwallpaperapp.sources.smg.json.SmgOutlookResult
import com.liveweatherwallpaperapp.sources.smg.json.SmgUvResult
import com.liveweatherwallpaperapp.sources.smg.json.SmgWarningResult
import retrofit2.http.POST
import retrofit2.http.Query

interface SmgApi {
    @POST("weather_v2")
    fun getHourly(
        @Query("selection") selection: String = "48detail",
    ): Observable<SmgForecastResult>

    @POST("weather_v2")
    fun getDaily(
        @Query("selection") selection: String = "7daysforecast",
        @Query("lang") lang: String = "e",
    ): Observable<SmgForecastResult>

    @POST("weather_v2")
    fun getBulletin(
        @Query("selection") selection: String = "forecast",
        @Query("lang") lang: String = "e",
    ): Observable<SmgBulletinResult>

    @POST("weather_v2")
    fun getOutlook(
        @Query("selection") selection: String = "weatherForecastDesc",
    ): Observable<SmgOutlookResult>

    @POST("weather_v2")
    fun getCurrent(
        @Query("selection") selection: String = "actualweather",
        @Query("lang") lang: String = "e",
    ): Observable<SmgCurrentResult>

    @POST("weather_v2")
    fun getUVIndex(
        @Query("selection") selection: String = "actualUVI",
        @Query("lang") lang: String = "e",
    ): Observable<SmgUvResult>

    @POST("weather_v2")
    fun getWarning(
        @Query("selection") warning: String,
        @Query("lang") lang: String = "e",
    ): Observable<SmgWarningResult>
}
