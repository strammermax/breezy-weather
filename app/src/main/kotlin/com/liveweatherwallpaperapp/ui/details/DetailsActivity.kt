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

package com.liveweatherwallpaperapp.ui.details

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.isBackgroundAnimationEnabled
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.wallpaper.WallpaperEffectView
import com.liveweatherwallpaperapp.wallpaper.toFrostedBackground
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetailsActivity : BreezyActivity() {

    private lateinit var effectView: WallpaperEffectView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No placeholder background here: the window is translucent (see
        // BreezyWeatherTheme.Details), so the previous screen (the live wallpaper behind
        // MainActivity) stays visible on its own until DetailsScreen's snapshot render is
        // ready and crossfades the real background in.
        setContent {
            DailyWeatherScreen(onBackPressed = { finish() })
        }

        // Animated weather effects (clouds, rain, snow, fog …) via the same
        // WallpaperWeatherEffectRenderer used by the live wallpaper. Sits between
        // the static WallpaperSceneSnapshot backdrop and the transparent Compose
        // scaffold. Hardware acceleration is required for the AGSL shader to work.
        effectView = WallpaperEffectView(this)
        SettingsManager.getInstance(this).let {
            effectView.setFrosted(it.appBackgroundFrosted, it.appBackgroundFrostStrength, it.appBackgroundFrostTint)
        }

        val contentFrame = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        contentFrame.addView(
            effectView,
            0,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val viewModel = ViewModelProvider(this)[DetailsViewModel::class.java]
        lifecycleScope.launch {
            viewModel.backgroundState.collect { (weatherKind, isDaylight) ->
                effectView.setWeather(weatherKind, if (isDaylight) 1f else 0f)
                effectView.setDrawable(isBackgroundAnimationEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::effectView.isInitialized) effectView.setDrawable(isBackgroundAnimationEnabled)
    }

    override fun onPause() {
        super.onPause()
        if (::effectView.isInitialized) effectView.setDrawable(false)
    }

    /** Lets [DetailsScreen]'s snapshot renderer hand the near/foreground photo layer to [effectView]. */
    fun setForegroundPhoto(bitmap: android.graphics.Bitmap?, greyscaleAmount: Float) {
        if (::effectView.isInitialized) {
            val frosted = SettingsManager.getInstance(this).appBackgroundFrosted
            val settings = SettingsManager.getInstance(this)
            effectView.setFrosted(frosted, settings.appBackgroundFrostStrength, settings.appBackgroundFrostTint)
            effectView.setForegroundPhoto(
                if (frosted) bitmap?.toFrostedBackground(settings.appBackgroundFrostStrength) else bitmap,
                greyscaleAmount
            )
        }
    }

    companion object {
        const val KEY_FORMATTED_LOCATION_ID = "FORMATTED_LOCATION_ID"
        const val KEY_CURRENT_DAILY_INDEX = "CURRENT_DAILY_INDEX"
        const val KEY_CURRENT_PAGE = "CURRENT_PAGE"
    }
}
