package com.livewallpaperweather.sources.veduris

import android.content.Context
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceContinent
import livewallpaperweather.domain.source.SourceFeature
import com.livewallpaperweather.common.extensions.code
import com.livewallpaperweather.common.extensions.currentLocale
import com.livewallpaperweather.common.extensions.getCountryName
import com.livewallpaperweather.common.source.HttpSource
import com.livewallpaperweather.common.source.LocationParametersSource
import com.livewallpaperweather.common.source.NonFreeNetSource
import com.livewallpaperweather.common.source.ReverseGeocodingSource
import com.livewallpaperweather.common.source.WeatherSource
import com.livewallpaperweather.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import com.livewallpaperweather.common.source.WeatherSource.Companion.PRIORITY_NONE

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
