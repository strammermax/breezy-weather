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

package com.liveweatherwallpaperapp.domain.settings

import com.liveweatherwallpaperapp.BreezyWeather
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GlobalDefaults(
    val todayForecastTime: String = "07:00",
    val tomorrowForecastTime: String = "21:00",
)

@Serializable
data class NotificationDefaults(
    val alertPushEnabled: Boolean = true,
    val precipitationPushEnabled: Boolean = false,
    val appUpdatePushEnabled: Boolean = true,
    val todayForecastEnabled: Boolean = false,
    val tomorrowForecastEnabled: Boolean = false,
    val widgetNotificationEnabled: Boolean = false,
    val widgetNotificationPersistent: Boolean = true,
    val widgetNotificationStyle: String = "daily",
    val widgetNotificationTemperatureIconEnabled: Boolean = false,
    val widgetNotificationUsingFeelsLike: Boolean = false,
)

@Serializable
data class BackgroundUpdatesDefaults(
    val ignoreUpdatesWhenBatteryLow: Boolean = true,
)

@Serializable
data class AppearanceDefaults(
    val mainScreenBackgroundEnabled: Boolean = true,
    val appBackgroundFrosted: Boolean = true,
    val appBackgroundFrostStrength: Int = 2,
    val appBackgroundFrostTint: String = "system",
    val trendHorizontalLinesEnabled: Boolean = true,
    val backgroundAnimationMode: String = "system",
    val tileCardStyle: String = "auto",
    val tileCardAlpha: Int = 40,
    val tileTextColor: String = "auto",
    val gravitySensorEnabled: Boolean = true,
    val cardsFadeInEnabled: Boolean = true,
    val elementsAnimationEnabled: Boolean = true,
    val useNumberFormatter: Boolean = true,
    val useMeasureFormat: Boolean = true,
)

@Serializable
data class WeatherSourcesDefaults(
    val radarTileSource: String = "rainviewer",
    val radarTileMapStyle: String = "auto",
    val autoUpdateSourcesPerLocation: Boolean = false,
)

@Serializable
data class WallpaperDefaults(
    val weatherKind: String = "auto",
    val dayNightType: String = "auto",
    val animationsEnabled: Boolean = false,
    val parallaxEnabled: Boolean = false,
    val seasonGradingEnabled: Boolean = true,
    val seasonGradingStrength: Float = 0.5f,
    val newCloudsEnabled: Boolean = true,
    val cloudDirectionDegrees: Float = 70f,
    val fogDirectionDegrees: Float = 70f,
    val precipitationLayers: Float = 16f,
)

@Serializable
data class WidgetDefaults(
    val widgetWeekIconMode: String = "auto",
    val widgetMonochromeIcons: Boolean = false,
)

@Serializable
data class DebugDefaults(
    val maxTelemetryEvents: Int = 20,
)

@Serializable
data class FindMyPhoneDefaults(
    /** RMS level (dB relative to full scale) that ambient sound must clear before the clap/
     * whistle detector bothers analyzing a buffer. */
    val rmsGateDb: Float = -35f,
    /** How long the screen must stay continuously off/locked before the microphone starts
     * listening. */
    val armDelayMinutes: Int = 15,
)

@Serializable
data class AppDefaultsRoot(
    val global: GlobalDefaults = GlobalDefaults(),
    val notifications: NotificationDefaults = NotificationDefaults(),
    val backgroundUpdates: BackgroundUpdatesDefaults = BackgroundUpdatesDefaults(),
    val appearance: AppearanceDefaults = AppearanceDefaults(),
    val weatherSources: WeatherSourcesDefaults = WeatherSourcesDefaults(),
    val wallpaper: WallpaperDefaults = WallpaperDefaults(),
    val widget: WidgetDefaults = WidgetDefaults(),
    val debug: DebugDefaults = DebugDefaults(),
    val findMyPhone: FindMyPhoneDefaults = FindMyPhoneDefaults(),
)

/**
 * Central registry of factory-default setting values, sourced from
 * `assets/default_settings.json` so they can be tuned without touching Kotlin code. Parsed once
 * and cached; falls back to the [AppDefaultsRoot] constructor defaults above if the asset is
 * missing or malformed, so a bad JSON edit can never crash the app.
 */
object AppDefaults {

    private val json = Json { ignoreUnknownKeys = true }

    private val root: AppDefaultsRoot by lazy {
        runCatching {
            BreezyWeather.instance.assets.open("default_settings.json").use { stream ->
                json.decodeFromString(AppDefaultsRoot.serializer(), stream.bufferedReader().readText())
            }
        }.getOrElse { AppDefaultsRoot() }
    }

    val global: GlobalDefaults get() = root.global
    val notifications: NotificationDefaults get() = root.notifications
    val backgroundUpdates: BackgroundUpdatesDefaults get() = root.backgroundUpdates
    val appearance: AppearanceDefaults get() = root.appearance
    val weatherSources: WeatherSourcesDefaults get() = root.weatherSources
    val wallpaper: WallpaperDefaults get() = root.wallpaper
    val widget: WidgetDefaults get() = root.widget
    val debug: DebugDefaults get() = root.debug
    val findMyPhone: FindMyPhoneDefaults get() = root.findMyPhone
}
