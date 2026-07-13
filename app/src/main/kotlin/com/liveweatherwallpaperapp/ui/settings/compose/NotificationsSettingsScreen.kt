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
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.background.forecast.TodayForecastNotificationJob
import com.liveweatherwallpaperapp.background.forecast.TomorrowForecastNotificationJob
import com.liveweatherwallpaperapp.common.extensions.plus
import com.liveweatherwallpaperapp.common.options.UpdateInterval
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.ui.common.composables.AnimatedVisibilitySlideVertically
import com.liveweatherwallpaperapp.ui.common.widgets.Material3Scaffold
import com.liveweatherwallpaperapp.ui.common.widgets.generateCollapsedScrollBehavior
import com.liveweatherwallpaperapp.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.liveweatherwallpaperapp.ui.settings.preference.bottomInsetItem
import com.liveweatherwallpaperapp.ui.settings.preference.composables.PreferenceScreen
import com.liveweatherwallpaperapp.ui.settings.preference.composables.PreferenceViewWithCard
import com.liveweatherwallpaperapp.ui.settings.preference.composables.SwitchPreferenceView
import com.liveweatherwallpaperapp.ui.settings.preference.composables.TimePickerPreferenceView
import com.liveweatherwallpaperapp.ui.settings.preference.largeSeparatorItem
import com.liveweatherwallpaperapp.ui.settings.preference.listPreferenceItem
import com.liveweatherwallpaperapp.ui.settings.preference.sectionFooterItem
import com.liveweatherwallpaperapp.ui.settings.preference.sectionHeaderItem
import com.liveweatherwallpaperapp.ui.settings.preference.smallSeparatorItem
import com.liveweatherwallpaperapp.ui.settings.preference.switchPreferenceItem
import com.liveweatherwallpaperapp.ui.settings.preference.timePickerPreferenceItem
import com.liveweatherwallpaperapp.wallpaper.photo.WeetjeStore
import kotlin.math.roundToInt

@Composable
fun NotificationsSettingsScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    hasNotificationPermission: Boolean,
    postNotificationPermissionEnsurer: (succeedCallback: () -> Unit) -> Unit,
    todayForecastEnabled: Boolean,
    tomorrowForecastEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = generateCollapsedScrollBehavior()
    val weetjeStore = remember(context) { WeetjeStore(context) }
    var weetjeEnabled by remember { mutableStateOf(weetjeStore.notificationsEnabled) }
    var weetjeMaxPerDay by remember { mutableFloatStateOf(weetjeStore.maxNotificationsPerDay.toFloat()) }
    var weetjeDwellMinutes by remember { mutableFloatStateOf(weetjeStore.dwellThresholdMinutes.toFloat()) }

    Material3Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = stringResource(R.string.settings_notifications),
                onBackPressed = onNavigateBack,
                actions = { AboutActivityIconButton(context) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddings ->
        PreferenceScreen(
            paddingValues = paddings.plus(PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin)))
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listPreferenceItem(R.string.settings_notifications_permission) { title ->
                    AnimatedVisibilitySlideVertically(
                        visible = !hasNotificationPermission
                    ) {
                        PreferenceViewWithCard(
                            iconId = R.drawable.ic_about,
                            title = stringResource(title),
                            summary = stringResource(
                                R.string.settings_notifications_permission_summary,
                                stringResource(R.string.action_grant_permission)
                            ),
                            surface = MaterialTheme.colorScheme.primaryContainer,
                            onSurface = MaterialTheme.colorScheme.onPrimaryContainer,
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            isFirst = true,
                            isLast = true,
                            modifier = Modifier.padding(bottom = dimensionResource(R.dimen.normal_margin)),
                            onClick = {
                                postNotificationPermissionEnsurer { /* no callback */ }
                            }
                        )
                    }
                }
            }

            largeSeparatorItem()

            sectionHeaderItem(R.string.settings_notifications_section_general)
            switchPreferenceItem(R.string.settings_notifications_alerts_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = if (SettingsManager.getInstance(context).updateInterval !=
                        UpdateInterval.INTERVAL_NEVER
                    ) {
                        R.string.settings_disabled
                    } else {
                        R.string.settings_unavailable_no_background_updates
                    },
                    checked = SettingsManager.getInstance(context).isAlertPushEnabled &&
                        SettingsManager.getInstance(context).updateInterval != UpdateInterval.INTERVAL_NEVER,
                    enabled = SettingsManager.getInstance(context).updateInterval != UpdateInterval.INTERVAL_NEVER &&
                        hasNotificationPermission,
                    isFirst = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isAlertPushEnabled = it
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_notifications_precipitations_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = if (SettingsManager.getInstance(context).updateInterval !=
                        UpdateInterval.INTERVAL_NEVER
                    ) {
                        R.string.settings_disabled
                    } else {
                        R.string.settings_unavailable_no_background_updates
                    },
                    checked = SettingsManager.getInstance(context).isPrecipitationPushEnabled &&
                        SettingsManager.getInstance(context).updateInterval != UpdateInterval.INTERVAL_NEVER,
                    enabled = SettingsManager.getInstance(context).updateInterval != UpdateInterval.INTERVAL_NEVER &&
                        hasNotificationPermission,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isPrecipitationPushEnabled = it
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_notifications_app_update_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = SettingsManager.getInstance(context).isAppUpdatePushEnabled,
                    enabled = hasNotificationPermission,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isAppUpdatePushEnabled = it
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.widget_live_wallpaper_weetje_notifications) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.widget_live_wallpaper_weetje_notifications_summary,
                    summaryOffId = R.string.settings_disabled,
                    checked = weetjeEnabled,
                    withState = false,
                    enabled = hasNotificationPermission,
                    isLast = true,
                    onValueChanged = {
                        weetjeEnabled = it
                        weetjeStore.notificationsEnabled = it
                    }
                )
            }
            if (weetjeEnabled) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensionResource(R.dimen.normal_margin))
                    ) {
                        Text(
                            text = stringResource(
                                R.string.widget_live_wallpaper_weetje_max_per_day,
                                weetjeMaxPerDay.roundToInt()
                            ),
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = weetjeMaxPerDay,
                            onValueChange = { weetjeMaxPerDay = it.roundToInt().toFloat() },
                            valueRange = WeetjeStore.MIN_MAX_PER_DAY.toFloat()..WeetjeStore.MAX_MAX_PER_DAY.toFloat(),
                            steps = WeetjeStore.MAX_MAX_PER_DAY - WeetjeStore.MIN_MAX_PER_DAY - 1,
                            onValueChangeFinished = {
                                weetjeStore.maxNotificationsPerDay = weetjeMaxPerDay.roundToInt()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.widget_live_wallpaper_weetje_dwell_minutes,
                                weetjeDwellMinutes.roundToInt()
                            ),
                            fontWeight = FontWeight.Bold
                        )
                        val dwellRangeStart = WeetjeStore.MIN_DWELL_THRESHOLD_MINUTES.toFloat()
                        val dwellRangeEnd = WeetjeStore.MAX_DWELL_THRESHOLD_MINUTES.toFloat()
                        Slider(
                            value = weetjeDwellMinutes,
                            onValueChange = { weetjeDwellMinutes = it.roundToInt().toFloat() },
                            valueRange = dwellRangeStart..dwellRangeEnd,
                            onValueChangeFinished = {
                                weetjeStore.dwellThresholdMinutes = weetjeDwellMinutes.roundToInt()
                            }
                        )
                    }
                }
            }
            sectionFooterItem(R.string.settings_notifications_section_general)

            largeSeparatorItem()

            sectionHeaderItem(R.string.settings_notifications_section_forecast)
            switchPreferenceItem(R.string.settings_notifications_forecast_today_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = todayForecastEnabled,
                    withState = false,
                    enabled = hasNotificationPermission,
                    isFirst = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isTodayForecastEnabled = it
                        TodayForecastNotificationJob.setupTask(context, false)
                    }
                )
            }
            smallSeparatorItem()
            timePickerPreferenceItem(R.string.settings_notifications_forecast_time_today_title) { id ->
                TimePickerPreferenceView(
                    titleId = id,
                    currentTime = SettingsManager.getInstance(context).todayForecastTime,
                    enabled = todayForecastEnabled && hasNotificationPermission,
                    onValueChanged = {
                        SettingsManager.getInstance(context).todayForecastTime = it
                        TodayForecastNotificationJob.setupTask(context, false)
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_notifications_forecast_tomorrow_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = tomorrowForecastEnabled,
                    withState = false,
                    enabled = hasNotificationPermission,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isTomorrowForecastEnabled = it
                        TomorrowForecastNotificationJob.setupTask(context, false)
                    }
                )
            }
            smallSeparatorItem()
            timePickerPreferenceItem(R.string.settings_notifications_forecast_time_tomorrow_title) { id ->
                TimePickerPreferenceView(
                    titleId = id,
                    currentTime = SettingsManager.getInstance(context).tomorrowForecastTime,
                    enabled = tomorrowForecastEnabled && hasNotificationPermission,
                    isLast = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).tomorrowForecastTime = it
                        TomorrowForecastNotificationJob.setupTask(context, false)
                    }
                )
            }
            sectionFooterItem(R.string.settings_notifications_section_forecast)

            bottomInsetItem()
        }
    }
}
