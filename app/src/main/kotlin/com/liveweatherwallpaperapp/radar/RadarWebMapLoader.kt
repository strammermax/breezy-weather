/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.radar

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import java.util.Locale

internal object RadarWebMapLoader {

    private var ventuskyWarmedUp = false

    /** Opens the official Buienradar app if installed, otherwise its precipitation-radar page.
     *  Shared by the full radar screen's Buienradar tab and the home-screen radar tile, so both
     *  behave the same when Buienradar is the selected source. */
    fun openBuienradarAppOrWebsite(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(BUIENRADAR_APP_PACKAGE)
        context.startActivity(
            launchIntent ?: Intent(Intent.ACTION_VIEW, BUIENRADAR_WEBSITE_URL.toUri())
        )
    }

    private const val BUIENRADAR_APP_PACKAGE = "com.supportware.Buienradar"
    private const val BUIENRADAR_WEBSITE_URL = "https://www.buienradar.nl/nederland/neerslag/buienradar"

    /**
     * Buienradar's interactive site (the previous approach here) is JS/canvas-heavy in the same
     * way Meteoblue was, and doesn't take a location — it always shows all of NL regardless — so
     * instead this displays the animated radar-loop GIF their own Home Assistant integration uses
     * (`image.buienradar.nl/2.0/image/animation/RadarMapRainNL`, `renderBranding=False` to skip
     * their logo overlay), navigating straight to the image URL rather than embedding it in our own
     * HTML: WebView (like any browser) renders a bare image URL as its own auto-playing document,
     * and wrapping it in an `<img>` inside `loadDataWithBaseURL` HTML — which works fine for
     * [loadVentusky]'s text/markup content — left this one blank, apparently a WebView quirk
     * specific to image-only pages.
     *
     * Buienradar also publishes an official Leaflet-based `<iframe>` embed widget, driven by a
     * lat/lon/zoom query string, at gadgets.buienradar.nl/gadget/zoommap/ (see
     * buienradar.nl/overbuienradar/gratis-weerdata) — that would restore per-location centering
     * this GIF doesn't have, and its page does load (confirmed via WebViewClient callbacks: jQuery
     * present, readyState complete, no request errors), but its Leaflet map never actually paints
     * even after forcing a `window resize` event once our own CSS gives its container real size.
     * Not investigated further; left for another attempt.
     */
    fun loadBuienradar(webView: WebView) {
        configure(webView)
        webView.loadUrl(BUIENRADAR_ANIMATION_URL)
    }

    /**
     * Ventusky's "how to embed" guide describes an `embed.ventusky.com` subdomain driven by URL
     * params, but in practice it checks that it's actually running inside an iframe and, loaded
     * either directly or inside our own wrapper `<iframe>`, its map canvas fails to size itself
     * correctly (0×0, per its own console errors) — so instead this loads the regular site's radar
     * page directly, same as [loadBuienradar]. It already defaults to the radar layer, so it needs
     * only the `p` (position) param to center on the given location.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun loadVentusky(webView: WebView, latitude: Double? = null, longitude: Double? = null, compact: Boolean = false) {
        configureVentusky(webView, compact)
        val url = if (latitude != null && longitude != null) {
            String.format(
                Locale.US,
                "%s?p=%.4f;%.4f;%d",
                VENTUSKY_RADAR_URL,
                latitude,
                longitude,
                VENTUSKY_DEFAULT_ZOOM
            )
        } else {
            VENTUSKY_RADAR_URL
        }
        webView.loadUrl(url)
    }

    private fun configure(webView: WebView) {
        // Navigating straight to an image URL renders WebView's built-in bare-image viewer, which
        // without these two shows the GIF at native pixel size (cropped to the top-left corner of
        // the viewport) instead of scaled to fit it.
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.webViewClient = WebViewClient()
    }

    private const val BUIENRADAR_ANIMATION_URL =
        "https://image.buienradar.nl/2.0/image/animation/RadarMapRainNL" +
            "?width=700&height=700&renderBackground=True&renderBranding=False&renderText=True"

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureVentusky(webView: WebView, compact: Boolean) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.setSupportMultipleWindows(true)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(HIDE_VENTUSKY_CHROME_JS, null)
                if (compact) {
                    // The tile is small, so place/precipitation labels, webcam markers, the radar
                    // type badge and the date/time strip are just clutter there — full detail is
                    // still available in the full-screen radar view.
                    view.evaluateJavascript(HIDE_VENTUSKY_TILE_CLUTTER_JS, null)
                }
                // The very first WebView load of the app's lifetime (right after a cold start) can
                // leave Ventusky's own loading spinner stuck forever — its map data fetch appears to
                // race the OS network stack still warming up. A later reload always works (it's what
                // navigating to the full radar screen and back effectively does), so force exactly
                // one automatic reload the first time this loader is used, then leave it alone.
                if (!ventuskyWarmedUp) {
                    ventuskyWarmedUp = true
                    view.postDelayed({ view.reload() }, 4000)
                }
            }
        }
        // The webcam markers open their preview via window.open(), which a bare WebView silently
        // swallows (no visible effect on tap). Hand any such popup navigation to the external
        // browser instead of trying to render a second WebView inline.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val popup = WebView(view.context)
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        openExternally(v, request.url.toString())
                        return true
                    }
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    private fun openExternally(view: WebView, url: String) {
        runCatching {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private const val VENTUSKY_RADAR_URL = "https://www.ventusky.com/nl/weerradar-kaart"
    private const val VENTUSKY_DEFAULT_ZOOM = 7

    // Removing these nodes outright (el.remove()) broke the webcam markers' own click handler —
    // it reaches for something under #header/menu and throws ("Cannot read properties of null
    // (reading 'classList')"), silently aborting the click. A stylesheet rule keeps the nodes in
    // the DOM (so the site's own JS still finds them) while hiding them. It also survives the
    // premium banner's habit of resetting its own inline style="display: block": a stylesheet
    // !important rule always wins over a non-!important inline style, no matter how often that's
    // re-applied — inline el.style.display="none" (tried first) does not, because their reset
    // overwrites that same inline property outright.
    // #logo is the wordmark; menu holds both the search (li#g) and hamburger (li#x) buttons.
    private const val HIDE_VENTUSKY_CHROME_JS = """
        (function() {
            var style = document.createElement('style');
            style.textContent = '#header, #logo, menu, .la.premium { display: none !important; }';
            document.head.appendChild(style);
        })();
    """

    // #i: zoom/legend control, .aa.jz and .aa.rc: place/precipitation and webcam markers,
    // #r: the radar-type badge ("Weerradar / dBZ, EURAD"), #d: the date/time strip.
    private const val HIDE_VENTUSKY_TILE_CLUTTER_JS = """
        (function() {
            var style = document.createElement('style');
            style.textContent = '#i, .aa.jz, .aa.rc, #r, #d { display: none !important; }';
            document.head.appendChild(style);
        })();
    """
}
