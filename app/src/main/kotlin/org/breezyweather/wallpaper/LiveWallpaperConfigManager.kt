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

package org.breezyweather.wallpaper

import android.content.Context
import org.breezyweather.domain.settings.ConfigStore

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

    /** ACT-012: experimental seasonal colour/light grading, off by default. */
    val seasonGradingEnabled: Boolean

    /** ACT-012: user-chosen strength in 0f..1f, scaled down by [WallpaperSeasonGrading.MAX_SEASON_GRADING_STRENGTH]. */
    val seasonGradingStrength: Float

    /**
     * Experimental: render clouds via the `:cloud-engine` module (the wolkentypes prototype's
     * PNG-sprite clouds) instead of the built-in AGSL/Canvas cloud renderer. Off by default;
     * the built-in renderer is unaffected either way.
     */
    val newCloudsEnabled: Boolean

    /**
     * Debug-only tuning (see [org.breezyweather.wallpaper.LiveWallpaperConfigActivity]'s
     * `BuildConfig.DEBUG`-gated section): extra multipliers layered on top of
     * [CloudEngineAdapter]'s weather-derived values, both 1f (neutral) by default.
     */
    val newCloudsWindMultiplier: Float
    val newCloudsDensityMultiplier: Float

    init {
        val config = ConfigStore(context, SP_LIVE_WALLPAPER_CONFIG)
        weatherKind = normalizeWallpaperWeatherType(config.getString(KEY_WEATHER_KIND, null) ?: "auto")
        dayNightType = config.getString(KEY_DAY_NIGHT_TYPE, null) ?: "auto"
        animationsEnabled = config.getBoolean(KEY_ANIMATIONS_ENABLED, false)
        parallaxEnabled = config.getBoolean(KEY_PARALLAX_ENABLED, false)
        qualityProfile = WallpaperQualityProfileFactory.fromName(config.getString(KEY_QUALITY_PROFILE, null))
        seasonGradingEnabled = config.getBoolean(KEY_SEASON_GRADING_ENABLED, false)
        seasonGradingStrength = config.getFloat(KEY_SEASON_GRADING_STRENGTH, DEFAULT_SEASON_GRADING_STRENGTH)
            .coerceIn(0f, 1f)
        newCloudsEnabled = config.getBoolean(KEY_NEW_CLOUDS_ENABLED, false)
        newCloudsWindMultiplier = config.getFloat(KEY_NEW_CLOUDS_WIND_MULTIPLIER, 1f).coerceIn(0.1f, 4f)
        newCloudsDensityMultiplier = config.getFloat(KEY_NEW_CLOUDS_DENSITY_MULTIPLIER, 1f).coerceIn(0.1f, 3f)
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
        private const val DEFAULT_SEASON_GRADING_STRENGTH = 0.5f
        private const val KEY_NEW_CLOUDS_ENABLED = "new_clouds_enabled"
        private const val KEY_NEW_CLOUDS_WIND_MULTIPLIER = "new_clouds_wind_multiplier"
        private const val KEY_NEW_CLOUDS_DENSITY_MULTIPLIER = "new_clouds_density_multiplier"

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
            newCloudsWindMultiplier: Float? = null,
            newCloudsDensityMultiplier: Float? = null,
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
            if (newCloudsWindMultiplier != null) {
                editor.putFloat(KEY_NEW_CLOUDS_WIND_MULTIPLIER, newCloudsWindMultiplier.coerceIn(0.1f, 4f))
            }
            if (newCloudsDensityMultiplier != null) {
                editor.putFloat(KEY_NEW_CLOUDS_DENSITY_MULTIPLIER, newCloudsDensityMultiplier.coerceIn(0.1f, 3f))
            }
            editor.apply()
        }
    }
}
