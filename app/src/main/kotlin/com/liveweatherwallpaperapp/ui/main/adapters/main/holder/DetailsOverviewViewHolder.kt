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
import androidx.compose.ui.platform.ComposeView
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.domain.weather.model.validAirQuality
import com.liveweatherwallpaperapp.ui.details.components.DetailsOverviewGrid
import com.liveweatherwallpaperapp.ui.details.components.DetailsOverviewSource
import com.liveweatherwallpaperapp.ui.theme.ThemeManager
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import livewallpaperweather.domain.location.model.Location

class DetailsOverviewViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_details_overview, parent, false)
) {
    private val composeView: ComposeView = itemView.findViewById(R.id.details_overview_compose_view)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        composeView.setContent {
            BreezyWeatherTheme(!ThemeManager.isLightTheme(context, location)) {
                DetailsOverviewGrid(
                    location = location,
                    source = DetailsOverviewSource.ofCurrent(
                        location.weather?.current,
                        location.weather?.nextHourlyForecast?.firstOrNull()
                    ),
                    daily = location.weather?.today,
                    overallAirQuality = location.weather?.validAirQuality,
                    onNavigate = { chart ->
                        IntentHelper.startDailyWeatherActivity(
                            context as BreezyActivity,
                            location.formattedId,
                            location.weather?.todayIndex,
                            chart
                        )
                    }
                )
            }
        }
    }
}
