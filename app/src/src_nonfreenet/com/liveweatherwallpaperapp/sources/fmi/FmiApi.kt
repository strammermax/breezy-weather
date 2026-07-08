package com.liveweatherwallpaperapp.sources.fmi

import com.liveweatherwallpaperapp.sources.fmi.xml.FmiSimpleResult
import com.liveweatherwallpaperapp.sources.fmi.xml.FmiStationsResult
import io.reactivex.rxjava3.core.Observable
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface FmiApi {
    @GET("wfs")
    fun getForecast(
        @Query("service") service: String = "WFS",
        @Query("version") version: String = "2.0.0",
        @Query("request") request: String = "getFeature",
        @Query("storedquery_id") storedQueryId: String,
        @Query("latlon") latlon: String, // e.g. 61.2,21
        @Query("endtime") endtime: String,
    ): Call<FmiSimpleResult>

    @GET("wfs")
    fun getCurrent(
        @Query("service") service: String = "WFS",
        @Query("version") version: String = "2.0.0",
        @Query("request") request: String = "getFeature",
        @Query("storedquery_id") storedQueryId: String = "fmi::observations::weather::simple",
        @Query("fmisid") fmisid: String,
        @Query("starttime") starttime: String,
    ): Call<FmiSimpleResult>

    @GET("wfs")
    fun getStations(
        @Query("service") service: String = "WFS",
        @Query("version") version: String = "2.0.0",
        @Query("request") request: String = "getFeature",
        @Query("storedquery_id") storedQueryId: String = "fmi::ef::stations",
    ): Observable<FmiStationsResult>

    @GET("wfs")
    fun getNormals(
        @Query("service") service: String = "WFS",
        @Query("version") version: String = "2.0.0",
        @Query("request") request: String = "getFeature",
        @Query("storedquery_id") storedQueryId: String = "fmi::observations::weather::monthly::30year::simple",
        @Query("fmisid") fmisid: String,
    ): Call<FmiSimpleResult>
}
