# LiveWeatherApp — Functieoverzicht

Overzicht van alle functionaliteit in de app (`com.liveweatherwallpaperapp`, een fork van Breezy
Weather). Voor veel onderdelen bestaat al een diepgaande spec in `docs/Eisen/ACT-*.md` — dit
document is de brede kaart; de ACT-documenten zijn de detailkaarten.

## Inhoudsopgave

1. [Weergave van het weer](#1-weergave-van-het-weer)
2. [Live wallpaper — achtergrondfoto's](#2-live-wallpaper--achtergrondfoto's)
3. [Live wallpaper — rendering en effecten](#3-live-wallpaper--rendering-en-effecten)
4. [Locatiebeheer](#4-locatiebeheer)
5. [Radar / Buienradar](#5-radar--buienradar)
6. [Meldingen](#6-meldingen)
7. [Instellingenschermen](#7-instellingenschermen)
8. [Navigatie (details, radar)](#8-navigatie-details-radar)
9. [Camera — lucht fotograferen](#9-camera--lucht-fotograferen)
10. [RemoveSky-backend](#10-removesky-backend)
11. [Overige onderdelen](#11-overige-onderdelen)

---

## 1. Weergave van het weer

- **Hoofdscherm** — header met huidige conditie/temperatuur/gevoelstemperatuur en herschikbare
  glasachtige kaartblokken (waarschuwingen, nowcasting neerslag, dag-/uurtrends, neerslag, wind,
  luchtkwaliteit, pollen, luchtvochtigheid, UV, zicht, luchtdruk, zon, maan, optioneel een klok).
  Zie `docs/Eisen/HOMEPAGE.md`. Code: `ui/main/`,
  `ui/settings/activities/CardDisplayManageActivity.kt`,
  `DailyTrendDisplayManageActivity.kt`, `HourlyTrendDisplayManageActivity.kt`.
- **Dag-/uurdetailtabs** — conditie, luchtkwaliteit, wind, UV, neerslag, zonneschijn,
  gevoelstemperatuur, luchtvochtigheid/dauwpunt, luchtdruk, bewolking, zicht. Code: `ui/details/`,
  `ui/main/adapters/trend/`.
- **Dagdetails / graaddagen** — verwarmings-/koelingsgraaddagen per dag. Zie
  `docs/Eisen/DAY_DETAILS.md`.
- **Weerbronnen** — 40+ nationale/wereldwijde bronnen (Open-Meteo, AccuWeather, KNMI, DMI, NWS,
  JMA, ...), per locatie te kiezen voor voorspelling/actueel/luchtkwaliteit/pollen/nowcasting/
  waarschuwingen/normalen/adreszoeken. Volledig gecatalogiseerd in `docs/Eisen/SOURCES.md`.
- **Weerupdate-proces** — achtergrondrefresh (± elke 1,5 uur, instelbaar), handmatige
  pull-to-refresh, per-feature cachevensters. Zie `docs/Eisen/UPDATES.md`. Code:
  `background/weather/WeatherUpdateJob.kt`, `AdaptiveRefreshDecision.kt`.

## 2. Live wallpaper — achtergrondfoto's

- **RemoveSky-fotoservice** — per locatie haalt de app landschapsfoto's zonder lucht op bij de
  RemoveSky-backend (`wallpaper/photo/RemoveSkyProvider.kt`), zet ze om naar WebP en cachet ze
  lokaal per locatie (`WallpaperImageStore.kt`). Zie ook §10 en
  `docs/Eisen/RemoveSky integratie - getest schema.md`.
- **Automatische fotoverversing** — `WallpaperPhotoRefreshWorker` (WorkManager) haalt periodiek
  een nieuwe foto per locatie op, rekening houdend met netwerk/batterij, cachelimieten en
  geschiedenis, zodat foto's roteren zonder dat de wallpaper zelf netwerk-/GPS-calls doet. Zie
  `docs/Eisen/ACT-010 - Centrale automatische foto-refresh.md`.
  - **Vaste/fictieve locaties gebruiken altijd hun eigen coördinaten**, nooit de live
    GPS-positie van het toestel — alleen de actieve "huidige locatie" (current position) krijgt
    een verse GPS-fix. Dit voorkomt dat een foto van de fysieke locatie van het toestel onterecht
    onder een andere (vaste of fictieve) locatie terechtkomt.
  - **Directe refresh bij locatiewissel** — het wisselen, bewerken of verwijderen van de actieve
    (bovenste) locatie triggert direct een verversing, in plaats van te wachten op de volgende
    geplande cyclus.
  - **Eenmalige hotfix (build ≤ 2026.07.27.436)** — bestaande installaties met al gecachte foto's
    kregen automatisch één keer een volledige cache-wipe, zodat elke locatie na de bovenstaande
    fix opnieuw en schoon synchroniseert. Nieuwe installaties merken hier niets van.
- **Fotosortering/-selectie** — een centrale sorteer-/selectieflow
  (`wallpaper/photo/SortLocationRecs.kt`, `WallpaperPhotoPriority.kt`, `CustomSortLogic.kt`)
  kiest welke gecachte foto per locatie als volgende wordt getoond, op basis van recentheid,
  weergavefrequentie en afstand; gedraagt zich anders voor GPS-locaties dan voor vaste/fictieve
  locaties. Zie `docs/Eisen/ACT-021 - Herziening foto-sortering en -selectie.md`,
  `customsortlogic.md`, `ANALYSE - Sorteerfunctie wallpaper foto's.md`.
- **"Ververs nu" / "Volgende"-knoppen** (Instellingen → Live wallpaper instellen) — "Ververs nu"
  vraagt de RemoveSky-backend om een nieuwe foto; "Volgende" schakelt in plaats daarvan naar de
  volgende al gedownloade foto in de lokale rotatiepool, zonder netwerkverkeer — handig als de
  backend niet bereikbaar is. Code: `LiveWallpaperConfigActivity.kt` (`runRefresh()`/`runNext()`).
- **Cachegrootte- en limietinstellingen** — schuifregelaars voor cachelimiet (MB), maximaal
  aantal foto's per locatie, en het aantal recent getoonde foto-URL's dat onthouden/vermeden
  wordt ("herhaling voorkomen"), plus een "Cache legen"-actie (per locatie of alles). Code:
  `LiveWallpaperConfigActivity.kt`, `WallpaperImageStore.kt`.
- **Aan/uit-schakelaar en ververinterval** — de fotoachtergrond kan volledig worden uitgezet;
  het ververinterval (in minuten) is instelbaar.

## 3. Live wallpaper — rendering en effecten

- **Tilt-gebaseerde parallax** — schakelaar (`parallaxEnabled`) die de achtergrondfoto/-scene laat
  meebewegen met het kantelen van het toestel (Rotation Vector Sensor), voor een dieptegevoel.
  Bewust gebaseerd op toestelkanteling in plaats van camera/gezichtsherkenning (privacy/batterij).
  Code: `MaterialLiveWallpaperService.kt`, `WallpaperSceneRenderer.kt`.
- **Wolkenrenderer** — procedureel drijvende wolken (`CloudField.kt`, `CloudEngineAdapter.kt`,
  `CloudTuningActivity.kt`/`CloudTuningWeatherTypes.kt` voor het afstemmen van het wolkbeeld per
  weertype). Zie `docs/Eisen/ACT-003 - clouds.md` en
  `docs/Eisen/ACT-018 - Rendering pipeline en architectuur overzicht.md`. Bevat een verborgen
  "easter egg"-variant (willekeurige extra wolken, laag opaciteit, snelheid gekoppeld aan
  windsnelheid) die bewust geen instelling is — het moet een verrassing blijven.
- **Weeranimaties/-effecten** — regen-op-glas (`GlassRainField.kt`), sneeuw-/hagelpartikels
  (`WallpaperParticlePool.kt`, `WallpaperParticleTrajectory.kt`), mist-/waasdieptelagen
  (`FogField.kt`), zon-/maangloed (`CelestialGlow.kt`, `CelestialTiming.kt`), sterrenhemel 's
  nachts (`StarField.kt`, zie `docs/Eisen/ACT-014 - sterrenhemel.md`), luchtkleurovergangen
  (`SkyColors.kt`) — allemaal gecoördineerd via een centrale scene-state/renderer
  (`WallpaperSceneState.kt`, `WallpaperSceneSnapshot.kt`, `TransitionManager.kt`). Zie
  `docs/Eisen/ACT-001` en `ACT-002`.
- **Seizoensgrading** — optionele subtiele seizoensgebonden kleur-/lichtaanpassing (kouder in de
  winter, warmer in de zomer), met aan/uit-schakelaar en sterkteregelaar; experimenteel. Zie
  `docs/Eisen/ACT-012 - Seizoensgrading als experiment.md`. Code: `WallpaperSeasonGrading.kt`.
- **Kwaliteitsprofielen / performance** — rendering past zich aan aan de mogelijkheden van het
  toestel. Zie `docs/Eisen/ACT-007` en `ACT-020 - Batterij- en prestatie-optimalisatie.md`. Code:
  `WallpaperQualityProfile.kt`, `FrameTelemetry.kt`.
- **Actualiteitsindicator** — toont een relatieve "laatst bijgewerkt X geleden"/verouderd-markering
  voor zowel de achtergrondfoto als de weergegevens. Zie `docs/Eisen/ACT-011`. Code:
  `DataFreshness.kt`.
- **Vloeiende overgangen** — geen harde wissel bij achtergrondwijziging (crossfade in plaats van
  hard swap) en een crossfade + fade-timing bij navigatie tussen hoofdscherm en detail-/
  radarscherm, zodat er geen "flits" van een placeholder-achtergrond zichtbaar is.

## 4. Locatiebeheer

Er zijn drie soorten locaties, elk met een eigen rol in zowel het weer als de wallpaperfoto's:

1. **Huidige locatie (GPS)** — volgt de live GPS-positie van het toestel; wordt gebruikt voor
   zowel het weer als de wallpaperfoto's van de plek waar je daadwerkelijk bent.
2. **Vaste locaties ("vaste locatie")** — handmatig toegevoegde/gezochte plaatsen (bijv. Rome) met
   vaste coördinaten, toegevoegd via `ui/search/`. Weer én foto's blijven altijd bij die eigen
   plaats horen, ook als het toestel zich fysiek ergens anders bevindt.
3. **Fictieve locaties** — niet-bestaande plekken (bijv. een Ghibli-achtige plaats), intern
   gemarkeerd met land `"Fictief"` (`SearchActivityRepository.FICTIONAL_COUNTRY`). Het weer
   gebruikt de echte GPS-positie alleen voor dag/nacht/seizoenscontext; de foto's blijven altijd
   bij de fictieve locatie's eigen identiteit horen, nooit bij de fysieke GPS-plek.

- **Locatiewissel/-volgorde** — de bovenste (eerste) locatie in de lijst bepaalt welke locatie de
  zichtbare live wallpaper aanstuurt en wat er op de achtergrond wordt ververst; de lijst
  herschikken verandert dus welke locatie prioriteit krijgt. Zie `docs/Eisen/UPDATES.md` en
  `ACT-021`.
- **Locatie-instellingen** — `ui/settings/compose/LocationSettingsScreen.kt`
  (locatieservice, zoekbron-configuratie).

## 5. Radar / Buienradar

- **Radarscherm** — `radar/RadarActivity.kt` biedt drie omschakelbare radarbronnen: RainViewer
  (wereldwijde geanimeerde radarkaart), Buienradar (Nederlandse/Belgische neerslagtrend +
  kaart, via `BuienradarNowcastSource.kt`) en Ventusky. Inclusief een neerslagtrendgrafiek
  (`RainTrendChart.kt`).
- **Doortikken naar de Buienradar-app** — de ingesloten Buienradar-kaartweergave opent bij een
  tik de echte Buienradar-app (`com.supportware.Buienradar`), of anders de website, via
  `RadarWebMapLoader.openBuienradarAppOrWebsite()`.
- **Optionele radartegel op het hoofdscherm** — een compacte, niet-interactieve RainViewer-
  radarpreview kan aan het hoofdscherm worden toegevoegd via Instellingen → Tegels; een tik erop
  opent het volledige radarscherm. Zie `docs/Eisen/ACT-017 - Optionele regenradartegel.md` en
  achtergrond in `docs/Eisen/RADAR.md`.
- **Radartegel-uiterlijk** — `ui/settings/activities/RadarTileSettingsActivity.kt`.

## 6. Meldingen

- **App-update-melding met changelog** — controleert op nieuwe releases; bij het openen van de
  app na een update (nooit bij een schone install) verschijnt, mits de instelling
  "App-update" aan staat, een melding met de titel "geüpdatet naar versie X" en de eerste
  changelog-regel als preview. Tikken op de melding opent het volledige changelogscherm
  (`ReleaseNotesActivity.kt`, bron: `GithubReleaseNotesSource.kt`). Er wordt daarnaast in-app
  OTA-auto-update ondersteund via een eigen HTTPS-server — zie `docs/Eisen/ACT-015` en
  `ACT-016 - In-app OTA Auto-update via Eigen HTTPS Server.md`.
- **"Weetjes"-meldingen** — zodra je lang genoeg op een locatie "verblijft" (dwell), toont de app
  een leuk feitje over die plek (`WeetjeManager.kt`/`WeetjeDwellChecker.kt`/`WeetjeStore.kt`);
  volgende meldingen binnen dezelfde dwell-sessie worden gelijkmatig over de dag verspreid (met
  een dagmaximum) en slaan stille uren over.
- **Weerwaarschuwingen** — meldingen bij nieuwe ernstige waarschuwingen, alleen voor de eerste
  (primaire) locatie. Zie `docs/Eisen/UPDATES.md`.
- **Neerslagmeldingen (nowcasting)** — waarschuwt voor neerslag die binnen enkele minuten begint,
  als de weerbron dat ondersteunt.
- **Dagvoorspellingsmeldingen** — "Voorspelling voor vandaag" en "voor morgen", elk apart
  aan/uit te zetten met een eigen instelbaar tijdstip
  (`TodayForecastNotificationJob.kt`, `TomorrowForecastNotificationJob.kt`).
- **"Vind mijn telefoon" (klap/fluit-detectie)** — een achtergrondservice
  (`FindMyPhoneService.kt`) luistert via de microfoon naar een klap of een fluittoon en laat de
  telefoon overgaan/trillen om hem terug te vinden; "Klap" en "Fluit" zijn los in/uit te
  schakelen, met een kalibratiedialoog (`FindMyPhoneCalibrationDialog.kt`).

## 7. Instellingenschermen

- **Hoofdinstellingen** (`RootSettingsScreen.kt`) — ingang naar: Live wallpaper instellen, Live
  wallpaper modules, Achtergrondupdates, Uiterlijk, Hoofdscherm, Meldingen, Modules, Locatie,
  Weerbronnen, Debug.
- **Live wallpaper instellen** (`LiveWallpaperConfigActivity.kt`) — zie §2/§3: weertype-
  override, dag/nacht-override, animaties aan/uit, parallax aan/uit, seizoensgrading aan/uit +
  sterkte, fotocache-instellingen, ververs-/volgende-/cache-legen-knoppen.
- **Achtergrondupdates** (`BackgroundUpdatesSettingsScreen.kt`) — ververinterval en
  probleemoplossing voor de periodieke weerupdate-worker.
- **Uiterlijk** (`AppearanceSettingsScreen.kt`) — thema/donkere modus, alarmlijnen in
  trendgrafieken, icoonpakket.
- **Hoofdscherm** (`MainScreenSettingsScreen.kt`) — zichtbaarheid/volgorde van kaartblokken,
  temperatuuranimatie, klokweergave.
- **Meldingen** (`NotificationsSettingsScreen.kt`) — alle schakelaars uit §6, plus het aanvragen
  van meldingstoestemming.
- **Modules** (`ModulesSettingsScreen.kt`) — optionele functiemodules aan/uit.
- **Locatie** (`LocationSettingsScreen.kt`) — locatie-/zoekbronconfiguratie.
- **Weerbronnen** (`WeatherSourcesSettingsScreen.kt`) — API-sleutels en voorkeuren per bron.
- **Eenheden** (`UnitSettingsScreen.kt`) — keuze van meeteenheden.
- **Tegels/widgets** (`TileAppearanceActivity.kt`, `WidgetTileSelectActivity.kt`) — welke
  tegels/widgets getoond worden en hun uiterlijk.
- **Debug** (`DebugSettingsScreen.kt`, `WorkerInfoActivity.kt`) — status/diagnostiek van
  achtergrondworkers, debug-schakelaars.
- **Overige**: Privacybeleid (`PrivacyPolicyActivity.kt`), Licenties (`DependenciesActivity.kt`),
  Changelog (`ReleaseNotesActivity.kt`).

## 8. Navigatie (details, radar)

- Kaartblokken op het hoofdscherm zijn aantikbaar en openen een detailscherm (`ui/details/`),
  zie `docs/Eisen/HOMEPAGE.md`.
- Het radarscherm is bereikbaar via de werkbalk en (indien ingeschakeld) via de compacte
  radartegel op het hoofdscherm; bronwissel (RainViewer/Buienradar/Ventusky) gebeurt via tabs
  binnen dat scherm.
- Navigatie tussen hoofdscherm en detail-/radarscherm gebruikt een doorzichtig venster-thema met
  crossfade zodat er geen placeholder-achtergrond flitst (zie §3, "Vloeiende overgangen").

## 9. Camera — lucht fotograferen

- **Lucht fotograferen en uploaden** — `ui/camera/CameraActivity.kt` laat je de lucht
  fotograferen (met een horizonlijn als hulplijn) of een foto uit de galerij importeren; de foto
  wordt geüpload naar RemoveSky (`WallpaperRepository.uploadCameraPhoto()`), waarna
  pass/fail-validatie wordt getoond (lucht bovenin, buiten, kleur, GPS, datum, dag/nacht,
  seizoen) voordat de foto eventueel de actieve wallpaperfoto wordt. Bekende UX-knelpunten
  (geen voortgangsdetail bij upload, geen voorbeeld/afkeur-stap vooraf, dubbele thumbnail,
  ontbrekende Bewaar/Sluit-knoppen) en een verbeterplan staan in
  `docs/Eisen/camera-improvement.md`.

## 10. RemoveSky-backend

RemoveSky is een zelf-gehoste achtergrond-verwijderingsservice
(`https://removesky.vanburik.info/api/v1`). Voor de gebruiker is dit de motor achter de
fotografische wallpaper-achtergrond: foto's (uit de automatische verversing per locatie of een
handmatige camera-/galerij-upload) worden naar de service gestuurd, die de lucht verwijdert en
een gevalideerde, bewerkte afbeelding teruggeeft als de foto voldoet aan de eisen voor een
buiten-landschapsfoto met lucht bovenin. De app cachet het resultaat vervolgens lokaal en
gebruikt het als live-wallpaperachtergrond. Zie `docs/Eisen/RemoveSky integratie - getest
schema.md`.

## 11. Overige onderdelen

- **Over-scherm** (`ui/about/AboutScreen.kt`/`AboutActivity.kt`/`AboutViewModel.kt`) —
  versie-/buildinfo, credits, medewerkers, links.
- **Changelog-scherm** (`ReleaseNotesActivity.kt`) — toont de changelog via
  `GithubReleaseNotesSource.kt`.
- **Widgets** — een groot aanbod homescreen-widgets (`background/receiver/widget/`): Material
  You-widgets (actueel/voorspelling), multi-stad-widget, dag/week/uur/dagtrend-widgets,
  klok-varianten, een platte-tekstwidget, plus een geplande "weetje"-widget-alarm.
- **Quick Settings-tegel** — `background/interfaces/TileService.kt`.
- **Gadgetbridge-integratie** — weergegevens kunnen naar Gadgetbridge (wearable-companion-app)
  gestuurd worden. Zie `docs/Eisen/UPDATES.md`.
- **Hoofdnavigatiestructuur** — op telefoons: locatielijst + hoofdweerpager; op grote schermen
  (`layout-w640dp`): een permanent zijmenu met de locatielijst naast het detailvenster, in plaats
  van een onderbalk.
- **Gevraagde rechten** — locatie (GPS), camera (lucht fotograferen), microfoon ("Vind mijn
  telefoon"), meldingen (Android 13+), opslag/media (galerij-import), voorgrondservice-rechten
  voor "Vind mijn telefoon".
- **Admin-/testschermen** — `ui/about/TestSettingsActivity.kt` en een testpagina voor
  sorteervolgorde, zie `docs/Eisen/ACT-019 - Testpagina sorteervolgorde (admin).md`
  (ontwikkelaars-/QA-gereedschap, geen normale eindgebruikersfunctie).
