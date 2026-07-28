# Batterij-optimalisatie, kleine fixes en fotosortering — 28 juli 2026

Samenvatting van een lange sessie met drie hoofdonderdelen: (1) een
stapsgewijze batterij-/CPU-/GPU-optimalisatie van `LiveWeatherApp` in 3
modules, met gemeten bewijs op fysieke hardware, (2) een aantal kleine,
losstaande fixes in `removesky-service`, en (3) een nog lopende analyse van
waarom foto-sortering voor gebruikers niet klopt, uitgewerkt in concrete
scenario's.

## LiveWeatherApp — batterij-/prestatie-optimalisatie (3 modules)

Aanpak: per module eerst auditten, dan pas fixen, en elke claim bewijzen met
echte metingen op een fysiek toestel (Samsung SM-S928B) i.p.v. aannames.
Volledig rapport met alle metingen: zie
[`docs/ACT-020 - Batterij- en prestatie-optimalisatie (resultatenrapport).md`](../ACT-020%20-%20Batterij-%20en%20prestatie-optimalisatie%20(resultatenrapport).md).

### Module 1 — Live wallpaper service
`MaterialLiveWallpaperService.kt` bleek al correct: 30fps-cap, stopt
rendering bij onzichtbaarheid, geen per-frame allocaties. Buiten scope maar
wel gevonden en gefixt: de **in-app frosted achtergrond**
(`WallpaperEffectView.kt`) liep ongecapt tot 90-120fps op high-refresh-rate
panelen. **Fix:** gecapt op 60fps (`Choreographer.postFrameCallbackDelayed`).
**Gemeten:** 60Hz-paneel → 61.25fps (geen regressie); 120Hz-paneel → ~40fps
(ruim onder de 120fps die het zonder cap zou halen).

### Module 2 — Caching en achtergrondtaken
`WallpaperRepository.kt` decodeerde bitmaps bij elke aanroep opnieuw vanaf
disk, zonder downsampling. **Fixes:**
- in-memory bitmap-cache (path+lastModified-keyed) voor foto en depth-map;
- downsampling (`inSampleSize`) op ~1.5x langste schermzijde;
- `RGB_565` voor de achtergrondfoto op low-RAM-toestellen (nooit de
  depth-map, om precisieverlies in de afstandswaarden te voorkomen);
- netwerk-/batterijconstraints toegevoegd aan 4 voorheen ongeconstrainede
  WorkManager-jobs (`WallpaperPhotoRefreshWorker`, `WeatherUpdateJob`).

### Module 3 — Macrobenchmark-tooling
Nieuwe `:benchmark`-module (`com.android.test`, target `:app`), inclusief
een eigen `breezy.android.test` convention-plugin en een nieuw `benchmark`-
buildtype op `:app` (release-shaped, debug-signed). **Geverifieerd op het
fysieke toestel:** cold-startup `timeToInitialDisplayMs` mediaan 438.5ms
(404-489ms range, 5 iteraties).

**Eerlijke conclusie aan de gebruiker:** dit was vooral een
correctheids-/hygiëneronde. Het enige concreet merkbare verschil is de
frosted-achtergrond-fix, en dat is klein en situationeel (hoge refresh rate
+ app lang open) — geen "batterij gaat nu twee keer zo lang mee".

**Open eindjes (ook in ACT-020):** Module 2 heeft geen eigen voor/na-meting
(alleen functioneel geverifieerd), RGB_565-pad nooit getest op een echt
low-RAM-toestel, en drie extra risicogebieden expliciet benoemd maar niet
opgepakt: GPS-nauwkeurigheid/frequentie, foto-downloads die ook over
mobiele data kunnen lopen (`NetworkType.CONNECTED` i.p.v. wifi-only), en een
langdurige geheugentest.

**Commits (LiveWeatherApp, `strammermax/breezy-weather`):** `d11fd60af`,
`47e0f2277`, `69628f79d`, `11d121c50`, `95342222d`, `3970453c6`.

## removesky-service — kleine fixes

Op verzoek "kleine puntjes fixen in de service laag" is eerst een audit
gedaan (geen grote problemen gevonden), gevolgd door drie fixes:

1. **Pytest kon niet draaien zonder handmatige `PYTHONPATH`** — alle 25
   tests slaagden al, maar waren oncollectable. **Fix:**
   `[tool.pytest.ini_options] pythonpath = ["."]` in `pyproject.toml`.
2. **API-key-vergelijking met `!=` i.p.v. constant-time** — kleine
   timing-attack-surface op `REMOVESKY_API_KEY`/`REMOVESKY_ADMIN_API_KEY`.
   **Fix:** `secrets.compare_digest` in `app/api/deps.py`.
3. **Verwarrende bestandsnamen** — `app/api/v1/test_compare.py` en
   `test_img2time.py` zijn écht gemounte FastAPI-routers, geen tests.
   **Fix:** hernoemd naar `compare.py`/`img2time.py`, imports in
   `router.py` bijgewerkt.

**Commits:** `b721e84`, `0c30eb4`. Gepusht en automatisch gedeployed
(push-naar-main triggert een herstart via de self-hosted CI-runner, geen
aparte release-tag nodig voor dit project). Ook getagd als `v2026.07.17.398`
voor het versienummer.

### Losse bug: filterdropdowns triggerden een tabel-sort
Gebruiker meldde (met screenshot van de Manage-tabel): een filter kiezen in
een dropdown-kolom (bijv. "Locatie") sorteerde de tabel ook, in alle
dropdown-kolommen. **Root cause:** elke `th[data-col]` zonder eigen
`.manage-sortable`-span wordt automatisch tot volledig sorteerbare
kolomkop gepromoveerd (`static/index.html:4110-4114`) — de filter-`<select>`
zit dus *binnen* het klikbare sorteergebied, en een klik daarop borrelt op
naar de `<th>`. **Fix:** de gedeelde `wireSortHeaders`-click-handler
negeert nu clicks die uit een `select`/`input`/`button` komen (zelfde
patroon dat de kolom-resize-handle al had). Trof Manage-, Weetjes- en
Cities-tabel tegelijk (gedeelde functie). **Commit:** `b48193f`, gepusht en
gedeployed.

## Fotosortering — analyse (nog niet afgerond)

Aanleiding: gebruikers klagen dat de "sortering" van achtergrondfoto's niet
klopt. Eerst de huidige procedure end-to-end in kaart gebracht (client +
server), daarna twee concrete scenario's uitgewerkt op een aparte pagina:
**[Foto-sortering — scenario's](https://claude.ai/code/artifact/8a7aaad8-69aa-485b-8def-e32c92442c0f)**
(gepubliceerde Artifact-pagina, geen gitgeversioneerd bestand).

### Wat we ontdekten over de huidige procedure
- `WallpaperPhotoRefreshWorker` draait puur op een vaste tijdklok
  (standaard 30 min, instelbaar 15-180 min) — **geen** locatie- of
  bewegingsdetectie. Elke tick lost gewoon de actuele GPS-fix opnieuw op.
- `refreshFor()` checkt eerst de **lokale cache** (`buildShowlist()` op
  `photoCatalog.getForLocation(...)`); de server wordt pas geraadpleegd als
  de lokale voorraad voor die locatie leeg/uitgeput is.
- De server-side "nieuwe foto's beschikbaar?"-check (`checkForNewPhotos()`)
  wordt **nooit automatisch** aangeroepen — alleen via een handmatige knop
  in "Achtergrondafbeeldingen beheren".
- De backend sorteert kandidaten binnen de zoekstraal op `processed_at
  DESC` (meest recent verwerkt), **niet op afstand** — en heeft geen
  rating/kwaliteitskolom; de duim-omhoog/omlaag-rating is puur lokaal op
  het device (en is in een eerdere sessie überhaupt al verwijderd, zie
  removesky-service issue #12 hierboven/in `260726-bugs-doornemen.md`).

### Scenario 1 — twee dagen thuis, elke 2 uur kijken
Conclusie: de gebruiker ziet vrijwel zeker steeds dezelfde, ooit-gedownloade
set foto's roteren. Foto's die ná het eerste bezoek aan die locatie op de
server zijn toegevoegd, verschijnen niet automatisch.

### Scenario 2 — reisdag Hoofddorp → Bovenkarspel → Amsterdam
Twee losse problemen:
1. **Dekkingsgat:** geen foto's voor het kleine Bovenkarspel → de
   coördinaten-zoekopdracht valt terug op het 10km verderop gelegen
   Enkhuizen, zonder dat kenbaar te maken aan de gebruiker.
2. **Geen reisdetectie:** een toevallige worker-tick tijdens het rijden
   (bijv. bij Lelystad) wordt net zo serieus behandeld als een echte
   bestemming — onnodige serveraanvragen/cache-churn voor een plek die de
   gebruiker een paar minuten later alweer voorbij is.

### Voorgestelde richting (nog niet uitgewerkt/geïmplementeerd)
Gebruiker stelde voor: elke ~5 min positie bepalen, en als de afgeleide
snelheid boven ~45km/u ligt (duidelijk gemotoriseerd verkeer), een
`is_moving`-vlag zetten die de refresh voor die tick overslaat — blijf bij
de laatst getoonde foto tot de snelheid weer zakt. Wandelen en fietsen
blijven ruim onder die drempel, dus daar verandert er niets (nog steeds
leuke wisselende foto's van de omgeving tijdens een boswandeling/fietstocht).

Relevante platform-opties, besproken maar nog niet gekozen/gebouwd:
- **`Location.getSpeed()`** — ligt al klaar in de `Location`-fix die
  `WallpaperLocationResolver` toch al ophaalt via `requestNetworkLocation()`
  (geen GPS-chip, maar WiFi/cell-tower — werkt dus ook met GPS uit, zolang
  locatieservices in het algemeen aanstaan). Geen extra permissie nodig.
- **Activity Recognition API** (Google Play Services) — herkent
  transportmodus met confidence-score i.p.v. rauwe snelheid, robuuster
  (bijv. geen valse "gestopt"-melding bij stilstaan in de trein), maar
  vereist een aparte permissie (`ACTIVITY_RECOGNITION`) en meer opzet.
- **IP-gebaseerde locatie** als laatste redmiddel wanneer alle
  locatieservices uitstaan — bestaat al server-side (`geo.py`, via
  ip-api.com) maar is nu niet gekoppeld aan de foto-flow.

Gebruiker: "we zitten voor de helft goed, maar er moet wat fine-tuning" —
sessie eindigde hier, verder uitwerken (drempelwaarde, hysterese rond de
45km/u-grens, samenspel met de refresh-interval) volgt een volgende keer.

## Openstaand
- Fotosortering: `is_moving`-detectie op basis van snelheid nog te
  ontwerpen en implementeren (zie hierboven).
- Fotosortering: dekkingsgat-probleem (scenario 2, punt 1) — geen
  concrete oplossing besproken, alleen benoemd.
- Fotosortering: server zou automatisch "nieuwe foto's?" moeten checken
  i.p.v. alleen op handmatig verzoek (scenario 1).
- Battery-optimalisatie: GPS-nauwkeurigheid, wifi-only foto-downloads, en
  langdurige geheugentest — genoemd als vervolgstappen, niet opgepakt.
- Telefoon (`R5CX80DCFQE`) staat sinds Module 3 op de `benchmark`-buildvariant
  i.p.v. een normale build — moet nog teruggezet worden.
