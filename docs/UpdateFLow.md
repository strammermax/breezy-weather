# Update Flow — foto's ophalen, verversen en verwijderen

Dit document beschrijft hoe de app foto's ophaalt (flow 1), hoe de app lokaal een
wallpaper kiest (flow 2/3), en hoe verwijderingen door de beheerder de app snel
bereiken zonder overbodig databundelverbruik (flow 4/5).

**Status: ✅ = gebouwd, gedeployed en end-to-end getest tegen productie · ⏳ = nog niet
gebouwd.** Alle knelpunten uit eerdere versies van dit document zijn opgelost — zie
"Opgeloste knelpunten" onderaan voor de geschiedenis.

---

## Flow 1 — Foto's ophalen voor een locatie

| nummer | omschrijving | status | verwijzing |
|---|---|---|---|
| 1 | Als gebruiker kom ik op een nieuwe locatie Lisse | — | → 1.a |
| 1.a | App kijkt naar GPS > converteert het naar locatie & land | — (bestond al) | → 1.b |
| 1.b | App checkt of hij al records **en een `lastRefreshedAt`** heeft voor deze locatie | ✅ `WallpaperImageStore.searchSinceFor(locationId, purpose)` | → 1.c |
| 1.c | App haalt lijst van foto's via service => `GET /api/v1/search?lat=&lon=&since=<lastRefreshedAt of leeg>` | ✅ | → 1.d |
| 1.d | Service checkt: is er binnen de zoekstraal van lat/lon iets veranderd sinds `since` (nieuwe foto verwerkt, curator heeft iets verwijderd/uitgeschakeld, status gewijzigd)? | ✅ `get_last_changed(lat, lon, radius_km)` — GPS-based | ja = 1.e / nee = 1.j |
| 1.e | Service kijkt of hij al records heeft van location:lisse, land:nederland | — (bestond al) | ja = 1.e.2 / nee = 1.e.1 |
| 1.e.1 | Zo nee: de service doet een `force`-search bij externe providers | — (bestond al) | → 1.e.3 |
| 1.e.2 | Zo ja: als de laatste automatische verversing voor deze locatie >24u geleden is, start de service een achtergrond-search (max. `BACKGROUND_FILL_MAX_RESULTS` nieuwe beelden); anders niets extra's | — (bestond al) | → 1.e.3 |
| 1.e.3 | De service serveert alle **enabled** records van Lisse aan de app (gzip), **plus een nieuwe `checked_at`-timestamp** | ✅ | → 1.f |
| 1.j | Niets veranderd: service antwoordt met een lichte respons (geen volledige lijst) — alleen de nieuwe `checked_at` | ✅ | → 1.k |
| 1.k | App slaat `checked_at` op als nieuwe `lastRefreshedAt`; cache/database blijven ongewijzigd | ✅ | → 3.b |
| 1.f | App krijgt resultaat terug (gzip) + `checked_at` | ✅ | → 1.f.1 |
| 1.f.1 | De app vergelijkt het resultaat met de records in zijn eigen database | — (bestond al, `checkForNewPhotos`/`pruneDisabledPhotos`) | → 1.f.2 |
| 1.f.2 | De app zet alle records met location:lisse, land:nederland die niet in het aangeleverde resultaat voorkomen op een delete-lijst | — (bestond al) | → 1.f.3 |
| 1.f.3 | De app verwijdert alle afbeeldingen op de delete-lijst uit de cache | — (bestond al) | → 1.f.4 |
| 1.f.4 | De app verwijdert alle records op de delete-lijst uit de database | — (bestond al) | → 1.f.5 |
| 1.f.5 | De app verwerkt alle records uit het resultaat (gzip) in de database met `addUpsert(resultaat)` | — (bestond al) | → 1.f.6 |
| 1.f.6 | App slaat `checked_at` op als nieuwe `lastRefreshedAt` | ✅ | → 1.f.7 |
| 1.f.7 | De app bekijkt het aantal records | — (bestond al) | 0 records = 1.f.8 / anders 2.a |
| 1.f.8 | Als het resultaat leeg is: retry met oplopende back-off (10m → 30m → 1u → daarna elke 6u) i.p.v. altijd elke 10 minuten | ⏳ nog niet gebouwd (huidige `RETRY_DELAY_MINUTES_ON_EMPTY` is nog een vast getal) | → 1.c |

**Belangrijke fix tijdens het testen:** `pruneDisabledPhotos()` en `checkForNewPhotos()`
delen hetzelfde `since`-mechanisme, maar hadden aanvankelijk dezelfde opslag-sleutel.
Omdat `pruneDisabledPhotos()` bij élke worker-tick draait (ongeacht of er iets nieuws is),
"consumeerde" die de freshness-timestamp voordat `checkForNewPhotos()` de kans kreeg om
zelf iets nieuws te zien — waardoor de "Check for new images"-knop soms ten onrechte
"niets nieuws" meldde. Opgelost door `since` per-doel te namespacen (`"prune"` vs.
`"checkNew"`), zodat beide onafhankelijk hun eigen laatst-geziene-staat bijhouden.

---

## Flow 2/3 — Lokale selectie en weergave (ongewijzigd)

| nummer | omschrijving | verwijzing |
|---|---|---|
| 2.a | De app haalt de volgende gegevens op: is_dag, seizoen, GPS, locatie, land | → 2.a.1 |
| 2.a.1 | Nacht/dag-foto: `is_dag = (datetime > sunrise and datetime < sunset)` | → 2.a.2 |
| 2.a.2 | Seizoen: `season = winter` (nov–feb) / `lente` (mrt–mei) / `zomer` (jun–aug) / `herfst` (sep–okt) | → 2.a.3 |
| 2.a.3 | `GPS = getGps()` | → 2.a.4 |
| 2.a.4 | `_locatie = getLocatie(GPS)`; `_country = getCountry(GPS)` | → 2.a.5 |
| 2.a.5 | Datalist = alle records uit database met `location = _locatie` en `country = _country` | → 2.a.6 |
| 2.a.6 | Datalist bouwt showlist van minimaal 6 afbeeldingen | → 2.a.7 |
| 2.a.7 – 2.a.18 | Oplopende fallback-ladder: radius 1km → 2km → 5km, filters (dag, status=enabled, seizoen, weer) worden stap voor stap losgelaten totdat er ≥6 unieke resultaten zijn; sortering steeds op nieuwste, minst gezien, "duim omlaag" laatst | → 3.0 zodra 6 beelden bereikt zijn |
| 3.0 | Showlist (bv. 11 resultaten) → app laadt de eerste 4 nieuwe afbeeldingen die nog niet in de cache staan; bij overschrijding van max-per-locatie wordt de oudste in cache verwijderd | → 3.a |
| 3.a | Toon afbeeldingen in volgorde van de showlist, voor zover ze al in cache staan | → 3.b |
| 3.b | Elke x minuten wordt er van afbeelding gewisseld | — |
| 3.c | Elke x minuten: als de locatie anders is dan de vorige keer → 1.c | → 1.c |
| 3.c | Elke x minuten: als de locatie hetzelfde is **en** `lastRefreshedAt` ouder is dan 1 uur → 1.c (dankzij 1.d/1.j is dit een goedkope check, geen volledige herdownload) | → 1.c |

---

## Flow 4 — Snel verwijderingen doorgeven via polling (child-safety vangnet)

Geen aparte 5-min poll (Android staat geen WorkManager-interval <15 min toe) — in plaats
daarvan optimaliseren we de bestaande tick, die toch al bij **elke** worker-run draait
(`WallpaperPhotoRefreshWorker.kt:90`, ongeacht locatiewijziging, elke 15-180 min
instelbaar via `photoRefreshIntervalMinutes`).

| nummer | omschrijving | status | verwijzing |
|---|---|---|---|
| 4.0 | `pruneDisabledPhotos()` draait al bij elke tick en haalt nu met `since` op | ✅ | → 4.1 |
| 4.1 | Service geeft via `/search?since=` de volle lijst terug zodra er iets veranderd is (incl. verwijderingen/disabled), of `changed:false` als niets veranderd is | ✅ server-side (`get_last_changed` kijkt naar zowel `processed_at` als `removed_at`) | → 4.2 |
| 4.2 | App verwijdert URLs die niet meer in de (nieuwe) lijst voorkomen **direct** uit cache én database — bestaande diff-logica in `pruneDisabledPhotos`/`checkForNewPhotos` | — (bestond al) | → 4.3 |
| 4.3 | App slaat `checked_at` op als nieuwe `since` voor de volgende tick | ✅ `store.setSearchSince(...)` | → 4.0 (volgende tick) |
| — | Los `GET /api/v1/removed?lat=&lon=&since=` endpoint | ✅ gebouwd server-side (GPS-based), **nog niet aangeroepen door de app** — bewaard voor eventueel toekomstig gebruik als reconciliatie na een gemiste flow-5-push | — |

Dit blijft het vangnet: als flow 5 (push) een keer niet aankomt (device offline, of —
zoals we op de emulator zagen — soms trage/onbetrouwbare FCM-aflevering), vangt deze
polling het alsnog op, binnen maximaal het ingestelde refresh-interval.

---

## Flow 5 ✅ — Push-notificatie (Firebase Cloud Messaging)

De laag die de child-safety-eis ("nooit tonen na verwijdering door beheerder") echt
waarmaakt: de meeste verwijderingen bereiken de app binnen enkele seconden, i.p.v. te
wachten op de volgende tick.

| nummer | omschrijving | status | verwijzing |
|---|---|---|---|
| 5.0 | Zodra de beheerder een foto verwijdert/uitschakelt: service stuurt direct een Firebase-data-push met de betreffende URL('s) naar alle actieve app-instanties | ✅ `app/services/push.py` (`send_purge_urls`), aangeroepen vanuit `manage.py` bij `delete_image`/`update_image_status` | → 5.1 |
| 5.1 | App ontvangt push, verwijdert de URL('s) direct uit cache én database — ongeacht huidige weergave | ✅ `RemoveSkyMessagingService.onMessageReceived` → `WallpaperRepository.purgeUrls()` | → 5.2 |
| 5.2 | Was de verwijderde foto de actief-getoonde wallpaper? Dan activeert de app meteen een andere gecachete foto voor die locatie, of triggert een verse achtergrond-download als er lokaal niets meer over is | ✅ `purgeUrls()` gebruikt `selectWallpaperPhoto()` / `WallpaperPhotoRefreshWorker.startNow()` | → 5.3 |
| 5.3 | Open schermen ("Manage background images", "Live wallpaper"-preview) verversen automatisch, ook als ze al open stonden toen de push binnenkwam | ✅ `WallpaperRepository.catalogChanged` (SharedFlow), gecollect door beide schermen | einde |

**Vereiste server-config** (eenmalig, buiten git): `REMOVESKY_FCM_CREDENTIALS_PATH` (pad
naar Firebase service-account JSON) en `REMOVESKY_PUSH_BASE_URL` (zie hieronder) in
`removesky.env`.

**Bekende beperking, geen bug:** op de Android-**emulator** is FCM-aflevering soms
merkbaar trager of blijft een enkele keer helemaal uit (Google Play Services-beperking
van emulators, niet van de code) — flow 4 vangt dat dan alsnog op binnen het
refresh-interval. Op een echt toestel is dit doorgaans niet zichtbaar.

---

## Opgeloste knelpunten (geschiedenis)

### 1. `location`/`country` vs GPS
`get_last_changed`/`get_removed_since` verwachtten aanvankelijk `location`+`country`
tekst, terwijl de app alleen `lat`/`lon` stuurt. Opgelost door beide DAO-functies te
herschrijven naar GPS+straal (`haversine_km`), zoals `processed_dao.search()` zelf ook
al filtert.

### 2. Depth-map per ongeluk als eigen foto geregistreerd
`synchronizePhotoCatalog()`'s directory-scan registreerde `<hash>_depth.webp`-bestanden
(bedoeld als bijlage van hun foto) als eigen "onbekende lokale foto" — zichtbaar als een
losse entry zonder land/dag-metadata die de rauwe zwart/grijze depth-data toonde in
plaats van de echte foto. Gefixed (scan slaat `_depth`-bestanden nu over) + eenmalige
opschoning van bestaande foute rijen. Geverifieerd: cache ging van 23 → 13 foto's na de
eerste opschoning, geen depth-mixup meer zichtbaar.

### 3. Verwijderde foto bleef op het scherm hangen
`MaterialLiveWallpaperService.ensureForeground()` wiste bij een ontbrekende cache wel
`cachedPhotoPath`, maar niet de in-memory `mForeground`-bitmap — een net gepurgede foto
bleef daardoor oneindig getekend worden. Gefixed; plus `purgeUrls()` activeert nu meteen
een vervangende foto (of triggert een verse refresh) i.p.v. de gebruiker op een kale
sky-achtergrond te laten wachten.

### 4. Locatie verwijderen uit de weer-app ruimde wallpaper-cache niet op
`MainActivityViewModel.deleteLocation()` riep `locationRepository.delete()` aan, maar
raakte de losse wallpaper-photo-cache (bestanden + `wallpaper_photos`-DB-rijen) nooit
aan. Toegevoegd: `WallpaperRepository.clearLocation()` + `deleteForLocation`-query,
aangeroepen vanuit de delete-flow. Geverifieerd: 0 cachebestanden en 0 DB-rijen over na
het verwijderen van een testlocatie.

### 5. `checkForNewPhotos` miste foto's die `pruneDisabledPhotos` al had gezien
Zie flow 1 hierboven — gedeelde `since`-sleutel tussen de twee functies, opgelost door
per-doel namespacing.

### 6. Push gebruikte soms een LAN-IP i.p.v. het publieke domein
Als de beheerder Beheren via het lokale netwerk-IP benaderde (niet via het publieke
domein), bouwde de push-URL óók een LAN-IP op — onbruikbaar voor de app, die zijn cache
altijd op het publieke domein sleutelt. Toegevoegd: `config.PUSH_BASE_URL`
(`REMOVESKY_PUSH_BASE_URL`), een vast publiek adres specifiek voor push-payloads, los van
de flexibele `make_base_url(request)` die `/search`/`/upload` gebruiken (die moet juist
wél meebewegen met LAN vs. publiek, voor lokale test-clients).

### 7. `REMOVESKY_SEARCH_MAX_LIMIT` stond nog op 25 in productie
De app vraagt `limit=200` op (`fetchEnabledPhotos`); de server wees dat af met een 422
omdat `removesky.env` nog expliciet `25` had staan (ouder dan onze latere default-bump
naar 200 in `config.py`). Handmatig aangepast naar 200 + service herstart.

---

## Nog openstaand
Niets meer — alle punten uit dit document zijn gebouwd, gedeployed en getest.

## Recent afgerond
- ✅ `GET /removed` levert nu volledige `processed_url`'s i.p.v. onbruikbare `local-N`-ids,
  en wordt bij app-start aangeroepen (`reconcileRemovalsOnStartup`, alleen als
  `photoBackgroundEnabled`) als extra reconciliatie voor de actieve locatie — vangt een
  curator-verwijdering op die gemist werd terwijl de app dicht stond (FCM bereikt alleen
  een draaiende app). Eigen `since`-namespace ("removedReconcile"), los van
  prune/checkNew.
- ✅ Flow 1.f.8: oplopende back-off bij lege zoekresultaten (10m → 30m → 1u → daarna elke
  6u, per locatie bijgehouden via `WallpaperImageStore.emptyRetryCountFor`, reset zodra
  een refresh weer wél iets vindt).
- ✅ `attributionValue` in de Live-wallpaper-preview wordt nu ook bijgewerkt door
  `catalogChanged`, samen met foto/aantal/locatie.
