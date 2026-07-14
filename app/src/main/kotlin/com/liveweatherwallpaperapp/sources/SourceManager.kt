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

package com.liveweatherwallpaperapp.sources

import android.content.Context
import com.liveweatherwallpaperapp.BreezyWeather
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.source.BroadcastSource
import com.liveweatherwallpaperapp.common.source.ConfigurableSource
import com.liveweatherwallpaperapp.common.source.FeatureSource
import com.liveweatherwallpaperapp.common.source.HttpSource
import com.liveweatherwallpaperapp.common.source.LocationSearchSource
import com.liveweatherwallpaperapp.common.source.LocationSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import com.liveweatherwallpaperapp.common.source.PollenIndexSource
import com.liveweatherwallpaperapp.common.source.PreferencesParametersSource
import com.liveweatherwallpaperapp.common.source.RemovedSource
import com.liveweatherwallpaperapp.common.source.ReverseGeocodingSource
import com.liveweatherwallpaperapp.common.source.Source
import com.liveweatherwallpaperapp.common.source.TimeZoneSource
import com.liveweatherwallpaperapp.common.source.WeatherSource
import com.liveweatherwallpaperapp.common.source.WeatherSource.Companion.PRIORITY_NONE
import com.liveweatherwallpaperapp.domain.settings.SourceConfigStore
import com.liveweatherwallpaperapp.sources.accu.AccuService
import com.liveweatherwallpaperapp.sources.aemet.AemetService
import com.liveweatherwallpaperapp.sources.android.AndroidGeocoderService
import com.liveweatherwallpaperapp.sources.android.AndroidLocationService
import com.liveweatherwallpaperapp.sources.atmo.AtmoAuraService
import com.liveweatherwallpaperapp.sources.atmo.AtmoFranceService
import com.liveweatherwallpaperapp.sources.atmo.AtmoGrandEstService
import com.liveweatherwallpaperapp.sources.atmo.AtmoHdfService
import com.liveweatherwallpaperapp.sources.atmo.AtmoSudService
import com.liveweatherwallpaperapp.sources.baiduip.BaiduIPLocationService
import com.liveweatherwallpaperapp.sources.bmd.BmdService
import com.liveweatherwallpaperapp.sources.bmkg.BmkgService
import com.liveweatherwallpaperapp.sources.breezytz.BreezyTimeZoneService
import com.liveweatherwallpaperapp.sources.breezyupdatenotifier.BreezyUpdateNotifierService
import com.liveweatherwallpaperapp.sources.brightsky.BrightSkyService
import com.liveweatherwallpaperapp.sources.china.ChinaService
import com.liveweatherwallpaperapp.sources.climweb.AnamBfService
import com.liveweatherwallpaperapp.sources.climweb.AnametService
import com.liveweatherwallpaperapp.sources.climweb.DccmsService
import com.liveweatherwallpaperapp.sources.climweb.DmnNeService
import com.liveweatherwallpaperapp.sources.climweb.DwrGmService
import com.liveweatherwallpaperapp.sources.climweb.EthioMetService
import com.liveweatherwallpaperapp.sources.climweb.GMetService
import com.liveweatherwallpaperapp.sources.climweb.IgebuService
import com.liveweatherwallpaperapp.sources.climweb.InmgbService
import com.liveweatherwallpaperapp.sources.climweb.MaliMeteoService
import com.liveweatherwallpaperapp.sources.climweb.MeteoBeninService
import com.liveweatherwallpaperapp.sources.climweb.MeteoTchadService
import com.liveweatherwallpaperapp.sources.climweb.MettelsatService
import com.liveweatherwallpaperapp.sources.climweb.MsdZwService
import com.liveweatherwallpaperapp.sources.climweb.SmaScService
import com.liveweatherwallpaperapp.sources.climweb.SmaSuService
import com.liveweatherwallpaperapp.sources.climweb.SsmsService
import com.liveweatherwallpaperapp.sources.cwa.CwaService
import com.liveweatherwallpaperapp.sources.debug.DebugService
import com.liveweatherwallpaperapp.sources.dmi.DmiService
import com.liveweatherwallpaperapp.sources.eccc.EcccService
import com.liveweatherwallpaperapp.sources.ekuk.EkukService
import com.liveweatherwallpaperapp.sources.epdhk.EpdHkService
import com.liveweatherwallpaperapp.sources.fmi.FmiService
import com.liveweatherwallpaperapp.sources.fpas.FpasService
import com.liveweatherwallpaperapp.sources.gadgetbridge.GadgetbridgeService
import com.liveweatherwallpaperapp.sources.geonames.GeoNamesService
import com.liveweatherwallpaperapp.sources.geosphereat.GeoSphereAtService
import com.liveweatherwallpaperapp.sources.hko.HkoService
import com.liveweatherwallpaperapp.sources.ilmateenistus.IlmateenistusService
import com.liveweatherwallpaperapp.sources.imd.ImdService
import com.liveweatherwallpaperapp.sources.ims.ImsService
import com.liveweatherwallpaperapp.sources.infoplaza.InfoplazaService
import com.liveweatherwallpaperapp.sources.ipma.IpmaService
import com.liveweatherwallpaperapp.sources.ipsb.IpSbLocationService
import com.liveweatherwallpaperapp.sources.jma.JmaService
import com.liveweatherwallpaperapp.sources.knmi.KnmiService
import com.liveweatherwallpaperapp.sources.lhmt.LhmtService
import com.liveweatherwallpaperapp.sources.lvgmc.LvgmcService
import com.liveweatherwallpaperapp.sources.meteoam.MeteoAmService
import com.liveweatherwallpaperapp.sources.meteolux.MeteoLuxService
import com.liveweatherwallpaperapp.sources.metie.MetIeService
import com.liveweatherwallpaperapp.sources.metno.MetNoService
import com.liveweatherwallpaperapp.sources.metoffice.MetOfficeService
import com.liveweatherwallpaperapp.sources.mf.MfService
import com.liveweatherwallpaperapp.sources.mgm.MgmService
import com.liveweatherwallpaperapp.sources.namem.NamemService
import com.liveweatherwallpaperapp.sources.naturalearth.NaturalEarthService
import com.liveweatherwallpaperapp.sources.ncdr.NcdrService
import com.liveweatherwallpaperapp.sources.ncei.NceiService
import com.liveweatherwallpaperapp.sources.nlsc.NlscService
import com.liveweatherwallpaperapp.sources.nominatim.NominatimService
import com.liveweatherwallpaperapp.sources.nws.NwsService
import com.liveweatherwallpaperapp.sources.openmeteo.OpenMeteoService
import com.liveweatherwallpaperapp.sources.openweather.OpenWeatherService
import com.liveweatherwallpaperapp.sources.pagasa.PagasaService
import com.liveweatherwallpaperapp.sources.pirateweather.PirateWeatherService
import com.liveweatherwallpaperapp.sources.polleninfo.PollenInfoService
import com.liveweatherwallpaperapp.sources.recosante.RecosanteService
import com.liveweatherwallpaperapp.sources.smg.SmgService
import com.liveweatherwallpaperapp.sources.smhi.SmhiService
import com.liveweatherwallpaperapp.sources.veduris.VedurIsService
import com.liveweatherwallpaperapp.sources.wmosevereweather.WmoSevereWeatherService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.source.SourceFeature
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
    // LiveWallpaperWeather: trimmed to the free, key-less sources.
    // The other services are still injected above (harmless) but hidden from the chooser.
    private val worldwideWeatherSourceList = persistentListOf(
        openMeteoService,
        metNoService,
        wmoSevereWeatherService,
        fpasService
    )

    // Region-specific or national weather sources.
    // LiveWallpaperWeather: only key-less national sources are exposed, so "current location"
    // (e.g. while travelling) always has a working best-source pick without asking the user to
    // configure an API key. Sources requiring a key (AccuWeather, AEMET, Atmo*, OpenWeather,
    // PirateWeather, ECCC, CWA, Météo-France, Met Office, MET Éireann, Infoplaza, BMKG, ...)
    // are intentionally left out.
    private val nationalWeatherSourceList = persistentListOf(
        knmiService,
        bmdService,
        brightSkyService,
        chinaService,
        dmiService,
        ekukService,
        epdHkService,
        fmiService,
        geoSphereAtService,
        hkoService,
        ilmateenistusService,
        imdService,
        imsService,
        ipmaService,
        jmaService,
        lhmtService,
        lvgmcService,
        meteoAmService,
        meteoLuxService,
        mgmService,
        namemService,
        ncdrService,
        nceiService,
        nlscService,
        nwsService,
        pagasaService,
        recosanteService,
        smgService,
        smhiService,
        vedurIsService,
        // ClimWeb network (Africa) — shared implementation, key-less for all countries
        anamBfService,
        anametService,
        dccmsService,
        dmnNeService,
        dwrGmService,
        ethioMetService,
        gMetService,
        igebuService,
        inmgbService,
        maliMeteoService,
        meteoBeninService,
        meteoTchadService,
        mettelsatService,
        msdZwService,
        smaScService,
        smaSuService,
        ssmsService
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
     * For alerts, default source is AccuWeather if configured, otherwise WMO Severe Weather
     * Information Centre (keyless, no configuration required) so alerts have a working default
     * out of the box
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

            SourceFeature.ALERT -> {
                val accu = if (BuildConfig.FLAVOR != "freenet") {
                    getWeatherSource("accu")?.takeIf {
                        it is ConfigurableSource && it.isConfigured && !it.isRestricted
                    }
                } else {
                    null
                }
                accu ?: getWeatherSource("wmosevereweather")?.takeIf {
                    it.isFeatureSupportedForLocation(location, feature)
                }
            }
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
