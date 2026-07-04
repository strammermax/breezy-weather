/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.ui.main.adapters.main.holder

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import livewallpaperweather.domain.location.model.Location
import com.livewallpaperweather.R
import com.livewallpaperweather.common.activities.BreezyActivity
import com.livewallpaperweather.radar.RadarActivity
import com.livewallpaperweather.radar.RadarWebMapLoader
import com.livewallpaperweather.radar.RainViewerMap
import com.livewallpaperweather.domain.settings.SettingsManager
import com.livewallpaperweather.ui.theme.resource.providers.ResourceProvider

class RadarViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_radar, parent, false)
) {
    private val radarMap: WebView = itemView.findViewById(R.id.radar_map)

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
            "buienradar" -> RadarWebMapLoader.loadBuienradar(radarMap, location.latitude, location.longitude)
            "meteoblue" -> RadarWebMapLoader.loadMeteoblue(radarMap, location.latitude, location.longitude)
            else -> {
                val dark = when (settings.radarTileMapStyle) {
                    "dark" -> true
                    "light" -> false
                    else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
                }
                RainViewerMap.load(radarMap, location.latitude, location.longitude, dark, compact = true)
            }
        }

        itemView.contentDescription = context.getString(R.string.action_radar)
        itemView.setOnClickListener {
            context.startActivity(RadarActivity.createIntent(context, location, settings.radarTileSource))
        }
        radarMap.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) itemView.performClick()
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
