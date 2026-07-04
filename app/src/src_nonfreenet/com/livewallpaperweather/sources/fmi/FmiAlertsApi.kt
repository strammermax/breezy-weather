package com.livewallpaperweather.sources.fmi

import io.reactivex.rxjava3.core.Observable
import com.livewallpaperweather.sources.common.xml.CapAlert
import com.livewallpaperweather.sources.fmi.xml.FmiAlertsResult
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

interface FmiAlertsApi {
    @GET("cap/feed/rss_en-GB.rss")
    fun getAlerts(): Call<FmiAlertsResult>

    @GET
    fun getAlert(
        @Url url: String,
    ): Observable<CapAlert>
}
