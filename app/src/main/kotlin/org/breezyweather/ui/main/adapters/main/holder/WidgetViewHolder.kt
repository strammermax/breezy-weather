/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.ui.main.adapters.main.holder

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.options.appearance.WidgetTileType
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.remoteviews.presenters.ClockDayHorizontalWidgetIMP
import org.breezyweather.remoteviews.presenters.ClockDayVerticalWidgetIMP
import org.breezyweather.remoteviews.presenters.DayWidgetIMP
import org.breezyweather.remoteviews.presenters.WeekWidgetIMP
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

class WidgetViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_widget, parent, false)
) {
    private val widgetContainer: FrameLayout =
        itemView.findViewById(R.id.widget_tile_container)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        widgetContainer.removeAllViews()
        val ctx = widgetContainer.context
        val widgetType = SettingsManager.getInstance(ctx).widgetTileType
        try {
            val views = when (widgetType) {
                WidgetTileType.DAY -> DayWidgetIMP.getRemoteViews(
                    ctx, location,
                    viewStyle = "symmetry",
                    cardStyle = "none",
                    cardAlpha = 0,
                    textColor = "light",
                    textSize = 0,
                    hideSubtitle = false,
                    subtitleData = "weather",
                    pollenIndexSource = null,
                )
                WidgetTileType.CLOCK_DAY_VERTICAL -> ClockDayVerticalWidgetIMP.getRemoteViews(
                    ctx, location,
                    viewStyle = "vertical",
                    cardStyle = "none",
                    cardAlpha = 0,
                    textColor = "light",
                    textSize = 0,
                    hideSubtitle = false,
                    subtitleData = "weather",
                    clockFont = null,
                    pollenIndexSource = null,
                )
                WidgetTileType.CLOCK_DAY_HORIZONTAL -> ClockDayHorizontalWidgetIMP.getRemoteViews(
                    ctx, location,
                    cardStyle = "none",
                    cardAlpha = 0,
                    textColor = "light",
                    textSize = 0,
                    clockFont = null,
                    hideAlternateCalendar = true,
                )
                WidgetTileType.WEEK -> WeekWidgetIMP.getRemoteViews(
                    ctx, location,
                    viewStyle = "5_days",
                    cardStyle = "none",
                    cardAlpha = 0,
                    textColor = "light",
                    textSize = 0,
                )
            }
            widgetContainer.addView(views.apply(ctx, widgetContainer))
        } catch (_: Exception) {
            // Widget RemoteViews failed to apply; leave container empty.
        }
    }
}
