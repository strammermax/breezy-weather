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

package com.liveweatherwallpaperapp.ui.main.adapters.main.holder

import android.view.LayoutInflater
import android.view.ViewGroup
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import livewallpaperweather.domain.location.model.Location
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.common.extensions.formatPercent
import com.liveweatherwallpaperapp.common.extensions.getFormattedTime
import com.liveweatherwallpaperapp.common.extensions.getVisibilityDescription
import com.liveweatherwallpaperapp.common.extensions.is12Hour
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.common.options.appearance.DetailsOverviewDisplay
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.domain.weather.model.getDirection
import com.liveweatherwallpaperapp.domain.weather.model.getDescription
import com.liveweatherwallpaperapp.domain.weather.model.getIndex
import com.liveweatherwallpaperapp.domain.weather.model.getIndexName
import com.liveweatherwallpaperapp.domain.weather.model.getLevel
import com.liveweatherwallpaperapp.domain.weather.model.getName
import com.liveweatherwallpaperapp.domain.weather.model.validAirQuality
import com.liveweatherwallpaperapp.ui.theme.ThemeManager
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import com.liveweatherwallpaperapp.unit.formatting.UnitWidth
import com.liveweatherwallpaperapp.unit.pollutant.PollutantConcentrationUnit

class DetailsOverviewViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_details_overview, parent, false)
) {
    private val composeView: ComposeView = itemView.findViewById(R.id.details_overview_compose_view)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        composeView.setContent {
            BreezyWeatherTheme(!ThemeManager.isLightTheme(context, location)) {
                DetailsOverviewGrid(location)
            }
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

    @Composable
    private fun DetailsOverviewGrid(location: Location) {
        val temperatureUnit = SettingsManager.getInstance(context).getTemperatureUnit(context)
        val current = location.weather?.current
        val currentHourly = location.weather?.nextHourlyForecast?.firstOrNull()
        val windDegree = current?.wind?.degree

        val items = listOf(
            DetailItem(
                R.string.precipitation_probability,
                R.drawable.ic_umbrella,
                currentHourly?.precipitationProbability?.total?.formatPercent(context, UnitWidth.NARROW) ?: "-",
                DetailScreen.TAG_PRECIPITATION,
                R.string.precipitation_probability,
                context.getString(R.string.precipitation_probability_about_description),
                DetailsOverviewDisplay.TAG_PRECIPITATION_PROBABILITY
            ),
            DetailItem(
                R.string.precipitation,
                R.drawable.ic_precipitation,
                currentHourly?.precipitation?.total?.formatMeasure(context, valueWidth = UnitWidth.NARROW) ?: "-",
                DetailScreen.TAG_PRECIPITATION,
                R.string.precipitation,
                context.getString(R.string.precipitation_about_description),
                DetailsOverviewDisplay.TAG_PRECIPITATION
            ),
            DetailItem(
                R.string.humidity,
                R.drawable.ic_humidity_percentage,
                current?.relativeHumidity?.formatPercent(context, UnitWidth.NARROW) ?: "-",
                DetailScreen.TAG_HUMIDITY,
                R.string.humidity_about,
                context.getString(R.string.humidity_about_description),
                DetailsOverviewDisplay.TAG_HUMIDITY
            ),
            DetailItem(
                R.string.uv_index,
                R.drawable.ic_uv,
                current?.uV?.getLevel(context) ?: "-",
                DetailScreen.TAG_UV_INDEX,
                R.string.uv_index_about,
                context.getString(R.string.uv_index_about_description),
                DetailsOverviewDisplay.TAG_UV_INDEX
            ),
            DetailItem(
                R.string.cloud_cover,
                R.drawable.ic_cloud,
                current?.cloudCover?.formatPercent(context, UnitWidth.NARROW) ?: "-",
                DetailScreen.TAG_CLOUD_COVER,
                R.string.cloud_cover,
                context.getString(R.string.cloud_cover_about_description),
                DetailsOverviewDisplay.TAG_CLOUD_COVER
            ),
            DetailItem(
                R.string.visibility,
                R.drawable.ic_eye,
                current?.visibility?.formatMeasure(context, valueWidth = UnitWidth.NARROW) ?: "-",
                DetailScreen.TAG_VISIBILITY,
                R.string.visibility,
                current?.visibility?.getVisibilityDescription(context)
                    ?: context.getString(R.string.visibility_about_description),
                DetailsOverviewDisplay.TAG_VISIBILITY,
                labelOverride = context.getString(
                    R.string.details_overview_visibility_label,
                    current?.visibility?.getVisibilityDescription(context) ?: "-",
                ),
            ),
            DetailItem(
                R.string.wind,
                R.drawable.ic_wind,
                current?.wind?.speed?.formatMeasure(context, valueWidth = UnitWidth.NARROW) ?: "-",
                DetailScreen.TAG_WIND,
                R.string.wind,
                context.getString(R.string.wind_speed_about_description),
                DetailsOverviewDisplay.TAG_WIND
            ),
            DetailItem(
                R.string.wind_direction,
                if (windDegree != null && windDegree != -1.0) R.drawable.wind_arrow else R.drawable.wind_variable,
                current?.wind?.getDirection(context, short = true) ?: "-",
                DetailScreen.TAG_WIND,
                R.string.wind_direction,
                context.getString(R.string.wind_direction_about_description),
                DetailsOverviewDisplay.TAG_WIND_DIRECTION,
                windDegree?.takeIf { it != -1.0 }?.toFloat() ?: 0f
            ),
            DetailItem(
                R.string.temperature_feels_like,
                R.drawable.ic_device_thermostat,
                current?.temperature?.feelsLikeTemperature?.formatMeasure(context, temperatureUnit, UnitWidth.NARROW)
                    ?: "-",
                DetailScreen.TAG_CONDITIONS,
                R.string.temperature_feels_like,
                context.getString(R.string.temperature_feels_like_details),
                DetailsOverviewDisplay.TAG_FEELS_LIKE
            ),
            DetailItem(
                R.string.dew_point,
                R.drawable.ic_humidity_percentage,
                current?.dewPoint?.formatMeasure(context, temperatureUnit, valueWidth = UnitWidth.NARROW) ?: "-",
                DetailScreen.TAG_HUMIDITY,
                R.string.dew_point,
                current?.relativeHumidity?.formatPercent(context)?.let {
                    context.getString(R.string.dew_point_about_description, it)
                } ?: context.getString(R.string.dew_point),
                DetailsOverviewDisplay.TAG_DEW_POINT
            ),
            DetailItem(
                R.string.pressure,
                R.drawable.ic_gauge,
                current?.pressure?.formatMeasure(context, valueWidth = UnitWidth.NARROW) ?: "-",
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
                current?.airQuality?.o3?.let {
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
                location.weather?.today?.sun?.riseDate?.getFormattedTime(location, context, context.is12Hour) ?: "-",
                DetailScreen.TAG_SUN_MOON,
                R.string.ephemeris_about,
                context.getString(R.string.ephemeris_about_rise),
                DetailsOverviewDisplay.TAG_SUN,
            ),
            DetailItem(
                R.string.ephemeris_moon,
                R.drawable.weather_clear_night_mini_xml,
                location.weather?.today?.moonPhase?.getDescription(context) ?: "-",
                DetailScreen.TAG_SUN_MOON,
                R.string.ephemeris_about,
                context.getString(R.string.ephemeris_about_rise),
                DetailsOverviewDisplay.TAG_MOON,
            ),
            DetailItem(
                R.string.air_quality,
                R.drawable.weather_haze_mini_xml,
                location.weather?.validAirQuality?.getIndex()?.toString() ?: "-",
                DetailScreen.TAG_AIR_QUALITY,
                R.string.air_quality_index_about,
                context.getString(R.string.air_quality_index_about_description_1),
                DetailsOverviewDisplay.TAG_AIR_QUALITY,
                labelOverride = location.weather?.validAirQuality?.getName(context)
                    ?.let { "${context.getString(R.string.air_quality)}: $it" },
            ),
            DetailItem(
                R.string.pollen,
                R.drawable.ic_allergy,
                location.weather?.today?.pollen?.getIndexName(context) ?: "-",
                DetailScreen.TAG_POLLEN,
                R.string.pollen,
                context.getString(R.string.pollen),
                DetailsOverviewDisplay.TAG_POLLEN,
            )
        )

        val selectedTags = remember {
            SettingsManager.getInstance(context).detailsOverviewDisplayList
        }
        val itemsByTag = items.associateBy { it.tag }
        val visibleItems = selectedTags.mapNotNull { itemsByTag[it] }

        var infoDialogItem by remember { mutableStateOf<DetailItem?>(null) }

        Column(
            modifier = Modifier
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
                        DetailCell(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                IntentHelper.startDailyWeatherActivity(
                                    context as BreezyActivity,
                                    location.formattedId,
                                    location.weather?.todayIndex,
                                    item.chart
                                )
                            },
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
    private fun DetailCell(
        item: DetailItem,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
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
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .align(Alignment.Top)
                    .size(32.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_help),
                    contentDescription = stringResource(R.string.action_help),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
