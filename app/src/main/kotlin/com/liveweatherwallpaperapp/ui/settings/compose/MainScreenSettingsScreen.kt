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

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.liveweatherwallpaperapp.BreezyWeather
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.isMotionReduced
import com.liveweatherwallpaperapp.common.extensions.plus
import com.liveweatherwallpaperapp.common.options.appearance.BackgroundAnimationMode
import com.liveweatherwallpaperapp.common.options.appearance.CardDisplay
import com.liveweatherwallpaperapp.common.options.appearance.DailyTrendDisplay
import com.liveweatherwallpaperapp.common.options.appearance.HourlyTrendDisplay
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.common.utils.helpers.SnackbarHelper
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.ui.common.widgets.Material3Scaffold
import com.liveweatherwallpaperapp.ui.common.widgets.generateCollapsedScrollBehavior
import com.liveweatherwallpaperapp.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.liveweatherwallpaperapp.ui.settings.preference.bottomInsetItem
import com.liveweatherwallpaperapp.ui.settings.preference.clickablePreferenceItem
import com.liveweatherwallpaperapp.ui.settings.preference.composables.ListPreferenceView
import com.liveweatherwallpaperapp.ui.settings.preference.composables.PreferenceScreen
import com.liveweatherwallpaperapp.ui.settings.preference.composables.PreferenceViewWithCard
import com.liveweatherwallpaperapp.ui.settings.preference.composables.SwitchPreferenceView
import com.liveweatherwallpaperapp.ui.settings.preference.largeSeparatorItem
import com.liveweatherwallpaperapp.ui.settings.preference.listPreferenceItem
import com.liveweatherwallpaperapp.ui.settings.preference.sectionFooterItem
import com.liveweatherwallpaperapp.ui.settings.preference.sectionHeaderItem
import com.liveweatherwallpaperapp.ui.settings.preference.smallSeparatorItem
import com.liveweatherwallpaperapp.ui.settings.preference.switchPreferenceItem
import kotlinx.collections.immutable.ImmutableList

@Composable
fun MainScreenSettingsScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    cardDisplayList: ImmutableList<CardDisplay>,
    dailyTrendDisplayList: ImmutableList<DailyTrendDisplay>,
    hourlyTrendDisplayList: ImmutableList<HourlyTrendDisplay>,
    updateWidgetIfNecessary: (Context) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = generateCollapsedScrollBehavior()

    Material3Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = stringResource(R.string.settings_main),
                onBackPressed = onNavigateBack,
                actions = { AboutActivityIconButton(context) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddings ->
        PreferenceScreen(
            paddingValues = paddings.plus(PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin)))
        ) {
            sectionHeaderItem(R.string.settings_main_section_displayed_data)
            clickablePreferenceItem(
                R.string.settings_main_cards_title
            ) {
                PreferenceViewWithCard(
                    title = stringResource(it),
                    summary = CardDisplay.getSummary(context, cardDisplayList),
                    isFirst = true
                ) {
                    (context as? Activity)?.let { a ->
                        IntentHelper.startCardDisplayManageActivity(a)
                    }
                }
            }
            smallSeparatorItem()
            clickablePreferenceItem(
                R.string.settings_main_daily_trends_title
            ) {
                PreferenceViewWithCard(
                    title = stringResource(it),
                    summary = DailyTrendDisplay.getSummary(context, dailyTrendDisplayList)
                ) {
                    (context as? Activity)?.let { a ->
                        IntentHelper.startDailyTrendDisplayManageActivity(a)
                    }
                }
            }
            smallSeparatorItem()
            clickablePreferenceItem(
                R.string.settings_main_hourly_trends_title
            ) {
                PreferenceViewWithCard(
                    title = stringResource(it),
                    summary = HourlyTrendDisplay.getSummary(context, hourlyTrendDisplayList),
                    isLast = true
                ) {
                    (context as? Activity)?.let { a ->
                        IntentHelper.startHourlyTrendDisplayManageActivity(a)
                    }
                }
            }
            sectionFooterItem(R.string.settings_main_section_displayed_data)

            largeSeparatorItem()

            sectionHeaderItem(R.string.settings_main_section_options)
            switchPreferenceItem(R.string.settings_main_background_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager.getInstance(context).mainScreenBackgroundEnabled,
                    isFirst = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).mainScreenBackgroundEnabled = it
                    }
                )
            }
            switchPreferenceItem(R.string.settings_main_threshold_lines_on_charts) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager.getInstance(context).isTrendHorizontalLinesEnabled,
                    isLast = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isTrendHorizontalLinesEnabled = it
                        updateWidgetIfNecessary(context) // Has some widgets with it

                        // FIXME: Doesn't work without restart on nowcasting chart
                        SnackbarHelper.showSnackbar(
                            content = context.getString(R.string.settings_changes_apply_after_restart),
                            action = context.getString(R.string.action_restart)
                        ) {
                            BreezyWeather.instance.recreateAllActivities()
                        }
                    }
                )
            }
            sectionFooterItem(R.string.settings_main_section_options)

            largeSeparatorItem()

            sectionHeaderItem(R.string.settings_main_section_animations)
            listPreferenceItem(R.string.settings_main_background_animation_title) { id ->
                ListPreferenceView(
                    titleId = id,
                    selectedKey = SettingsManager.getInstance(context).backgroundAnimationMode.id,
                    valueArrayId = R.array.background_animation_values,
                    nameArrayId = R.array.background_animation,
                    card = true,
                    isFirst = true,
                    onValueChanged = {
                        SettingsManager
                            .getInstance(context)
                            .backgroundAnimationMode = BackgroundAnimationMode.getInstance(it)

                        SnackbarHelper.showSnackbar(
                            content = context.getString(R.string.settings_changes_apply_after_restart),
                            action = context.getString(R.string.action_restart)
                        ) {
                            BreezyWeather.instance.recreateAllActivities()
                        }
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_main_gravity_sensor_switch) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager.getInstance(context).isGravitySensorEnabled,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isGravitySensorEnabled = it

                        SnackbarHelper.showSnackbar(
                            content = context.getString(R.string.settings_changes_apply_after_restart),
                            action = context.getString(R.string.action_restart)
                        ) {
                            BreezyWeather.instance.recreateAllActivities()
                        }
                    }
                )
            }
            smallSeparatorItem()

            val animationsEnabled = !context.isMotionReduced
            switchPreferenceItem(R.string.settings_main_cards_fade_in_switch) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = if (animationsEnabled) {
                        R.string.settings_enabled
                    } else {
                        R.string.settings_unavailable_no_animations
                    },
                    summaryOffId = if (animationsEnabled) {
                        R.string.settings_disabled
                    } else {
                        R.string.settings_unavailable_no_animations
                    },
                    checked = SettingsManager.getInstance(context).isCardsFadeInEnabled && animationsEnabled,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isCardsFadeInEnabled = it
                    },
                    enabled = animationsEnabled
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_main_cards_other_element_animations_switch) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = if (animationsEnabled) {
                        R.string.settings_enabled
                    } else {
                        R.string.settings_unavailable_no_animations
                    },
                    summaryOffId = if (animationsEnabled) {
                        R.string.settings_disabled
                    } else {
                        R.string.settings_unavailable_no_animations
                    },
                    enabled = animationsEnabled,
                    checked = SettingsManager.getInstance(context).isElementsAnimationEnabled && animationsEnabled,
                    isLast = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isElementsAnimationEnabled = it
                    }
                )
            }
            sectionFooterItem(R.string.settings_main_section_animations)

            bottomInsetItem()
        }
    }
}
