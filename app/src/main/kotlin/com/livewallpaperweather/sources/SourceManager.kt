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

package com.livewallpaperweather.sources

import android.content.Context
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import com.livewallpaperweather.BreezyWeather
import com.livewallpaperweather.BuildConfig
import com.livewallpaperweather.common.extensions.currentLocale
import com.livewallpaperweather.common.source.BroadcastSource
import com.livewallpaperweather.common.source.ConfigurableSource
import com.livewallpaperweather.common.source.FeatureSource
import com.livewallpaperweather.common.source.HttpSource
import com.livewallpaperweather.common.source.LocationSearchSource
import com.livewallpaperweather.common.source.LocationSource
import com.livewallpaperweather.common.source.NonFreeNetSource
import com.livewallpaperweather.common.source.PollenIndexSource
import com.livewallpaperweather.common.source.PreferencesParametersSource
import com.livewallpaperweather.common.source.RemovedSource
import com.livewallpaperweather.common.source.ReverseGeocodingSource
import com.livewallpaperweather.common.source.Source
import com.livewallpaperweather.common.source.TimeZoneSource
import com.livewallpaperweather.common.source.WeatherSource
import com.livewallpaperweather.common.source.WeatherSource.Companion.PRIORITY_NONE
import com.livewallpaperweather.domain.settings.SourceConfigStore
import com.livewallpaperweather.sources.accu.AccuService
import com.livewallpaperweather.sources.aemet.AemetService
import com.livewallpaperweather.sources.android.AndroidGeocoderService
import com.livewallpaperweather.sources.android.AndroidLocationService
import com.livewallpaperweather.sources.atmo.AtmoAuraService
import com.livewallpaperweather.sources.atmo.AtmoFranceService
import com.livewallpaperweather.sources.atmo.AtmoGrandEstService
import com.livewallpaperweather.sources.atmo.AtmoHdfService
import com.livewallpaperweather.sources.atmo.AtmoSudService
import com.livewallpaperweather.sources.baiduip.BaiduIPLocationService
import com.livewallpaperweather.sources.bmd.BmdService
import com.livewallpaperweather.sources.bmkg.BmkgService
import com.livewallpaperweather.sources.breezytz.BreezyTimeZoneService
import com.livewallpaperweather.sources.breezyupdatenotifier.BreezyUpdateNotifierService
import com.livewallpaperweather.sources.brightsky.BrightSkyService
import com.livewallpaperweather.sources.china.ChinaService
import com.livewallpaperweather.sources.climweb.AnamBfService
import com.livewallpaperweather.sources.climweb.AnametService
import com.livewallpaperweather.sources.climweb.DccmsService
import com.livewallpaperweather.sources.climweb.DmnNeService
import com.livewallpaperweather.sources.climweb.DwrGmService
import com.livewallpaperweather.sources.climweb.EthioMetService
import com.livewallpaperweather.sources.climweb.GMetService
import com.livewallpaperweather.sources.climweb.IgebuService
import com.livewallpaperweather.sources.climweb.InmgbService
import com.livewallpaperweather.sources.climweb.MaliMeteoService
import com.livewallpaperweather.sources.climweb.MeteoBeninService
import com.livewallpaperweather.sources.climweb.MeteoTchadService
import com.livewallpaperweather.sources.climweb.MettelsatService
import com.livewallpaperweather.sources.climweb.MsdZwService
import com.livewallpaperweather.sources.climweb.SmaScService
import com.livewallpaperweather.sources.climweb.SmaSuService
import com.livewallpaperweather.sources.climweb.SsmsService
import com.livewallpaperweather.sources.cwa.CwaService
import com.livewallpaperweather.sources.debug.DebugService
import com.livewallpaperweather.sources.dmi.DmiService
import com.livewallpaperweather.sources.eccc.EcccService
import com.livewallpaperweather.sources.ekuk.EkukService
import com.livewallpaperweather.sources.epdhk.EpdHkService
import com.livewallpaperweather.sources.fmi.FmiService
import com.livewallpaperweather.sources.fpas.FpasService
import com.livewallpaperweather.sources.gadgetbridge.GadgetbridgeService
import com.livewallpaperweather.sources.geonames.GeoNamesService
import com.livewallpaperweather.sources.geosphereat.GeoSphereAtService
import com.livewallpaperweather.sources.hko.HkoService
import com.livewallpaperweather.sources.ilmateenistus.IlmateenistusService
import com.livewallpaperweather.sources.imd.ImdService
import com.livewallpaperweather.sources.ims.ImsService
import com.livewallpaperweather.sources.infoplaza.InfoplazaService
import com.livewallpaperweather.sources.ipma.IpmaService
import com.livewallpaperweather.sources.ipsb.IpSbLocationService
import com.livewallpaperweather.sources.jma.JmaService
import com.livewallpaperweather.sources.knmi.KnmiService
import com.livewallpaperweather.sources.lhmt.LhmtService
import com.livewallpaperweather.sources.lvgmc.LvgmcService
import com.livewallpaperweather.sources.meteoam.MeteoAmService
import com.livewallpaperweather.sources.meteolux.MeteoLuxService
import com.livewallpaperweather.sources.metie.MetIeService
import com.livewallpaperweather.sources.metno.MetNoService
import com.livewallpaperweather.sources.metoffice.MetOfficeService
import com.livewallpaperweather.sources.mf.MfService
import com.livewallpaperweather.sources.mgm.MgmService
import com.livewallpaperweather.sources.namem.NamemService
import com.livewallpaperweather.sources.naturalearth.NaturalEarthService
import com.livewallpaperweather.sources.ncdr.NcdrService
import com.livewallpaperweather.sources.ncei.NceiService
import com.livewallpaperweather.sources.nlsc.NlscService
import com.livewallpaperweather.sources.nominatim.NominatimService
import com.livewallpaperweather.sources.nws.NwsService
import com.livewallpaperweather.sources.openmeteo.OpenMeteoService
import com.livewallpaperweather.sources.openweather.OpenWeatherService
import com.livewallpaperweather.sources.pagasa.PagasaService
import com.livewallpaperweather.sources.pirateweather.PirateWeatherService
import com.livewallpaperweather.sources.polleninfo.PollenInfoService
import com.livewallpaperweather.sources.recosante.RecosanteService
import com.livewallpaperweather.sources.smg.SmgService
import com.livewallpaperweather.sources.smhi.SmhiService
import com.livewallpaperweather.sources.veduris.VedurIsService
import com.livewallpaperweather.sources.wmosevereweather.WmoSevereWeatherService
import java.text.Collator
import javax.inject.Inject

class SourceManager @Inject constructor(
    @ApplicationContext context: Context,
    accuService: AccuService,
    aemetService: AemetService,
    anamBfService: AnamBfService,
    anametService: AnametService,
    androidGeocoderService: AndroidGeocoderService,
    androidLocationService: AndroidLocationService,
    atmoAuraService: AtmoAuraService,
    atmoFranceService: AtmoFranceService,
    atmoGrandEstService: AtmoGrandEstService,
    atmoHdfService: AtmoHdfService,
    atmoSudService: AtmoSudService,
    baiduIPService: BaiduIPLocationService,
    bmdService: BmdService,
    bmkgService: BmkgService,
    breezyTimeZoneService: BreezyTimeZoneService,
    breezyUpdateNotifierService: BreezyUpdateNotifierService,
    brightSkyService: BrightSkyService,
    chinaService: ChinaService,
    cwaService: CwaService,
    dccmsService: DccmsService,
    debugService: DebugService,
    dmnNeService: DmnNeService,
    dmiService: DmiService,
    dwrGmService: DwrGmService,
    ecccService: EcccService,
    ekukService: EkukService,
    epdHkService: EpdHkService,
    ethioMetService: EthioMetService,
    fmiService: FmiService,
    fpasService: FpasService,
    gadgetbridgeService: GadgetbridgeService,
    geoNamesService: GeoNamesService,
    geoSphereAtService: GeoSphereAtService,
    gMetService: GMetService,
    hkoService: HkoService,
    igebuService: IgebuService,
    ilmateenistusService: IlmateenistusService,
    imdService: ImdService,
    imsService: ImsService,
    infoplazaService: InfoplazaService,
    inmgbService: InmgbService,
    ipmaService: IpmaService,
    ipSbService: IpSbLocationService,
    jmaService: JmaService,
    knmiService: KnmiService,
    lhmtService: LhmtService,
    lvgmcService: LvgmcService,
    maliMeteoService: MaliMeteoService,
    meteoAmService: MeteoAmService,
    meteoBeninService: MeteoBeninService,
    meteoLuxService: MeteoLuxService,
    meteoTchadService: MeteoTchadService,
    metIeService: MetIeService,
    metNoService: MetNoService,
    metOfficeService: MetOfficeService,
    mettelsatService: MettelsatService,
    mfService: MfService,
    mgmService: MgmService,
    msdZwService: MsdZwService,
    namemService: NamemService,
    naturalEarthService: NaturalEarthService,
    ncdrService: NcdrService,
    nceiService: NceiService,
    nlscService: NlscService,
    nominatimService: NominatimService,
    nwsService: NwsService,
    openMeteoService: OpenMeteoService,
    openWeatherService: OpenWeatherService,
    pagasaService: PagasaService,
    pirateWeatherService: PirateWeatherService,
    pollenInfoService: PollenInfoService,
    recosanteService: RecosanteService,
    smaScService: SmaScService,
    smaSuService: SmaSuService,
    smgService: SmgService,
    smhiService: SmhiService,
    ssmsService: SsmsService,
    vedurIsService: VedurIsService,
    wmoSevereWeatherService: WmoSevereWeatherService,
) {
    // Location sources
    // LiveWallpaperWeather: Baidu IP (China) removed; keep Android GPS + IP.sb fallback.
    private val locationSourceList = persistentListOf(
        androidLocationService,
        ipSbService
    )

    // Location search sources
    // LiveWallpaperWeather: GeoNames removed — Open-Meteo already provides place-name search.
    private val locationSearchSourceList = persistentListOf<LocationSearchSource>()

    // Reverse geocoding sources
    // LiveWallpaperWeather: Nominatim removed — it shows an OSM usage-policy prompt.
    // The offline Natural Earth + Android geocoder cover NL/EU without any prompt.
    private val reverseGeocodingSourceList = persistentListOf(
        naturalEarthService,
        androidGeocoderService
    )

    // Worldwide weather sources, excluding national sources with worldwide support,
    // with the exception of MET Norway.
    // LiveWallpaperWeather: trimmed to the free, key-less sources relevant for NL/EU.
    // The other services are still injected above (harmless) but hidden from the chooser.
    private val worldwideWeatherSourceList = persistentListOf(
        openMeteoService,
        metNoService,
        wmoSevereWeatherService
    )

    // Region-specific or national weather sources.
    // LiveWallpaperWeather: NL-focused — only KNMI is kept here.
    private val nationalWeatherSourceList = persistentListOf(
        knmiService
    )

    // Broadcast sources
    private val broadcastSourceList = persistentListOf(
        breezyUpdateNotifierService,
        gadgetbridgeService
    )

    private val timeZoneSource = breezyTimeZoneService

    // The order of this list is preserved in "source chooser" dialogs
    private val sourceList: ImmutableList<Source> = buildList {
        addAll(locationSourceList)
        addAll(locationSearchSourceList)
        addAll(reverseGeocodingSourceList)
        addAll(worldwideWeatherSourceList)
        if (BreezyWeather.instance.debugMode) {
            add(debugService)
        }
        addAll(
            nationalWeatherSourceList
                .sortedWith { ws1, ws2 ->
                    // Sort by name because there are now a lot of sources
                    Collator.getInstance(context.currentLocale).compare(ws1.name, ws2.name)
                }
        )
        addAll(broadcastSourceList)
    }.toImmutableList()

    fun getSource(id: String): Source? = sourceList.firstOrNull { it.id == id }
    fun getHttpSources(): ImmutableList<HttpSource> = sourceList
        .filterIsInstance<HttpSource>()
        .toImmutableList()

    // Location
    fun getLocationSources(): ImmutableList<LocationSource> = sourceList
        .filterIsInstance<LocationSource>()
        .toImmutableList()

    fun getLocationSource(id: String): LocationSource? = getLocationSources().firstOrNull { it.id == id }
    fun getLocationSourceOrDefault(id: String): LocationSource = getLocationSource(id)
        ?: getLocationSource(BuildConfig.DEFAULT_LOCATION_SOURCE)!!

    fun getFeatureSources(): ImmutableList<FeatureSource> = sourceList
        .filterIsInstance<FeatureSource>()
        .toImmutableList()

    fun getFeatureSource(id: String): FeatureSource? = getFeatureSources().firstOrNull { it.id == id }

    fun getWeatherSources(): ImmutableList<WeatherSource> = sourceList
        .filterIsInstance<WeatherSource>()
        .toImmutableList()

    fun getWeatherSource(id: String): WeatherSource? = getWeatherSources().firstOrNull { it.id == id }

    // Secondary weather
    fun getPollenIndexSource(id: String): PollenIndexSource? = sourceList
        .filterIsInstance<PollenIndexSource>()
        .firstOrNull { it.id == id }

    // Location search
    fun getLocationSearchSources(): ImmutableList<LocationSearchSource> = sourceList
        .filterIsInstance<LocationSearchSource>()
        .toImmutableList()

    fun getLocationSearchSource(id: String): LocationSearchSource? = getLocationSearchSources()
        .firstOrNull { it.id == id }

    fun getLocationSearchSourceOrDefault(id: String): LocationSearchSource = getLocationSearchSource(id)
        ?: getLocationSearchSource(BuildConfig.DEFAULT_LOCATION_SEARCH_SOURCE)!!

    fun getConfiguredLocationSearchSources(): ImmutableList<LocationSearchSource> = getLocationSearchSources()
        .filter { it !is ConfigurableSource || it.isConfigured }
        .toImmutableList()

    // Reverse geocoding
    fun getReverseGeocodingSources(): ImmutableList<ReverseGeocodingSource> = sourceList
        .filterIsInstance<ReverseGeocodingSource>()
        .filter { it.supportedFeatures.containsKey(SourceFeature.REVERSE_GEOCODING) }
        .toImmutableList()

    fun getReverseGeocodingSource(id: String): ReverseGeocodingSource? = getReverseGeocodingSources()
        .firstOrNull { it.id == id }

    fun getReverseGeocodingSourceOrDefault(id: String): ReverseGeocodingSource = getReverseGeocodingSource(id)
        ?: getReverseGeocodingSource(BuildConfig.DEFAULT_GEOCODING_SOURCE)!!

    // Broadcast
    fun getBroadcastSources(): ImmutableList<BroadcastSource> = sourceList
        .filterIsInstance<BroadcastSource>()
        .toImmutableList()

    fun isBroadcastSourcesEnabled(context: Context): Boolean {
        return getBroadcastSources().any {
            (SourceConfigStore(context, it.id).getString("packages", null) ?: "").isNotEmpty()
        }
    }

    // Configurables sources
    fun getConfigurableSources(): ImmutableList<ConfigurableSource> = sourceList
        .filterIsInstance<ConfigurableSource>()
        .toImmutableList()

    // Time zone sources
    fun getTimeZoneSource(): TimeZoneSource = timeZoneSource

    fun sourcesWithPreferencesScreen(
        location: Location,
    ): ImmutableList<PreferencesParametersSource> {
        val preferencesScreenSources = mutableListOf<PreferencesParametersSource>()

        with(location) {
            listOf(
                Pair(forecastSource, SourceFeature.FORECAST),
                Pair(currentSource, SourceFeature.CURRENT),
                Pair(airQualitySource, SourceFeature.AIR_QUALITY),
                Pair(pollenSource, SourceFeature.POLLEN),
                Pair(minutelySource, SourceFeature.MINUTELY),
                Pair(alertSource, SourceFeature.ALERT),
                Pair(normalsSource, SourceFeature.NORMALS)
            ).forEach {
                val source = getWeatherSource(it.first ?: location.forecastSource)
                if (source is PreferencesParametersSource &&
                    source.hasPreferencesScreen(location, listOf(it.second)) &&
                    !preferencesScreenSources.contains(source)
                ) {
                    preferencesScreenSources.add(source)
                }
            }
        }

        return preferencesScreenSources
            /*.sortedWith { s1, s2 ->
                // Sort by name because there are now a lot of sources
                Collator.getInstance(
                    SettingsManager.getInstance(context).language.locale
                ).compare(s1.name, s2.name)
            })*/
            .toImmutableList()
    }

    fun getSupportedFeatureSources(
        feature: SourceFeature? = null,
        location: Location? = null,
        // Optional id of the source that will always be taken, even if not matching the criteria
        sourceException: String? = null,
    ): ImmutableList<FeatureSource> = getFeatureSources()
        .filter {
            it.id != "naturalearth" && (
                it.id == sourceException ||
                    (
                        it !is RemovedSource && (
                            feature == null ||
                                (
                                    it.supportedFeatures.containsKey(feature) &&
                                        (
                                            location == null ||
                                                (location.isCurrentPosition && !location.isUsable) ||
                                                it.isFeatureSupportedForLocation(location, feature)
                                            )
                                    )
                            )
                        )
                )
        }.toImmutableList()

    /**
     * Best source is determined using the priority given by sources, excluding unconfigured and restricted sources
     */
    fun getBestSourceForFeature(
        location: Location,
        feature: SourceFeature,
    ): FeatureSource? {
        return getSupportedFeatureSources(feature, location)
            .filter {
                it !is RemovedSource &&
                    it.isFeatureSupportedForLocation(location, feature) &&
                    it.getFeaturePriorityForLocation(location, feature) > PRIORITY_NONE &&
                    (it !is ConfigurableSource || (it.isConfigured && !it.isRestricted)) &&
                    (BuildConfig.FLAVOR != "freenet" || it !is NonFreeNetSource)
            }
            .maxByOrNull { it.getFeaturePriorityForLocation(location, feature) }
    }

    /**
     * For air quality, default source is Open-Meteo (except India due to different times from the preselected IMD forecast)
     * For pollen:
     * - Open-Meteo in Europe
     * - AccuWeather in USA/Canada
     * - None in other countries
     * For alerts, default source is AccuWeather (may be FPAS or WMO SWIC in the future)
     * For normals, default source is AccuWeather (may be NCEI in the future), unless:
     * - In China: no normals source, due to firewall
     * For other cases, default source is Open-Meteo
     */
    fun getDefaultSourceForFeature(
        location: Location,
        feature: SourceFeature,
    ): FeatureSource? {
        return when (feature) {
            SourceFeature.AIR_QUALITY -> if (!location.countryCode.equals("IN", ignoreCase = true) ||
                BuildConfig.FLAVOR == "freenet"
            ) {
                getWeatherSource("openmeteo")
            } else {
                null
            }

            SourceFeature.POLLEN -> getWeatherSource("openmeteo")?.let {
                if (it.isFeatureSupportedForLocation(location, feature)) it else null
            } ?: getWeatherSource("accu")?.takeIf {
                it.isFeatureSupportedForLocation(location, feature) && BuildConfig.FLAVOR != "freenet"
            }

            SourceFeature.ALERT -> if (BuildConfig.FLAVOR != "freenet") getWeatherSource("accu") else null
            SourceFeature.NORMALS -> if (!location.countryCode.equals("CN", ignoreCase = true) &&
                BuildConfig.FLAVOR != "freenet"
            ) {
                getWeatherSource("accu")
            } else {
                null
            }

            SourceFeature.REVERSE_GEOCODING -> getReverseGeocodingSource("naturalearth")
            else -> getWeatherSource("openmeteo")
        }
    }

    fun getBestSourceForFeatureOrDefault(
        location: Location,
        feature: SourceFeature,
    ): FeatureSource? {
        return if (feature != SourceFeature.REVERSE_GEOCODING ||
            location.isCurrentPosition ||
            location.needsGeocodeRefresh
        ) {
            getBestSourceForFeature(location, feature)
                ?: getDefaultSourceForFeature(location, feature)
        } else {
            null
        }
    }
}
