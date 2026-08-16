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

package com.liveweatherwallpaperapp.ui.details.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.common.extensions.formatPercent
import com.liveweatherwallpaperapp.common.extensions.getFormattedTime
import com.liveweatherwallpaperapp.common.extensions.getVisibilityDescription
import com.liveweatherwallpaperapp.common.extensions.is12Hour
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.common.options.appearance.DetailsOverviewDisplay
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.domain.weather.model.getDescription
import com.liveweatherwallpaperapp.domain.weather.model.getDirection
import com.liveweatherwallpaperapp.domain.weather.model.getIndex
import com.liveweatherwallpaperapp.domain.weather.model.getIndexName
import com.liveweatherwallpaperapp.domain.weather.model.getLevel
import com.liveweatherwallpaperapp.domain.weather.model.getName
import com.liveweatherwallpaperapp.unit.formatting.UnitWidth
import com.liveweatherwallpaperapp.unit.distance.Distance
import com.liveweatherwallpaperapp.unit.pollutant.PollutantConcentrationUnit
import com.liveweatherwallpaperapp.unit.pressure.Pressure
import com.liveweatherwallpaperapp.unit.ratio.Ratio
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.weather.model.AirQuality
import livewallpaperweather.domain.weather.model.Current
import livewallpaperweather.domain.weather.model.Daily
import livewallpaperweather.domain.weather.model.Hourly
import livewallpaperweather.domain.weather.model.Precipitation
import livewallpaperweather.domain.weather.model.PrecipitationProbability
import livewallpaperweather.domain.weather.model.Temperature
import livewallpaperweather.domain.weather.model.UV
import livewallpaperweather.domain.weather.model.Wind
import com.liveweatherwallpaperapp.unit.temperature.Temperature as TemperatureValue

/**
 * The set of fields the "Details" tile grid ([DetailsOverviewGrid]) reads -- present on both
 * [Current] and [Hourly], which otherwise don't share a common type. [ofCurrent] preserves the
 * main screen's existing behavior exactly (current conditions, with precipitation taken from the
 * next hourly forecast entry, as before); [ofHourly] is the per-hour equivalent used on the
 * details page.
 */
internal data class DetailsOverviewSource(
    val temperature: Temperature?,
    val precipitation: Precipitation?,
    val precipitationProbability: PrecipitationProbability?,
    val wind: Wind?,
    val airQuality: AirQuality?,
    val uV: UV?,
    val relativeHumidity: Ratio?,
    val dewPoint: TemperatureValue?,
    val pressure: Pressure?,
    val cloudCover: Ratio?,
    val visibility: Distance?,
) {
    companion object {
        fun ofCurrent(current: Current?, precipitationHourly: Hourly?) = DetailsOverviewSource(
            temperature = current?.temperature,
            precipitation = precipitationHourly?.precipitation,
            precipitationProbability = precipitationHourly?.precipitationProbability,
            wind = current?.wind,
            airQuality = current?.airQuality,
            uV = current?.uV,
            relativeHumidity = current?.relativeHumidity,
            dewPoint = current?.dewPoint,
            pressure = current?.pressure,
            cloudCover = current?.cloudCover,
            visibility = current?.visibility
        )

        fun ofHourly(hourly: Hourly?) = DetailsOverviewSource(
            temperature = hourly?.temperature,
            precipitation = hourly?.precipitation,
            precipitationProbability = hourly?.precipitationProbability,
            wind = hourly?.wind,
            airQuality = hourly?.airQuality,
            uV = hourly?.uV,
            relativeHumidity = hourly?.relativeHumidity,
            dewPoint = hourly?.dewPoint,
            pressure = hourly?.pressure,
            cloudCover = hourly?.cloudCover,
            visibility = hourly?.visibility
        )
    }
}

private data class DetailItem(
    @StringRes val labelId: Int,
    @DrawableRes val iconId: Int,
    val value: String,
    val chart: DetailScreen,
    @StringRes val infoTitleId: Int,
    val infoText: String,
    val tag: DetailsOverviewDisplay,
    val iconRotation: Float = 0f,
    val labelOverride: String? = null,
)

/**
 * The "Details" tile grid: a compact at-a-glance summary of every secondary metric
 * (precipitation, humidity, UV, wind, pressure, air quality, sun/moon, pollen, ...), each tile
 * tappable to jump to its full chart. Shared between the main screen's own overview card (fed
 * "now") and the details page's Conditions tab (fed the selected/centered hour).
 */
@Composable
internal fun DetailsOverviewGrid(
    location: Location,
    source: DetailsOverviewSource,
    daily: Daily?,
    onNavigate: (DetailScreen) -> Unit,
    modifier: Modifier = Modifier,
    // Separate from source.airQuality (used for the O3 sub-tile): the general "Air quality"
    // tile prefers a fallback (e.g. current -> today, see Weather.validAirQuality) over a
    // single-source reading, so callers pass that fallback explicitly.
    overallAirQuality: AirQuality? = source.airQuality,
) {
    val context = LocalContext.current
    val temperatureUnit = SettingsManager.getInstance(context).getTemperatureUnit(context)
    val windDegree = source.wind?.degree

    val items = listOf(
        DetailItem(
            R.string.precipitation_probability,
            R.drawable.ic_umbrella,
            source.precipitationProbability?.total?.formatPercent(context, UnitWidth.NARROW) ?: "-",
            DetailScreen.TAG_PRECIPITATION,
            R.string.precipitation_probability,
            context.getString(R.string.precipitation_probability_about_description),
            DetailsOverviewDisplay.TAG_PRECIPITATION_PROBABILITY
        ),
        DetailItem(
            R.string.precipitation,
            R.drawable.ic_precipitation,
            source.precipitation?.total?.formatMeasure(context, valueWidth = UnitWidth.NARROW) ?: "-",
            DetailScreen.TAG_PRECIPITATION,
            R.string.precipitation,
            context.getString(R.string.precipitation_about_description),
            DetailsOverviewDisplay.TAG_PRECIPITATION
        ),
        DetailItem(
            R.string.humidity,
            R.drawable.ic_humidity_percentage,
            source.relativeHumidity?.formatPercent(context, UnitWidth.NARROW) ?: "-",
            DetailScreen.TAG_HUMIDITY,
            R.string.humidity_about,
            context.getString(R.string.humidity_about_description),
            DetailsOverviewDisplay.TAG_HUMIDITY
        ),
        DetailItem(
            R.string.uv_index,
            R.drawable.ic_uv,
            source.uV?.getLevel(context) ?: "-",
            DetailScreen.TAG_UV_INDEX,
            R.string.uv_index_about,
            context.getString(R.string.uv_index_about_description),
            DetailsOverviewDisplay.TAG_UV_INDEX
        ),
        DetailItem(
            R.string.cloud_cover,
            R.drawable.ic_cloud,
            source.cloudCover?.formatPercent(context, UnitWidth.NARROW) ?: "-",
            DetailScreen.TAG_CLOUD_COVER,
            R.string.cloud_cover,
            context.getString(R.string.cloud_cover_about_description),
            DetailsOverviewDisplay.TAG_CLOUD_COVER
        ),
        DetailItem(
            R.string.visibility,
            R.drawable.ic_eye,
            source.visibility?.formatMeasure(context, valueWidth = UnitWidth.NARROW) ?: "-",
            DetailScreen.TAG_VISIBILITY,
            R.string.visibility,
            source.visibility?.getVisibilityDescription(context)
                ?: context.getString(R.string.visibility_about_description),
            DetailsOverviewDisplay.TAG_VISIBILITY,
            labelOverride = context.getString(
                R.string.details_overview_visibility_label,
                source.visibility?.getVisibilityDescription(context) ?: "-"
            )
        ),
        DetailItem(
            R.string.wind,
            R.drawable.ic_wind,
            source.wind?.speed?.formatMeasure(context, valueWidth = UnitWidth.NARROW) ?: "-",
            DetailScreen.TAG_WIND,
            R.string.wind,
            context.getString(R.string.wind_speed_about_description),
            DetailsOverviewDisplay.TAG_WIND
        ),
        DetailItem(
            R.string.wind_direction,
            if (windDegree != null && windDegree != -1.0) R.drawable.wind_arrow else R.drawable.wind_variable,
            source.wind?.getDirection(context, short = true) ?: "-",
            DetailScreen.TAG_WIND,
            R.string.wind_direction,
            context.getString(R.string.wind_direction_about_description),
            DetailsOverviewDisplay.TAG_WIND_DIRECTION,
            windDegree?.takeIf { it != -1.0 }?.toFloat() ?: 0f
        ),
        DetailItem(
            R.string.temperature_feels_like,
            R.drawable.ic_device_thermostat,
            source.temperature?.feelsLikeTemperature?.formatMeasure(context, temperatureUnit, UnitWidth.NARROW)
                ?: "-",
            DetailScreen.TAG_CONDITIONS,
            R.string.temperature_feels_like,
            context.getString(R.string.temperature_feels_like_details),
            DetailsOverviewDisplay.TAG_FEELS_LIKE
        ),
        DetailItem(
            R.string.dew_point,
            R.drawable.ic_humidity_percentage,
            source.dewPoint?.formatMeasure(context, temperatureUnit, valueWidth = UnitWidth.NARROW) ?: "-",
            DetailScreen.TAG_HUMIDITY,
            R.string.dew_point,
            source.relativeHumidity?.formatPercent(context)?.let {
                context.getString(R.string.dew_point_about_description, it)
            } ?: context.getString(R.string.dew_point),
            DetailsOverviewDisplay.TAG_DEW_POINT
        ),
        DetailItem(
            R.string.pressure,
            R.drawable.ic_gauge,
            source.pressure?.formatMeasure(context, valueWidth = UnitWidth.NARROW) ?: "-",
            DetailScreen.TAG_PRESSURE,
            R.string.pressure_about,
            context.getString(R.string.pressure_about_description1) +
                " " +
                context.getString(R.string.pressure_about_description2),
            DetailsOverviewDisplay.TAG_PRESSURE
        ),
        DetailItem(
            R.string.air_quality_o3_voice,
            R.drawable.weather_haze_mini_xml,
            source.airQuality?.o3?.let {
                PollutantConcentrationUnit.MICROGRAM_PER_CUBIC_METER.formatMeasure(
                    context,
                    it.inMicrogramsPerCubicMeter,
                    UnitWidth.NARROW
                )
            } ?: "-",
            DetailScreen.TAG_AIR_QUALITY,
            R.string.air_quality_o3_voice,
            context.getString(R.string.air_quality_o3_sources),
            DetailsOverviewDisplay.TAG_OZONE
        ),
        DetailItem(
            R.string.ephemeris_sun,
            R.drawable.weather_clear_day_mini_xml,
            daily?.sun?.riseDate?.getFormattedTime(location, context, context.is12Hour) ?: "-",
            DetailScreen.TAG_SUN_MOON,
            R.string.ephemeris_about,
            context.getString(R.string.ephemeris_about_rise),
            DetailsOverviewDisplay.TAG_SUN
        ),
        DetailItem(
            R.string.ephemeris_moon,
            R.drawable.weather_clear_night_mini_xml,
            daily?.moonPhase?.getDescription(context) ?: "-",
            DetailScreen.TAG_SUN_MOON,
            R.string.ephemeris_about,
            context.getString(R.string.ephemeris_about_rise),
            DetailsOverviewDisplay.TAG_MOON
        ),
        DetailItem(
            R.string.air_quality,
            R.drawable.weather_haze_mini_xml,
            overallAirQuality?.getIndex()?.toString() ?: "-",
            DetailScreen.TAG_AIR_QUALITY,
            R.string.air_quality_index_about,
            context.getString(R.string.air_quality_index_about_description_1),
            DetailsOverviewDisplay.TAG_AIR_QUALITY,
            labelOverride = overallAirQuality?.getName(context)
                ?.let { "${context.getString(R.string.air_quality)}: $it" }
        ),
        DetailItem(
            R.string.pollen,
            R.drawable.ic_allergy,
            daily?.pollen?.getIndexName(context) ?: "-",
            DetailScreen.TAG_POLLEN,
            R.string.pollen,
            context.getString(R.string.pollen),
            DetailsOverviewDisplay.TAG_POLLEN
        )
    )

    val selectedTags = remember {
        SettingsManager.getInstance(context).detailsOverviewDisplayList
    }
    val itemsByTag = items.associateBy { it.tag }
    val visibleItems = selectedTags.mapNotNull { itemsByTag[it] }

    var infoDialogItem by remember { mutableStateOf<DetailItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimensionResource(R.dimen.normal_margin))
    ) {
        Text(
            text = stringResource(R.string.details_overview),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        visibleItems.chunked(2).forEachIndexed { index, rowItems ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    DetailOverviewCell(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(item.chart) },
                        onInfoClick = { infoDialogItem = item }
                    )
                }
            }
        }
    }

    infoDialogItem?.let { item ->
        AlertDialog(
            onDismissRequest = { infoDialogItem = null },
            confirmButton = {
                TextButton(onClick = { infoDialogItem = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            title = {
                Text(stringResource(item.infoTitleId))
            },
            text = {
                Text(item.infoText)
            }
        )
    }
}

@Composable
private fun DetailOverviewCell(
    item: DetailItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onInfoClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(item.iconId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(dimensionResource(R.dimen.material_icon_size))
                .rotate(item.iconRotation)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = item.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.labelOverride ?: stringResource(item.labelId),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .align(Alignment.Top)
                .size(32.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_help),
                contentDescription = stringResource(R.string.action_help),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
