# LiveWeatherApp — Functioneel document

*Opgesteld: 2026-08-16. Gebaseerd op `docs/APP_FUNCTIES.md`, de bijbehorende `docs/Eisen/ACT-*.md`-documenten,
en verificatie tegen de huidige broncode (`app/`, `data/`, `domain/`, `cloud-engine/`). Waar documentatie en
code van elkaar afweken, is de code leidend; afwijkingen zijn expliciet gemarkeerd met 🔶.*

LiveWeatherApp is een Android-weerapp, gebouwd als fork van het open-source project **Breezy Weather**.
Het onderscheidende kenmerk is een **live wallpaper met echte, lucht-vrije landschapsfoto's** per locatie,
gecombineerd met een geanimeerde weer-rendering (wolken, regen-op-glas, sneeuw, mist, sterrenhemel,
seizoensgrading) die op de achtergrond van het toestel draait.

## 🔶 Belangrijkste afwijkingen t.o.v. bestaande documentatie

Dit is de belangrijkste bevinding van de verificatie en geldt voor vrijwel alle onderliggende ACT-documenten:

1. **Packagenaam is gewijzigd.** Alle docs verwijzen naar `org.breezyweather.*`. De daadwerkelijke code
   staat sinds de fork onder **`com.liveweatherwallpaperapp.*`** (app-module) en **`livewallpaperweather.*`**
   (de losse `domain`/`data`-modules, bijv. `livewallpaperweather.domain.location.model.Location`). Een
   oude kopie onder `org.breezyweather` bestaat nog in `.worktrees/act1/` — dat is een niet-actuele
   parallelle checkout en geen onderdeel van de huidige app.
2. **Achtergrond-weerverversing is 15 minuten (instelbaar), niet 1,5 uur.** `INTERVAL_AUTO` in
   `UpdateInterval.kt` staat op 15 minuten (WorkManager-minimum); gebruikers kunnen kiezen tussen "nooit"
   en 15 min t/m 24 uur.
3. **`docs/Eisen/RADAR.md` is inhoudelijk verouderd.** Dat document is de ongewijzigde upstream
   Breezy Weather-tekst ("we hebben geen radarfunctie"). Deze fork heeft juist wél een volwaardig eigen
   radarscherm (§5) — het document moet als niet-van-toepassing worden gemarkeerd.
4. **Foto-ververinterval van de wallpaper is 15–180 minuten (standaard 30 min), niet 24 uur** zoals
   `ACT-010` als ontwerp noemt.
5. **ACT-021 (herziening foto-sortering) is volledig geïmplementeerd**, niet langer een plan — met dien
   verstande dat de oude `WallpaperPhotoPriority`-logica (die ACT-021 wilde laten vervallen) in de praktijk
   nog naast de nieuwe pijplijn bestaat en gebruikt wordt.
6. **`GithubReleaseNotesSource.kt` doet geen GitHub-aanroep meer.** De naam is historisch; de klasse leest
   nu een lokaal gebundelde `release-notes.json` (gegenereerd bij het bouwen), geen netwerkverkeer.
7. **"Vind mijn telefoon" (klap-detectie) vereist 3 klappen binnen een tijdvenster**, niet één klap. De
   stille uren (20:00–08:00) zijn hardcoded, niet gebruikersinstelbaar (in tegenstelling tot de
   dwell-tijd en het dagmaximum van de "weetjes"-functie, die dat wél zijn).
8. **`docs/Eisen/ACT-014 - sterrenhemel.md`** is inhoudelijk geen bruikbare spec (een onaffe AI-taakschets),
   de functionaliteit zelf (`StarField.kt`) is wél volledig geïmplementeerd en hieronder beschreven op basis
   van de code.
9. De widget voor "weetjes" (`WidgetFactAlarmReceiver.kt`/`WidgetFactAlarmScheduler.kt`), die
   `APP_FUNCTIES.md` als "gepland" omschrijft, staat al in de broncode.
10. **"Vind mijn telefoon" is uitgeschakeld via een feature-flag.**
    `FindMyPhoneStore.FEATURE_ENABLED = false`
    (`background/findmyphone/FindMyPhoneStore.kt:107`). De volledige implementatie (service, klap-/
    fluitdetectie, kalibratiedialoog) bestaat en werkt, maar de instellingenschakelaar in
    `NotificationsSettingsScreen.kt` wordt alleen getoond als deze vlag `true` is — voor eindgebruikers
    is de functie dus momenteel onzichtbaar/niet te activeren. `APP_FUNCTIES.md` en §6 hieronder
    beschrijven deze als een normaal beschikbare functie; dat klopt dus alleen zolang de vlag uit staat
    niet.

Verderop in dit document worden bestandspaden consequent met het **actuele** package (`com.liveweatherwallpaperapp`,
`livewallpaperweather`) genoemd.

---

## Inhoudsopgave

1. [Weergave van het weer](#1-weergave-van-het-weer)
2. [Live wallpaper — achtergrondfoto's](#2-live-wallpaper--achtergrondfoto's)
3. [Live wallpaper — rendering en effecten](#3-live-wallpaper--rendering-en-effecten)
4. [Locatiebeheer](#4-locatiebeheer)
5. [Radar / Buienradar](#5-radar--buienradar)
6. [Meldingen](#6-meldingen)
7. [Instellingenschermen](#7-instellingenschermen)
8. [Navigatie](#8-navigatie)
9. [Camera — lucht fotograferen](#9-camera--lucht-fotograferen)
10. [RemoveSky-backend](#10-removesky-backend)
11. [Overige onderdelen](#11-overige-onderdelen)

---

## 1. Weergave van het weer

### 1.1 Hoofdscherm

Het hoofdscherm (`ui/main/MainActivity.kt`, `MainActivityViewModel.kt`, met fragmenten
`fragments/HomeFragment.kt`, `MainModuleFragment.kt`, `ManagementFragment.kt`) toont het weer voor de
geselecteerde locatie:

- **Bovenbalk**: terugknop naar de locatielijst, plaatsnaam, "openen in andere app"-knop (coördinaten),
  bewerkknop met opties (hoofdscherm herschikken, locatieservice wijzigen [alleen huidige locatie], weerbron
  per feature wijzigen, brongerelateerde voorkeuren). Direct daaronder: tijdstip laatste verversing.
- **Header**: weerconditie-omschrijving, temperatuur (geanimeerd bij binnenkomst, uit te zetten),
  gevoelstemperatuur indien afwijkend, min/max-temperatuur voor het huidige en volgende dagdeel.
- **Kaartblokken** (herschikbaar via sleep-en-neerzetten of via instellingen, beheerd in
  `ui/settings/activities/CardDisplayManageActivity.kt` + `CardDisplayAdapter`): waarschuwingen (altijd
  bovenaan, niet te verwijderen), neerslag-nowcasting, dagvoorspelling, uurvoorspelling, neerslag, wind,
  luchtkwaliteit, pollen, luchtvochtigheid, UV, zicht, luchtdruk, zon, maan, klok (standaard uit), en
  optioneel een **compacte regenradartegel** (zie §5). Alle blokken zijn aantikbaar voor een detailscherm.
  Aantal kolommen per rij (1–5) is afhankelijk van schermbreedte en lettergrootte.
- Trend-tabbladen (dag/uur) zijn apart in/uit te schakelen en te herschikken via
  `DailyTrendDisplayManageActivity.kt` / `HourlyTrendDisplayManageActivity.kt` (eigen adapters
  `DailyTrendDisplayAdapter` / `HourlyTrendDisplayAdapter`).
- Een extra, niet eerder gedocumenteerd scherm `DetailsOverviewDisplayManageActivity.kt` beheert welke
  detail-tegels op het *detailscherm* zichtbaar zijn (naast de twee trend-managers hierboven).

**Blokinhoud (zie ook `docs/Eisen/HOMEPAGE.md`, upstream-doc, nog grotendeels geldig):**

| Blok | Inhoud |
|---|---|
| Waarschuwingen | Actuele waarschuwingen met start-/eindtijd; toekomstige waarschuwingen aantikbaar |
| Neerslag-nowcasting | Alleen zichtbaar met compatibele bron én neerslag binnen het komende uur; grafiek per 5 minuten, sleepbare peillijn |
| Dagvoorspelling | Tabs per trend (conditie, luchtkwaliteit, wind, UV, neerslag, zonneschijn, gevoelstemperatuur); kolom per dag |
| Uurvoorspelling | Zelfde trend-tabs als dagvoorspelling plus luchtvochtigheid/dauwpunt, luchtdruk, bewolking, zicht; toont maximaal de eerstvolgende 24 uur |
| Neerslag | Verwachte hoeveelheid voor het huidige dagdeel, met type (regen/sneeuw/natte sneeuw) indien bekend |
| Wind | Huidige windsnelheid/-richting, windstoten indien aanwezig |
| Luchtkwaliteit | AQI o.b.v. Plume-index 2023 (O3/NO2/PM10/PM2.5, max. van de vier); ¾-cirkel met kleurcodering, max. schaalwaarde 250 |
| Pollen | Max. 2 dominante pollensoorten (excl. schimmel) met concentratie > 0 |
| Luchtvochtigheid | Percentage + dauwpunt |
| UV-index | Huidige UV-index + 5-punts schaalindicator |
| Zicht | Huidige zicht + niveauclassificatie (zeer slecht t/m perfect helder, 0–40+ km) |
| Luchtdruk | Actuele zeeniveaudruk, cirkel gevuld tussen 963–1063 hPa |
| Zon | Boog zonsopgang→zonsondergang voor de waargenomen dag; poolnacht/-dag speciale gevallen |
| Maan | Boog maanopkomst→maanondergang + maanfase; speciale regels bij "gisteren-maan" die doorloopt |
| Klok | Actuele tijd in de locatie's tijdzone, analoog op Android 12+ of gelijke tijdzone als toestel |

### 1.2 Dag-/uurdetailschermen

`ui/details/` bevat `DetailsActivity.kt`, `DetailsScreen.kt`, `DetailsViewModel.kt`, `DetailsUiState.kt`
en per-onderwerp Composables: conditie, luchtkwaliteit, wind, UV, neerslag, zon/maan, luchtvochtigheid,
luchtdruk, bewolking, zicht, pollen. `ui/main/adapters/trend/daily/` en `.../hourly/` leveren de
onderliggende trend-grafiek-adapters (temperatuur, luchtkwaliteit, gevoelstemperatuur, neerslag,
zonneschijn, UV, wind voor dag; plus luchtvochtigheid, luchtdruk, bewolking, zicht voor uur — sommige
metrics zijn bewust alleen als uurtrend beschikbaar).

**Dagdetails / graaddagen** (`docs/Eisen/DAY_DETAILS.md`): verwarmings-/koelingsgraaddagen per dag.
Ontbreekt de brongegevens, dan wordt een generieke EU-formule gebruikt: geen graaddag tussen 15–24 °C
daggemiddelde; onder 15 °C = 18 °C − gemiddelde (verwarming); boven 24 °C = gemiddelde − 21 °C (koeling).

### 1.3 Weerbronnen

40+ nationale/wereldwijde weerbronnen, gecatalogiseerd per land in `docs/Eisen/SOURCES.md` en
geïmplementeerd als losse modules onder `app/src/main/kotlin/com/liveweatherwallpaperapp/sources/`
(58 submappen, waarvan de meeste een concrete bron zijn: o.a. Open-Meteo, AccuWeather, KNMI, DMI, NWS,
JMA, MET Norway, SMHI, GeoSphere Austria, IPMA, ECCC, plus infrastructuur-submappen als `nominatim`,
`geonames`, `naturalearth`, `gadgetbridge`, `wmosevereweather`). Per locatie is te kiezen welke bron
voorspelling/actueel/luchtkwaliteit/pollen/nowcasting/waarschuwingen/normalen/adreszoeken levert.

### 1.4 Weerupdate-proces

Code: `background/weather/WeatherUpdateJob.kt`, `AdaptiveRefreshDecision.kt`, caching in `RefreshHelper.kt`.

- **Achtergrondverversing**: standaard/auto-interval **15 minuten** (`INTERVAL_AUTO`), door de gebruiker
  instelbaar van 15 minuten tot 24 uur (of "nooit") via Instellingen → Achtergrondupdates. Per cyclus wordt
  standaard alleen de eerste locatie ververst; secundaire locaties maximaal 1×/dag, en alleen tot een maximum
  van 3 (widget), 4 (notificatie-widget) of 5 (Gadgetbridge/datadeling) locaties — overige locaties worden pas
  ververst zodra de gebruiker ze opent.
- **🔶 Adaptieve refresh (nieuw, niet in oude docs)**: `AdaptiveRefreshDecision.needsAdaptiveRefresh()` plant
  een eenmalige extra verversing 10 minuten later wanneer er een actieve weerwaarschuwing is, of neerslag
  binnen 2 uur verwacht begint/stopt — los van het normale interval, alleen als dat interval langer dan
  10 minuten is.
- **Handmatige verversing**: pull-to-refresh, bij het openen van een verouderde locatie, bij wisselen van
  weerbron, of (Open-Meteo) bij wisselen van weermodel.
- **Cachevensters** per feature (`RefreshHelper.kt`): hoofdweer 5 min (15 min bij bronnen met API-sleutel-
  restrictie), actueel weer 1 min (15 min), luchtkwaliteit/pollen 5 min (1 uur), nowcasting 1–5 min,
  waarschuwingen 1–5 min. Cache wordt genegeerd als coördinaten >5 km zijn gewijzigd; adres-hercodering
  alleen buiten 50 km.
- Na iedere geslaagde update: widgets, notificatie-widget, snelkoppelingen bijwerken; bij ingeschakeld
  Gadgetbridge (`sources/gadgetbridge/GadgetbridgeService.kt`) een broadcast; waarschuwings-/
  nowcastingmeldingen alleen voor de eerste locatie (zie §6); elke 24 uur een app-updatecheck.

---

## 2. Live wallpaper — achtergrondfoto's

### 2.1 RemoveSky-fotoservice

Per locatie haalt de app landschapsfoto's zonder lucht op bij de zelf-gehoste RemoveSky-backend
(`wallpaper/photo/RemoveSkyProvider.kt`), converteert ze naar WebP en cachet ze lokaal per locatie
(`WallpaperImageStore.kt`, SharedPreferences-achtige config/cache-store) en per foto
(`data/.../wallpaper/WallpaperPhotoRepository.kt`, SQLDelight-tabel `wallpaper_photos`).

### 2.2 Automatische fotoverversing

`WallpaperPhotoRefreshWorker.kt` (WorkManager `CoroutineWorker`) + `WallpaperPhotoRefreshPlanner.kt`
(pure planlogica: `needsRefresh`, `locationsToProcess`, `shouldActivateLocation`).

- **Periodieke planning**: `PeriodicWorkRequest`, vereist `NetworkType.CONNECTED` +
  `requiresBatteryNotLow`, interval **15–180 minuten (standaard 30 min)**, instelbaar per stap van 15 min;
  exponentiële backoff (30 min basis); wijziging van interval pakt direct door
  (`ExistingPeriodicWorkPolicy.UPDATE`).
- **Handmatige trigger** ("Ververs nu"): eenmalige taak, alleen netwerkvereiste (bewust geen
  batterij-constraint, expliciete gebruikersactie).
- **Retry bij mislukking**: eenmalige taak met oplopende vertraging (10 → 30 → 60 → 360 min na
  opeenvolgende lege resultaten), met waarschuwing in de UI na 3 mislukkingen op rij.
- Maximaal 5 locaties per uitvoering (niet instelbaar).
- **Vaste/fictieve locaties gebruiken altijd hun eigen coördinaten**, nooit de live GPS-positie —
  alleen als de locatie zowel actief wordt geactiveerd (`isActivating`) als een "huidige locatie" (GPS,
  `location.isCurrentPosition`) is, wordt een verse GPS-fix opgehaald. Voor fictieve locaties wordt wél
  apart de echte GPS-positie opgevraagd, uitsluitend om dag/nacht/seizoenscontext te bepalen — nooit om de
  coördinaten van de fictieve locatie zelf te wijzigen. In de code staat hierover een expliciete
  bugfix-toelichting over een eerdere regressie waarbij dit misging.
- **Directe refresh bij locatiewissel**: wijzigen, bewerken of verwijderen van de actieve (bovenste)
  locatie triggert direct een verversing.
- **Eenmalige hotfix (build ≤ 2026.07.27.436)**: bestaande installaties kregen één keer een volledige
  cache-wipe (`store.hotfixCacheResetDone`-vlag), zodat elke locatie opnieuw synchroniseert. Nieuwe
  installaties merken hier niets van.
- Dezelfde worker voert ook een 24-uurlijkse RemoveSky-gezondheidscheck uit en controleert de
  "weetjes"-dwell-notificatie voor de actieve locatie.

### 2.3 Fotosortering/-selectie

Volledig geïmplementeerd (`wallpaper/photo/SortLocationRecs.kt`, `CustomSortLogic.kt`, en de
`getSortedResultlist()`-pijplijn in `WallpaperRepository.kt`).

- **Voor de GPS "huidige locatie"** (`sortLocationRecsByGPSLocation()`): een radius-cascade
  (ongelimiteerd → 5 km → 2 km → 1 km → 500 m → 200 m → 100 m); de kleinste ring met ≥ 4 foto's wint,
  gesorteerd op afstand.
- **Voor vaste/fictieve locaties** (`sortLocationRecsByLocation(fictief)`): geen radius-cascade, direct
  gesorteerd op aanmaakdatum, met als "werklocatie" de echte GPS-positie als de locatie fictief is,
  anders de locatie zelf.
- **Eindsortering** (`sortByRecencyViewsDistance()`): recentheidsmarge (5 dagen, op basis van
  verwerkingsdatum) > minst-vertoonde foto (view count) > kortste afstand.
- De rotatie onthoudt per locatie een sorteerlijst + index (in-memory, niet gepersisteerd — reset bij
  app-herstart); "volgende" foto wordt gepakt via `getNextSortedResultlistItem()`, waarbij foto's die de
  gebruiker heeft verborgen worden overgeslagen.
- 🔶 De oudere `WallpaperPhotoPriority.kt` (`selectWallpaperPhoto()`/`buildShowlist()`) bestaat nog en
  wordt nog gebruikt in `WallpaperRepository.refreshFor()` (o.a. camera-uploads), naast de nieuwe
  pijplijn — nog niet volledig uitgefaseerd zoals eerder gepland.

### 2.4 "Ververs nu" / "Volgende"-knoppen

`LiveWallpaperConfigActivity.kt`:
- **"Ververs nu"** (`runRefresh()`): controleert eerst de RemoveSky-gezondheidsstatus, haalt de eerste
  locatie op, wacht op een eenmalige worker-run (`WallpaperPhotoRefreshWorker.startNowAndAwait()`), toont
  daarna een voorbeeld + statusbericht.
- **"Volgende"** (`runNext()`): schakelt zonder netwerkverkeer naar de volgende al gedownloade foto in de
  lokale rotatiepool — bruikbaar als de RemoveSky-backend niet bereikbaar is.

### 2.5 Cachegrootte- en limietinstellingen

Alle instellingen in `LiveWallpaperConfigActivity.kt`, opgeslagen via `WallpaperImageStore.kt`:

| Instelling | Bereik | Standaard |
|---|---|---|
| Foto-ververinterval | 15–180 min (stappen van 15) | 30 min |
| Cachelimiet | 25–500 MB (stappen van 25) | 100 MB |
| Max. foto's per locatie | 4–50 | 12 |
| Aantal te vermijden recent-getoonde URL's | 1–20 | 4 |

"Cache legen" is een tweestapsdialoog met keuze tussen de huidige locatie of alle locaties.
Debug-/testbouwen tonen extra opties (seizoensgrading, wolkenafstemming, verborgen "Rotating"-weertype,
link naar `WallpaperPhotoManagerActivity` voor fotobeheer).

### 2.6 Aan/uit-schakelaar

De fotoachtergrond is volledig uit te zetten (annuleert de periodieke worker); bij inschakelen wordt de
worker opnieuw ingepland.

---

## 3. Live wallpaper — rendering en effecten

De renderpipeline combineert een echte foto met procedurele weeranimaties in een gelaagd
compositing-systeem (`wallpaper/WallpaperEffectView.kt`, `MaterialLiveWallpaperService.kt`,
`wallpaper/WallpaperWeatherEffectRenderer.kt`), aangestuurd door een centrale, immutable scene-state
(`WallpaperSceneState.kt`).

### 3.1 Lagenopbouw (achter → voor)

| # | Laag | Parallaxfactor | Bronbestand |
|---|---|---|---|
| 1 | Luchtgradiënt (achtergrond) | 5% | `SkyColors.kt` |
| 2 | Zon/maan met gloed | 2% | `CelestialGlow.kt`, `CelestialTiming.kt` |
| 3 | Weereffecten achtergrondpas (wolken, sterren, mist) | — | `CloudField.kt`/`CloudEngineAdapter.kt`, `StarField.kt`, `FogField.kt` |
| 4 | Voorgrondfoto (RGBA, lucht-vrij) | 15% | `WallpaperPhotoLayout.kt` |
| 5 | Weereffecten voorgrondpas | — | `WallpaperParticlePool.kt`, `WallpaperParticleTrajectory.kt` |
| 6 | Regen-op-glas refractie (optioneel) | — | `GlassRainField.kt` |
| 7 | Seizoensgrading (optioneel) | — | `WallpaperSeasonGrading.kt` |

Op Android 13+/API 33+ wordt een AGSL `RuntimeShader` gebruikt (hardwareversneld); op oudere versies een
Canvas-fallback. Aangestuurd door de oriëntatiesensor van het toestel (Rotation Vector Sensor) voor
tilt-gebaseerde parallax — bewust gebaseerd op kanteling in plaats van camera/gezichtsherkenning, om
privacy- en batterijredenen.

### 3.2 Sky removal / voorgrondfoto

- **Server-side** (standaard): `RemoveSkyProvider.kt` levert al lucht-vrije, transparante PNG's.
- **On-device fallback**: `SkySegmenter.kt`, TFLite-model `sky_segmentation_ade20k.tflite` (DeepLab +
  MobileNetV3, invoer 512×512, luchtklasse-ID 3). Validatieregels: minimaal 8% luchtfractie, bovenste 15%
  van de afbeelding ≥ 30% lucht, luchtcentroïde Y < 0,4, onderste band < 15% lucht, minimaal 60%
  luchtkleur.
- Download- en cachepijplijn (`WallpaperRepository.kt`): resolveImage → downloadSkyBitmap (direct gebruik
  bij server-verwerking, anders on-device fallback) → cache als WebP met sha256-prefix → activeren.

### 3.3 Wolkenrenderer

Hybride architectuur: `CloudField.kt` (117 regels) definieert vijf pure procedurele ruislagen
(diepte/schaal/snelheid/dekking/donkerte/verticale positie, basissnelheden `[0.35, 0.50, 0.65, 0.82, 1.00]`,
gemoduleerd door windsnelheid). Daarnaast bestaat een apart Gradle-submodule **`cloud-engine`** met
sprite-gebaseerde rendering (`CloudEngineRenderer.kt`: horizonbank, stratuslagen, hoge sluierwolken,
losse billboard-instanties, regenfront) die `CloudField`/`CloudEngineAdapter.kt` als parameterbron
gebruikt. Afstemming per weertype via `CloudTuningActivity.kt`/`CloudTuningWeatherTypes.kt` (debugtool).

**Verborgen "easter egg"**: 1–2× per dag (kalender-/dagseed-gestuurd) kruisen willekeurige extra
wolk-sprites (losse asset-pool) met lage opaciteit het scherm; snelheid gekoppeld aan windsnelheid;
uitgeschakeld bij helder weer en 's nachts. Bewust geen instelling — het moet een verrassing blijven.
(Debug-only: `triggerEasterEggNow()` om te forceren tijdens testen.)

### 3.4 Overige weeranimaties/-effecten

- **Regen-op-glas**: `GlassRainField.kt` — druppelprofielen per kwaliteitsniveau.
- **Sneeuw/hagel**: `WallpaperParticlePool.kt` (vaste buffers, geen per-frame allocatie) +
  `WallpaperParticleTrajectory.kt`.
- **Mist/waas**: `FogField.kt` — 3–5 horizontale dieptebanden (hoogte/alpha/snelheid/blur per band).
- **Zon-/maangloed**: `CelestialGlow.kt`, `CelestialTiming.kt`.
- **Sterrenhemel** (getoond 's nachts): `StarField.kt`.
- **Luchtkleurovergangen**: `SkyColors.kt`.
- Alles gecoördineerd via `WallpaperSceneState.kt` (immutable snapshot, sterk uitgebreid t.o.v. het
  oorspronkelijke ontwerp met o.a. seizoens-, kwaliteits- en telemetrievelden) en
  `WallpaperSceneSnapshot.kt` (render-klare afgeleide staat).

### 3.5 Vloeiende overgangen

`TransitionManager.kt`: easing (`smoothStep`), lineaire interpolatie van waarden/kleuren/hoeken
(kortste-route voor hoeken). Overgangsduren: 60s normale weerwisseling, 45s bij start/stop van neerslag,
30s bij onweer, 3s bij geforceerde weertype-override, 2s bij "Rotating"-testmodus, 0s als animaties uit
staan. Ook crossfade + fade-timing bij navigatie tussen hoofdscherm en detail-/radarscherm (geen "flits"
van een placeholder-achtergrond).

### 3.6 Seizoensgrading (experimenteel)

`WallpaperSeasonGrading.kt`: subtiele seizoensgebonden kleur-/lichtaanpassing (kouder in de winter,
warmer in de zomer) als laatste sfeerlaag, met aan/uit-schakelaar en sterkteregelaar; wijzigt de
bronfoto niet.

### 3.7 Kwaliteitsprofielen / performance

`WallpaperQualityProfile.kt`: drie profielen (BATTERY_SAVER, BALANCED, HIGH) met een `QualityBudget` per
profiel. `QualityDegradationTracker` degradeert automatisch en tijdelijk bij trage frames (met
hysterese) en herstelt bij stabiele prestaties. `FrameTelemetry.kt` en `WallpaperLifecycleTelemetry`
meten gemiddelde/percentiel-frametijd, dropped frames en zichtbaarheids-levenscyclus (debug-only).

Reeds doorgevoerde optimalisaties (`docs/Eisen/ACT-020`): fps-cap op 60, in-memory bitmap-cache met
downsampling, WorkManager-constraints op de vier achtergrondtaken, `RGB_565`-bitmaps op low-RAM
toestellen (behalve dieptekaarten).

### 3.8 Actualiteitsindicator

`DataFreshness.kt`: relatieve "laatst bijgewerkt X geleden"/verouderd-markering, zowel voor de
achtergrondfoto als voor de weergegevens.

---

## 4. Locatiebeheer

Drie soorten locaties, elk met een eigen rol in zowel weer als wallpaperfoto's. Het onderscheid zit in
`livewallpaperweather.domain.location.model.Location`:

```kotlin
val isCurrentPosition: Boolean = false
val isFictional: Boolean
    get() = country.equals("Fictief", ignoreCase = true) || countryCode.equals("ZZ", ignoreCase = true)
```

1. **Huidige locatie (GPS)** (`isCurrentPosition = true`) — volgt de live GPS-positie; gebruikt voor
   zowel weer als wallpaperfoto's van de fysieke plek van het toestel.
2. **Vaste locaties** — handmatig toegevoegd/gezocht via `ui/search/SearchActivity.kt`, vaste
   coördinaten; weer én foto's blijven bij die eigen plaats horen, ongeacht de fysieke toestelpositie.
3. **Fictieve locaties** (`isFictional = true`, land `"Fictief"`, landcode `"ZZ"`) — niet-bestaande
   plekken. Ook dít is een echte zoek-/toevoegflow, niet alleen een interne marker:
   `SearchActivityRepository.searchLocationList()` combineert reguliere zoekresultaten met
   `searchFictionalCities(query)`, die een apart RemoveSky-eindpunt bevraagt
   (`/api/v1/manage/cities/table?q=...&is_fictional=1`). Het weer gebruikt de echte GPS-positie alleen
   voor dag/nacht/seizoenscontext; de foto's blijven altijd bij de fictieve locatie's eigen identiteit
   horen.

- **Locatiewissel/-volgorde**: de bovenste (eerste) locatie in de lijst bepaalt welke locatie de
  zichtbare live wallpaper aanstuurt en op de achtergrond wordt ververst.
- **Locatie-instellingen**: `ui/settings/compose/LocationSettingsScreen.kt` (locatieservice,
  zoekbronconfiguratie).

---

## 5. Radar / Buienradar

🔶 *`docs/Eisen/RADAR.md` is verouderde upstream-tekst en niet van toepassing op deze fork — de fork heeft
wél een volwaardig eigen radarscherm, hieronder beschreven op basis van de huidige code.*

`radar/RadarActivity.kt` (Compose) biedt drie omschakelbare radarbronnen via tabs/filter-chips:

- **RainViewer** — wereldwijde geanimeerde radarkaart (`RainViewerRadarSource.kt`, `RadarModels.kt`:
  `RadarFrame`/`RadarFrames`).
- **Buienradar** — Nederlandse/Belgische neerslagtrend + kaart (`BuienradarNowcastSource.kt`, beperkt tot
  NL/Benelux, zet Buienradar-intensiteitscodes 0–255 om naar mm/u; bevat verplichte
  attributievermelding). Inclusief neerslagtrendgrafiek (`RainTrendChart.kt`).
- **Ventusky** — geladen via WebView.

Bronnen worden geladen via `RadarWebMapLoader.kt` (`loadBuienradar()`, `loadVentusky()`). Tikken op de
ingesloten Buienradar-kaart opent de echte Buienradar-app (`com.supportware.Buienradar`) of anders de
website, via `openBuienradarAppOrWebsite()`.

**Optionele radartegel op het hoofdscherm**: een compacte, niet-interactieve RainViewer-preview, toe te
voegen via Instellingen → Tegels (`CardDisplay.CARD_RADAR`-enumwaarde); een tik opent het volledige
radarscherm. Uiterlijk instelbaar via `ui/settings/activities/RadarTileSettingsActivity.kt`.

---

## 6. Meldingen

- **App-update-melding met changelog**: bij openen van de app na een update (nooit bij schone install),
  mits "App-update" aan staat, verschijnt een melding "geüpdatet naar versie X" met de eerste
  changelog-regel als preview. 🔶 De changelog zelf (`ReleaseNotesActivity.kt` +
  `common/update/GithubReleaseNotesSource.kt`) komt niet meer van GitHub, maar uit een lokaal gebundelde
  `release-notes.json` die bij het bouwen wordt gegenereerd. De daadwerkelijke "is er een nieuwe
  versie"-check loopt via een apart mechanisme:
  - `docs/Eisen/ACT-015`: backend-eindpunt `POST /updateapp` (FastAPI) — ontvangt `versionCode`, scant een
    `updates/`-map op de hoogste beschikbare versie, geeft `204` (up-to-date) of streamt de nieuwste APK.
  - `docs/Eisen/ACT-016`: in-app OTA — bij koude start post de app zijn `versionCode` naar een eigen
    HTTPS-server op het lokale netwerk (self-signed certificaat via een eigen `X509TrustManager`); bij
    `200` wordt de APK gedownload naar de cache en via `PackageInstaller`/`FileProvider` geïnstalleerd
    (met dialoog als "onbekende bronnen installeren" nog niet is toegestaan). Check gebeurt alleen bij
    app-start, geen achtergrondpolling.
- **"Weetjes"-meldingen**: bij lang genoeg "verblijven" (dwell) op een locatie toont de app een leuk
  feitje (`wallpaper/photo/WeetjeManager.kt`/`WeetjeDwellChecker.kt`/`WeetjeStore.kt`). Dwell-tijd en
  dagmaximum zijn instelbaar; opeenvolgende meldingen binnen dezelfde dwell-sessie worden gelijkmatig
  over de dag verspreid. 🔶 Stille uren (20:00–08:00) zijn **hardcoded**, niet gebruikersinstelbaar — dit
  is een bewuste, harde regel in de code, in tegenstelling tot dwell-tijd/dagmaximum.
- **Weerwaarschuwingen**: meldingen bij nieuwe ernstige waarschuwingen, alleen voor de eerste
  (primaire) locatie; geen meldingen bij minimale ernst, geüpdatete bestaande waarschuwingen, of als de
  bron geen waarschuwingen ondersteunt voor dat land.
- **Neerslagmeldingen (nowcasting)**: waarschuwt voor neerslag binnen enkele minuten, indien de bron dat
  ondersteunt; alleen voor de eerste locatie.
- **Dagvoorspellingsmeldingen**: "Voorspelling voor vandaag" en "voor morgen"
  (`background/forecast/TodayForecastNotificationJob.kt`, `TomorrowForecastNotificationJob.kt`), elk
  apart aan/uit met een eigen instelbaar tijdstip.
- **"Vind mijn telefoon"** (`background/findmyphone/FindMyPhoneService.kt`,
  `FindMyPhoneCalibrator.kt`/`FindMyPhoneStore.kt`): achtergrondservice die via de microfoon luistert.
  🔶 Klap-detectie vereist **3 klappen binnen een tijdvenster** (RMS-onset-detector), niet één klap;
  fluit-detectie gebruikt een autocorrelatie-toonhoogteschatter binnen een (evt. gekalibreerde)
  frequentieband en wordt kort onderdrukt na een klap om kruisactivatie te voorkomen. "Klap" en "Fluit"
  zijn los in/uit te schakelen, met een kalibratiedialoog (`ui/settings/compose/FindMyPhoneCalibrationDialog.kt`).
  🔶 **Functie staat momenteel uit**: `FindMyPhoneStore.FEATURE_ENABLED = false`
  (`FindMyPhoneStore.kt:107`) verbergt de bijbehorende instellingenschakelaar volledig in
  `NotificationsSettingsScreen.kt`. De code is af en functioneel correct volgens bovenstaande
  beschrijving, maar is voor eindgebruikers niet bereikbaar totdat deze vlag op `true` wordt gezet.

---

## 7. Instellingenschermen

Alle onderstaande schermen zijn geverifieerd aanwezig (paden onder `com.liveweatherwallpaperapp`):

- **Hoofdinstellingen** (`ui/settings/compose/RootSettingsScreen.kt`) — ingang naar alle onderstaande
  schermen, plus de setup-wizard.
- **Live wallpaper instellen** (`wallpaper/LiveWallpaperConfigActivity.kt`, let op: niet in `ui/settings`
  maar in `wallpaper/`) — zie §2/§3.
- **Achtergrondupdates** (`BackgroundUpdatesSettingsScreen.kt`) — ververinterval, waarschuwing bij
  "nooit", probleemoplossing.
- **Uiterlijk** (`AppearanceSettingsScreen.kt`), **Hoofdscherm** (`MainScreenSettingsScreen.kt`),
  **Meldingen** (`NotificationsSettingsScreen.kt`), **Modules** (`ModulesSettingsScreen.kt`),
  **Locatie** (`LocationSettingsScreen.kt`), **Weerbronnen** (`WeatherSourcesSettingsScreen.kt`),
  **Eenheden** (`UnitSettingsScreen.kt`) — Composable-schermen, aangestuurd via `SettingsScreenRouter.kt`.
- **Tegels/widgets**: `ui/settings/activities/TileAppearanceActivity.kt`, `WidgetTileSelectActivity.kt`,
  `RadarTileSettingsActivity.kt`.
- **Debug**: `DebugSettingsScreen.kt` (compose), `WorkerInfoActivity.kt`.
- **Overige**: `PrivacyPolicyActivity.kt`, `DependenciesActivity.kt`, `ReleaseNotesActivity.kt`,
  `PreviewIconActivity.kt`, en een tweede `WeatherVistaPrivacyPolicyActivity.kt` (mogelijk voor een
  whitelabel-/submerkvariant).

---

## 8. Navigatie

- Kaartblokken op het hoofdscherm zijn aantikbaar en openen een detailscherm (`ui/details/`).
- Het radarscherm is bereikbaar via de werkbalk en (indien ingeschakeld) via de compacte radartegel;
  bronwissel gebeurt via tabs binnen dat scherm.
- Navigatie tussen hoofdscherm en detail-/radarscherm gebruikt een doorzichtig venster-thema met
  crossfade, zodat er geen placeholder-achtergrond flitst.
- **Grote schermen** (`res/layout-w640dp/`, `res/menu-w640dp/`): een permanent zijmenu met de
  locatielijst naast het detailvenster, in plaats van een onderbalk — bevestigd aanwezig
  (`activity_main.xml`, `fragment_home.xml` in `layout-w640dp`; `MainActivity.kt` verwijst naar
  `w640dp`/`sw640dp`).

---

## 9. Camera — lucht fotograferen

`ui/camera/CameraActivity.kt` (één Activity, CameraX `Preview`/`ImageCapture`, geen Fragments/Compose,
geen ViewModel — alle state als velden op de Activity) laat de gebruiker de lucht fotograferen (met een
horizonlijn als hulplijn, `camera_horizon_guide`) of een foto uit de galerij importeren. De foto wordt
geüpload naar RemoveSky (`WallpaperRepository.uploadCameraPhoto()` → `RemoveSkyProvider.uploadFile()`,
synchrone OkHttp-call), waarna pass/fail-validatie wordt getoond (lucht bovenin, buiten, kleur, GPS,
datum, dag/nacht, seizoen — via `RemoveSkyProvider.checkImage()`) voordat de foto eventueel de actieve
wallpaperfoto wordt.

**Bekende UX-knelpunten** (gedetailleerd in `docs/Eisen/camera-improvement.md`, nog niet opgelost):
1. Geen zichtbare voortgang tijdens upload — alleen een statische "Foto uploaden..."-tekst met
   indeterminate spinner.
2. Geen beoordelingsmoment vóór upload — de upload start automatisch na het maken van de foto.
3. De originele foto wordt twee keer getoond op het resultaatscherm (preview-stap + thumbnail op de
   resultaatkaart).
4. Na een succesvolle losse camera-upload verschijnen geen "Opslaan"- of "Sluiten"-knoppen — alleen
   "Herkansing" (in de galerij-uploadflow wél, via `setLiveWallpaperButton`/`closeButton`).

Een viertraps-implementatieplan (preview vóór upload, stapsgewijze statusweergave, één foto op het
resultaatscherm, Opslaan/Sluiten-knoppen toevoegen) staat uitgewerkt in `camera-improvement.md` maar is
nog niet uitgevoerd.

---

## 10. RemoveSky-backend

RemoveSky is een zelf-gehoste achtergrond-verwijderingsservice (`https://removesky.vanburik.info/api/v1`),
de motor achter de fotografische wallpaper-achtergrond. Bevestigde API-oppervlak (`RemoveSkyProvider.kt`):

- **`POST /upload`** (multipart: bestand of URL, plus locatie/lat/lon/opnametijd/FCM-token) — levert direct
  een verwerkte, transparante PNG terug, óf (bij camera-uploads) een `status: "pending"`-respons waarna het
  resultaat later via een FCM-push binnenkomt.
- **`GET /check?url=`** — synchrone sky-/CLIP-diagnostiek op een reeds gehoste afbeelding: `ok`, `reason`
  (bijv. `no_sky_at_top`), luchtfractie, en een `checks`-object (lucht bovenin, buiten, stedelijk, kleur,
  GPS, datum, dag/nacht, seizoen) — komt vrijwel één-op-één overeen met de validatiecriteria die de
  gebruiker in de camera-flow te zien krijgt.
- **`GET /search`** — zoekt reeds verwerkte afbeeldingen op locatie/coördinaten, met een
  `already_processed`-vlag zodat de client kan hergebruiken zonder opnieuw te uploaden.
- **`GET /health`**, **`GET /version`** — gezondheidscheck (gebruikt door de achtergrond-refreshworker).
- **`GET /weetjes/nearby`**, **`POST /weetjes/request-more`** — de "weetjes"-feiten-functie.
- **`POST /fcm/register`**, **`GET /removed`** — pushregistratie en reconciliatie van door een curator
  verwijderde foto's.
- Bekend configuratiepunt: de backend geeft soms `http://` in plaats van `https://` terug in
  verwerkte-afbeelding-URL's; de client corrigeert dit zelf (`normalizeServiceUrl()`).

Als een foto (uit automatische verversing of handmatige camera-/galerij-upload) niet voldoet aan de eisen
voor een buiten-landschapsfoto met lucht bovenin, wordt deze afgewezen en niet actief als wallpaper
gebruikt.

---

## 11. Overige onderdelen

- **Over-scherm** (`ui/about/AboutScreen.kt`/`AboutActivity.kt`/`AboutViewModel.kt`) — versie-/
  buildinfo, credits, medewerkers, links.
- **Changelog-scherm** (`ReleaseNotesActivity.kt`) — leest lokale `release-notes.json` (zie §6).
- **Widgets** (`background/receiver/widget/`): Material You-widgets (actueel/voorspelling),
  multi-stad-widget, dag/week/uur/dagtrend-widgets, klok-varianten (dag/week/uur/statistieken/
  detailweergave), een platte-tekstwidget. 🔶 De "weetje"-widget-alarm is, in tegenstelling tot wat
  `APP_FUNCTIES.md` als "gepland" omschrijft, al geïmplementeerd (`WidgetFactAlarmReceiver.kt`,
  `WidgetFactAlarmScheduler.kt`).
- **Quick Settings-tegel**: `background/interfaces/TileService.kt`.
- **Gadgetbridge-integratie** (`sources/gadgetbridge/GadgetbridgeService.kt` + JSON-modellen voor
  dag-/uurvoorspelling en luchtkwaliteit): weergegevens naar de Gadgetbridge-wearable-companion-app,
  aangestuurd vanuit `WeatherUpdateJob`.
- **Hoofdnavigatiestructuur**: op telefoons locatielijst + hoofdweerpager; op grote schermen
  (`layout-w640dp`) een permanent zijmenu (zie §8).
- **Gevraagde rechten** (`AndroidManifest.xml`, bevestigd): `ACCESS_FINE/COARSE/BACKGROUND_LOCATION`
  (GPS), `CAMERA` (lucht fotograferen), `RECORD_AUDIO` (Vind mijn telefoon), `POST_NOTIFICATIONS`
  (Android 13+), `WRITE_EXTERNAL_STORAGE`(legacy)/`READ_MEDIA_IMAGES`/`READ_MEDIA_VISUAL_USER_SELECTED`/
  `ACCESS_MEDIA_LOCATION` (galerij-import), `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_DATA_SYNC`
  (Vind mijn telefoon), plus `INTERNET`, `ACCESS_NETWORK_STATE`/`ACCESS_WIFI_STATE`,
  `RECEIVE_BOOT_COMPLETED`, `SET_ALARM`, `SCHEDULE_EXACT_ALARM`,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `EXPAND_STATUS_BAR`.
- **Admin-/testschermen**: `ui/about/TestSettingsActivity.kt` (developer-/QA-tooling, geen
  eindgebruikersfunctie). Daarnaast bestaat een **losse, niet-Android backend-testtool** voor de
  fotosorteervolgorde (`docs/Eisen/ACT-019`): een FastAPI-beheerpagina (`/test/sortorder`, poort 12345,
  zelfde server als de update-endpoint uit §6) waarmee een beheerder sorteerkolommen (GPS met
  radiuskeuze, aanmaakdatum, status, dag/nacht, weertype) kan aan/uitzetten, herordenen, wegen en
  simuleren tegen testcoördinaten/-tijdstip — puur een simulatie-/testomgeving, raakt de
  productie-sorteerlogica van de app niet.

---

## Bijlage: overzicht van kernbestanden per onderdeel

| Onderdeel | Kernbestanden (onder `app/src/main/kotlin/com/liveweatherwallpaperapp/`, tenzij anders vermeld) |
|---|---|
| Weerupdate | `background/weather/WeatherUpdateJob.kt`, `AdaptiveRefreshDecision.kt`, `RefreshHelper.kt` |
| Wallpaper-foto's | `wallpaper/photo/RemoveSkyProvider.kt`, `WallpaperImageStore.kt`, `WallpaperPhotoRefreshWorker.kt`, `WallpaperPhotoRefreshPlanner.kt`, `SortLocationRecs.kt`, `CustomSortLogic.kt`, `WallpaperRepository.kt`, `data/.../wallpaper/WallpaperPhotoRepository.kt` |
| Wallpaper-rendering | `wallpaper/WallpaperEffectView.kt`, `MaterialLiveWallpaperService.kt`, `WallpaperWeatherEffectRenderer.kt`, `WallpaperSceneState.kt`, `WallpaperSceneSnapshot.kt`, `TransitionManager.kt`, `CloudField.kt`, `CloudEngineAdapter.kt`, `cloud-engine/.../CloudEngineRenderer.kt`, `GlassRainField.kt`, `WallpaperParticlePool.kt`, `FogField.kt`, `StarField.kt`, `CelestialGlow.kt`, `CelestialTiming.kt`, `SkyColors.kt`, `WallpaperSeasonGrading.kt`, `WallpaperQualityProfile.kt`, `FrameTelemetry.kt`, `DataFreshness.kt` |
| Locatiebeheer | `livewallpaperweather.domain.location.model.Location`, `ui/search/SearchActivityRepository.kt`, `ui/settings/compose/LocationSettingsScreen.kt` |
| Radar | `radar/RadarActivity.kt`, `RainViewerRadarSource.kt`, `BuienradarNowcastSource.kt`, `RadarWebMapLoader.kt`, `RainTrendChart.kt`, `ui/settings/activities/RadarTileSettingsActivity.kt` |
| Meldingen | `wallpaper/photo/WeetjeManager.kt`, `WeetjeDwellChecker.kt`, `WeetjeStore.kt`, `background/forecast/TodayForecastNotificationJob.kt`, `TomorrowForecastNotificationJob.kt`, `background/findmyphone/FindMyPhoneService.kt`, `FindMyPhoneCalibrator.kt` |
| Camera | `ui/camera/CameraActivity.kt` |
| Instellingen | `ui/settings/compose/RootSettingsScreen.kt` + de losse `*SettingsScreen.kt`-schermen, `ui/settings/activities/*Activity.kt` |
| Widgets/overig | `background/receiver/widget/*.kt`, `background/interfaces/TileService.kt`, `sources/gadgetbridge/GadgetbridgeService.kt`, `ui/about/AboutActivity.kt` |
