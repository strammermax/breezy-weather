package com.liveweatherwallpaperapp.sources.veduris

import android.content.Context
import com.liveweatherwallpaperapp.common.extensions.code
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.getCountryName
import com.liveweatherwallpaperapp.common.source.HttpSource
import com.liveweatherwallpaperapp.common.source.LocationParametersSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import com.liveweatherwallpaperapp.common.source.ReverseGeocodingSource
import com.liveweatherwallpaperapp.common.source.WeatherSource
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_NONE
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class VedurIsServiceStub(context: Context) :
    HttpSource(),
    WeatherSource,
    ReverseGeocodingSource,
    LocationParametersSource,
    NonFreeNetSource {

    override val id = "veduris"
    private val countryName = context.currentLocale.getCountryName("IS")
    override val name by lazy {
        if (context.currentLocale.code.startsWith("is")) {
            "Veðurstofa Íslands"
        } else {
            "Icelandic Met Office"
        }.let {
            if (it.contains(countryName)) {
                it
            } else {
                "$it ($countryName)"
            }
        }
    }
    override val continent = SourceContinent.EUROPE

    protected val weatherAttribution by lazy {
        if (context.currentLocale.code.startsWith("is")) {
            "Veðurstofa Íslands"
        } else {
            "Icelandic Met Office"
        }
    }
    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to weatherAttribution,
        SourceFeature.CURRENT to weatherAttribution,
        SourceFeature.ALERT to weatherAttribution,
        SourceFeature.REVERSE_GEOCODING to weatherAttribution
    )

    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return location.countryCode.equals("IS", ignoreCase = true)
    }

    override fun getFeaturePriorityForLocation(
        location: Location,
        feature: SourceFeature,
    ): Int {
        return when {
            isFeatureSupportedForLocation(location, feature) -> PRIORITY_HIGHEST
            else -> PRIORITY_NONE
        }
    }

    // Only supports its own country
    override val knownAmbiguousCountryCodes: Array<String>? = null
}
