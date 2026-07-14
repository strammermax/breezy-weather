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

package com.liveweatherwallpaperapp.wallpaper

import android.content.Context
import com.liveweatherwallpaperapp.domain.settings.AppDefaults
import com.liveweatherwallpaperapp.domain.settings.ConfigStore

internal const val WEATHER_TYPE_ROTATING_TEST = "200"
internal const val WEATHER_TYPE_DUTCH_CLOUDS = "201"

internal fun normalizeWallpaperWeatherType(value: String): String = when (value) {
    // Migrate values written by versions before the visual-only types received numeric IDs.
    "rotating" -> WEATHER_TYPE_ROTATING_TEST
    "HOLLANDSE_LUCHT" -> WEATHER_TYPE_DUTCH_CLOUDS
    else -> value
}

class LiveWallpaperConfigManager(context: Context) {
    val weatherKind: String
    val dayNightType: String
    val animationsEnabled: Boolean
    val parallaxEnabled: Boolean
    val qualityProfile: WallpaperQualityProfile

    /** ACT-012: seasonal colour/light grading, on by default. */
    val seasonGradingEnabled: Boolean

    /** ACT-012: user-chosen strength in 0f..1f, scaled down by [WallpaperSeasonGrading.MAX_SEASON_GRADING_STRENGTH]. */
    val seasonGradingStrength: Float

    /**
     * Renders clouds via the `:cloud-engine` module ("Real Clouds": the wolkentypes prototype's
     * PNG-sprite clouds) instead of the built-in AGSL/Canvas cloud renderer. On by default.
     */
    val newCloudsEnabled: Boolean

    init {
        val config = ConfigStore(context, SP_LIVE_WALLPAPER_CONFIG)
        weatherKind = normalizeWallpaperWeatherType(
            config.getString(KEY_WEATHER_KIND, null) ?: AppDefaults.wallpaper.weatherKind
        )
        dayNightType = config.getString(KEY_DAY_NIGHT_TYPE, null) ?: AppDefaults.wallpaper.dayNightType
        animationsEnabled = config.getBoolean(KEY_ANIMATIONS_ENABLED, AppDefaults.wallpaper.animationsEnabled)
        parallaxEnabled = config.getBoolean(KEY_PARALLAX_ENABLED, AppDefaults.wallpaper.parallaxEnabled)
        qualityProfile = WallpaperQualityProfileFactory.fromName(config.getString(KEY_QUALITY_PROFILE, null))
        seasonGradingEnabled = config.getBoolean(
            KEY_SEASON_GRADING_ENABLED,
            AppDefaults.wallpaper.seasonGradingEnabled
        )
        seasonGradingStrength = config.getFloat(
            KEY_SEASON_GRADING_STRENGTH,
            AppDefaults.wallpaper.seasonGradingStrength
        ).coerceIn(0f, 1f)
        newCloudsEnabled = config.getBoolean(KEY_NEW_CLOUDS_ENABLED, AppDefaults.wallpaper.newCloudsEnabled)
    }

    companion object {
        private const val SP_LIVE_WALLPAPER_CONFIG = "live_wallpaper_config"
        private const val KEY_WEATHER_KIND = "weather_kind"
        private const val KEY_DAY_NIGHT_TYPE = "day_night_type"
        private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
        private const val KEY_PARALLAX_ENABLED = "parallax_enabled"
        private const val KEY_QUALITY_PROFILE = "quality_profile"
        private const val KEY_SEASON_GRADING_ENABLED = "season_grading_enabled"
        private const val KEY_SEASON_GRADING_STRENGTH = "season_grading_strength"
        private const val KEY_NEW_CLOUDS_ENABLED = "new_clouds_enabled"

        fun update(
            context: Context,
            weatherKind: String?,
            dayNightType: String?,
            animationsEnabled: Boolean,
            parallaxEnabled: Boolean,
            qualityProfile: WallpaperQualityProfile? = null,
            seasonGradingEnabled: Boolean? = null,
            seasonGradingStrength: Float? = null,
            newCloudsEnabled: Boolean? = null,
        ) {
            val editor = ConfigStore(context, SP_LIVE_WALLPAPER_CONFIG)
                .edit()
                .putString(KEY_WEATHER_KIND, weatherKind)
                .putString(KEY_DAY_NIGHT_TYPE, dayNightType)
                .putBoolean(KEY_ANIMATIONS_ENABLED, animationsEnabled)
                .putBoolean(KEY_PARALLAX_ENABLED, parallaxEnabled)
            if (qualityProfile != null) {
                editor.putString(KEY_QUALITY_PROFILE, qualityProfile.name)
            }
            if (seasonGradingEnabled != null) {
                editor.putBoolean(KEY_SEASON_GRADING_ENABLED, seasonGradingEnabled)
            }
            if (seasonGradingStrength != null) {
                editor.putFloat(KEY_SEASON_GRADING_STRENGTH, seasonGradingStrength.coerceIn(0f, 1f))
            }
            if (newCloudsEnabled != null) {
                editor.putBoolean(KEY_NEW_CLOUDS_ENABLED, newCloudsEnabled)
            }
            editor.apply()
        }
    }
}
