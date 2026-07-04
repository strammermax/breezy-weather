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
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.plus
import com.liveweatherwallpaperapp.common.options.NotificationStyle
import com.liveweatherwallpaperapp.common.options.WidgetWeekIconMode
import com.liveweatherwallpaperapp.common.source.BroadcastSource
import com.liveweatherwallpaperapp.common.utils.helpers.SnackbarHelper
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.remoteviews.config.ClockDayDetailsWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.ClockDayHorizontalWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.ClockDayVerticalWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.ClockDayWeekWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.DailyTrendWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.DayWeekWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.DayWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.HourlyTrendWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.MultiCityWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.TextWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.config.WeekWidgetConfigActivity
import com.liveweatherwallpaperapp.remoteviews.presenters.ClockDayDetailsWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.ClockDayHorizontalWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.ClockDayVerticalWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.ClockDayWeekWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.DailyTrendWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.DayWeekWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.DayWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.HourlyTrendWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.MultiCityWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.TextWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.WeekWidgetIMP
import com.liveweatherwallpaperapp.remoteviews.presenters.notification.WidgetNotificationIMP
import com.liveweatherwallpaperapp.ui.common.composables.AnimatedVisibilitySlideVertically
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
import com.liveweatherwallpaperapp.wallpaper.launchLiveWallpaperPicker

@Composable
fun ModulesSettingsScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    hasNotificationPermission: Boolean,
    notificationEnabled: Boolean,
    postNotificationPermissionEnsurer: (succeedCallback: () -> Unit) -> Unit,
    updateWidgetIfNecessary: (Context) -> Unit,
    updateNotificationIfNecessary: (Context) -> Unit,
    broadcastDataIfNecessary: (Context, String) -> Unit,
    broadcastSources: ImmutableList<BroadcastSource>,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = generateCollapsedScrollBehavior()

    Material3Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = stringResource(R.string.settings_modules),
                onBackPressed = onNavigateBack,
                actions = { AboutActivityIconButton(context) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddings ->
        PreferenceScreen(
            paddingValues = paddings.plus(PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin)))
        ) {
            // widget.
            sectionHeaderItem(R.string.settings_modules_section_general)
            clickablePreferenceItem(R.string.settings_modules_live_wallpaper_title) { id ->
                PreferenceViewWithCard(
                    titleId = id,
                    summaryId = R.string.settings_modules_live_wallpaper_summary,
                    isFirst = true
                ) {
                    if (!launchLiveWallpaperPicker(context)) {
                        SnackbarHelper.showSnackbar(
                            context.getString(
                                R.string.settings_modules_live_wallpaper_error,
                                context.getString(R.string.brand_name)
                            )
                        )
                    }
                }
            }
            smallSeparatorItem()
            listPreferenceItem(R.string.settings_modules_week_icon_mode_title) { id ->
                ListPreferenceView(
                    titleId = id,
                    selectedKey = SettingsManager.getInstance(context).widgetWeekIconMode.id,
                    valueArrayId = R.array.week_icon_mode_values,
                    nameArrayId = R.array.week_icon_modes,
                    card = true,
                    onValueChanged = {
                        SettingsManager
                            .getInstance(context)
                            .widgetWeekIconMode = WidgetWeekIconMode.getInstance(it)
                        updateWidgetIfNecessary(context)
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_modules_monochrome_icons_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager.getInstance(context).isWidgetUsingMonochromeIcons,
                    isLast = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isWidgetUsingMonochromeIcons = it
                        updateWidgetIfNecessary(context)
                    }
                )
            }
            smallSeparatorItem()
            sectionFooterItem(R.string.settings_modules_section_general)

            val widgetsInUse = buildList {
                if (DayWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_day, DayWidgetConfigActivity::class.java))
                }
                if (WeekWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_week, WeekWidgetConfigActivity::class.java))
                }
                if (DayWeekWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_day_week, DayWeekWidgetConfigActivity::class.java))
                }
                if (ClockDayHorizontalWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_clock_day_horizontal, ClockDayHorizontalWidgetConfigActivity::class.java))
                }
                if (ClockDayDetailsWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_clock_day_details, ClockDayDetailsWidgetConfigActivity::class.java))
                }
                if (ClockDayVerticalWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_clock_day_vertical, ClockDayVerticalWidgetConfigActivity::class.java))
                }
                if (ClockDayWeekWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_clock_day_week, ClockDayWeekWidgetConfigActivity::class.java))
                }
                if (TextWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_text, TextWidgetConfigActivity::class.java))
                }
                if (DailyTrendWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_trend_daily, DailyTrendWidgetConfigActivity::class.java))
                }
                if (HourlyTrendWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_trend_hourly, HourlyTrendWidgetConfigActivity::class.java))
                }
                if (MultiCityWidgetIMP.isInUse(context)) {
                    add(Pair(R.string.widget_multi_city, MultiCityWidgetConfigActivity::class.java))
                }
            }
            if (widgetsInUse.isNotEmpty()) {
                largeSeparatorItem()
                sectionHeaderItem(R.string.settings_modules_section_widgets_in_use)
                widgetsInUse.forEachIndexed { index, widget ->
                    clickablePreferenceItem(widget.first) {
                        PreferenceViewWithCard(
                            title = stringResource(it),
                            summary = stringResource(R.string.settings_modules_configure_widget_summary),
                            isFirst = index == 0,
                            isLast = index == widgetsInUse.lastIndex
                        ) {
                            context.startActivity(Intent(context, widget.second))
                        }
                    }
                    if (index != widgetsInUse.lastIndex) {
                        smallSeparatorItem()
                    }
                }
                sectionFooterItem(R.string.settings_modules_section_widgets_in_use)
            }

            largeSeparatorItem()

            // notification.
            sectionHeaderItem(R.string.settings_modules_section_notification_widget)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listPreferenceItem(R.string.settings_notifications_permission) { title ->
                    AnimatedVisibilitySlideVertically(
                        visible = !hasNotificationPermission
                    ) {
                        PreferenceViewWithCard(
                            iconId = R.drawable.ic_about,
                            title = stringResource(title),
                            summary = stringResource(
                                R.string.settings_modules_notification_permission_summary,
                                stringResource(R.string.action_grant_permission)
                            ),
                            surface = MaterialTheme.colorScheme.primaryContainer,
                            onSurface = MaterialTheme.colorScheme.onPrimaryContainer,
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            isFirst = true,
                            isLast = true,
                            onClick = {
                                postNotificationPermissionEnsurer {
                                    updateNotificationIfNecessary(context)
                                }
                            }
                        )
                        largeSeparatorItem()
                    }
                }
            }
            switchPreferenceItem(R.string.settings_modules_notification_widget_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = notificationEnabled,
                    withState = false,
                    enabled = hasNotificationPermission,
                    isFirst = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isWidgetNotificationEnabled = it
                        if (it) { // open notification.
                            postNotificationPermissionEnsurer {
                                updateNotificationIfNecessary(context)
                            }
                        } else { // close notification.
                            WidgetNotificationIMP.cancelNotification(context)
                        }
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_modules_notification_persistent_switch) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager
                        .getInstance(context)
                        .isWidgetNotificationPersistent,
                    enabled = notificationEnabled && hasNotificationPermission,
                    onValueChanged = {
                        SettingsManager
                            .getInstance(context)
                            .isWidgetNotificationPersistent = it
                        updateNotificationIfNecessary(context)
                    }
                )
            }
            smallSeparatorItem()
            listPreferenceItem(R.string.settings_modules_notification_style_title) { id ->
                ListPreferenceView(
                    titleId = id,
                    selectedKey = SettingsManager.getInstance(context).widgetNotificationStyle.id,
                    valueArrayId = R.array.notification_style_values,
                    nameArrayId = R.array.notification_styles,
                    enabled = notificationEnabled && hasNotificationPermission,
                    card = true,
                    onValueChanged = {
                        SettingsManager
                            .getInstance(context)
                            .widgetNotificationStyle = NotificationStyle.getInstance(it)
                        updateNotificationIfNecessary(context)
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_modules_notification_temp_icon_switch) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager
                        .getInstance(context)
                        .isWidgetNotificationTemperatureIconEnabled,
                    enabled = notificationEnabled && hasNotificationPermission,
                    onValueChanged = {
                        SettingsManager
                            .getInstance(context)
                            .isWidgetNotificationTemperatureIconEnabled = it
                        updateNotificationIfNecessary(context)
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_modules_notification_feels_like_switch) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager
                        .getInstance(context)
                        .isWidgetNotificationUsingFeelsLike,
                    enabled = notificationEnabled &&
                        hasNotificationPermission,
                    isLast = true,
                    onValueChanged = {
                        SettingsManager
                            .getInstance(context)
                            .isWidgetNotificationUsingFeelsLike = it
                        updateNotificationIfNecessary(context)
                    }
                )
            }
            sectionFooterItem(R.string.settings_modules_section_notification_widget)

            bottomInsetItem()
        }
    }
}
