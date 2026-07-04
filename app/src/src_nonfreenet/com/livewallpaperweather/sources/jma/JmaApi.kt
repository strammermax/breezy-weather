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

package com.livewallpaperweather.sources.jma

import io.reactivex.rxjava3.core.Observable
import com.livewallpaperweather.sources.jma.json.JmaAlertResult
import com.livewallpaperweather.sources.jma.json.JmaAmedasResult
import com.livewallpaperweather.sources.jma.json.JmaAreasResult
import com.livewallpaperweather.sources.jma.json.JmaBulletinResult
import com.livewallpaperweather.sources.jma.json.JmaClass20sResult
import com.livewallpaperweather.sources.jma.json.JmaCurrentResult
import com.livewallpaperweather.sources.jma.json.JmaDailyResult
import com.livewallpaperweather.sources.jma.json.JmaForecastAreaResult
import com.livewallpaperweather.sources.jma.json.JmaHourlyResult
import com.livewallpaperweather.sources.jma.json.JmaRelmResult
import com.livewallpaperweather.sources.jma.json.JmaWeekAreaResult
import retrofit2.http.GET
import retrofit2.http.Path

interface JmaApi {
    @GET("bosai/common/const/relm.json")
    fun getRelm(): Observable<List<JmaRelmResult>>

    @GET("bosai/common/const/geojson/class20s_{part}.json")
    fun getClass20s(
        @Path("part") part: Int,
    ): Observable<JmaClass20sResult>

    @GET("bosai/common/const/area.json")
    fun getAreas(): Observable<JmaAreasResult>

    @GET("bosai/jmatile/data/wdist/VPFD/{class10s}.json")
    fun getHourly(
        @Path("class10s") class10s: String,
    ): Observable<JmaHourlyResult>

    @GET("bosai/forecast/data/forecast/{prefArea}.json")
    fun getDaily(
        @Path("prefArea") prefArea: String,
    ): Observable<List<JmaDailyResult>>

    @GET("bosai/forecast/const/week_area05.json")
    fun getWeekArea05(): Observable<Map<String, List<String>>>

    @GET("bosai/forecast/const/week_area.json")
    fun getWeekArea(): Observable<Map<String, List<JmaWeekAreaResult>>>

    @GET("bosai/forecast/const/forecast_area.json")
    fun getForecastArea(): Observable<Map<String, List<JmaForecastAreaResult>>>

    @GET("bosai/amedas/const/amedastable.json")
    fun getAmedas(): Observable<Map<String, JmaAmedasResult>>

    @GET("/bosai/amedas/data/point/{amedas}/{timestamp}.json")
    fun getCurrent(
        @Path("amedas") amedas: String,
        @Path("timestamp") timestamp: String,
    ): Observable<Map<String, JmaCurrentResult>>

    @GET("bosai/forecast/data/overview_forecast/{prefArea}.json")
    fun getBulletin(
        @Path("prefArea") prefArea: String,
    ): Observable<JmaBulletinResult>

    @GET("bosai/warning/data/warning/{prefArea}.json")
    fun getAlert(
        @Path("prefArea") prefArea: String,
    ): Observable<JmaAlertResult>
}
