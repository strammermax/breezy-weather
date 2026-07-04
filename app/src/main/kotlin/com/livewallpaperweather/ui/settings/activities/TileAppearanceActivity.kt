/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.ui.settings.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import com.livewallpaperweather.R
import com.livewallpaperweather.common.activities.BreezyActivity
import com.livewallpaperweather.common.extensions.plus
import com.livewallpaperweather.domain.settings.SettingsManager
import com.livewallpaperweather.ui.common.widgets.Material3Scaffold
import com.livewallpaperweather.ui.common.widgets.generateCollapsedScrollBehavior
import com.livewallpaperweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.livewallpaperweather.ui.settings.preference.bottomInsetItem
import com.livewallpaperweather.ui.settings.preference.composables.ListPreferenceViewWithCard
import com.livewallpaperweather.ui.settings.preference.composables.PreferenceScreen
import com.livewallpaperweather.ui.settings.preference.largeSeparatorItem
import com.livewallpaperweather.ui.settings.preference.listPreferenceItem
import com.livewallpaperweather.ui.theme.compose.BreezyWeatherTheme
import kotlin.math.roundToInt

@AndroidEntryPoint
class TileAppearanceActivity : BreezyActivity() {

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
        val context = LocalContext.current
        val settings = SettingsManager.getInstance(context)
        val scrollBehavior = generateCollapsedScrollBehavior()

        var cardStyle by remember { mutableStateOf(settings.tileCardStyle) }
        var cardAlpha by remember { mutableFloatStateOf(settings.tileCardAlpha.toFloat()) }
        var textColor by remember { mutableStateOf(settings.tileTextColor) }

        val cardStyleValues = stringArrayResource(R.array.widget_card_style_values)
        val cardStyleNames = stringArrayResource(R.array.widget_card_styles)
        val textColorValues = stringArrayResource(R.array.widget_text_color_values)
        val textColorNames = stringArrayResource(R.array.widget_text_colors)

        Material3Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                FitStatusBarTopAppBar(
                    title = stringResource(R.string.settings_tile_appearance_title),
                    onBackPressed = { finish() },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            PreferenceScreen(
                paddingValues = paddingValues.plus(
                    PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin))
                )
            ) {
                listPreferenceItem(R.string.widget_label_show_widget_card) {
                    ListPreferenceViewWithCard(
                        title = stringResource(it),
                        summary = { _, value ->
                            cardStyleNames.getOrNull(cardStyleValues.indexOf(value)) ?: value
                        },
                        selectedKey = cardStyle,
                        valueArray = cardStyleValues,
                        nameArray = cardStyleNames,
                        isFirst = true,
                        isLast = cardStyle == "none",
                        onValueChanged = { newValue ->
                            cardStyle = newValue
                            settings.tileCardStyle = newValue
                        }
                    )
                }

                if (cardStyle != "none") {
                    item {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = dimensionResource(R.dimen.normal_margin),
                                vertical = dimensionResource(R.dimen.small_margin)
                            )
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.settings_tile_card_alpha_label,
                                    cardAlpha.roundToInt()
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Slider(
                                value = cardAlpha,
                                onValueChange = { cardAlpha = it },
                                valueRange = 0f..100f,
                                steps = 99,
                                onValueChangeFinished = {
                                    settings.tileCardAlpha = cardAlpha.roundToInt()
                                }
                            )
                        }
                    }
                }

                largeSeparatorItem()

                listPreferenceItem(R.string.settings_tile_text_color_title) {
                    ListPreferenceViewWithCard(
                        title = stringResource(it),
                        summary = { _, value ->
                            textColorNames.getOrNull(textColorValues.indexOf(value)) ?: value
                        },
                        selectedKey = textColor,
                        valueArray = textColorValues,
                        nameArray = textColorNames,
                        isFirst = true,
                        isLast = true,
                        onValueChanged = { newValue ->
                            textColor = newValue
                            settings.tileTextColor = newValue
                        }
                    )
                }

                bottomInsetItem()
            }
        }
    }
}
