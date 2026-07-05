/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.radar

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
        configureMeteoblue(webView)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, true, false)
            }
        }
        // The maps app (not the limited widget embed) initializes mapbox-gl with `hash: 'coords'`,
        // a *named* hash param, so the URL must be "#coords=zoom/lat/lng&map=..." (not the bare
        // "#zoom/lat/lng" mapbox-gl uses when `hash: true`) to re-center it and pick the layers.
        // The "weil-am-rhein_..." style place slug in the path is cosmetic/SEO-only — the page
        // loads fine without it and the hash always wins for the displayed location and layers.
        val url = if (latitude != null && longitude != null) {
            String.format(Locale.US, "%s#coords=9/%.4f/%.4f&map=%s", METEOBLUE_MAPS_URL, latitude, longitude, METEOBLUE_MAP_LAYERS)
        } else {
            "$METEOBLUE_MAPS_URL#map=$METEOBLUE_MAP_LAYERS"
        }
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureMeteoblue(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // The page ignores the map= hash on initial load and defaults to Wind Animation.
                // Inject JS to programmatically select Weather Radar > EU (+2h Forecast).
                view.evaluateJavascript("""
                    (function() {
                        function clickText(text, root) {
                            var els = (root || document).querySelectorAll('li, a, div, span');
                            for (var i = 0; i < els.length; i++) {
                                if (els[i].childElementCount === 0 && els[i].textContent.trim() === text) {
                                    els[i].click(); return true;
                                }
                            }
                            return false;
                        }
                        if (!clickText('Weerradar')) clickText('Weather Radar');
                        setTimeout(function() {
                            if (!clickText('EU (+2u prognose)')) clickText('EU (+2h Forecast)');
                        }, 400);
                    })();
                """.trimIndent(), null)
            }
        }
    }

    private const val BUIENRADAR_WIDGET_URL = "https://www.buienradar.nl/nederland/neerslag/buienradar"

    private const val METEOBLUE_MAPS_URL = "https://www.meteoblue.com/nl/weer/kaarten/"

    // radar~radarMapEU~none~none~pressure2mOverlay: EU radar layer with a sea-level pressure overlay.
    private const val METEOBLUE_MAP_LAYERS = "radar~radarMapEU~none~none~pressure2mOverlay"
}
