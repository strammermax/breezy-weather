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

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.isBackgroundAnimationEnabled
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.wallpaper.WallpaperEffectView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetailsActivity : BreezyActivity() {

    private lateinit var effectView: WallpaperEffectView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // overridePendingTransition is deprecated and, on Android 14+, silently ignored in
        // favor of the platform/OEM default (e.g. Samsung's slide) -- overrideActivityTransition
        // is the replacement that's actually honored there. Both directions are set up front
        // (not just OPEN here / CLOSE in finish() like the old API needed).
        //
        // Main fades out fast (R.anim.fade_out_fast) so it's out of the way well before
        // Details' own slower fade-in (R.anim.fade_in_slow) finishes covering it -- equal-
        // duration fades made everything above the shared translucent background flash
        // invisible for a beat, since Details starts with nothing drawn yet.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in_slow, R.anim.fade_out_fast)
            // Closing: Main was visible underneath the whole time (translucent window), so it
            // needs no animation of its own -- only Details fades out on its way off screen.
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.fade_out_fast)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in_slow, R.anim.fade_out_fast)
        }

        // No placeholder background here: the window is translucent (see
        // BreezyWeatherTheme.Details), so the previous screen (the live wallpaper behind
        // MainActivity) stays visible on its own until DetailsScreen's snapshot render is
        // ready and crossfades the real background in.
        setContent {
            DailyWeatherScreen(onBackPressed = { finish() })
        }

        // Temporary performance test: keep every decorative details background layer off.
        if (!DETAILS_BACKGROUND_ENABLED) {
            window.setBackgroundDrawable(ColorDrawable(Color.rgb(24, 27, 34)))
            return
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
            // Let the activity enter transition and the first Compose layout finish before
            // constructing and animating the weather renderer. Initialising it during the
            // transition caused 350-500 ms frames on the UI thread.
            delay(EFFECT_START_DELAY_MILLIS)
            viewModel.backgroundState.collect { (weatherKind, isDaylight) ->
                effectView.setWeatherAsync(weatherKind, if (isDaylight) 1f else 0f)
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

    override fun finish() {
        super.finish()
        // Pre-34: overrideActivityTransition doesn't exist, so the close transition still has
        // to be (re-)applied right at finish() like the deprecated API always required.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.fade_out_fast)
        }
    }

    /** Lets [DetailsScreen]'s snapshot renderer hand the near/foreground photo layer to [effectView]. */
    fun setForegroundPhoto(bitmap: android.graphics.Bitmap?, greyscaleAmount: Float) {
        if (::effectView.isInitialized) {
            val frosted = SettingsManager.getInstance(this).appBackgroundFrosted
            val settings = SettingsManager.getInstance(this)
            effectView.setFrosted(frosted, settings.appBackgroundFrostStrength, settings.appBackgroundFrostTint)
            // Bitmap processing is done by DetailsScreen on Dispatchers.Default. This method
            // only assigns the prepared bitmap, so it never blocks a frame with scaling work.
            effectView.setForegroundPhoto(bitmap, greyscaleAmount)
        }
    }

    companion object {
        const val DETAILS_BACKGROUND_ENABLED = true
        private const val EFFECT_START_DELAY_MILLIS = 1_500L
        const val KEY_FORMATTED_LOCATION_ID = "FORMATTED_LOCATION_ID"
        const val KEY_CURRENT_DAILY_INDEX = "CURRENT_DAILY_INDEX"
        const val KEY_CURRENT_PAGE = "CURRENT_PAGE"
    }
}
