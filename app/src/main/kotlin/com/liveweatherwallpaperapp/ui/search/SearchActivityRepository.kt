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

package com.liveweatherwallpaperapp.ui.search

import android.content.Context
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.common.rxjava.ObserverContainer
import com.liveweatherwallpaperapp.common.rxjava.SchedulerTransformer
import com.liveweatherwallpaperapp.common.source.LocationSearchSource
import com.liveweatherwallpaperapp.domain.location.model.applyDefaultPreset
import com.liveweatherwallpaperapp.domain.settings.ConfigStore
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.sources.RefreshHelper
import com.liveweatherwallpaperapp.sources.SourceManager
import com.liveweatherwallpaperapp.ui.main.utils.RefreshErrorType
import com.liveweatherwallpaperapp.wallpaper.photo.RemoveSkyInterceptor
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.observers.DisposableObserver
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.location.model.LocationAddressInfo
import livewallpaperweather.domain.source.SourceFeature
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SearchActivityRepository @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val mRefreshHelper: RefreshHelper,
    private val mSourceManager: SourceManager,
    private val mCompositeDisposable: CompositeDisposable,
) {
    private val mConfig: ConfigStore = ConfigStore(context, PREFERENCE_SEARCH_CONFIG)
    private val wallpaperImageStore = WallpaperImageStore(context)
    private val fictionalLocationClient = OkHttpClient.Builder()
        .addInterceptor(RemoveSkyInterceptor(wallpaperImageStore))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun searchLocationList(
        context: Context,
        query: String,
        locationSearchSource: String,
        callback: (t: Pair<List<LocationAddressInfo>?, RefreshErrorType?>?, done: Boolean) -> Unit,
    ) {
        Observable.zip(
            mRefreshHelper.requestSearchLocations(context, query, locationSearchSource),
            Observable.fromCallable { searchFictionalCities(query) }
                .onErrorReturnItem(emptyList())
        ) { regular, fictional ->
            (regular + fictional).distinctBy {
                "${it.city?.lowercase()}|${it.country?.lowercase()}|${it.latitude}|${it.longitude}"
            }
        }
            .compose(SchedulerTransformer.create())
            .subscribe(
                ObserverContainer(
                    mCompositeDisposable,
                    object : DisposableObserver<List<LocationAddressInfo>>() {
                        override fun onNext(t: List<LocationAddressInfo>) {
                            callback(Pair<List<LocationAddressInfo>, RefreshErrorType?>(t, null), true)
                        }

                        override fun onError(e: Throwable) {
                            callback(
                                Pair<List<LocationAddressInfo>, RefreshErrorType?>(
                                    emptyList(),
                                    RefreshErrorType.getTypeFromThrowable(
                                        context,
                                        e,
                                        RefreshErrorType.LOCATION_SEARCH_FAILED
                                    )
                                ),
                                true
                            )
                        }

                        override fun onComplete() {
                            // do nothing.
                        }
                    }
                )
            )
    }

    suspend fun getLocationWithDisambiguatedCountryCode(
        location: Location,
        locationSearchSource: LocationSearchSource,
        context: Context,
    ): Location {
        if (location.isFictional) return location
        return if (locationSearchSource.knownAmbiguousCountryCodes?.any { cc ->
                location.countryCode.equals(cc, ignoreCase = true)
            } != false ||
            location.countryCode.equals("AN", ignoreCase = true)
        ) {
            mRefreshHelper.getLocationWithDisambiguatedCountryCode(location, context)
        } else {
            location
        }
    }

    fun getLocationWithAppliedPreference(
        location: Location,
        context: Context,
    ): Location {
        val defaultSource = SettingsManager.getInstance(context).defaultForecastSource

        return when (defaultSource) {
            "auto" -> location.applyDefaultPreset(mSourceManager)
            else -> {
                val source = mSourceManager.getWeatherSource(defaultSource)
                if (source == null) {
                    location.applyDefaultPreset(mSourceManager)
                } else {
                    location.copy(
                        forecastSource = source.id,
                        currentSource = if (SourceFeature.CURRENT in
                            source.supportedFeatures &&
                            source.isFeatureSupportedForLocation(
                                location,
                                SourceFeature.CURRENT
                            )
                        ) {
                            source.id
                        } else {
                            null
                        },
                        airQualitySource = if (SourceFeature.AIR_QUALITY in
                            source.supportedFeatures &&
                            source.isFeatureSupportedForLocation(
                                location,
                                SourceFeature.AIR_QUALITY
                            )
                        ) {
                            source.id
                        } else {
                            null
                        },
                        pollenSource = if (SourceFeature.POLLEN in
                            source.supportedFeatures &&
                            source.isFeatureSupportedForLocation(
                                location,
                                SourceFeature.POLLEN
                            )
                        ) {
                            source.id
                        } else {
                            null
                        },
                        minutelySource = if (SourceFeature.MINUTELY in
                            source.supportedFeatures &&
                            source.isFeatureSupportedForLocation(
                                location,
                                SourceFeature.MINUTELY
                            )
                        ) {
                            source.id
                        } else {
                            null
                        },
                        alertSource = if (SourceFeature.ALERT in
                            source.supportedFeatures &&
                            source.isFeatureSupportedForLocation(
                                location,
                                SourceFeature.ALERT
                            )
                        ) {
                            source.id
                        } else {
                            null
                        },
                        normalsSource = if (SourceFeature.NORMALS in
                            source.supportedFeatures &&
                            source.isFeatureSupportedForLocation(
                                location,
                                SourceFeature.NORMALS
                            )
                        ) {
                            source.id
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    var lastSelectedLocationSearchSource: String
        set(value) {
            mConfig.edit().putString(KEY_LAST_DEFAULT_SOURCE, value).apply()
        }
        get() {
            return mConfig.getString(KEY_LAST_DEFAULT_SOURCE, null)
                ?: BuildConfig.DEFAULT_LOCATION_SEARCH_SOURCE
        }

    fun cancel() {
        mCompositeDisposable.clear()
    }

    private fun searchFictionalCities(query: String): List<LocationAddressInfo> {
        val baseUrl = wallpaperImageStore.removeSkyBaseUrl.toHttpUrlOrNull() ?: return emptyList()
        val url = baseUrl.newBuilder()
            .addPathSegments("api/v1/manage/cities/table")
            .addQueryParameter("q", query)
            .addQueryParameter("is_fictional", "1")
            .build()
        val request = Request.Builder().url(url).get().build()
        return fictionalLocationClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val cities = JSONObject(response.body.string()).optJSONArray("results") ?: return emptyList()
            buildList {
                for (index in 0 until cities.length()) {
                    val city = cities.optJSONObject(index) ?: continue
                    val name = city.optString("city").trim()
                    if (name.isEmpty()) continue
                    add(
                        LocationAddressInfo(
                            latitude = city.optDouble("lat", 0.0),
                            longitude = city.optDouble("lng", 0.0),
                            timeZoneId = "UTC",
                            country = city.optString("country").ifBlank { FICTIONAL_COUNTRY },
                            // This endpoint is explicitly filtered by is_fictional=1. Keep one
                            // stable internal marker; the user-facing country may be any name.
                            countryCode = FICTIONAL_COUNTRY_CODE,
                            admin1 = city.optString("admin_name").takeIf { it.isNotBlank() },
                            city = name,
                            cityCode = city.optLong("id").takeIf { it != 0L }?.toString()
                        )
                    )
                }
            }
        }
    }

    companion object {
        private const val PREFERENCE_SEARCH_CONFIG = "SEARCH_CONFIG"
        private const val KEY_LAST_DEFAULT_SOURCE = "LAST_DEFAULT_SOURCE"
        private const val FICTIONAL_COUNTRY = "Fictief"
        private const val FICTIONAL_COUNTRY_CODE = "ZZ"
    }
}
