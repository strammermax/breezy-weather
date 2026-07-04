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

package com.livewallpaperweather.ui.settings.compose

import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import com.livewallpaperweather.BreezyWeather
import com.livewallpaperweather.R
import com.livewallpaperweather.background.weather.WeatherUpdateJob
import com.livewallpaperweather.common.extensions.plus
import com.livewallpaperweather.common.utils.CrashLogUtils
import com.livewallpaperweather.common.utils.DiagnosticLogger
import com.livewallpaperweather.common.utils.helpers.SnackbarHelper
import com.livewallpaperweather.domain.settings.SettingsManager
import com.livewallpaperweather.ui.common.composables.AnimatedVisibilitySlideVertically
import com.livewallpaperweather.ui.common.widgets.Material3Scaffold
import com.livewallpaperweather.ui.common.widgets.generateCollapsedScrollBehavior
import com.livewallpaperweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.livewallpaperweather.ui.main.utils.RefreshErrorType
import com.livewallpaperweather.ui.settings.activities.SettingsActivity
import com.livewallpaperweather.ui.settings.preference.bottomInsetItem
import com.livewallpaperweather.ui.settings.preference.clickablePreferenceItem
import com.livewallpaperweather.ui.settings.preference.composables.PreferenceScreen
import com.livewallpaperweather.ui.settings.preference.composables.PreferenceViewWithCard
import com.livewallpaperweather.ui.settings.preference.composables.SwitchPreferenceView
import com.livewallpaperweather.ui.settings.preference.largeSeparatorItem
import com.livewallpaperweather.ui.settings.preference.listPreferenceItem
import com.livewallpaperweather.ui.settings.preference.sectionFooterItem
import com.livewallpaperweather.ui.settings.preference.sectionHeaderItem
import com.livewallpaperweather.ui.settings.preference.smallSeparatorItem
import com.livewallpaperweather.ui.settings.preference.switchPreferenceItem

@Composable
fun DebugSettingsScreen(
    context: SettingsActivity,
    onNavigateBack: () -> Unit,
    hasNotificationPermission: Boolean,
    postNotificationPermissionEnsurer: (succeedCallback: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val diagnosticLogging = remember { mutableStateOf(DiagnosticLogger.isEnabled(context)) }
    val askToDeleteLogs = remember { mutableStateOf(false) }
    val scrollBehavior = generateCollapsedScrollBehavior()

    Material3Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = stringResource(R.string.settings_debug),
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
                                R.string.settings_debug_notification_permission,
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
                                postNotificationPermissionEnsurer { /* no callback */ }
                            }
                        )
                        largeSeparatorItem()
                    }
                }
            }
            clickablePreferenceItem(R.string.settings_debug_dump_crash_logs_title) { id ->
                PreferenceViewWithCard(
                    titleId = id,
                    summaryId = R.string.settings_debug_dump_crash_logs_summary,
                    enabled = hasNotificationPermission,
                    isFirst = true,
                    isLast = !BreezyWeather.instance.debugMode
                ) {
                    scope.launch {
                        if (CrashLogUtils(context).dumpLogs()) askToDeleteLogs.value = true
                    }
                }
            }

            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_debug_logging_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_debug_logging_summary_on,
                    summaryOffId = R.string.settings_debug_logging_summary_off,
                    checked = diagnosticLogging.value,
                    isLast = !BreezyWeather.instance.debugMode,
                    onValueChanged = { enabled ->
                        diagnosticLogging.value = enabled
                        DiagnosticLogger.setEnabled(context, enabled)
                    }
                )
            }

            if (BreezyWeather.instance.debugMode) {
                smallSeparatorItem()
                clickablePreferenceItem(R.string.settings_debug_force_weather_update) { id ->
                    PreferenceViewWithCard(
                        title = stringResource(id),
                        summary = stringResource(R.string.settings_debug_force_weather_update_summary),
                        enabled = hasNotificationPermission,
                        isLast = true
                    ) {
                        WeatherUpdateJob.startNow(context)
                    }
                }

                largeSeparatorItem()

                sectionHeaderItem(R.string.settings_debug_section_unit_formatting)
                switchPreferenceItem(R.string.settings_debug_use_numberformatter) { id ->
                    SwitchPreferenceView(
                        titleId = id,
                        summaryOnId = R.string.settings_enabled,
                        summaryOffId = R.string.settings_disabled,
                        checked = SettingsManager.getInstance(context).useNumberFormatter &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                        isFirst = true,
                        onValueChanged = {
                            SettingsManager.getInstance(context).useNumberFormatter = it
                        }
                    )
                }
                smallSeparatorItem()
                switchPreferenceItem(R.string.settings_debug_use_measureformat) { id ->
                    SwitchPreferenceView(
                        titleId = id,
                        summaryOnId = R.string.settings_enabled,
                        summaryOffId = R.string.settings_disabled,
                        checked = SettingsManager.getInstance(context).useMeasureFormat &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
                        isLast = true,
                        onValueChanged = {
                            SettingsManager.getInstance(context).useMeasureFormat = it
                        }
                    )
                }
                sectionFooterItem(R.string.settings_debug_section_unit_formatting)

                largeSeparatorItem()

                sectionHeaderItem(R.string.settings_debug_section_refresh_error)
                RefreshErrorType.entries.forEachIndexed { index, refreshError ->
                    clickablePreferenceItem(refreshError.shortMessage) { shortMessage ->
                        PreferenceViewWithCard(
                            titleId = shortMessage,
                            isFirst = index == 0,
                            isLast = index == RefreshErrorType.entries.lastIndex,
                            onClick = {
                                refreshError.showDialogAction?.let { showDialogAction ->
                                    SnackbarHelper.showSnackbar(
                                        content = context.getString(shortMessage),
                                        action = context.getString(refreshError.actionButtonMessage)
                                    ) {
                                        showDialogAction(context)
                                    }
                                } ?: SnackbarHelper.showSnackbar(context.getString(shortMessage))
                            }
                        )
                    }
                    if (index != RefreshErrorType.entries.lastIndex) {
                        smallSeparatorItem()
                    }
                }
                sectionFooterItem(R.string.settings_debug_section_refresh_error)
            }

            bottomInsetItem()
        }
    }

    if (askToDeleteLogs.value) {
        AlertDialog(
            onDismissRequest = { askToDeleteLogs.value = false },
            title = { Text(stringResource(R.string.settings_debug_delete_logs_title)) },
            text = { Text(stringResource(R.string.settings_debug_delete_logs_message)) },
            confirmButton = {
                TextButton(onClick = {
                    DiagnosticLogger.deleteAll(context)
                    askToDeleteLogs.value = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { askToDeleteLogs.value = false }) {
                    Text(stringResource(R.string.settings_debug_keep_logs))
                }
            },
        )
    }
}
