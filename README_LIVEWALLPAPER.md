# Live Weather Wallpaper — uitbreidingen op Breezy Weather

Deze app is gebouwd bovenop **[Breezy Weather](https://github.com/breezy-weather/breezy-weather)**
(native Kotlin/Compose, LGPL-3.0). Breezy levert al de echte Android live wallpaper
(`WallpaperService`), de weer-animaties (wolken/regen/sneeuw over de achtergrond), GPS-locatie,
16-daags forecast en weeralerts. Hieronder staat wat er voor **dit** project bovenop is gebouwd.

## Doel
Een YoWindow-achtige live wallpaper:
- foto van de locatie waar je bent als achtergrond,
- weer-animatie (wolken/regen) eroverheen, afhankelijk van het weertype,
- weersverwachting,
- (nog te bouwen) animated rain-radar.

## Nieuwe componenten (deze repo voegt toe)

Alles in package `com.livewallpaperweather.wallpaper.photo`:

| Bestand | Rol |
|---|---|
| [`LocationData.kt`](app/src/main/kotlin/org/breezyweather/wallpaper/photo/LocationData.kt) | Model dat een gebied (naam + lat/lon-bounding-box) koppelt aan een specifieke afbeelding-URL. Bevat `contains()` en haversine-`distanceKmTo()`. |
| [`WallpaperImageStore.kt`](app/src/main/kotlin/org/breezyweather/wallpaper/photo/WallpaperImageStore.kt) | Persistentie (SharedPreferences via Breezy's `ConfigStore`): aan/uit-vlag, Unsplash-key, lijst met `LocationData`, pad naar gecachete foto. |
| [`UnsplashPhotoSource.kt`](app/src/main/kotlin/org/breezyweather/wallpaper/photo/UnsplashPhotoSource.kt) | Zoekt een foto-URL op plaatsnaam via de Unsplash Search API (OkHttp). |
| [`WallpaperRepository.kt`](app/src/main/kotlin/org/breezyweather/wallpaper/photo/WallpaperRepository.kt) | De "hersenen": coördinaten → beste match → URL → download → lokale cache. |

**Matching-volgorde in `WallpaperRepository`:**
1. Handmatige `LocationData` waarvan de bounding-box de coördinaten bevat.
2. Dichtstbijzijnde `LocationData`-centrum binnen `maxMatchDistanceKm` (default 50 km).
3. Unsplash-zoekopdracht op plaatsnaam (stad).
4. Geen match → wallpaper houdt de originele kleurverloop-achtergrond.

**Aanpassing in de wallpaper-engine**
[`MaterialLiveWallpaperService.kt`](app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt):
- `setWeatherBackgroundDrawable()` gebruikt nu de gecachete foto (center-crop) als de
  foto-achtergrond aanstaat; anders de originele gradient.
- `maybeRefreshPhotoBackground()` downloadt/cachet de foto voor de huidige locatie zodra de
  wallpaper zichtbaar wordt — buiten de render-thread (eigen coroutine-scope).
- De weer-animatie (`CloudImplementor` etc.) wordt onveranderd **over** de foto getekend.

## Setup

### 1. Unsplash access key (gratis)
1. Maak een account op https://unsplash.com/developers en registreer een "New Application".
2. Kopieer de **Access Key** (Client-ID).
3. Zet de key in de app. Voor nu (geen UI-veld) kan dat via de store, bijv. eenmalig in code
   of via een debug-actie:
   ```kotlin
   WallpaperImageStore(context).unsplashAccessKey = "JOUW_ACCESS_KEY"
   ```
   > TODO (volgende stap): een tekstveld toevoegen aan het live-wallpaper-instellingenscherm
   > ([`LiveWallpaperConfigActivity.kt`](app/src/main/kotlin/org/breezyweather/wallpaper/LiveWallpaperConfigActivity.kt)).

### 2. Foto-achtergrond aanzetten
Er is nog geen UI-toggle (volgende stap). Tijdelijk aanzetten:
```kotlin
WallpaperImageStore(context).photoBackgroundEnabled = true
```
Daarna de live wallpaper opnieuw selecteren/zichtbaar maken, of het toestel even vergrendelen
en ontgrendelen zodat `onVisibilityChanged` opnieuw vuurt.

### 3. (Optioneel) Eigen foto's koppelen aan gebieden
```kotlin
WallpaperImageStore(context).addLocationData(
    LocationData(
        name = "Amsterdam",
        minLatitude = 52.30, maxLatitude = 52.43,
        minLongitude = 4.75, maxLongitude = 5.02,
        imageUrl = "https://…/amsterdam.jpg",
    )
)
```

## Bouwen & draaien op een echt toestel (USB)

Vereisten: **Android Studio** (al geïnstalleerd) — bevat JDK + Android SDK. Geen Flutter nodig;
dit is een pure Gradle/Kotlin Android-app.

1. Open de map `d:\Project\LiveWeatherApp` in Android Studio en laat Gradle synchroniseren.
2. Schakel **USB-debugging** in op je telefoon (Instellingen → Ontwikkelaarsopties).
3. Sluit de telefoon via USB aan en bevestig de debug-prompt.
4. Kies je toestel in Android Studio en klik **Run** (of: `.\gradlew assembleDebug` en installeer de APK).
5. Op de telefoon: **Achtergrond instellen → Live wallpapers → Breezy Weather**.
6. Voeg in de app minstens één locatie toe (of sta GPS toe) zodat er weerdata + coördinaten zijn.

Commandline-alternatief (vanuit de projectmap, met Android SDK/JDK op PATH):
```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\basic\debug\app-basic-debug.apk
```
(De exacte APK-naam hangt af van de product flavors; check `app\build\outputs\apk\`.)

## Neerslagradar (package `com.livewallpaperweather.radar`)

Twee onafhankelijke bronnen, bewust naast elkaar zodat we later kunnen vergelijken welke het
beste werkt:

| Bestand | Rol |
|---|---|
| [`BuienradarNowcastSource.kt`](app/src/main/kotlin/org/breezyweather/radar/BuienradarNowcastSource.kt) | NL/Benelux regen-trend (nowcast) via `gpsgadget.buienradar.nl/data/raintext`. 2u vooruit, per 5 min; waarde 0-255 → mm/u. |
| [`RainViewerRadarSource.kt`](app/src/main/kotlin/org/breezyweather/radar/RainViewerRadarSource.kt) | Wereldwijde animeerbare radar-frames via `api.rainviewer.com/public/weather-maps.json` (past + nowcast). |
| [`RainTrendChart.kt`](app/src/main/kotlin/org/breezyweather/radar/RainTrendChart.kt) | Compose Canvas-grafiek van de regen-trend (zoals de "2 uur neerslagvoorspelling"-kaart). |
| [`RadarActivity.kt`](app/src/main/kotlin/org/breezyweather/radar/RadarActivity.kt) | In-app scherm: toont de trend-grafiek (Buienradar) + radar-frame-status (RainViewer) voor je huidige locatie. |

**Status:**
- ✅ **Fase 1** — data-bronnen + Buienradar regen-trend-grafiek + radar-scherm.
- ✅ **Fase 2** — geanimeerde RainViewer-radarkaart (Leaflet in een WebView), getest op toestel.
  - Geleerde lessen die in de code zitten: WebView heeft een **expliciete pixel-hoogte** nodig
    (`#map{height:420px}`, anders 0); RainViewer-radar gaat tot **zoom 7** (`maxNativeZoom:7`);
    basiskaart = **CARTO dark** (OSM-tiles werden geannuleerd in de WebView).
- ✅ Knop naar de radar: **☔-icoon** in de toolbar van het hoofdscherm.
- ✅ **Fase 3** — Verbeterde weer-animaties (regen, sneeuw, mist, wolken, bliksem) met AGSL-ondersteuning (Android 13+) en een geoptimaliseerde Canvas-fallback (Android 6+). FPS beperkt tot 30 voor batterijbesparing.
- 🟡 **Fase 4** — regen-trend-strip onderaan de **live wallpaper** (Buienradar). Compileert en is
  zwaar afgeschermd (kan de wallpaper niet laten crashen), maar **nog niet visueel geverifieerd**.

## Andere wijzigingen in deze fork
- **Gecombineerde forecast-kaart**: daily + hourly in één tegel met een **Dag / Per uur**-schakelaar
  ([`DailyViewHolder.kt`](app/src/main/kotlin/org/breezyweather/ui/main/adapters/main/holder/DailyViewHolder.kt));
  trend-types in één dropdown. De losse uur-kaart is uit de lijst gehaald.
- **Home als 1 blok**: kaarten plat, zonder elevatie/ronding/verticale tussenruimte
  ([`AbstractMainCardViewHolder.kt`](app/src/main/kotlin/org/breezyweather/ui/main/adapters/main/holder/AbstractMainCardViewHolder.kt)).
  🟡 Nog niet visueel geverifieerd.
- **Bronnen gesnoeid** naar NL/EU gratis & key-loos (Open-Meteo, MET Norway, KNMI, WMO); Baidu IP +
  GeoNames verwijderd; offline reverse-geocoding als default (geen Nominatim-prompt).
- **Mapbox satelliet** als alternatieve achtergrond-bron (naast Unsplash), instelbaar in de
  wallpaper-settings (bron-keuze + token-veld).
- **About-scherm**: contributors/vertalers vervangen door een **Breezy-fork-vermelding**.
- **Opschoning**: "Animaties (gevaarlijk)" → "Animaties"; "Openen in andere app" verwijderd.

> ⚠️ **Nog visueel te checken** (toestel was weg tijdens bouwen): de **"home als 1 blok"**-look en
> de **regen-trend-strip op de wallpaper**. Beide compileren en zitten in aparte commits, dus
> eenvoudig terug te draaien met `git revert <hash>` als ze niet bevallen.

## Nog te bouwen / ideeën
- [ ] Menu/instelling om de wallpaper-regen-trend aan/uit te zetten.
- [ ] Beheer van `LocationData` in de UI (handmatig foto's aan gebieden koppelen).
- [ ] Periodieke achtergrond-refresh via WorkManager (nu alleen bij zichtbaar worden).
- [ ] Foto-attributie tonen (Unsplash/Mapbox-richtlijnen).
