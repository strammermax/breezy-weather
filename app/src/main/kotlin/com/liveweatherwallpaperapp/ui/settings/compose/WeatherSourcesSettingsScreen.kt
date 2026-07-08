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

package com.liveweatherwallpaperapp.ui.settings.compose

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.liveweatherwallpaperapp.BreezyWeather
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.plus
import com.liveweatherwallpaperapp.common.preference.EditTextPreference
import com.liveweatherwallpaperapp.common.preference.ListPreference
import com.liveweatherwallpaperapp.common.source.ConfigurableSource
import com.liveweatherwallpaperapp.common.source.FeatureSource
import com.liveweatherwallpaperapp.common.source.LocationSource
import com.liveweatherwallpaperapp.common.source.NonFreeNetSource
import com.liveweatherwallpaperapp.common.source.RemovedSource
import com.liveweatherwallpaperapp.common.source.getName
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.ui.common.composables.AlertDialogLink
import com.liveweatherwallpaperapp.ui.common.composables.SourceView
import com.liveweatherwallpaperapp.ui.common.widgets.Material3ExpressiveCardListItem
import com.liveweatherwallpaperapp.ui.common.widgets.Material3Scaffold
import com.liveweatherwallpaperapp.ui.common.widgets.generateCollapsedScrollBehavior
import com.liveweatherwallpaperapp.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.liveweatherwallpaperapp.ui.settings.preference.bottomInsetItem
import com.liveweatherwallpaperapp.ui.settings.preference.clickablePreferenceItem
import com.liveweatherwallpaperapp.ui.settings.preference.composables.EditTextPreferenceViewWithCard
import com.liveweatherwallpaperapp.ui.settings.preference.composables.ListPreferenceView
import com.liveweatherwallpaperapp.ui.settings.preference.composables.PreferenceScreen
import com.liveweatherwallpaperapp.ui.settings.preference.composables.SectionFooter
import com.liveweatherwallpaperapp.ui.settings.preference.composables.SectionHeader
import com.liveweatherwallpaperapp.ui.settings.preference.editTextPreferenceItem
import com.liveweatherwallpaperapp.ui.settings.preference.largeSeparatorItem
import com.liveweatherwallpaperapp.ui.settings.preference.listPreferenceItem
import com.liveweatherwallpaperapp.ui.settings.preference.sectionFooterItem
import com.liveweatherwallpaperapp.ui.settings.preference.sectionHeaderItem
import com.liveweatherwallpaperapp.ui.settings.preference.smallSeparatorItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.text.Collator

@Composable
fun WeatherSourcesSettingsScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    worldwideSources: ImmutableList<FeatureSource>,
    configurableSources: ImmutableList<ConfigurableSource>,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = generateCollapsedScrollBehavior()

    Material3Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = stringResource(R.string.settings_weather_sources),
                onBackPressed = onNavigateBack,
                actions = { AboutActivityIconButton(context) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddings ->
        PreferenceScreen(
            paddingValues = paddings.plus(PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin)))
        ) {
            if (BuildConfig.FLAVOR == "freenet") {
                clickablePreferenceItem(R.string.settings_weather_source_freenet_disclaimer) { id ->
                    val dialogLinkOpenState = remember { mutableStateOf(false) }

                    Material3ExpressiveCardListItem(
                        surface = MaterialTheme.colorScheme.secondaryContainer,
                        onSurface = MaterialTheme.colorScheme.onSecondaryContainer,
                        isFirst = true,
                        isLast = true,
                        modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.small_margin))
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                top = dimensionResource(R.dimen.normal_margin),
                                start = dimensionResource(R.dimen.normal_margin),
                                end = dimensionResource(R.dimen.normal_margin)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.settings_weather_source_freenet_disclaimer),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (BuildConfig.INSTALL_INSTRUCTIONS_LINK.startsWith("https://") &&
                                (
                                    !BuildConfig.INSTALL_INSTRUCTIONS_LINK.contains("breezy", ignoreCase = true) ||
                                        BreezyWeather.instance.isSignedByBreezy ||
                                        BreezyWeather.instance.debugMode
                                    )
                            ) {
                                TextButton(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    onClick = {
                                        dialogLinkOpenState.value = true
                                    }
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_learn_more)
                                    )
                                }
                            }
                        }
                    }
                    if (dialogLinkOpenState.value) {
                        AlertDialogLink(
                            onClose = { dialogLinkOpenState.value = false },
                            linkToOpen = BuildConfig.INSTALL_INSTRUCTIONS_LINK
                        )
                    }
                }
                largeSeparatorItem()
            }

            sectionHeaderItem(R.string.settings_weather_sources_section_general)
            listPreferenceItem(R.string.settings_weather_sources_default_source) { id ->
                val worldwideSourcesAssociated = worldwideSources.associate { it.id to it.name }
                val defaultWeatherSource = SettingsManager.getInstance(context).defaultForecastSource
                SourceView(
                    title = stringResource(id),
                    selectedKey = if (worldwideSourcesAssociated.contains(defaultWeatherSource)) {
                        defaultWeatherSource
                    } else {
                        "auto"
                    },
                    sourceList = buildList {
                        add(Triple("auto", stringResource(R.string.settings_automatic), true))
                        addAll(
                            worldwideSources.map {
                                Triple(
                                    it.id,
                                    it.getName(context),
                                    it !is RemovedSource &&
                                        (it !is ConfigurableSource || it.isConfigured) &&
                                        (BuildConfig.FLAVOR != "freenet" || it !is NonFreeNetSource)
                                )
                            }
                        )
                    }.toImmutableList(),
                    card = true,
                    isFirst = true,
                    isLast = true
                ) { defaultSource ->
                    SettingsManager.getInstance(context).defaultForecastSource = defaultSource
                }
            }
            sectionFooterItem(R.string.settings_weather_sources_section_general)

            configurableSources
                .filter {
                    // Exclude location sources configured in their own screen
                    it !is LocationSource && it.getPreferences(context).isNotEmpty()
                }
                .sortedWith { ws1, ws2 ->
                    // Sort by name because there are now a lot of sources
                    Collator.getInstance(context.currentLocale).compare(ws1.name, ws2.name)
                }
                .forEach { preferenceSource ->
                    largeSeparatorItem()
                    item(key = "header_${preferenceSource.id}") {
                        SectionHeader(title = preferenceSource.name)
                    }
                    preferenceSource.getPreferences(context).forEachIndexed { index, preference ->
                        when (preference) {
                            is ListPreference -> {
                                listPreferenceItem(preference.titleId) { id ->
                                    ListPreferenceView(
                                        titleId = id,
                                        selectedKey = preference.selectedKey,
                                        valueArrayId = preference.valueArrayId,
                                        nameArrayId = preference.nameArrayId,
                                        card = true,
                                        isFirst = index == 0,
                                        isLast = index == preferenceSource.getPreferences(context).lastIndex,
                                        onValueChanged = preference.onValueChanged
                                    )
                                }
                            }

                            is EditTextPreference -> {
                                editTextPreferenceItem(preference.titleId) { id ->
                                    EditTextPreferenceViewWithCard(
                                        titleId = id,
                                        summary = preference.summary,
                                        content = preference.content,
                                        placeholder = preference.placeholder,
                                        regex = preference.regex,
                                        regexError = preference.regexError,
                                        keyboardType = preference.keyboardType,
                                        isFirst = index == 0,
                                        isLast = index == preferenceSource.getPreferences(context).lastIndex,
                                        onValueChanged = preference.onValueChanged
                                    )
                                }
                            }
                        }
                        if (index != preferenceSource.getPreferences(context).lastIndex) {
                            smallSeparatorItem()
                        }
                    }
                    item(key = "footer_${preferenceSource.id}") {
                        SectionFooter()
                    }
                }

            bottomInsetItem()
        }
    }
}
