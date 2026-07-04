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

package com.livewallpaperweather.ui.main.adapters.main.holder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import livewallpaperweather.domain.location.model.Location
import com.livewallpaperweather.R
import com.livewallpaperweather.common.activities.BreezyActivity
import com.livewallpaperweather.common.extensions.formatMeasure
import com.livewallpaperweather.common.options.appearance.DetailScreen
import com.livewallpaperweather.common.utils.UnitUtils
import com.livewallpaperweather.common.utils.helpers.IntentHelper
import com.livewallpaperweather.domain.weather.model.getContentDescription
import com.livewallpaperweather.domain.weather.model.getDirection
import com.livewallpaperweather.ui.theme.resource.providers.ResourceProvider
import com.livewallpaperweather.unit.formatting.UnitWidth

class WindViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_wind, parent, false)
) {
    private val windDirectionView: ImageView = itemView.findViewById(R.id.wind_direction)
    private val windSpeedValueView: TextView = itemView.findViewById(R.id.wind_speed_value)
    private val windDetailView: TextView = itemView.findViewById(R.id.visibility_description)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val talkBackBuilder = StringBuilder(context.getString(R.string.wind))

        location.weather?.current?.wind?.let { wind ->
            talkBackBuilder.append(context.getString(R.string.colon_separator))
            talkBackBuilder.append(wind.getContentDescription(context))

            wind.speed?.let { speed ->
                windSpeedValueView.text = UnitUtils.formatUnitsHalfSize(
                    speed.formatMeasure(context, valueWidth = UnitWidth.NARROW)
                )
            }

            wind.degree?.let { degree ->
                if (degree != -1.0) {
                    windDirectionView.visibility = View.VISIBLE
                    windDirectionView.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.wind_arrow)
                    )
                    windDirectionView.rotation = degree.toFloat()
                } else {
                    windDirectionView.visibility = View.VISIBLE
                    windDirectionView.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.wind_variable)
                    )
                }
            } ?: run {
                windDirectionView.visibility = View.GONE
            }

            windDetailView.text = if (wind.speed != null && wind.gusts != null && wind.gusts!! > wind.speed!!) {
                context.getString(R.string.wind_gusts_short) +
                    context.getString(R.string.colon_separator) +
                    wind.gusts!!.formatMeasure(context, valueWidth = UnitWidth.NARROW)
            } else {
                wind.getDirection(context, short = true)?.let {
                    if (wind.degree!! in 0.0..360.0) {
                        context.getString(R.string.wind_origin, it)
                    } else {
                        it
                    }
                } ?: ""
            }
        }

        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_WIND
            )
        }
    }
}
