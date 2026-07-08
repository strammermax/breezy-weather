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
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.formatMeasure
import com.liveweatherwallpaperapp.common.extensions.getVisibilityDescription
import com.liveweatherwallpaperapp.common.options.appearance.DetailScreen
import com.liveweatherwallpaperapp.common.utils.UnitUtils
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import com.liveweatherwallpaperapp.unit.formatting.UnitWidth
import livewallpaperweather.domain.location.model.Location

class VisibilityViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_visibility, parent, false)
) {
    private val visibilityValueView: TextView = itemView.findViewById(R.id.visibility_value)
    private val visibilityDescriptionView: TextView = itemView.findViewById(R.id.visibility_description)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val talkBackBuilder = StringBuilder(context.getString(R.string.visibility))

        location.weather!!.current?.visibility?.let { visibility ->
            visibilityValueView.text = UnitUtils.formatUnitsHalfSize(
                visibility.formatMeasure(context)
            )
            visibilityDescriptionView.text = visibility.getVisibilityDescription(context)

            talkBackBuilder.append(context.getString(R.string.colon_separator))
            talkBackBuilder.append(visibility.formatMeasure(context, unitWidth = UnitWidth.LONG))
            talkBackBuilder.append(context.getString(com.liveweatherwallpaperapp.unit.R.string.locale_separator))
            talkBackBuilder.append(visibilityValueView.text)
        }

        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_VISIBILITY
            )
        }
    }
}
