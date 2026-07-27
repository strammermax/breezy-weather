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

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.background.findmyphone.FindMyPhoneService
import com.liveweatherwallpaperapp.background.findmyphone.FindMyPhoneStore
import com.liveweatherwallpaperapp.background.forecast.TodayForecastNotificationJob
import com.liveweatherwallpaperapp.background.forecast.TomorrowForecastNotificationJob
import com.liveweatherwallpaperapp.common.extensions.plus
import com.liveweatherwallpaperapp.common.extensions.powerManager
import com.liveweatherwallpaperapp.common.options.UpdateInterval
import com.liveweatherwallpaperapp.common.utils.helpers.SnackbarHelper
import com.liveweatherwallpaperapp.domain.settings.AppDefaults
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.domain.settings.TesterModeStore
import com.liveweatherwallpaperapp.remoteviews.presenters.MaterialYouForecastWidgetIMP
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
import com.liveweatherwallpaperapp.wallpaper.photo.WeetjeManager
import com.liveweatherwallpaperapp.wallpaper.photo.WeetjeStore
import com.liveweatherwallpaperapp.wallpaper.photo.toWallpaperPlaceQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val weetjeForceScope = rememberCoroutineScope()
    val weetjeStore = remember(context) { WeetjeStore(context) }
    var weetjeEnabled by remember { mutableStateOf(weetjeStore.notificationsEnabled) }
    var weetjeMaxPerDay by remember { mutableFloatStateOf(weetjeStore.maxNotificationsPerDay.toFloat()) }
    var weetjeDwellMinutes by remember { mutableFloatStateOf(weetjeStore.dwellThresholdMinutes.toFloat()) }

    val findMyPhoneStore = remember(context) { FindMyPhoneStore(context) }
    var findMyPhoneEnabled by remember { mutableStateOf(findMyPhoneStore.enabled) }
    var findMyPhonePendingEnable by remember { mutableStateOf(false) }
    val isTesterModeEnabled = remember(context) { TesterModeStore(context).isEnabled }
    var findMyPhoneRmsGateDb by remember {
        mutableFloatStateOf(findMyPhoneStore.testerRmsGateDbOverride ?: AppDefaults.findMyPhone.rmsGateDb)
    }
    var findMyPhoneArmDelayMinutes by remember {
        mutableFloatStateOf(
            (findMyPhoneStore.testerArmDelayMinutesOverride ?: AppDefaults.findMyPhone.armDelayMinutes).toFloat()
        )
    }
    var findMyPhoneClapEnabled by remember { mutableStateOf(findMyPhoneStore.clapEnabled) }
    var findMyPhoneWhistleEnabled by remember { mutableStateOf(findMyPhoneStore.whistleEnabled) }
    var showFindMyPhoneCalibration by remember { mutableStateOf(false) }
    val recordAudioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    LaunchedEffect(recordAudioPermissionState.status, findMyPhonePendingEnable) {
        if (findMyPhonePendingEnable && recordAudioPermissionState.status == PermissionStatus.Granted) {
            findMyPhonePendingEnable = false
            findMyPhoneEnabled = true
            findMyPhoneStore.enabled = true
            FindMyPhoneService.start(context)
            requestIgnoreBatteryOptimizationsForFindMyPhone(context)
            if (findMyPhoneWhistleEnabled) showFindMyPhoneCalibration = true
        }
    }

    if (showFindMyPhoneCalibration) {
        FindMyPhoneCalibrationDialog(
            context = context,
            onCalibrated = { hz ->
                findMyPhoneWhistleEnabled = true
                findMyPhoneStore.whistleEnabled = true
                if (hz != null) findMyPhoneStore.whistleCenterHz = hz
                showFindMyPhoneCalibration = false
            },
            onDeclined = {
                findMyPhoneWhistleEnabled = false
                findMyPhoneStore.whistleEnabled = false
                showFindMyPhoneCalibration = false
            }
        )
    }

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
                    isLast = !weetjeEnabled && !FindMyPhoneStore.FEATURE_ENABLED,
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
                        if (BuildConfig.DEBUG) {
                            // Debug-only: the real notification needs a full dwell period (up
                            // to hours) at a real location, so testers need a way to force one
                            // immediately instead of waiting.
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    weetjeForceScope.launch {
                                        val entryPoint = WeetjeManager.entryPoint(context)
                                        val location = entryPoint.locationRepository().getFirstLocation()
                                        val weetje = location?.let {
                                            entryPoint.weetjeManager().forceShowNow(
                                                it.latitude,
                                                it.longitude,
                                                it.toWallpaperPlaceQuery()
                                            )
                                        }
                                        SnackbarHelper.showSnackbar(
                                            if (weetje != null) {
                                                "Weetje notification sent"
                                            } else {
                                                "No location or no weetje available for it"
                                            }
                                        )
                                        // Also mirror it onto the widget fact banner (same
                                        // "briefly show, then hide" behavior as the real
                                        // scheduled trigger), so this one button can test both.
                                        if (weetje != null) {
                                            MaterialYouForecastWidgetIMP.updateFactVisibility(
                                                context,
                                                show = true,
                                                factText = weetje
                                            )
                                            delay(AppDefaults.widgetFact.durationSeconds * 1000L)
                                            MaterialYouForecastWidgetIMP.updateFactVisibility(
                                                context,
                                                show = false,
                                                factText = null
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Force a weetje now (debug)", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (FindMyPhoneStore.FEATURE_ENABLED) {
                smallSeparatorItem()
                switchPreferenceItem(R.string.find_my_phone_title) { id ->
                    SwitchPreferenceView(
                        titleId = id,
                        summaryOnId = R.string.find_my_phone_summary_on,
                        summaryOffId = R.string.find_my_phone_summary_off,
                        checked = findMyPhoneEnabled,
                        withState = false,
                        isLast = !findMyPhoneEnabled && !isTesterModeEnabled,
                        onValueChanged = {
                            if (it) {
                                if (recordAudioPermissionState.status == PermissionStatus.Granted) {
                                    findMyPhoneEnabled = true
                                    findMyPhoneStore.enabled = true
                                    FindMyPhoneService.start(context)
                                    requestIgnoreBatteryOptimizationsForFindMyPhone(context)
                                    if (findMyPhoneWhistleEnabled) showFindMyPhoneCalibration = true
                                } else {
                                    findMyPhonePendingEnable = true
                                    recordAudioPermissionState.launchPermissionRequest()
                                }
                            } else {
                                findMyPhoneEnabled = false
                                findMyPhoneStore.enabled = false
                                FindMyPhoneService.stop(context)
                            }
                        }
                    )
                }
                if (findMyPhoneEnabled) {
                    smallSeparatorItem()
                    switchPreferenceItem(R.string.find_my_phone_clap_title) { id ->
                        SwitchPreferenceView(
                            titleId = id,
                            summaryOnId = R.string.settings_enabled,
                            summaryOffId = R.string.settings_disabled,
                            checked = findMyPhoneClapEnabled,
                            withState = false,
                            onValueChanged = {
                                findMyPhoneClapEnabled = it
                                findMyPhoneStore.clapEnabled = it
                            }
                        )
                    }
                    smallSeparatorItem()
                    switchPreferenceItem(R.string.find_my_phone_whistle_title) { id ->
                        SwitchPreferenceView(
                            titleId = id,
                            summaryOnId = R.string.settings_enabled,
                            summaryOffId = R.string.settings_disabled,
                            checked = findMyPhoneWhistleEnabled,
                            withState = false,
                            isLast = !isTesterModeEnabled,
                            onValueChanged = {
                                if (it) {
                                    // Re-calibrate on every re-enable, not just the first time: the
                                    // user turning it back on is exactly the moment to (re)confirm
                                    // their whistle, e.g. after having declined or skipped before.
                                    showFindMyPhoneCalibration = true
                                } else {
                                    findMyPhoneWhistleEnabled = false
                                    findMyPhoneStore.whistleEnabled = false
                                }
                            }
                        )
                    }
                }
                if (isTesterModeEnabled && findMyPhoneEnabled) {
                    smallSeparatorItem()
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimensionResource(R.dimen.normal_margin))
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.find_my_phone_tester_rms_gate,
                                    findMyPhoneRmsGateDb.roundToInt()
                                ),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.find_my_phone_tester_rms_gate_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = findMyPhoneRmsGateDb,
                                onValueChange = { findMyPhoneRmsGateDb = it.roundToInt().toFloat() },
                                valueRange = FIND_MY_PHONE_TESTER_RMS_GATE_MIN..FIND_MY_PHONE_TESTER_RMS_GATE_MAX,
                                steps =
                                (FIND_MY_PHONE_TESTER_RMS_GATE_MAX - FIND_MY_PHONE_TESTER_RMS_GATE_MIN)
                                    .roundToInt() - 1,
                                onValueChangeFinished = {
                                    findMyPhoneStore.testerRmsGateDbOverride = findMyPhoneRmsGateDb
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.find_my_phone_tester_arm_delay,
                                    findMyPhoneArmDelayMinutes.roundToInt()
                                ),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.find_my_phone_tester_arm_delay_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = findMyPhoneArmDelayMinutes,
                                onValueChange = { findMyPhoneArmDelayMinutes = it.roundToInt().toFloat() },
                                valueRange = FIND_MY_PHONE_TESTER_ARM_DELAY_MIN..FIND_MY_PHONE_TESTER_ARM_DELAY_MAX,
                                steps =
                                (FIND_MY_PHONE_TESTER_ARM_DELAY_MAX - FIND_MY_PHONE_TESTER_ARM_DELAY_MIN).roundToInt() -
                                    1,
                                onValueChangeFinished = {
                                    findMyPhoneStore.testerArmDelayMinutesOverride =
                                        findMyPhoneArmDelayMinutes.roundToInt()
                                }
                            )
                        }
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

private const val FIND_MY_PHONE_TESTER_RMS_GATE_MIN = -60f
private const val FIND_MY_PHONE_TESTER_RMS_GATE_MAX = -10f
private const val FIND_MY_PHONE_TESTER_ARM_DELAY_MIN = 0f
private const val FIND_MY_PHONE_TESTER_ARM_DELAY_MAX = 15f

/**
 * Prompts to exempt the app from battery optimizations, so OEM battery managers (e.g. Samsung's
 * "sleeping apps") don't kill the "Find my phone" foreground service's microphone access after
 * the screen has been locked for a while. Silently does nothing if already exempted or if the
 * device has no activity to handle the request.
 */
@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizationsForFindMyPhone(context: Context) {
    if (context.powerManager.isIgnoringBatteryOptimizations(context.packageName)) return
    try {
        context.startActivity(
            Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: ActivityNotFoundException) {
        // No settings activity to handle this on the device -- the feature still works, just
        // more likely to be paused by the OEM's battery manager while locked for a long time.
    }
}
