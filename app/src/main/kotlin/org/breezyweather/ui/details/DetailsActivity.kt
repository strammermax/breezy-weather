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

package org.breezyweather.ui.details

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.isDarkMode
import org.breezyweather.common.extensions.isBackgroundAnimationEnabled
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.weatherView.WeatherView

@AndroidEntryPoint
class DetailsActivity : BreezyActivity() {

    private lateinit var weatherView: WeatherView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Static sky-gradient + photo snapshot as the base background layer.
        window.setBackgroundDrawableResource(R.drawable.bg_glass_sky)

        setContent {
            DailyWeatherScreen(onBackPressed = { finish() })
        }

        // Animated weather layer (clouds, rain, snow …) sits between the static
        // background bitmap and the Compose content (transparent glass cards).
        weatherView = ThemeManager.getInstance(this).weatherThemeDelegate.getWeatherView(this)
        weatherView.setGravitySensorEnabled(
            SettingsManager.getInstance(this).isGravitySensorEnabled
        )
        weatherView.setDoAnimate(isBackgroundAnimationEnabled)

        val contentFrame = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        contentFrame.addView(
            weatherView as android.view.View,
            0,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val viewModel = ViewModelProvider(this)[DetailsViewModel::class.java]
        lifecycleScope.launch {
            viewModel.backgroundState.collect { (weatherKind, isDaylight) ->
                weatherView.setWeather(weatherKind, isDaylight, isDarkMode)
                clearWeatherViewBackground()
                weatherView.setDrawable(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::weatherView.isInitialized) weatherView.setDrawable(true)
    }

    override fun onPause() {
        super.onPause()
        if (::weatherView.isInitialized) weatherView.setDrawable(false)
    }

    // Strip the WeatherView's own opaque sky gradient so the static snapshot
    // (window background) and the photo show through underneath the animations.
    private fun clearWeatherViewBackground() {
        val vg = weatherView as? ViewGroup ?: return
        for (i in 0 until vg.childCount) vg.getChildAt(i).background = null
    }

    companion object {
        const val KEY_FORMATTED_LOCATION_ID = "FORMATTED_LOCATION_ID"
        const val KEY_CURRENT_DAILY_INDEX = "CURRENT_DAILY_INDEX"
        const val KEY_CURRENT_PAGE = "CURRENT_PAGE"
    }
}
