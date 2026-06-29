/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.ui.settings.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.plus
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.generateCollapsedScrollBehavior
import org.breezyweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import org.breezyweather.ui.settings.preference.bottomInsetItem
import org.breezyweather.ui.settings.preference.composables.ListPreferenceViewWithCard
import org.breezyweather.ui.settings.preference.composables.PreferenceScreen
import org.breezyweather.ui.settings.preference.largeSeparatorItem
import org.breezyweather.ui.settings.preference.listPreferenceItem
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme

@AndroidEntryPoint
class RadarTileSettingsActivity : BreezyActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BreezyWeatherTheme {
                ContentView()
            }
        }
    }

    @Composable
    private fun ContentView() {
        val settings = SettingsManager.getInstance(this)
        val scrollBehavior = generateCollapsedScrollBehavior()
        var source by remember { mutableStateOf(settings.radarTileSource) }
        var mapStyle by remember { mutableStateOf(settings.radarTileMapStyle) }
        val sourceValues = stringArrayResource(R.array.radar_tile_source_values)
        val sourceNames = stringArrayResource(R.array.radar_tile_sources)
        val styleValues = stringArrayResource(R.array.radar_tile_map_style_values)
        val styleNames = stringArrayResource(R.array.radar_tile_map_styles)

        Material3Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                FitStatusBarTopAppBar(
                    title = stringResource(R.string.settings_radar_tile_title),
                    onBackPressed = { finish() },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            PreferenceScreen(
                paddingValues = paddingValues.plus(
                    PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin))
                )
            ) {
                listPreferenceItem(R.string.settings_radar_tile_source) {
                    ListPreferenceViewWithCard(
                        title = stringResource(it),
                        summary = { _, value -> sourceNames.getOrNull(sourceValues.indexOf(value)) ?: value },
                        selectedKey = source,
                        valueArray = sourceValues,
                        nameArray = sourceNames,
                        isFirst = true,
                        isLast = true,
                        onValueChanged = { newValue ->
                            source = newValue
                            settings.radarTileSource = newValue
                        },
                    )
                }

                if (source == "rainviewer") {
                    largeSeparatorItem()
                    listPreferenceItem(R.string.settings_radar_tile_world_style) {
                        ListPreferenceViewWithCard(
                            title = stringResource(it),
                            summary = { _, value -> styleNames.getOrNull(styleValues.indexOf(value)) ?: value },
                            selectedKey = mapStyle,
                            valueArray = styleValues,
                            nameArray = styleNames,
                            isFirst = true,
                            isLast = true,
                            onValueChanged = { newValue ->
                                mapStyle = newValue
                                settings.radarTileMapStyle = newValue
                            },
                        )
                    }
                }
                bottomInsetItem()
            }
        }
    }
}
