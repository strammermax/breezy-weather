/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.radar

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Locale

internal object RainViewerMap {

    @SuppressLint("SetJavaScriptEnabled")
    fun load(
        webView: WebView,
        latitude: Double,
        longitude: Double,
        dark: Boolean,
        compact: Boolean,
    ) {
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadDataWithBaseURL(
            "https://www.rainviewer.com",
            html(latitude, longitude, dark, compact),
            "text/html",
            "UTF-8",
            null
        )
    }

    fun html(latitude: Double, longitude: Double, dark: Boolean, compact: Boolean): String {
        val lat = String.format(Locale.US, "%.5f", latitude)
        val lon = String.format(Locale.US, "%.5f", longitude)
        val style = if (dark) "dark_all" else "light_all"
        val pageBg = if (dark) "#0e1116" else "#e8eaed"
        val pillBg = if (dark) "rgba(0,0,0,.65)" else "rgba(255,255,255,.85)"
        val pillFg = if (dark) "#fff" else "#222"
        val mapHeight = if (compact) "100vh" else "420px"
        val controls = if (compact) "false" else "true"
        val interactions = if (compact) {
            "dragging:false,scrollWheelZoom:false,doubleClickZoom:false,touchZoom:false,keyboard:false,boxZoom:false,"
        } else {
            ""
        }
        val animation = "setInterval(function(){ idx=(idx+1)%frames.length; show(idx); },700);"
        return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
 html,body{margin:0;padding:0;background:$pageBg;overflow:hidden}
 #map{width:100%;height:$mapHeight}
 .ts{position:absolute;top:8px;left:50%;transform:translateX(-50%);z-index:1000;
     background:$pillBg;color:$pillFg;padding:4px 12px;border-radius:14px;
     font:13px/1.4 sans-serif;white-space:nowrap}
</style></head><body>
<div id="map"></div><div class="ts" id="ts">...</div>
<script>
 var lat=$lat, lon=$lon;
 var map=L.map('map',{$interactions zoomControl:$controls,attributionControl:false}).setView([lat,lon],7);
 L.tileLayer('https://{s}.basemaps.cartocdn.com/$style/{z}/{x}/{y}.png',{subdomains:'abcd',maxZoom:12}).addTo(map);
 L.circleMarker([lat,lon],{radius:5,color:'#fff',weight:2,fillColor:'#2196f3',fillOpacity:1}).addTo(map);
 function fixSize(){ try{ map.invalidateSize(true); }catch(e){} }
 map.whenReady(function(){ setTimeout(fixSize,150); });
 window.addEventListener('load',fixSize);
 [150,400,800,1500].forEach(function(t){setTimeout(fixSize,t);});
 var frames=[],layers={},idx=0,host='',tsEl=document.getElementById('ts');
 function layerFor(f){
   if(!layers[f.path]){
     layers[f.path]=L.tileLayer(host+f.path+'/256/{z}/{x}/{y}/2/1_1.png',{opacity:0,zIndex:Math.round(f.time/1000),maxNativeZoom:7,maxZoom:12});
     layers[f.path].addTo(map);
   }
 }
 function show(i){
   for(var j=0;j<frames.length;j++){var l=layers[frames[j].path];if(l)l.setOpacity(j===i?0.75:0);}
   var d=new Date(frames[i].time*1000);
   tsEl.textContent=(frames[i].nowcast?'>> ':'')+d.toLocaleTimeString();
 }
 fetch('https://api.rainviewer.com/public/weather-maps.json').then(function(r){return r.json();}).then(function(d){
   host=d.host;
   var past=(d.radar&&d.radar.past)||[],now=(d.radar&&d.radar.nowcast)||[];
   now.forEach(function(f){f.nowcast=true;});
   frames=past.concat(now);
   if(!frames.length)throw new Error('no frames');
   frames.forEach(layerFor);
   idx=Math.max(0,past.length-1);show(idx);
   $animation
 }).catch(function(){tsEl.textContent='radar n/a';});
</script></body></html>
        """.trimIndent()
    }
}
