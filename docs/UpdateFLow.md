# Update Flow — foto's ophalen, verversen en verwijderen

Dit document beschrijft hoe de app foto's ophaalt (flow 1), hoe de app lokaal een
wallpaper kiest (flow 2/3), en hoe verwijderingen door de beheerder de app snel
bereiken zonder overbodig databundelverbruik (flow 4/5).

**Status: ✅ = gebouwd en getest (compileert) · ⚠️ = gebouwd maar met een open knelpunt
· ⏳ = nog niet gebouwd.** Zie "Openstaand knelpunt" onderaan vóór je verder bouwt —
dat raakt of flow 1 in de praktijk ook echt iets bespaart.

---

## Flow 1 — Foto's ophalen voor een locatie

| nummer | omschrijving | status | verwijzing |
|---|---|---|---|
| 1 | Als gebruiker kom ik op een nieuwe locatie Lisse | — | → 1.a |
| 1.a | App kijkt naar GPS > converteert het naar locatie & land | — (bestond al) | → 1.b |
| 1.b | App checkt of hij al records **en een `lastRefreshedAt`** heeft voor deze locatie | ✅ `WallpaperImageStore.searchSinceFor()` | → 1.c |
| 1.c | App haalt lijst van foto's via service => `GET /api/v1/search?lat=&lon=&since=<lastRefreshedAt of leeg>` | ✅ | → 1.d |
| 1.d | Service checkt: is er binnen de zoekstraal van lat/lon iets veranderd sinds `since` (nieuwe foto verwerkt, curator heeft iets verwijderd/uitgeschakeld, status gewijzigd)? | ✅ `get_last_changed(lat, lon, radius_km)` — GPS-based, zie opgelost knelpunt onderaan | ja = 1.e / nee = 1.j |
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

**Wat werkt al aantoonbaar:** `pruneDisabledPhotos()`/`checkForNewPhotos()` in
`WallpaperRepository.kt` sturen nu `since` mee en verwerken `changed:false` zonder de
lijst te downloaden/vergelijken — **mits** de service ooit `changed:false` teruggeeft.
Zie het knelpunt hieronder voor waarom dat nu nog niet gebeurt.

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
| 3.c | Elke x minuten: als de locatie hetzelfde is **en** `lastRefreshedAt` ouder is dan 1 uur → 1.c (dankzij 1.d/1.j is dit voortaan een goedkope check, geen volledige herdownload) 🆕 | → 1.c |

---

## Flow 4 (herzien) — Snel verwijderingen doorgeven (child-safety vangnet)

**Besluit tijdens het bouwen:** een losse periodieke poll van elke 5 minuten kan niet
met WorkManager (Android staat geen periodiek interval <15 min toe). In plaats daarvan
optimaliseren we de bestaande tick, die toch al bij **elke** worker-run draait
(`WallpaperPhotoRefreshWorker.kt:90`, ongeacht locatiewijziging, elke 15-180 min
instelbaar via `photoRefreshIntervalMinutes`) — dat is dus geen aparte flow meer, maar
onderdeel van flow 1/1.c geworden via `pruneDisabledPhotos()`.

| nummer | omschrijving | status | verwijzing |
|---|---|---|---|
| 4.0 | ~~Losse 5-min poll~~ → vervangen door: `pruneDisabledPhotos()` draait al bij elke tick en haalt nu met `since` op | ✅ (maar zie knelpunt: `since` werkt alleen als GPS-based lookup ook server-side ondersteund wordt) | → 4.1 |
| 4.1 | Service geeft via `/search?since=` de volle lijst terug zodra er iets veranderd is (incl. verwijderingen/disabled), of `changed:false` als niets veranderd is | ✅ server-side (`get_last_changed` kijkt naar zowel `processed_at` als `removed_at`) | → 4.2 |
| 4.2 | App verwijdert URLs die niet meer in de (nieuwe) lijst voorkomen **direct** uit cache én database — bestaande diff-logica in `pruneDisabledPhotos`/`checkForNewPhotos` | — (bestond al) | → 4.3 |
| 4.3 | App slaat `checked_at` op als nieuwe `since` voor de volgende tick | ✅ `store.setSearchSince(...)` | → 4.0 (volgende tick) |
| — | Los `GET /api/v1/removed?...` endpoint | ✅ gebouwd server-side, **nog niet aangeroepen door de app** — bewaard voor later gebruik (bv. als extra check tussen ticks door, of als reconciliatie na een gemiste flow-5-push) | — |

**Resterend gat t.o.v. de oorspronkelijke wens ("kind mag nooit een net verwijderde foto
zien"):** met alleen deze optimalisatie duurt het nog steeds tot de eerstvolgende tick
(15-180 min) voordat een verwijdering de app bereikt. Dat vangnet-probleem lossen we pas
echt op met flow 5 (push) hieronder.

---

## Flow 5 ⏳ — Push-notificatie (nog niet gebouwd, aanvullend op flow 4)

Flow 4 blijft het vangnet (werkt ook na een gemiste push, bv. device was offline).
Flow 5 zorgt dat de meeste verwijderingen al binnen enkele seconden aankomen i.p.v. binnen
15-180 minuten — dit is de enige laag die de child-safety-eis ("nooit tonen na verwijdering
door beheerder") echt waarmaakt.

| nummer | omschrijving | status | verwijzing |
|---|---|---|---|
| 5.0 | Zodra de beheerder een foto verwijdert/uitschakelt: service stuurt direct een Firebase-data-push met de betreffende ID('s) naar alle actieve app-instanties | ⏳ nog niet gebouwd | → 5.1 |
| 5.1 | App ontvangt push, verwijdert de ID('s) direct uit cache én database — ongeacht huidige weergave | ⏳ nog niet gebouwd | → 5.2 |
| 5.2 | App zet zijn `since` gelijk aan nu, zodat dezelfde verwijdering niet dubbel wordt verwerkt bij de eerstvolgende tick | ⏳ nog niet gebouwd | einde |

**Openstaand:** Firebase Cloud Messaging opzetten (server-side: service account + data-message
versturen bij `soft_delete`/`set_status`; app-side: FCM SDK, message-handler).

---

## ✅ Knelpunt location/country vs GPS — opgelost

Was: `get_last_changed`/`get_removed_since` verwachtten `location`+`country` tekst, terwijl
de app alleen `lat`/`lon` stuurt (Android's GPS is goedkoop, `place.city`/`place.country`
vereist reverse-geocoding en is dat niet). Gekozen oplossing: **optie 2** — beide
DAO-functies herschreven naar GPS+straal (`haversine_km`), exact zoals `processed_dao
.search()` zelf ook al filtert, i.p.v. de app locatienaam te laten meesturen.

- `get_last_changed(lat, lon, radius_km)` — `processed_dao.py`
- `get_removed_since(lat, lon, radius_km, since)` — `processed_dao.py`
- `run_search()` roept `get_last_changed` nu aan met `lat`/`lon` + `config
  .LOCAL_SEARCH_RADIUS_KM`, geen `location`/`country` meer nodig voor de kortsluiting
- `GET /removed` — `lat`/`lon` query params i.p.v. `location`/`country`

Getest: DAO-functies compileren en werken (smoke-test met echte coördinaten tegen de
lokale db). App-kant hoeft niets aan te passen — stuurde al `lat`/`lon`.
