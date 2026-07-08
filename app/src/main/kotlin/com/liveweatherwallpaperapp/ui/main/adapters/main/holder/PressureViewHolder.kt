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

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.common.extensions.formatValue
import com.liveweatherwallpaperapp.common.extensions.getThemeColor
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.ui.common.widgets.ArcProgress
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import com.liveweatherwallpaperapp.unit.formatting.UnitWidth
import livewallpaperweather.domain.location.model.Location

class PressureViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_pressure, parent, false)
) {
    private val pressureValueView: TextView = itemView.findViewById(R.id.pressure_value)
    private val pressureUnitView: TextView = itemView.findViewById(R.id.pressure_unit)
    private val pressureProgress: ArcProgress = itemView.findViewById(R.id.pressure_progress)
    private var mPressure = 963f
    private var mEnable = false

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val talkBackBuilder = StringBuilder(context.getString(R.string.pressure))
        location.weather!!.current?.pressure?.let {
            val pressureUnit = SettingsManager.getInstance(context).getPressureUnit(context)
            mPressure = it.inHectopascals.toFloat()
            mEnable = true
            pressureProgress.apply {
                progress = mPressure.minus(963f)
                pressureValueView.text = it.formatValue(context)
            }
            val pressureColor = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
            pressureProgress.apply {
                setProgressColor(pressureColor)
                setArcBackgroundColor(ColorUtils.setAlphaComponent(pressureColor, (255 * 0.1).toInt()))
                max = 100f
            }

            pressureUnitView.text = pressureUnit.getNominativeUnit(context)
            talkBackBuilder.append(context.getString(R.string.colon_separator))
            talkBackBuilder.append(it.formatMeasure(context, unitWidth = UnitWidth.LONG))
        }

        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_PRESSURE
            )
        }
    }
}
