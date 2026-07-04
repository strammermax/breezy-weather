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
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import livewallpaperweather.domain.location.model.Location
import com.livewallpaperweather.R
import com.livewallpaperweather.common.activities.BreezyActivity
import com.livewallpaperweather.common.extensions.currentLocale
import com.livewallpaperweather.common.options.appearance.DetailScreen
import com.livewallpaperweather.common.utils.helpers.IntentHelper
import com.livewallpaperweather.domain.weather.model.getContentDescription
import com.livewallpaperweather.domain.weather.model.getLevel
import com.livewallpaperweather.domain.weather.model.getShape
import com.livewallpaperweather.ui.theme.resource.providers.ResourceProvider
import com.livewallpaperweather.unit.formatting.format
import kotlin.math.roundToInt

class UvViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_uv, parent, false)
) {
    private val sunShapeView: ImageView = itemView.findViewById(R.id.sun_shape)
    private val uvIndexValueView: TextView = itemView.findViewById(R.id.uv_index_value)
    private val uvIndexLevelView: TextView = itemView.findViewById(R.id.uv_index_level)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val talkBackBuilder = StringBuilder(context.getString(R.string.uv_index))
        sunShapeView.setImageDrawable(
            AppCompatResources.getDrawable(
                context,
                location.weather!!.current?.uV?.getShape() ?: R.drawable.uv_unknown
            )
        )
        uvIndexValueView.text = location.weather!!.current?.uV?.index?.roundToInt()
            ?.format(decimals = 0, locale = context.currentLocale)
            ?: "-"
        uvIndexLevelView.text = location.weather!!.current?.uV?.getLevel(context) ?: "-"

        location.weather!!.current?.uV?.getContentDescription(context)?.let {
            talkBackBuilder.append(context.getString(R.string.colon_separator))
            talkBackBuilder.append(it)
        }

        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_UV_INDEX
            )
        }
    }
}
