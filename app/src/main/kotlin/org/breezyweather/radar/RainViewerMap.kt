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
        val ctrlBg = if (dark) "rgba(14,17,22,.90)" else "rgba(240,242,245,.93)"
        val btnFg = if (dark) "#e0e0e0" else "#333"
        val mapControls = if (compact) "false" else "true"
        val interactions = if (compact) {
            "dragging:false,scrollWheelZoom:false,doubleClickZoom:false,touchZoom:false,keyboard:false,boxZoom:false,"
        } else {
            ""
        }
        // Compact: 100vh fills the card WebView. Full: 400px CSS fallback, JS overrides to actual height - ctrl.
        val mapHeight = if (compact) "100vh" else "400px"
        val setHeightJs = if (compact) "" else """
 function setMapH(){var h=window.innerHeight-54;if(h>100){document.getElementById('map').style.height=h+'px';fixSize();}}
 [0,150,400,900].forEach(function(t){setTimeout(setMapH,t)});
 window.addEventListener('resize',setMapH);"""
        // Full-mode only: control bar HTML + player functions.
        val ctrlHtml = if (compact) "" else """
<div class="ctrl">
  <button class="btn" onclick="stepFrame(-1)">&#9664;</button>
  <button class="btn" id="playBtn" onclick="togglePlay()">&#9654;</button>
  <button class="btn" onclick="stepFrame(1)">&#9646;&#9654;</button>
  <input type="range" id="scrubber" min="0" max="0" value="0"
         oninput="scrub(this.value)" style="flex:1;margin:0 6px;accent-color:#2196f3">
</div>"""
        val playerFunctions = if (compact) "" else """
 var playing=false,timer=null;
 function updatePlayBtn(){var b=document.getElementById('playBtn');if(b)b.innerHTML=playing?'&#9646;&#9646;':'&#9654;';}
 function startPlay(){
   if(timer)return; playing=true; updatePlayBtn();
   timer=setInterval(function(){
     idx=(idx+1)%frames.length; show(idx);
     var sc=document.getElementById('scrubber'); if(sc)sc.value=idx;
   },700);
 }
 function stopPlay(){if(timer){clearInterval(timer);timer=null;} playing=false; updatePlayBtn();}
 function togglePlay(){if(playing)stopPlay();else startPlay();}
 function stepFrame(dir){
   stopPlay(); idx=(idx+dir+frames.length)%frames.length; show(idx);
   var sc=document.getElementById('scrubber'); if(sc)sc.value=idx;
 }
 function scrub(val){stopPlay(); idx=parseInt(val); show(idx);}"""
        val afterLoad = if (compact) {
            "setInterval(function(){idx=(idx+1)%frames.length;show(idx);},700);"
        } else {
            "var sc=document.getElementById('scrubber');if(sc){sc.max=frames.length-1;sc.value=idx;}startPlay();"
        }
        return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
 html,body{margin:0;padding:0;background:$pageBg;overflow:hidden}
 #map{width:100%;height:$mapHeight}
 .ts{position:absolute;top:8px;left:50%;transform:translateX(-50%);z-index:1000;pointer-events:none;
     background:$pillBg;color:$pillFg;padding:4px 12px;border-radius:14px;
     font:13px/1.6 sans-serif;white-space:nowrap;display:flex;align-items:center;gap:6px}
 .badge{padding:1px 7px;border-radius:10px;font-size:11px;font-weight:700;letter-spacing:.3px}
 .badge.live{background:#e53935;color:#fff}
 .badge.fc{background:#1976d2;color:#fff}
 .ctrl{width:100%;height:54px;display:flex;align-items:center;
       padding:0 12px;gap:8px;background:$ctrlBg;box-sizing:border-box}
 .btn{background:none;border:1px solid $btnFg;color:$btnFg;border-radius:8px;
      padding:6px 14px;font-size:15px;cursor:pointer;flex-shrink:0;line-height:1}
</style></head><body>
<div id="map"></div>
<div class="ts" id="ts"><span>...</span></div>
$ctrlHtml
<script>
 var lat=$lat, lon=$lon;
 var map=L.map('map',{${interactions}zoomControl:$mapControls,attributionControl:false}).setView([lat,lon],7);
 L.tileLayer('https://{s}.basemaps.cartocdn.com/$style/{z}/{x}/{y}.png',{subdomains:'abcd',maxZoom:12}).addTo(map);
 L.circleMarker([lat,lon],{radius:5,color:'#fff',weight:2,fillColor:'#2196f3',fillOpacity:1}).addTo(map);
 function fixSize(){try{map.invalidateSize(true);}catch(e){}}
 map.whenReady(function(){setTimeout(fixSize,150);});
 window.addEventListener('load',fixSize);
 [150,400,800,1500].forEach(function(t){setTimeout(fixSize,t);});
$setHeightJs
 var frames=[],layers={},idx=0,host='',pastCount=0;
 var tsEl=document.getElementById('ts');
 function layerFor(f){
   if(!layers[f.path]){
     layers[f.path]=L.tileLayer(host+f.path+'/256/{z}/{x}/{y}/2/1_1.png',{opacity:0,zIndex:Math.round(f.time/1000),maxNativeZoom:7,maxZoom:12});
     layers[f.path].addTo(map);
   }
 }
 function show(i){
   for(var j=0;j<frames.length;j++){var l=layers[frames[j].path];if(l)l.setOpacity(j===i?0.75:0);}
   var f=frames[i];
   var d=new Date(f.time*1000);
   var timeStr=d.toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});
   var html='';
   if(f.nowcast){
     var diffMin=Math.round((f.time-Date.now()/1000)/60);
     html='<span class="badge fc">+'+diffMin+'m</span>';
   } else if(i===pastCount-1){
     html='<span class="badge live">LIVE</span>';
   }
   tsEl.innerHTML=html+'<span>'+timeStr+'</span>';
 }
$playerFunctions
 fetch('https://api.rainviewer.com/public/weather-maps.json').then(function(r){return r.json();}).then(function(d){
   host=d.host;
   var past=(d.radar&&d.radar.past)||[],nowcast=(d.radar&&d.radar.nowcast)||[];
   nowcast.forEach(function(f){f.nowcast=true;});
   frames=past.concat(nowcast);
   pastCount=past.length;
   if(!frames.length)throw new Error('no frames');
   frames.forEach(layerFor);
   idx=Math.max(0,pastCount-1); show(idx);
   $afterLoad
 }).catch(function(){tsEl.innerHTML='<span>radar n/a</span>';});
</script></body></html>
        """.trimIndent()
    }
}
