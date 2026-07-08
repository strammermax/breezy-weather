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

package com.liveweatherwallpaperapp.ui.main.adapters.main.holder

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.liveweatherwallpaperapp.BreezyWeather
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.domain.weather.model.getTemperatureRangeSummary
import com.liveweatherwallpaperapp.ui.common.widgets.NumberAnimTextView
import com.liveweatherwallpaperapp.ui.main.widgets.TextRelativeClock
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import com.liveweatherwallpaperapp.unit.formatting.UnitWidth
import com.liveweatherwallpaperapp.unit.formatting.format
import livewallpaperweather.domain.location.model.Location
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class HeaderViewHolder(parent: ViewGroup) : AbstractMainViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_header, parent, false)
) {
    private val timezoneText: TextView = itemView.findViewById(R.id.container_main_header_timezone)
    private val refreshTimeText: TextRelativeClock = itemView.findViewById(R.id.refreshTimeText)
    private val mTemperatureContainer: RelativeLayout = itemView.findViewById(R.id.container_main_header_temperature)
    private val mTemperature: NumberAnimTextView = itemView.findViewById(R.id.container_main_header_temperature_value)
    private val mTemperatureUnitView: TextView = itemView.findViewById(R.id.container_main_header_temperature_unit)
    private val mWeatherText: TextView = itemView.findViewById(R.id.container_main_header_weather_condition_description)
    private val mFeelsLike: TextView = itemView.findViewById(R.id.container_main_header_feels_like)
    private val mTemperatureRange: TextView = itemView.findViewById(R.id.container_main_header_temperature_range)
    private var mTemperatureFrom = 0
    private var mTemperatureTo = 0

    @SuppressLint("SetTextI18n")
    override fun onBindView(
        context: Context,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(context, location, provider, listAnimationEnabled, itemAnimationEnabled)

        if (BreezyWeather.instance.debugMode) {
            timezoneText.visibility = View.VISIBLE
            timezoneText.text = arrayOf(location.countryCode, location.timeZone.id).joinToString(
                context.getString(R.string.dot_separator)
            )
        }

        location.weather?.base?.refreshTime?.let {
            refreshTimeText.visibility = View.VISIBLE
            refreshTimeText.setDate(it)
        } ?: run {
            refreshTimeText.visibility = View.GONE
        }

        val temperatureUnit = SettingsManager.getInstance(context).getTemperatureUnit(context)
        mWeatherText.visibility = View.GONE
        mTemperatureContainer.visibility = View.GONE
        mFeelsLike.visibility = View.GONE
        mTemperatureRange.visibility = View.GONE
    }

    override fun getEnterAnimator(pendingAnimatorList: List<Animator>): Animator {
        val a: Animator = ObjectAnimator.ofFloat(itemView, "alpha", 0f, 1f)
        a.duration = 300
        a.startDelay = 100
        a.interpolator = FastOutSlowInInterpolator()
        return a
    }

    @SuppressLint("DefaultLocale")
    override fun onEnterScreen() {
        super.onEnterScreen()
        mTemperature.setNumberString(
            mTemperatureFrom.format(decimals = 0, locale = context.currentLocale),
            mTemperatureTo.format(decimals = 0, locale = context.currentLocale)
        )
    }
}
