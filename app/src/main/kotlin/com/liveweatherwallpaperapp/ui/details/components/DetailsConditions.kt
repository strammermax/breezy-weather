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

import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.RecyclerView
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.fontScaleToApply
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.common.extensions.getCalendarMonth
import com.liveweatherwallpaperapp.common.extensions.getFormattedTime
import com.liveweatherwallpaperapp.common.extensions.getThemeColor
import com.liveweatherwallpaperapp.common.extensions.is12Hour
import com.liveweatherwallpaperapp.common.extensions.isLandscape
import com.liveweatherwallpaperapp.common.extensions.toBitmap
import com.liveweatherwallpaperapp.common.extensions.toDate
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.domain.weather.model.isToday
import com.liveweatherwallpaperapp.ui.common.widgets.AnimatableIconView
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendLayoutManager
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendRecyclerView
import com.liveweatherwallpaperapp.ui.main.adapters.trend.hourly.HourlyTemperatureAdapter
import com.liveweatherwallpaperapp.ui.theme.resource.ResourcesProviderFactory
import com.liveweatherwallpaperapp.unit.formatting.UnitWidth
import com.liveweatherwallpaperapp.unit.ratio.Ratio
import com.liveweatherwallpaperapp.unit.temperature.Temperature
import com.liveweatherwallpaperapp.unit.temperature.TemperatureUnit
import com.liveweatherwallpaperapp.unit.temperature.toTemperature
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import livewallpaperweather.domain.location.model.Location
import livewallpaperweather.domain.weather.model.Daily
import livewallpaperweather.domain.weather.model.Hourly
import livewallpaperweather.domain.weather.model.Normals
import livewallpaperweather.domain.weather.reference.WeatherCode
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun DetailsConditions(
    location: Location,
    hourlyList: ImmutableList<Hourly>,
    daily: Daily,
    normals: Normals?,
    selectedChart: DetailScreen,
    setShowRealTemp: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onCenteredDayChanged: ((Int) -> Unit)? = null,
    onNavigateToChart: ((DetailScreen) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val temperatureUnit = remember {
        SettingsManager.getInstance(context).getTemperatureUnit(context)
    }
    val mappedValues = remember(hourlyList, selectedChart) {
        hourlyList
            .filter {
                it.temperature?.temperature != null &&
                    if (selectedChart != DetailScreen.TAG_FEELS_LIKE) {
                        true
                    } else {
                        it.temperature?.feelsLikeTemperature != null
                    }
            }
            .associateBy { it.date.time }
            .toImmutableMap()
    }
    var activeItem: Pair<Date, Hourly>? by remember { mutableStateOf(null) }

    // Feeds the "Details" tile grid at the bottom of this tab: the tapped hour if there is one,
    // otherwise "now" for today's page, or this day's first hour for any other day.
    val defaultOverviewHourly = remember(hourlyList, daily) {
        if (daily.isToday(location)) {
            val now = System.currentTimeMillis()
            hourlyList.lastOrNull { it.date.time <= now } ?: hourlyList.firstOrNull()
        } else {
            hourlyList.firstOrNull()
        }
    }
    val overviewHourly = activeItem?.second ?: defaultOverviewHourly

    val mappedProbabilityValues = remember(hourlyList) {
        hourlyList
            .filter { it.precipitationProbability?.total != null }
            .associate { it.date.time to it.precipitationProbability!!.total!! }
            .toImmutableMap()
    }
    var activeProbabilityItem: Pair<Date, Ratio>? by remember { mutableStateOf(null) }
    val probabilityMarkerVisibilityListener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                activeProbabilityItem = targets.firstOrNull()?.let { target ->
                    mappedProbabilityValues.getOrElse(target.x.toLong()) { null }?.let {
                        Pair(target.x.toLong().toDate(), it)
                    }
                }
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                onShown(marker, targets)
            }

            override fun onHidden(marker: CartesianMarker) {
                activeProbabilityItem = null
            }
        }
    }

    val tooltipState = rememberTooltipState(isPersistent = true)
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = dimensionResource(R.dimen.normal_margin),
            vertical = dimensionResource(R.dimen.small_margin)
        )
    ) {
        item {
            TemperatureHeader(
                location,
                daily,
                activeItem,
                selectedChart != DetailScreen.TAG_FEELS_LIKE,
                normals,
                temperatureUnit
            )
        }
        item {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
        }
        if (mappedValues.size >= DetailScreen.CHART_MIN_COUNT) {
            item {
                HourlyForecastChart(
                    location,
                    temperatureUnit,
                    daily,
                    activeItem,
                    onHourSelected = { activeItem = it },
                    onCenteredDayChanged = onCenteredDayChanged
                )
            }
        } else {
            item {
                UnavailableChart(mappedValues.size)
            }
        }
        item {
            Text(
                text = stringResource(
                    if (selectedChart != DetailScreen.TAG_FEELS_LIKE) {
                        R.string.temperature_real_details
                    } else {
                        R.string.temperature_feels_like_details
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        // TODO: Short explanation
        if ((daily.day?.weatherSummary != null && daily.day!!.weatherText != daily.day!!.weatherSummary) ||
            (daily.night?.weatherSummary != null && daily.night!!.weatherText != daily.night!!.weatherSummary)
        ) {
            item {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
            }
            item {
                DetailsSectionHeader(stringResource(R.string.daily_summary))
            }
            item {
                DetailsCardText(
                    buildString {
                        if (daily.day?.weatherSummary == daily.night?.weatherSummary) {
                            append(daily.day!!.weatherSummary!!)
                        } else {
                            daily.day?.weatherSummary?.let {
                                append(stringResource(R.string.daytime))
                                append(stringResource(R.string.colon_separator))
                                append(it)
                            }
                            daily.night?.weatherSummary?.let {
                                if (it.isNotEmpty()) append("\n")
                                append(stringResource(R.string.nighttime))
                                append(stringResource(R.string.colon_separator))
                                append(it)
                            }
                        }
                    }
                )
            }
        }
        // TODO: Make a better design for degree day
        if (daily.degreeDay?.isValid == true) {
            item {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
            }
            if ((daily.degreeDay!!.heating?.value ?: 0) > 0) {
                item {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(R.string.temperature_degree_day_heating_explanation))
                            }
                        },
                        state = tooltipState
                    ) {
                        DetailsItem(
                            headlineText = stringResource(R.string.temperature_degree_day_heating),
                            supportingText = daily.degreeDay!!.heating!!.toDoubleDeviation(temperatureUnit)
                                .toTemperature(temperatureUnit)
                                .formatMeasure(context, temperatureUnit),
                            icon = R.drawable.ic_mode_heat,
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {}
                                .clearAndSetSemantics {
                                    contentDescription = resources.getString(R.string.temperature_degree_day_heating) +
                                        resources.getString(R.string.colon_separator) +
                                        daily.degreeDay!!.heating!!.toDoubleDeviation(temperatureUnit)
                                            .toTemperature(temperatureUnit)
                                            .formatMeasure(context, temperatureUnit, unitWidth = UnitWidth.LONG)
                                }
                                .clickable {
                                    coroutineScope.launch {
                                        tooltipState.show()
                                    }
                                },
                            withHelp = true
                        )
                    }
                }
            } else if ((daily.degreeDay!!.cooling?.value ?: 0) > 0) {
                item {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(R.string.temperature_degree_day_cooling_explanation))
                            }
                        },
                        state = tooltipState
                    ) {
                        DetailsItem(
                            headlineText = stringResource(R.string.temperature_degree_day_cooling),
                            supportingText = daily.degreeDay!!.cooling!!.toDoubleDeviation(temperatureUnit)
                                .toTemperature(temperatureUnit)
                                .formatMeasure(context, temperatureUnit),
                            icon = R.drawable.ic_mode_cool,
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {}
                                .clearAndSetSemantics {
                                    contentDescription = resources.getString(R.string.temperature_degree_day_cooling) +
                                        resources.getString(R.string.colon_separator) +
                                        daily.degreeDay!!.heating!!.toDoubleDeviation(temperatureUnit)
                                            .toTemperature(temperatureUnit)
                                            .formatMeasure(context, temperatureUnit, unitWidth = UnitWidth.LONG)
                                }
                                .clickable {
                                    coroutineScope.launch {
                                        tooltipState.show()
                                    }
                                },
                            withHelp = true
                        )
                    }
                }
            }
        }
        if (daily.day?.precipitationProbability != null ||
            daily.night?.precipitationProbability != null ||
            mappedProbabilityValues.isNotEmpty()
        ) {
            item {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
            }
            item {
                DetailsSectionDivider()
            }
            item {
                Text(
                    text = stringResource(R.string.precipitation_probability),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            item {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
            }
            item {
                PrecipitationProbabilityHeader(location, daily, activeProbabilityItem)
            }
            item {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
            }
            if (mappedProbabilityValues.size >= DetailScreen.CHART_MIN_COUNT) {
                item {
                    PrecipitationProbabilityChart(
                        location,
                        mappedProbabilityValues,
                        daily,
                        probabilityMarkerVisibilityListener
                    )
                }
            } else {
                item {
                    UnavailableChart(mappedProbabilityValues.size)
                }
            }
            // TODO: Short explanation
            item {
                PrecipitationProbabilityDetails(
                    daily.day?.precipitationProbability,
                    daily.night?.precipitationProbability
                )
            }
            item {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
            }
            item {
                DetailsSectionDivider()
            }
            item {
                // Same "Details" tile grid as the main screen's overview card, but fed the
                // selected/centered hour from the strip above (see overviewHourly) instead of
                // "now" -- sun/moon/pollen stay day-level since they aren't hourly concepts.
                DetailsOverviewGrid(
                    location = location,
                    source = DetailsOverviewSource.ofHourly(overviewHourly),
                    daily = daily,
                    onNavigate = { chart -> onNavigateToChart?.invoke(chart) }
                )
            }
        }
        bottomDetailsInset()
    }
}

@Composable
fun TemperatureHeader(
    location: Location,
    daily: Daily,
    activeItem: Pair<Date, Hourly>?,
    showRealTemp: Boolean,
    normals: Normals?,
    temperatureUnit: TemperatureUnit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    activeItem?.let {
        WeatherConditionItem(
            header = {
                TextFixedHeight(
                    text = it.first.getFormattedTime(location, context, context.is12Hour),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            showRealTemp = showRealTemp,
            temperature = it.second.temperature,
            weatherCode = it.second.weatherCode,
            weatherText = it.second.weatherText,
            isDaytime = it.second.isDaylight,
            animated = false, // Doesn't redraw otherwise
            normals = null,
            monthFormatted = "",
            keepSpaceForSubtext = normals?.daytimeTemperature != null || normals?.nighttimeTemperature != null,
            temperatureUnit = temperatureUnit
        )
    } ?: WeatherConditionSummary(
        daily,
        showRealTemp,
        normals,
        daily.date.getCalendarMonth(location).getDisplayName(context.currentLocale),
        temperatureUnit
    )
}

@Composable
private fun WeatherConditionSummary(
    daily: Daily,
    showRealTemp: Boolean,
    normals: Normals?,
    monthFormatted: String,
    temperatureUnit: TemperatureUnit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.normal_margin)),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics { isTraversalGroup = true }
        ) {
            daily.day?.let { day ->
                WeatherConditionItem(
                    header = { DaytimeLabel() },
                    showRealTemp = showRealTemp,
                    temperature = day.temperature,
                    weatherCode = day.weatherCode,
                    weatherText = day.weatherText,
                    isDaytime = true,
                    animated = true,
                    normals = normals,
                    monthFormatted = monthFormatted,
                    keepSpaceForSubtext = normals?.daytimeTemperature != null || normals?.nighttimeTemperature != null,
                    temperatureUnit = temperatureUnit
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics { isTraversalGroup = true }
        ) {
            daily.night?.let { night ->
                WeatherConditionItem(
                    header = { NighttimeLabelWithInfo() },
                    showRealTemp = showRealTemp,
                    temperature = night.temperature,
                    weatherCode = night.weatherCode,
                    weatherText = night.weatherText,
                    isDaytime = false,
                    animated = true,
                    normals = normals,
                    monthFormatted = monthFormatted,
                    keepSpaceForSubtext = normals?.daytimeTemperature != null || normals?.nighttimeTemperature != null,
                    temperatureUnit = temperatureUnit
                )
            }
        }
    }
}

@Composable
private fun WeatherConditionItem(
    header: @Composable () -> Unit,
    showRealTemp: Boolean,
    temperature: livewallpaperweather.domain.weather.model.Temperature?,
    weatherCode: WeatherCode?,
    weatherText: String?,
    isDaytime: Boolean,
    animated: Boolean,
    normals: Normals?,
    monthFormatted: String,
    keepSpaceForSubtext: Boolean,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        header()
        Row {
            Column {
                (if (showRealTemp) temperature?.temperature else temperature?.feelsLikeTemperature).let { temp ->
                    TextFixedHeight(
                        text = temp?.formatMeasure(context, temperatureUnit, unitWidth = UnitWidth.NARROW) ?: "",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier
                            .clearAndSetSemantics {
                                temp?.let {
                                    contentDescription = it.formatMeasure(
                                        context,
                                        temperatureUnit,
                                        unitWidth = UnitWidth.LONG
                                    )
                                }
                            }
                    )
                }
                if (!showRealTemp) {
                    TextFixedHeight(
                        text = temperature?.temperature?.let {
                            stringResource(R.string.temperature_real) +
                                stringResource(R.string.colon_separator) +
                                it.formatMeasure(context, temperatureUnit)
                        } ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clearAndSetSemantics {
                                if (temperature?.temperature != null) {
                                    contentDescription = resources.getString(R.string.temperature_real) +
                                        resources.getString(R.string.colon_separator) +
                                        temperature.temperature!!.formatMeasure(
                                            context,
                                            temperatureUnit,
                                            unitWidth = UnitWidth.LONG
                                        )
                                }
                            }
                    )
                } else {
                    NormalsDepartureLabel(
                        temperature?.temperature,
                        normals,
                        monthFormatted,
                        isDaytime,
                        keepSpaceForSubtext,
                        temperatureUnit
                    )
                }
            }
            if (context.isLandscape) {
                Spacer(Modifier.width(dimensionResource(R.dimen.large_margin)))
                WeatherCondition(weatherCode, weatherText, isDaytime = isDaytime, animated = animated)
            }
        }
        if (!context.isLandscape) {
            Spacer(Modifier.height(dimensionResource(R.dimen.small_margin)))
            WeatherCondition(weatherCode, weatherText, isDaytime = isDaytime, animated = animated)
        }
    }
}

@Composable
fun NormalsDepartureLabel(
    halfDayTemperature: Temperature?,
    normals: Normals?,
    monthFormatted: String,
    isDaytime: Boolean,
    keepSpaceForSubtext: Boolean,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val normal = if (isDaytime) normals?.daytimeTemperature else normals?.nighttimeTemperature

    if (halfDayTemperature != null && normal != null) {
        val context = LocalContext.current
        val resources = LocalResources.current
        val tooltipState = rememberTooltipState(isPersistent = true)
        val coroutineScope = rememberCoroutineScope()
        val departure = remember(halfDayTemperature, normal) {
            halfDayTemperature.toDouble(temperatureUnit) - normal.toDouble(temperatureUnit)
        }

        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = {
                PlainTooltip {
                    Text(
                        stringResource(
                            if (isDaytime) {
                                R.string.temperature_normals_deviation_explanation_maximum
                            } else {
                                R.string.temperature_normals_deviation_explanation_minimum
                            },
                            monthFormatted
                        )
                    )
                }
            },
            state = tooltipState
        ) {
            Row(
                modifier = modifier
                    .clickable {
                        coroutineScope.launch {
                            tooltipState.show()
                        }
                    }
                    .height(
                        with(LocalDensity.current) {
                            MaterialTheme.typography.labelLarge.lineHeight.toDp()
                        }
                    ),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.temperature_normals_deviation) +
                        stringResource(R.string.colon_separator) +
                        "" +
                        departure.toTemperature(temperatureUnit).formatMeasure(
                            context,
                            temperatureUnit,
                            unitWidth = UnitWidth.NARROW,
                            showSign = true
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    // TooltipBox already takes care of adding the info that there is a tooltip:
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight()
                )
            }
        }
    } else if (keepSpaceForSubtext) {
        TextFixedHeight(text = "", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun WeatherCondition(
    weatherCode: WeatherCode?,
    weatherText: String?,
    isDaytime: Boolean,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        if (weatherCode != null) {
            val provider = ResourcesProviderFactory.newInstance
            if (animated) {
                AndroidView(
                    factory = {
                        AnimatableIconView(context).apply {
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            setAnimatableIcon(
                                provider.getWeatherIcons(weatherCode, isDaytime),
                                provider.getWeatherAnimators(weatherCode, isDaytime)
                            )
                            setOnClickListener {
                                startAnimators()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(dimensionResource(R.dimen.small_weather_icon_size))
                )
            } else {
                Image(
                    bitmap = provider.getWeatherIcon(weatherCode, isDaytime).toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(dimensionResource(R.dimen.small_weather_icon_size))
                )
            }
        }
        TextFixedHeight(
            text = weatherText ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )
    }
}

/**
 * Hour-by-hour forecast strip. This embeds the exact same [TrendRecyclerView] +
 * [HourlyTemperatureAdapter] widget used on the main-page "Verwachting per uur" card
 * (hour, weather icon, precipitation probability, wind, temperature lines against the
 * daytime/nighttime normals reference lines, precipitation), so the two look identical.
 * Both the real temperature (yellow) and the feels-like temperature (gray) are drawn at
 * once. Tapping an hour selects it via [onHourSelected] instead of the adapter's normal
 * behavior of navigating to that hour's daily details screen.
 *
 * Since [hourlyList] spans multiple days (see [hourlyTrendForecast]), scrolling this strip past
 * a midnight boundary reports the newly-centered day via [onCenteredDayChanged] (a
 * `dailyForecast` index), so the caller can follow along with the day-tab row above -- without
 * changing the rest of the page, which stays on whichever day was actually navigated to.
 */
@Composable
private fun HourlyForecastChart(
    location: Location,
    temperatureUnit: TemperatureUnit,
    daily: Daily,
    activeItem: Pair<Date, Hourly>?,
    onHourSelected: (Pair<Date, Hourly>?) -> Unit,
    onCenteredDayChanged: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as BreezyActivity
    val provider = ResourcesProviderFactory.newInstance
    // Must match the adapter's own internal list (weather.hourlyTrendForecast, see
    // HourlyTemperatureAdapter) 1:1 -- onHourClicked below maps an adapter position back to a
    // Hourly via this list, so a mismatched source list here would select the wrong hour.
    val hourlyList = remember(location) { location.weather?.hourlyTrendForecast.orEmpty() }
    // Every day's page shares the same underlying [hourlyList], so opening a page other than
    // today must NOT reuse [Weather.hourlyTrendCurrentIndex] (that's always "now", i.e. today) --
    // otherwise every day's strip opens showing today's hours instead of that day's own. Center
    // on "now" only for today's page; for any other day, center on that day's first hour.
    val initialCenterIndex = remember(location, daily) {
        if (daily.isToday(location)) {
            location.weather?.hourlyTrendCurrentIndex ?: 0
        } else {
            hourlyList.indexOfFirst { it.date.time >= daily.date.time }.takeIf { it >= 0 } ?: 0
        }
    }
    val dailyDates = remember(location) { location.weather?.dailyForecast?.map { it.date }.orEmpty() }
    val highlightedPosition = remember(activeItem, hourlyList) {
        activeItem?.let { (date, _) -> hourlyList.indexOfFirst { it.date.time == date.time }.takeIf { it >= 0 } }
    }
    // rememberUpdatedState so the click listener set once in `factory` always calls the latest
    // lambda, without needing to touch the adapter/RecyclerView on every recomposition.
    val onHourSelectedState = rememberUpdatedState(onHourSelected)
    val onCenteredDayChangedState = rememberUpdatedState(onCenteredDayChanged)
    val hourlyListState = rememberUpdatedState(hourlyList)

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.hourly_trend_item_height)),
        factory = { ctx ->
            val trendRecyclerView = TrendRecyclerView(ctx).apply {
                setHasFixedSize(true)
                layoutManager = TrendLayoutManager(ctx)
                // This RecyclerView already disambiguates horizontal drags itself (see
                // NestedHorizontalRecyclerView) via requestDisallowInterceptTouchEvent, so it
                // doesn't need nested-scroll dispatch. With it enabled, flinging this list bridges
                // into the enclosing Compose day-tabs Pager's nested-scroll connection, and a
                // cancelled Pager scroll throws an uncaught CancellationException from the fling
                // loop and crashes the app.
                isNestedScrollingEnabled = false
            }
            val adapter = HourlyTemperatureAdapter(
                activity,
                location,
                provider,
                temperatureUnit = temperatureUnit,
                showFeelsLikeLine = true
            )
            adapter.onHourClicked = { position ->
                val hourly = hourlyListState.value.getOrNull(position)
                onHourSelectedState.value(
                    if (hourly == null || adapter.highlightedPosition == position) null else Pair(hourly.date, hourly)
                )
            }
            // Live-reports which day is centered as the user scrolls this multi-day strip, so
            // the day-tab row above can follow along across midnight without changing the rest
            // of the page (see the onCenteredDayChanged kdoc above).
            trendRecyclerView.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    private var lastReportedDayIndex: Int? = null

                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val callback = onCenteredDayChangedState.value ?: return
                        val list = hourlyListState.value
                        val centeredDate = recyclerView.centeredItemDate(list) ?: return
                        val dayIndex = dailyDates.indexOfLast { it.time <= centeredDate.time }
                            .takeIf { it >= 0 } ?: return
                        if (dayIndex != lastReportedDayIndex) {
                            lastReportedDayIndex = dayIndex
                            callback(dayIndex)
                        }
                    }
                }
            )
            trendRecyclerView.setLineColor(
                context.getThemeColor(com.google.android.material.R.attr.colorOutline)
            )
            trendRecyclerView.setTextColor(
                context.getThemeColor(R.attr.colorTitleText)
            )
            // Set once here (not in `update`) — reassigning a RecyclerView's adapter on every
            // recomposition interrupts an in-progress fling, which crashed the app with an
            // uncaught CancellationException from an enclosing Compose Pager's nested scroll.
            trendRecyclerView.adapter = adapter
            trendRecyclerView.setKeyLineVisibility(
                SettingsManager.getInstance(context).isTrendHorizontalLinesEnabled
            )
            adapter.bindBackgroundForHost(trendRecyclerView)
            // Same "center on current hour" behavior as the main-page hourly card (see
            // DailyViewHolder.centerOnHour) -- the strip starts at midnight, so without this it
            // opens scrolled all the way to the start instead of showing "now".
            trendRecyclerView.centerOnHour(initialCenterIndex)
            trendRecyclerView
        },
        update = { trendRecyclerView ->
            (trendRecyclerView.adapter as HourlyTemperatureAdapter).setHighlightedPosition(highlightedPosition)
        }
    )
}

/**
 * Scrolls so [index] is centered in this RecyclerView's visible width. Right after
 * adapter/layoutManager assignment the RecyclerView's width is still 0 (its measure/layout pass
 * hasn't run yet), so this re-posts itself until a real width is available -- same approach as
 * [com.liveweatherwallpaperapp.ui.main.adapters.main.holder.DailyViewHolder.centerOnHour].
 */
private fun TrendRecyclerView.centerOnHour(index: Int, attemptsLeft: Int = 10) {
    val layoutManager = layoutManager as? TrendLayoutManager ?: return
    val itemCount = adapter?.itemCount ?: 0
    if (index < 0 || index >= itemCount) return
    val width = width
    if (width <= 0) {
        if (attemptsLeft > 0) {
            post { centerOnHour(index, attemptsLeft - 1) }
        }
        return
    }
    val itemWidth = (
        context.resources.getDimensionPixelSize(R.dimen.trend_item_width) * context.fontScaleToApply
        ).roundToInt()
    val offset = ((width - itemWidth) / 2).coerceAtLeast(0)
    layoutManager.scrollToPositionWithOffset(index, offset)
}

/**
 * The [Hourly.date] of whichever visible child's horizontal center is closest to this
 * RecyclerView's own horizontal center, or null if nothing is laid out yet. Used to figure out
 * which day is "centered" while the user scrolls the multi-day hourly strip.
 */
private fun RecyclerView.centeredItemDate(hourlyList: List<Hourly>): Date? {
    val layoutManager = layoutManager as? TrendLayoutManager ?: return null
    val first = layoutManager.findFirstVisibleItemPosition()
    val last = layoutManager.findLastVisibleItemPosition()
    if (first < 0 || last < 0) return null
    val center = width / 2f
    var bestPosition = -1
    var bestDistance = Float.MAX_VALUE
    for (position in first..last) {
        val child = layoutManager.findViewByPosition(position) ?: continue
        val childCenter = (child.left + child.right) / 2f
        val distance = kotlin.math.abs(childCenter - center)
        if (distance < bestDistance) {
            bestDistance = distance
            bestPosition = position
        }
    }
    return hourlyList.getOrNull(bestPosition)?.date
}
