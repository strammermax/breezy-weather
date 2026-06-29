/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.radar

import android.annotation.SuppressLint
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Locale

internal object RadarWebMapLoader {

    @SuppressLint("SetJavaScriptEnabled")
    fun loadBuienradar(webView: WebView, latitude: Double? = null, longitude: Double? = null) {
        configure(webView)
        val url = if (latitude != null && longitude != null) {
            String.format(Locale.US, "%s?lat=%.4f&lon=%.4f", BUIENRADAR_WIDGET_URL, latitude, longitude)
        } else {
            BUIENRADAR_WIDGET_URL
        }
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun loadMeteoblue(webView: WebView, latitude: Double? = null, longitude: Double? = null) {
        configure(webView)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, true, false)
            }
        }
        // The widget's own "geoloc=detect" centers on the device's IP-based location, which
        // ignores the app's selected location entirely. The page initializes mapbox-gl with
        // `hash: 'coords'`, a *named* hash param, so the URL must be "#coords=zoom/lat/lng"
        // (not the bare "#zoom/lat/lng" mapbox-gl uses when `hash: true`) to re-center it.
        val url = if (latitude != null && longitude != null) {
            String.format(Locale.US, "%s#coords=9/%.4f/%.4f", METEOBLUE_WIDGET_URL, latitude, longitude)
        } else {
            METEOBLUE_WIDGET_URL
        }
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
    }

    private const val BUIENRADAR_WIDGET_URL = "https://www.buienradar.nl/nederland/neerslag/buienradar"

    private const val METEOBLUE_WIDGET_URL = "https://www.meteoblue.com/nl/weer/kaarten/widget/" +
        "?windAnimation=1&gust=1&satellite=1&cloudsAndPrecipitation=1&temperature=1" +
        "&sunshine=1&extremeForecastIndex=1&geoloc=detect&tempunit=C&lengthunit=metric" +
        "&windunit=km%2Fh&zoom=9&autowidth=auto&user_key=0a5701c1a618c300" +
        "&embed_key=b96976639e93ec69" +
        "&sig=f1a56965f0827f57cd89becfe1203c2d8490581410f23cd74a415ac95d423cbf"
}
