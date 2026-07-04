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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.livewallpaperweather.R
import com.livewallpaperweather.common.activities.BreezyActivity
import com.livewallpaperweather.common.bus.EventBus
import com.livewallpaperweather.common.options.appearance.WidgetTileType
import com.livewallpaperweather.domain.settings.SettingsChangedMessage
import com.livewallpaperweather.domain.settings.SettingsManager
import com.livewallpaperweather.ui.common.widgets.Material3Scaffold
import com.livewallpaperweather.ui.common.widgets.generateCollapsedScrollBehavior
import com.livewallpaperweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.livewallpaperweather.ui.theme.compose.BreezyWeatherTheme

class WidgetTileSelectActivity : BreezyActivity() {

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
        val scrollBehavior = generateCollapsedScrollBehavior()
        var selectedType by remember {
            mutableStateOf(SettingsManager.getInstance(context).widgetTileType)
        }

        val options = WidgetTileType.entries.map { type ->
            type to stringResource(type.nameRes)
        }

        Material3Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                FitStatusBarTopAppBar(
                    title = stringResource(R.string.widget_tile_select),
                    onBackPressed = { finish() },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = dimensionResource(R.dimen.normal_margin),
                    end = dimensionResource(R.dimen.normal_margin),
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 16.dp,
                ),
            ) {
                items(options) { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedType = type
                                SettingsManager.getInstance(context).widgetTileType = type
                                EventBus.instance.with(SettingsChangedMessage::class.java).postValue(SettingsChangedMessage())
                                finish()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = null,
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
