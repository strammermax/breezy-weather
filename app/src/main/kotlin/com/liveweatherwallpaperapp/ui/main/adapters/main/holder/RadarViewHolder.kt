/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.ui.main.adapters.main.holder

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.radar.RadarActivity
import com.liveweatherwallpaperapp.radar.RadarWebMapLoader
import com.liveweatherwallpaperapp.radar.RainViewerMap
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import livewallpaperweather.domain.location.model.Location

class RadarViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_radar, parent, false)
) {
    private val radarMap: WebView = itemView.findViewById(R.id.radar_map)

    @SuppressLint("ClickableViewAccessibility") // WebView cannot override performClick; its click listener delegates explicitly.
    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        radarMap.onResume()
        val settings = SettingsManager.getInstance(context)
        when (settings.radarTileSource) {
            "buienradar" -> RadarWebMapLoader.loadBuienradar(radarMap)
            "ventusky" ->
                RadarWebMapLoader.loadVentusky(radarMap, location.latitude, location.longitude, compact = true)
            else -> {
                val dark = when (settings.radarTileMapStyle) {
                    "dark" -> true
                    "light" -> false
                    else ->
                        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                            Configuration.UI_MODE_NIGHT_YES
                }
                RainViewerMap.load(radarMap, location.latitude, location.longitude, dark, compact = true)
            }
        }

        itemView.contentDescription = context.getString(R.string.action_radar)
        itemView.setOnClickListener {
            // Buienradar selected: go straight to the app/website, same as tapping the map
            // inside the full radar screen's Buienradar tab -- see RadarActivity.BuienradarMap.
            if (settings.radarTileSource == "buienradar") {
                RadarWebMapLoader.openBuienradarAppOrWebsite(context)
            } else {
                context.startActivity(RadarActivity.createIntent(context, location, settings.radarTileSource))
            }
        }
        radarMap.setOnClickListener { itemView.performClick() }
        radarMap.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) radarMap.performClick()
            true
        }
    }

    override fun onRecycleView() {
        super.onRecycleView()
        radarMap.stopLoading()
        radarMap.onPause()
        radarMap.loadUrl("about:blank")
    }
}
