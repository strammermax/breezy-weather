# ACT-021 - Herziening foto-sortering en -selectie (SortLocationRecsByLocation)

## Status

- **Type:** Implementatieopdracht (Android app, on-device)
- **Prioriteit:** Hoog — vervangt bestaande, inconsistente sorteerlogica
- **Omvang:** Groot
- **Risico:** Middel (raakt de kernflow die bepaalt welke wallpaper-foto getoond wordt)
- **Prerequisite:** `getImagesDataByCity`/`getImagesDataByGPS`/`updateImagesDataByCity`/
  `updateImagesDataByGPS` in `RemoveSkyProvider.kt` (al gebouwd, zie git-historie van deze
  sessie) en het bestaande `wallpaper_photos`-schema (`data/src/main/sqldelight/.../wallpaper_photos.sq`).
- **Bronnen:** `Flow app.excalidraw`, `Flow app v2-2026-07-22-1658.svg/.png`, `customsortlogic.md`,
  `ANALYSE - Sorteerfunctie wallpaper foto's (huidige werking).md`, `ACT-019 - Testpagina
  sorteervolgorde (admin).md`.

## 1. Opdracht in een zin

**Doel:** de app heeft elke X minuten een vers gesorteerde lijst met foto's voor de huidige
locatie (of de door de gebruiker gekozen vaste/fictieve locatie) klaarstaan. Vervang de twee
bestaande, onderling inconsistente sorteeralgoritmes in `WallpaperPhotoPriority.kt` door één
samenhangende flow: een periodieke locatie-check (`Every x mins`) → sync met RemoveSky (al
gebouwd) → `SortLocationRecsByGPSLocation(location, minimal)` (cascaderende
afstands-/kwaliteitsfilter) óf `SortLocationRecsByLocation(location, minimal, fictief)` (geen
cascade, sorteert op `createdDate`) — afhankelijk van het locatietype, zie §7 — beide met
`GetMinimalLocationRecs()` als gedeelde interne fallback-ladder → een gate
(`Resultlist.count >= minimal`, anders niets doen) → `CustomSort(Resultlist)` uit
`customsortlogic.md` (recentheid > weergaven > afstand) → ontbrekende afbeeldingen uit die
lijst downloaden naar de cache.

## 2. Waarom deze wijziging nodig is

`ANALYSE - Sorteerfunctie wallpaper foto's (huidige werking).md` beschrijft tien concrete
inconsistenties tussen de twee huidige algoritmes (`selectWallpaperPhoto` en `buildShowlist`):
verschillende criteria-volgorde, tegengestelde `createdAt`-richting, `lastShownAt` die in één
pad wél en in het andere pad niet meetelt, etc. Gebruikersklachten ("steeds dezelfde foto's",
"het klopt niet") zijn hier waarschijnlijk direct aan te herleiden. Dit document/schema is het
antwoord daarop: één uniforme, expliciet gespecificeerde flow in plaats van twee losse,
impliciet gegroeide algoritmes.

## 3. Belangrijkste bevinding vooraf — geen nieuwe database nodig

Een eerdere sessie in dit traject bouwde `RemoveSkyImageDao.kt` (een losse
`SQLiteOpenHelper`-tabel) om `upsertDataDB(json)`/`deleteRecordsDataDB(json)` op te vangen.
Bij het uitwerken van dit schema bleek dat **`data/src/main/sqldelight/.../wallpaper_photos.sq`
+ `WallpaperPhotoRepository` al bestaat** en vrijwel elk veld al heeft dat nodig is:
`view_count`, `created_at`, `updated_at`, `last_shown_at`, `disabled`, `rating`,
`location_key`, `resolved_lat/lon`, `exif_lat/lon`, `day_period`, `season`, `weather`,
`status`. Dit is bovendien de database die `WallpaperPhotoPriority.kt` nu al leest.

**Besluit (voorstel, graag bevestigen):** `RemoveSkyImageDao.kt` komt te vervallen.
`upsertDataDB`/`deleteRecordsDataDB` worden dunne wrappers rond de bestaande
`WallpaperPhotoRepository`:
- `upsertDataDB(json)` → per item in `results[]`: `WallpaperPhotoRepository.upsertDownloaded(...)`.
- `deleteRecordsDataDB(json)` → per url in `removed_urls[]`: opzoeken via `source_url`
  (nieuwe lookup-query nodig, zie §7) en `setDisabled(id, true)` (zacht) of `deleteById(id)`
  (hard) — te bepalen in §7.

Dit voorkomt twee parallelle, potentieel uit-de-pas-lopende databases voor dezelfde foto's.

## 4. Wat er al bestaat (uit eerdere sessie, hergebruiken)

- `RemoveSkyProvider.getImagesDataByCity/GPS` en `updateImagesDataByCity/GPS` — leveren de
  JSON die `upsertDataDB`/`deleteRecordsDataDB` verwerken. Backend ondersteunt inmiddels
  `radius_km` (voor "range") en `location`-based `since`/`removed` (voor de stad-flow zonder
  afstandslimiet).
- `WallpaperPhotoRepository` (SQLDelight) — bestaande CRUD, inclusief `getForLocation`,
  `markShown` (verhoogt `view_count`, zet `last_shown_at` — exact wat `customsortlogic.md`
  nodig heeft), `setDisabled`, `deleteById`.
- `WallpaperPhotoPriority.gpsDistanceKmOrWorst()` / `haversineKm()` — herbruikbare
  afstandsberekening (EXIF-GPS eerst, dan `resolvedLat/Lon`, anders "oneindig ver").
- `WallpaperSeasonGrading.seasonFor()` / `isCurrentlyNight()` (`CelestialTiming`) — bestaande
  dag/nacht- en seizoensbepaling, te hergebruiken voor de `is_day`/`season`-filters in het
  nieuwe schema (geen nieuwe zon-op/onder-logica nodig).

## 4a. Architectuurprincipe: kleine, losstaande functies

Expliciete eis (op verzoek): elke stap in dit ontwerp wordt een **eigen, kleine, pure
functie** — geen grote inline `for`/`when`-blokken die meerdere stappen combineren. Doel:
onderdelen moeten straks apart aan te passen, te vervangen of te unit-testen zijn zonder de
rest te raken (bijv. "we willen de 5km-stap laten vervallen" of "de relaxatie-volgorde in
GetMinimalLocationRecs omdraaien" mag een wijziging in precies één functie zijn). Concreet,
per onderdeel (uitgewerkt in §7-§10):

- Elke cascade-ring in `SortLocationRecsByGPSLocation` (`locationrecs`, `_5km`, `_2km`, `_1km`,
  `_500m`, `_200m`, `_100m`) is een losse functie-aanroep met een eigen naam, niet een
  loop over een lijst met radii. (`SortLocationRecsByLocation`, scenario 2/3, heeft geen
  cascade — zie §7a.)
- Elke relaxatiestap in `GetMinimalLocationRecs` (dag+seizoen+weer / dag+weer / dag /
  geen filter) is een losse, benoemde filterfunctie.
- De drie regels van `customsortlogic.md` (recentheid-marge, weergaven, afstand) zijn losse
  comparator-functies, samengesteld tot één `Comparator` — niet één grote `when`-blok.
- De eind-gate (`Resultlist.count >= minimal`), de sortering en de download-stap zijn drie
  losse functies die na elkaar aangeroepen worden vanuit de worker, niet samengevoegd tot één
  "doe alles"-functie.

## 5. Afbakening

### Wel uitvoeren
- `SortLocationRecsByGPSLocation(location, minimal): List<WallpaperPhotoRecord>` — nieuwe
  functie, cascaderende afstandsvernauwing, voor de GPS-getrackte "current position" (§7.4).
- `SortLocationRecsByLocation(location, minimal, fictief): List<WallpaperPhotoRecord>` —
  nieuwe functie, geen afstandscascade, voor vaste/fictieve locaties (§7a).
- `GetMinimalLocationRecs(locationrecs, minimal, sortitem, location): List<WallpaperPhotoRecord>`
  — nieuwe, gegeneraliseerde functie, cascaderende filterverslapping, gedeeld door beide
  sorteerpaden hierboven (§8).
- `sorteerItems(lijst)` / Kotlin-equivalent van `customsortlogic.md` — nieuwe functie, wint
  het van de bestaande tiebreak-volgorde in beide oude algoritmes (§9).
- De periodieke worker-trigger ("Every x mins…") uitbreiden met de `selectedLocationType`-
  vertakking (§7/§10.0), de "zelfde locatie/X minuten"-check (alleen voor `CURRENT_POSITION`)
  en de "is location new"-vertakking naar de juiste `getImagesDataByGPS`/`updateImagesDataByGPS`
  óf `getImagesDataByCity`/`updateImagesDataByCity`-aanroep (§10).
- Per-locatie state voor `_currentSortedResultlist` en de rotatie-index (§10.0a), i.p.v. één
  globale waarde.
- `upsertDataDB`/`deleteRecordsDataDB` als wrappers rond `WallpaperPhotoRepository` (§6/§7).
- `RemoveSkyImageDao.kt` verwijderen (of expliciet behouden — zie besluit in §3).
- `WallpaperPhotoPriority.selectWallpaperPhoto()` volledig verwijderen (**niet** overzetten naar
  de nieuwe pipeline): een curator-afwijzing/disable hoeft niet instant een vervanger te
  krijgen, die komt vanzelf naar voren bij de eerstvolgende `getSortedResultlist()`-cyclus
  (§10.0). Geen aparte spoed-vervangingsflow nodig.
- `rating`/duim-omlaag-omhoog laten vervallen — **ook de UI** (niet alleen de filter-/
  sorteercriteria in code): de duim-knoppen zelf verdwijnen uit de app. Dit raakt strikt
  genomen andere schermen dan dit document (bv. de wallpaper-instellingen-UI waar de
  duim-knoppen nu staan) — losstaand op te pakken, maar wel genoteerd omdat het `rating`-veld
  in `wallpaper_photos.sq` daarmee uiteindelijk ook overbodig wordt.

### Niet uitvoeren (in deze opdracht)
- ACT-019 (admin-testpagina voor sorteergewichten) — losstaand traject, niet nodig om dit
  schema te implementeren.
- Geen wijzigingen aan het backend-datamodel; alle nieuwe velden (`view_count`, `created_at`
  etc.) bestaan al in `wallpaper_photos.sq`.

## 6. Datamodel-mapping: customsortlogic.md → bestaande velden

| `customsortlogic.md`-veld | Bron in de app | Opmerking |
|---|---|---|
| `distance` | `WallpaperPhotoPriority.gpsDistanceKmOrWorst(record, lat, lon)` | Dynamisch berekend t.o.v. **huidige** GPS-positie, niet opgeslagen. De JS-`"100m"`-string in het voorbeeld is illustratief; Kotlin werkt al met `Double` (km). |
| `aantalkeer_gezien` | `WallpaperPhotoRecord.viewCount` | Bestaat al; wordt verhoogd door `WallpaperPhotoRepository.markShown()`. |
| `created_date` | **Nieuw veld nodig** — RemoveSky's `processed.processed_at` (wanneer de service de foto heeft verwerkt), niet `WallpaperPhotoRecord.createdAt` (lokale eerste-sync-tijd) en niet `capturedAt` (EXIF-fotodatum). | **Beslist.** Bestaat serverside al (`processed_dao._COLUMNS` bevat `processed_at`), maar wordt momenteel **niet** meegegeven in de `/search`-JSON (geverifieerd: ontbreekt in `run_search()`'s resultaatdict, `app/services/processing.py`). Vereist: (1) backend — `processed_at` toevoegen aan de `/search`-resultaten; (2) app — nieuw veld op `WallpaperPhotoRecord`/`RemoveSkyEnabledPhoto` (bv. `processedAt: String?`) + nieuwe kolom in `wallpaper_photos.sq`, apart van `created_at`/`captured_at`. Zie §12 implementatiestappen. |
| `imageurl` | `WallpaperPhotoRecord.sourceUrl` / `filePath` | Alleen nodig voor identificatie, geen sorteercriterium. |

## 7. Twee sorteerpaden — GPS-cascade vs. vaste/fictieve locatie

**Belangrijke correctie (bijgewerkt schema):** er is niet één `SortLocationRecsByLocation`,
maar een vertakking naar **twee verschillende functies**, afhankelijk van het type
geselecteerde locatie ("Selected location" in het bijgewerkte hoofd-diagram, zie §10.0):

| Scenario | Sync-aanroep | Sorteerfunctie | Sorteert op |
|---|---|---|---|
| 1. **Current position** — gebruiker is al langere tijd op dezelfde GPS-plek (bv. Hoofddorp), maar de exacte positie kan binnen die plek verschuiven (centrum vs. Haarlemmermeerderbos) | `getImagesDataByGPS`/`updateImagesDataByGPS` (al gebouwd) | `SortLocationRecsByGPSLocation(location, minimal)` (§7.4) | `distance` t.o.v. huidige exacte GPS |
| 2. **Vaste locatie** — gebruiker koos zelf een vaste achtergrond-plek (bv. Rome) | `getImagesDataByCity`/`updateImagesDataByCity` (al gebouwd) | `SortLocationRecsByLocation(location, minimal, fictief=false)` (§7a) | `createdDate` (= `processedAt`, §6) — **geen afstand** |
| 3. **Fictieve locatie** — zoals 2 (bv. Ghibli), maar dag/nacht/seizoen/weer-context komt van de **echte** huidige GPS-positie, niet van de (niet-bestaande) locatie zelf | `getImagesDataByCity`/`updateImagesDataByCity` (al gebouwd) | `SortLocationRecsByLocation(location, minimal, fictief=true)` (§7a) | `createdDate` — **geen afstand** |

Scenario 2 en 3 gebruiken dus dezelfde functie, alleen met een andere `fictief`-vlag; alleen
scenario 1 gebruikt de afstand-cascade. Zie §10.0 voor waar de "Selected location"-keuze
gemaakt wordt en hoe die naar de juiste sync-aanroep + sorteerfunctie routet.

### 7.4 `SortLocationRecsByGPSLocation(location, minimal)` — ontwerp (scenario 1)

Bron: het onderste maroon diagram (`SortLocationRecsByGPSLocation`) in de bijgewerkte SVG.

```
fun sortLocationRecsByGPSLocation(
    locationKey: String,
    latitude: Double,
    longitude: Double,
    minimal: Int,          // ondergrens: is er na de hele cascade sowieso genoeg voor een
                           // nieuwe lijst? Zelfde waarde als de finale Resultlist.count-gate
                           // in §10 en als target voor GetMinimalLocationRecs.
    maxImages: Int = store.maxCachedPhotosPerLocation,  // bestaande app-instelling, hergebruikt
    now: Long = System.currentTimeMillis(),
): List<WallpaperPhotoRecord>
```

**Beslist:** `maxImages` is géén nieuwe instelling — het is exact
`WallpaperImageStore.maxCachedPhotosPerLocation` (de bestaande, al door de gebruiker
instelbare cache-cap per locatie, huidige default 12), rechtstreeks hergebruikt om de
cascade-ringen aan te sturen. `minimal` is daarentegen **niet door de gewone gebruiker
aanpasbaar** — geen zichtbare instelling in de normale app-instellingen — maar wél een
aanpasbare waarde, alleen bereikbaar via de bestaande admin/debug-instellingen
(`DebugSettingsScreen.kt`/`ui/settings/compose`), net als andere daar al aanwezige
debug-only tunables. Wordt net als `maxCachedPhotosPerLocation` opgeslagen in
`WallpaperImageStore`, alleen zonder een zichtbaar item in de reguliere instellingen-UI.
Exacte default-waarde nog te bepalen (huidige `buildShowlist`-equivalent is `minSize = 6`,
bruikbaar als startpunt).

Stappen:
1. `getForLocation(locationKey)` → alle lokale records voor deze plek (bestaande query).
2. Voor elk record: bereken `distance = gpsDistanceKmOrWorst(record, latitude, longitude)`.
3. Sorteer op `distance` oplopend.
4. Filter op `is_day` (huidige dag/nacht) + `season` + `weather` → `locationrecs`.
5. **Cascade** (elke stap alleen als de vorige `< maxImages` opleverde — zie §7.4.1 voor het
   gevonden inconsistentie-punt in het schema):
   `locationrecs` (geen afstandsgrens) → `_5km` → `_2km` → `_1km` → `_500m` → `_200m` → `_100m`,
   telkens hetzelfde kwaliteitsfilter (is_day/season/weather) plus een steeds kleinere
   afstandsgrens.
6. Zodra een stap `< maxImages` oplevert (of de laatste stap, `_100m`, bereikt is): roep
   `GetMinimalLocationRecs(<die stap se resultaat>, minimal, sortitem="distance", Location=huidige GPS)`
   aan (§8 — generieke sorteerparameter) en retourneer het resultaat. Zodra een stap **wél**
   `>= maxImages` haalt, ga je door naar de volgende (kleinere) ring — het algoritme probeert
   dus de **kleinst mogelijke straal** te vinden die nog steeds genoeg kwaliteitsvolle
   kandidaten heeft, in plaats van juist te verbreden bij te weinig resultaten.

### 7.4.1 Gevonden inconsistentie in het brondiagram — **bevestigd en opgelost**

Alle zeven groene `GetMinimalLocationRecs(...)`-blokjes in de SVG verwezen letterlijk naar
dezelfde variabele `locationrecs_5km` — bevestigd als copy-paste-fout, inmiddels gecorrigeerd
in het bronbestand. Elke cascade-stap gebruikt zijn **eigen** gefilterde set: `locationrecs`,
`locationrecs_5km`, `_2km`, `_1km`, `_500m`, `_200m`, `_100m`, zoals hierboven beschreven.

### 7.4.2 Functie-opsplitsing (zie §4a)

Geen enkele grote functie met een lus over radii — elke ring is een eigen, benoemde functie
die de vorige als bouwsteen gebruikt, zodat bijvoorbeeld "voeg een `_50m`-ring toe" of
"laat de `_2km`-ring vervallen" een wijziging van/nabij precies één functie is:

```
fun contextFilter(records, isDayNight, season, weather): List<WallpaperPhotoRecord>   // stap 4
fun withinRadiusKm(records, latitude, longitude, radiusKm): List<WallpaperPhotoRecord> // generieke afstandsfilter
fun locationRecsUnrestricted(records): List<WallpaperPhotoRecord>
fun locationRecs5km(records, lat, lon): List<WallpaperPhotoRecord> = withinRadiusKm(records, lat, lon, 5.0)
fun locationRecs2km(records, lat, lon): List<WallpaperPhotoRecord> = withinRadiusKm(records, lat, lon, 2.0)
fun locationRecs1km(records, lat, lon): List<WallpaperPhotoRecord> = withinRadiusKm(records, lat, lon, 1.0)
fun locationRecs500m(records, lat, lon): List<WallpaperPhotoRecord> = withinRadiusKm(records, lat, lon, 0.5)
fun locationRecs200m(records, lat, lon): List<WallpaperPhotoRecord> = withinRadiusKm(records, lat, lon, 0.2)
fun locationRecs100m(records, lat, lon): List<WallpaperPhotoRecord> = withinRadiusKm(records, lat, lon, 0.1)
fun hasEnough(records, maxImages): Boolean = records.size >= maxImages
```

`sortLocationRecsByGPSLocation` zelf wordt dan een korte cascade die deze losse functies na
elkaar aanroept (een lijst van ring-functies + `hasEnough`-check + vroege return), niet één
functie die alle radii/filters zelf uitrekent.

### 7.4.3 `deleteRecordsDataDB` — zacht of hard verwijderen?

`updateImagesDataByCity/GPS`'s `removed`-JSON bevat URLs die *disabled* of *soft-deleted* zijn
bij RemoveSky. `WallpaperPhotoRepository` heeft zowel `setDisabled(id, true)` (zacht, laat de
rij en `file_path`-veld intact op `NULL` na) als `deleteById(id)` (hard, verwijdert de rij
volledig). Voorstel: altijd `setDisabled`, nooit hard verwijderen — consistent met hoe
`getDisabledSourceUrls()`/`WallpaperPhotoPriority` nu al met `disabled` omgaat, en omdat een
harde delete `view_count`/`createdAt`-geschiedenis weggooit die bij een latere re-enable weer
nuttig zou zijn. Vereist een nieuwe `WallpaperPhotoRepository`-query `getIdBySourceUrl(url)`
(bestaat nog niet) omdat `deleteRecordsDataDB` alleen URLs krijgt, geen ids.

## 7a. `SortLocationRecsByLocation(location, minimal, fictief)` — ontwerp (scenario 2 en 3)

Bron: het paarse diagram (`SortLocationRecsByLocation`) in de bijgewerkte SVG. Voor een
**vaste** locatie (bv. Rome) of een **fictieve** locatie (bv. Ghibli) is er geen zinvolle
"afstand tot mij" — de gebruiker staat niet fysiek in Rome. Daarom **geen cascade van
afstandsringen** zoals in §7.4, gewoon direct door naar `GetMinimalLocationRecs`, gesorteerd
op `createdDate` (= `processedAt`, §6) in plaats van `distance`.

```
fun sortLocationRecsByLocation(
    locationKey: String,
    minimal: Int,
    fictief: Boolean,
    currentLatitude: Double,   // alleen gebruikt als fictief == true
    currentLongitude: Double,  // alleen gebruikt als fictief == true
): List<WallpaperPhotoRecord>
```

Stappen:
1. `getForLocation(locationKey)` → alle lokale records voor deze plek (bestaande query) →
   `locationrecs`.
2. `maxImages` ophalen (bestaande instelling, zie §7) — **niet gebruikt** in deze functie
   (geen cascade), maar wel gelezen in het brondiagram; waarschijnlijk alleen relevant voor
   `SortLocationRecsByGPSLocation`. Te verifiëren tijdens implementatie of dit hier
   inderdaad overbodig is.
3. Bepaal `Werklocation`:
   - `fictief == true` → `Werklocation = currentLocation` (de **echte** huidige GPS-positie
     van het toestel) — nodig omdat een fictieve plek geen eigen echt weer/dag-nacht-cyclus
     heeft; die context moet van waar de gebruiker daadwerkelijk is komen.
   - `fictief == false` → `Werklocation = locationKey` zelf (Rome heeft wél een eigen,
     echte dag/nacht/seizoen/weer-context).
4. `GetMinimalLocationRecs(locationrecs, minimal, sortitem="createdDate", Location=Werklocation)`
   (§8) → resultaat direct retourneren als `Return Sorted list`.

**Bevestigd door de gebruiker:** scenario 2 en 3 zijn functioneel identiek, op deze
`Werklocation`-bepaling na — vandaar één functie met een `fictief`-vlag in plaats van twee
losse functies.

## 8. `GetMinimalLocationRecs(locationrecs, minimal, sortitem, Location)` — ontwerp

Bron: onderste groene diagram in `Flow app v2-....svg`, gegeneraliseerd t.o.v. de eerdere
versie. Dit is vrijwel 1-op-1 de bestaande filterverslappings-ladder uit `buildShowlist()`
(`WallpaperPhotoPriority.kt:152-162`), maar dan als aparte, herbruikbare functie in plaats
van inline in `buildShowlist`, en nu bruikbaar voor **beide** sorteerpaden uit §7:

```
fun getMinimalLocationRecs(
    locationRecs: List<WallpaperPhotoRecord>,   // al eventueel op afstand voorgefilterd (§7.4), of ongefilterd (§7a)
    minimal: Int,
    sortitem: SortItem,       // DISTANCE (scenario 1, §7.4) of CREATED_DATE (scenario 2/3, §7a)
    location: LocationContext, // lat/lon voor de is_day/season/weather-berekening (en voor
                                // DISTANCE ook de referentie voor de afstandsberekening zelf)
): List<WallpaperPhotoRecord>
```

`sortitem` bepaalt niet alleen waarop uiteindelijk gesorteerd wordt, maar is een generieke
parameter zodat dezelfde relaxatie-cascade voor beide scenario's herbruikt kan worden — geen
losse `GetMinimalLocationRecs`-implementatie per sorteerpad.

Cascade (stopt zodra een stap `> minimal` oplevert; sorteert steeds op `sortitem` oplopend):
1. Filter op `is_day` (o.b.v. `location`) + `season` + `weather`.
2. Filter op `day` + `weather` (season laten vallen).
3. Filter op `is_day` alleen (weather ook laten vallen).
4. Geen filter — hele `locationRecs`-set, alleen gesorteerd op `sortitem`.

**Beslist: vervangend, niet optellend.** De eerste stap die op zichzelf al `> minimal`
oplevert, wordt in zijn geheel het resultaat — de voorgaande (strengere) stap wordt volledig
weggegooid, niet aangevuld. Dit wijkt bewust af van de bestaande `buildShowlist`
(`filterStages`: `true to true, false to true, true to false, false to false`), die juist
optelt over stappen — dat oude gedrag wordt hiermee dus niet overgenomen.

### 8.1 Functie-opsplitsing (zie §4a)

Elke relaxatiestap als eigen, benoemde filterfunctie, plus een losse `hasEnough`-check
(dezelfde soort functie als in §7.4.2, hergebruikt) — zodat de volgorde of het aantal stappen
wijzigen een lokale aanpassing is. De sorteerstap zelf is een kleine `when`/strategie over
`sortitem`, niet twee losse cascades:

```
fun filterDaySeasonWeather(records, isDayNight, season, weather): List<WallpaperPhotoRecord>
fun filterDayWeather(records, isDayNight, weather): List<WallpaperPhotoRecord>
fun filterDayOnly(records, isDayNight): List<WallpaperPhotoRecord>
fun noFilter(records): List<WallpaperPhotoRecord> = records
fun sortBySortItem(records, sortitem: SortItem, location: LocationContext): List<WallpaperPhotoRecord> =
    when (sortitem) {
        SortItem.DISTANCE -> records.sortedBy { gpsDistanceKmOrWorst(it, location.latitude, location.longitude) }
        SortItem.CREATED_DATE -> records.sortedBy { it.processedAt }   // §6 — RemoveSky's processed_at
    }
fun enoughForMinimal(records, minimal): Boolean = records.size > minimal
```

`getMinimalLocationRecs` roept deze vier filterfuncties na elkaar aan (elk gevolgd door
`sortBySortItem` + `enoughForMinimal`-check), zelf zonder eigen filterlogica.

## 9. `customsortlogic.md` — Kotlin-vertaling

Directe vertaling van de JS in het document naar de bestaande datastructuur. Per §4a: elke
regel (recentheid-marge, weergaven, afstand) is een **losse comparator-functie**, samengesteld
tot één `Comparator` — niet één `when`-blok dat alle drie regels combineert. Zo is bijvoorbeeld
de 5-dagen-marge of de volgorde van de regels straks een wijziging in precies één functie:

```
fun recencyMarginComparator(marginDays: Long = 5, now: Long): Comparator<WallpaperPhotoRecord> =
    Comparator { a, b ->
        // processedAt (nieuw veld, §6) = wanneer RemoveSky de foto verwerkte, NIET createdAt
        // (lokale eerste-sync-tijd) en NIET capturedAt (EXIF-fotodatum).
        val dA = (now - a.processedAt) / MS_PER_DAG
        val dB = (now - b.processedAt) / MS_PER_DAG
        when {
            dB <= dA - marginDays -> 1   // B minimaal marginDays dagen recenter
            dA <= dB - marginDays -> -1  // A minimaal marginDays dagen recenter
            else -> 0                    // binnen de marge: geen uitspraak, volgende regel beslist
        }
    }

fun viewCountComparator(): Comparator<WallpaperPhotoRecord> =
    Comparator { a, b -> a.viewCount - b.viewCount }

fun distanceComparator(latitude: Double, longitude: Double): Comparator<WallpaperPhotoRecord> =
    Comparator { a, b ->
        gpsDistanceKmOrWorst(a, latitude, longitude)
            .compareTo(gpsDistanceKmOrWorst(b, latitude, longitude))
    }

fun sortByRecencyViewsDistance(
    records: List<WallpaperPhotoRecord>,
    latitude: Double,
    longitude: Double,
    now: Long = System.currentTimeMillis(),
): List<WallpaperPhotoRecord> = records.sortedWith(
    recencyMarginComparator(now = now)
        .thenComparing(viewCountComparator())
        .thenComparing(distanceComparator(latitude, longitude))
)
```

Dit vervangt de losse tiebreak-comparators die nu in beide oude algoritmes staan
(`.thenBy { it.viewCount }.thenBy { it.lastShownAt ... }.thenBy { it.createdAt }` in
`selectWallpaperPhoto`, en `.thenByDescending { it.createdAt }.thenBy { it.viewCount }` in
`buildShowlist`) — precies de twee punten die de analyse als inconsistent aanmerkt (punt 1
en 3).

**Beslist:** `rating`/duim-omlaag-omhoog komt als feature te vervallen. Geen filter, geen
sorteercriterium — de "niet-thumbs-down"-tier uit `selectWallpaperPhoto` heeft dus geen
equivalent nodig in de nieuwe flow.

## 10. `GetsortedResultlist()` en de rotatielus

Bron: bijgewerkte diagrammen (`Flow app v2-....svg`, laatste versie). Er zijn nu **twee**
periodieke lussen, met mogelijk verschillende intervallen:

1. **Buiten** (nieuw, boven in het schema): "Every x mins change background" — de eigenlijke
   wallpaper-rotatie. Roept elke tik `GetsortedResultlist()` aan en beslist op basis daarvan
   welke foto getoond wordt.
2. **Binnen** `GetsortedResultlist()` (het bruine blok): de locatie-check/sync-lus die ik
   eerder als "main loop" beschreef — grotendeels ongewijzigd, alleen nu benoemd als de
   inhoud van deze functie, en met een **persistente state-variabele** `_currentSortedResultlist`
   die het laatst bekende goede resultaat vasthoudt tussen aanroepen in.

### 10.0a Multi-locatie: per-locatie state + welke locatie is actief

**Bevestigd door de gebruiker.** De app onderhoudt al meerdere opgeslagen locaties
(`WallpaperPhotoRefreshWorker.locationsToProcess`, tot `MAX_LOCATIONS`), waarvan er steeds
maar **één** de zichtbare wallpaper aanstuurt (bestaande `shouldActivateLocation(index)`). Dit
raakt het hele ontwerp hieronder:

- `_currentSortedResultlist` en de rotatie-index (§10.2) zijn **per locatie**
  (`Map<locationKey, List<WallpaperPhotoRecord>>` / `Map<locationKey, Int>`), niet één
  globale waarde — net als het bestaande `store.photoRefreshedAtFor(location.formattedId)`.
  Zo kun je switchen tussen locaties zonder dat "thuis" en "Rome" elkaars rotatie overschrijven.
- De **rotatielus** (§10.2/§10.3, "Every x mins change background") draait alleen voor de
  momenteel **actieve** locatie. De overige (tot `MAX_LOCATIONS - 1`) locaties worden wel op
  de achtergrond gesynchroniseerd (`getImagesDataBy*`/`updateImagesDataBy*` + `upsertDataDB`/
  `deleteRecordsDataDB`, zodat hun `wallpaper_photos`-data actueel blijft), maar nemen niet
  deel aan de zichtbare rotatie totdat ze zelf actief worden.
- De bestaande `pruneDisabledPhotos()`-aanroep in de worker wordt vermoedelijk overbodig
  zodra `deleteRecordsDataDB` dit al afhandelt voor elke gesynchroniseerde locatie — te
  bevestigen/verwijderen tijdens implementatie (niet honderd procent zeker, dus niet
  blind weghalen zonder te verifiëren dat er geen gat ontstaat).

### 10.0 `GetsortedResultlist()` — binnenkant (bijgewerkt met "Selected location")

Nieuw in het schema: vóórdat er gesynchroniseerd wordt, wordt bepaald **welk type** locatie
dit is (zie §7 voor de drie scenario's) — dat bepaalt zowel welke sync-aanroep (GPS vs. city)
als welke sorteerfunctie (§7.4 vs. §7a) gebruikt wordt.

```
fun getSortedResultlist(locationKey: String): List<WallpaperPhotoRecord> {
  when (selectedLocationType(locationKey)) {   // "Current position" / "vaste locatie" / "fictieve locatie"

    CURRENT_POSITION -> {
      huidige GPS ophalen
      dichtstbijzijnde bekende locatie zoeken (lokale DB, binnen Xkm)
      als "nog geen X minuten op deze exacte GPS-plek" → return _currentSortedResultlist[locationKey] (niets doen)
      anders:
        als locationKey nieuw (nog niet in lokale DB) → getImagesDataByGPS(GPS, datetime, range)
        anders                                        → updateImagesDataByGPS(GPS, datetime, lastUpdate, range)
        upsertDataDB(resultaat.upserted) [+ deleteRecordsDataDB(resultaat.removed) bij update]
        als er nieuwe/gewijzigde records zijn:
          Resultlist = sortLocationRecsByGPSLocation(locationKey, minimal, ...)   // §7.4
    }

    VASTE_LOCATIE, FICTIEVE_LOCATIE -> {
      als "if location is new db?" (nog geen rijen in wallpaper_photos voor deze locationKey):
        getImagesDataByCity(datetime, locationKey)              // géén GPS/range nodig
      anders:
        updateImagesDataByCity(datetime, lastUpdate, locationKey)
      upsertDataDB(resultaat.upserted) [+ deleteRecordsDataDB(resultaat.removed) bij update]
      als er nieuwe/gewijzigde records zijn:
        Resultlist = sortLocationRecsByLocation(                 // §7a
            locationKey, minimal,
            fictief = (selectedLocationType(locationKey) == FICTIEVE_LOCATIE),
            currentLatitude, currentLongitude,                    // alleen gebruikt als fictief
        )
    }
  }

  als geen nieuwe Resultlist berekend (bovenstaande guards sloegen over) →
    return _currentSortedResultlist[locationKey]   // niets doen

  als Resultlist.count < minimal:
    // "Nee, te weinig, doe niks": behoud het bestaande resultaat ongewijzigd
    _currentSortedResultlist[locationKey] = _currentSortedResultlist[locationKey]
  anders:
    sortedResultlist = sortByRecencyViewsDistance(Resultlist, ...)   // customsortlogic.md, precies één keer, op het hele Resultlist
    downloadMissingImages(sortedResultlist)                         // §10.1
    _currentSortedResultlist[locationKey] = sortedResultlist        // nieuw resultaat wordt het huidige, vóór deze locatie

  return _currentSortedResultlist[locationKey]
}
```

`_currentSortedResultlist[locationKey]` is dus een **persistente cache per locatie** van het
laatst berekende resultaat — elke aanroep van `getSortedResultlist()` is daardoor goedkoop
wanneer er niets veranderd is (de guards hierboven zorgen dat het meeste werk wordt
overgeslagen), en retourneert alleen een écht nieuwe lijst wanneer er ook echt iets gewijzigd
is. De rotatielus in §10.2 roept dit alleen aan voor de momenteel **actieve** locatie (§10.0a);
de overige locaties worden apart, buiten deze functie om, gesynchroniseerd (dezelfde
sync-aanroepen, maar zonder de Resultlist/CustomSort/download-staart, en zonder eigen rotatie).

Ook hier per §4a: `sortLocationRecsByGPSLocation`/`sortLocationRecsByLocation`,
`sortByRecencyViewsDistance` en `downloadMissingImages` zijn losse aanroepen na elkaar, niet
samengevoegd tot één functie — zo is bijvoorbeeld de download-stap apart te testen/vervangen
zonder de sorteerlogica te raken.

Dit bouwt direct voort op `getImagesDataByGPS`/`updateImagesDataByGPS`/`getImagesDataByCity`/
`updateImagesDataByCity` (al gebouwd). Nieuw is: de `selectedLocationType`-vertakking, de
"zelfde locatie/X minuten"-guard (alleen voor `CURRENT_POSITION`), de "is locatie nieuw"-check
tegen de lokale DB (simpelweg `getForLocation(locationKey).isEmpty()`), de per-locatie
`_currentSortedResultlist`-state, en de aanroep-keten na een succesvolle sync.
Waarschijnlijke landingsplek: `WallpaperPhotoRefreshWorker.kt`/`WallpaperRepository.kt`, naast
de bestaande `WallpaperPhotoRefreshPlanner.needsRefresh()`-check (die al een vergelijkbare "is
het weer tijd"-gate heeft) en `location.isFictional`/`toWallpaperPlaceQuery()` (al aanwezig,
bepaalt vermoedelijk grotendeels al `selectedLocationType`).

### 10.1 Nieuwe eindstap: "Download missing images in cache"

Toegevoegd in de bijgewerkte hoofd-flow, ná `CustomSort(Resultlist)`: van de uiteindelijke
gesorteerde lijst worden de afbeeldingen die nog geen lokaal bestand hebben
(`WallpaperPhotoRecord.filePath == null`) gedownload en gecachet, in de volgorde van de
gesorteerde lijst (belangrijkste/bovenste foto's eerst). Dit is grotendeels een hergebruik van
bestaande downloadlogica in `WallpaperRepository` (`cacheFile()`, `enforceCacheLimit()` /
`pruneLocationCache()` / `prunePhotoCache()`, zie eerdere sessie) — nieuw is vooral dat de
downloadvolgorde nu expliciet door `sortByRecencyViewsDistance` bepaald wordt, in plaats van
door de download-op-eerste-match-logica die `resolveImage()`/`refreshFor()` nu gebruiken.
**Beslist (verfijnd na een concreet voorbeeld):**
- `sortedResultlist` blijft de volledige, gesorteerde lijst met **records/metadata** (kan
  ruim boven de 12 zitten) — er wordt niet aan die lijst zelf geknipt. Alleen het aantal
  daadwerkelijk **gedownloade afbeeldingsbestanden** wordt begrensd: van
  `missingFromCache(sortedResultlist)` wordt alleen de eerste `maxCachedPhotosPerLocation`
  (huidige default 12) daadwerkelijk als bestand gedownload/gecachet; de rest van de lijst
  blijft gewoon bestaan als metadata zonder lokaal bestand, klaar om alsnog gedownload te
  worden zodra er ruimte vrijkomt (bv. bij een volgende cyclus).
- **Na het downloaden wordt de bestaande `pruneLocationCache()`-logica meteen aangeroepen**
  (niet pas ooit later): zodra het totaal aantal lokaal gecachete bestanden voor deze locatie
  boven `maxCachedPhotosPerLocation` uitkomt, gaan de **oudste** bestanden (op download-/
  cache-tijdstip, `File::lastModified` — dus niet op sorteerpositie in `sortedResultlist`)
  direct weg. Dit is geen nieuwe eviction-regel; `pruneLocationCache()` doet dit al precies
  zo, hij wordt alleen nu expliciet als vaste stap ná `downloadMissingImages` ingepland in
  plaats van losstaand/toevallig elders getriggerd.

  Voorbeeld ter verificatie: 6 foto's al gecached, nieuwe lijst bevat 10 foto's waarvan 4 al
  geladen zijn → 6 nieuwe downloads. Zodra het totaal boven de 12 komt, verdwijnen de oudste
  bestanden het eerst — ongeacht of ze toevallig nog wél in de nieuwe `Resultlist` voorkomen.
- Bestanden die niet meer in de nieuwe `Resultlist` voorkomen én binnen de cap blijven, worden
  *niet* apart verwijderd puur omdat ze uit de lijst zijn gevallen — alleen de count-cap
  hierboven (en de bestaande globale MB-cap, `prunePhotoCache()`) bepalen wat wegmoet.

Ook hier, per §4a, als losse functies:

```
fun missingFromCache(sortedRecords): List<WallpaperPhotoRecord> = sortedRecords.filter { it.filePath == null }
fun downloadOne(record): WallpaperPhotoRecord  // download + cacheFile() + upsertDownloaded(filePath=...)
fun downloadMissingImages(sortedRecords, maxCount: Int = store.maxCachedPhotosPerLocation) {
    missingFromCache(sortedRecords).take(maxCount).forEach { downloadOne(it) }   // volgorde = sorteervolgorde
    pruneLocationCache(place, maxCachedPhotosPerLocation)   // bestaande functie, direct aangeroepen
}
```

### 10.2 Buitenkant — de rotatielus (nieuw)

Nieuw in het schema: een aparte, buitenste periodieke lus die de daadwerkelijke
wallpaper-wisseling doet, los van de locatie-sync-lus in §10.0 (mogelijk een ander interval).
Draait per §10.0a **alleen voor de momenteel actieve locatie**.

```
Elke X min ("change background"):
  activeLocationKey = huidige actieve locatie (bestaande shouldActivateLocation-mechanisme)
  vorige = _currentSortedResultlist[activeLocationKey]           // snapshot vóór deze aanroep
  sortedResultlist = getSortedResultlist(activeLocationKey)      // §10.0 — kan ongewijzigd of gloednieuw zijn
  als sortedResultlist == vorige:
    // niets veranderd: gewoon doorschuiven naar de volgende foto in de bestaande lijst
    item = getNextSortedResultlistItem(activeLocationKey)
  anders:
    // de lijst is (opnieuw) gesorteerd/gewijzigd: begin weer vooraan
    item = getFirstSortedResultlistItem(activeLocationKey)
  while isBannedByUser(item):           // §10.3 — meteen overslaan, niet wachten op volgende sync
    item = getNextSortedResultlistItem(activeLocationKey)
  showImageBackground(item)
```

Twee nieuwe, losse functies (per §4a) horen hierbij, met de rotatie-index ook per locatie
(`Map<locationKey, Int>`, per §10.0a):

```
fun getFirstSortedResultlistItem(locationKey: String): WallpaperPhotoRecord  // reset rotatie-index[locationKey] naar 0
fun getNextSortedResultlistItem(locationKey: String): WallpaperPhotoRecord    // rotatie-index[locationKey] + 1 (met wraparound aan het eind)
```

Dit is de eigenlijke reden dat `sortByRecencyViewsDistance` maar **één keer** wordt
toegepast, op het complete `Resultlist` (beantwoordt open vraag §11.8): de rotatielus loopt
zelf door die ene, vaste sortering heen — een tussentijdse hersortering zou de rotatie-index
zinloos maken.

### 10.3 "Is item banned by user?" — nieuwe kolom, niet het bestaande `disabled`-veld

Nieuw in het schema: na het kiezen van het volgende/eerste item checkt de rotatielus of de
gebruiker deze foto zelf heeft verborgen, en slaat hem dan meteen over (`getNextSortedResultlistItem`
opnieuw, tot een niet-verborgen item gevonden is).

**Beslist:** dit hergebruikt **niet** het bestaande `disabled`-veld op `WallpaperPhotoRecord`.
Dat veld wordt namelijk al gedeeld tussen twee verschillende, onafhankelijke redenen:
1. Curator-side (RemoveSky schakelt een foto uit/verwijdert 'm — via `purgeUrls()` /
   `deleteRecordsDataDB` uit deze pipeline);
2. Gebruiker-side (de bestaande "afbeeldingen beheren"-UI, `WallpaperPhotoManagerActivity`
   → `setPhotoDisabled` → `WallpaperRepository.setDisabled`).

Beide delen nu dezelfde kolom, wat betekent dat een server-resync een persoonlijke
gebruikerskeuze zou kunnen overschrijven (of omgekeerd). Nieuwe, losse kolom
`user_banned` (`wallpaper_photos.sq` + `WallpaperPhotoRecord`) lost dit op: de bestaande
"afbeeldingen beheren"-UI (die al bestaat, geen nieuw scherm nodig) wijst voortaan naar dit
nieuwe veld in plaats van naar het gedeelde `disabled`.

```
fun isBannedByUser(record: WallpaperPhotoRecord): Boolean = record.userBanned
```

### 10.4 Renderer moet voortaan de DB/rotatielus volgen

Bevestigd via `MaterialLiveWallpaperService.kt` (regels 926, 1084): de renderer leest op dit
moment **alleen** `WallpaperImageStore.cachedPhotoPath`/`loadCachedBitmap()` — hij weet niets
van `wallpaper_photos`, de sorteerlijst, of de rotatie-index. Dit is precies het punt dat
`ANALYSE - Sorteerfunctie wallpaper foto's (huidige werking).md` al signaleerde ("de renderer
leest alleen `store.cachedPhotoPath`").

**Nodig:** de rotatielus (§10.2/§10.3) moet, ná het kiezen van een niet-gebande `item`, dat
item ook daadwerkelijk **wegschrijven** naar `cachedPhotoPath`/`cachedPhotoUrl` (zoals nu al
gebeurt bij een normale refresh), zodat de bestaande renderer-code ongewijzigd kan blijven —
de rotatielus wordt dus de nieuwe enige plek die bepaalt *welke* foto in
`cachedPhotoPath` terechtkomt, in plaats van de oude `buildShowlist`/`selectWallpaperPhoto`.

### 10.5 Migratie: bestandsgebaseerde cache → `wallpaper_photos`

**Beslist (patroon):**
```
als bestandsgebaseerde cache bestaat (WallpaperImageStore.allRecentUrls()/cachedPhotoUrl
    bevat data die nog niet als rij in wallpaper_photos staat):
  migreer die data naar wallpaper_photos (upsertDownloaded per URL, minimale velden)
  als migratie geslaagd (alle URLs succesvol geschreven):
    verwijder de bestandsgebaseerde cache-state (WallpaperImageStore.recentUrls leegmaken)
  anders:
    bestandsgebaseerde cache blijft staan, opnieuw proberen bij volgende opstart
```

Dit hergebruikt het patroon van de eerder deze sessie ontworpen (en sindsdien geschrapte,
zie §3) `RemoveSkyImageDao.seedFromFileCache()` — nu gericht op `WallpaperPhotoRepository`
in plaats van de losse SQLite-tabel:

```
fun fileCacheExists(store: WallpaperImageStore): Boolean =
    store.allRecentUrls().isNotEmpty() || store.cachedPhotoUrl != null

fun migrateFileCacheToRepository(store: WallpaperImageStore, repo: WallpaperPhotoRepository): Boolean {
    val urls = store.allRecentUrls().values.flatten().toSet() + listOfNotNull(store.cachedPhotoUrl)
    val allOk = urls.all { url -> runCatching { repo.upsertDownloaded(/* minimale velden, sourceUrl = url */) }.isSuccess }
    return allOk
}

fun runMigrationOnce(store: WallpaperImageStore, repo: WallpaperPhotoRepository) {
    if (!fileCacheExists(store)) return
    if (migrateFileCacheToRepository(store, repo)) {
        store.setRecentUrls(..., emptyList())   // bestaande functie, per placeKey leegmaken
        // of: alle placeKeys in allRecentUrls() doorlopen en leegmaken
    }
    // bij falen: geen state-wijziging, volgende app-start probeert opnieuw
}
```

Net als bij de eerdere seed-migratie ontbreken bij een gemigreerde rij de meeste
`customsortlogic.md`-velden (`processed_at`, `view_count`, enz.) — die worden vanzelf
gevuld zodra de eerstvolgende echte `getSortedResultlist()`-cyclus voor die locatie draait
(`upsertDataDB`/`upsertDownloaded` overschrijft dan met volledige data).

## 11. Open vragen (graag beantwoorden vóór implementatie)

1. ~~**§3** — RemoveSkyImageDao.kt laten vervallen?~~ — **beantwoord: ja.**
   `RemoveSkyImageDao.kt` komt te vervallen; `upsertDataDB`/`deleteRecordsDataDB` worden
   wrappers rond `WallpaperPhotoRepository`.
2. ~~**§7.4.1** — cascade-variabele-kopieerfout~~ — **beantwoord:** bevestigd als kopieerfout,
   gecorrigeerd in de SVG. Elke stap gebruikt zijn eigen gefilterde set.
3. ~~**§7** — relatie `maxImages`/`minimal`~~ — **beantwoord:** `maxImages` is geen nieuwe
   instelling, maar exact `WallpaperImageStore.maxCachedPhotosPerLocation` (bestaande,
   gebruiker-instelbare cache-cap per locatie), rechtstreeks hergebruikt.
4. ~~**§8** — optellen of vervangen~~ — **beantwoord: vervangend.** De eerste stap die op
   zichzelf al `> minimal` oplevert, wordt het hele resultaat; geen optelling over stappen
   (wijkt dus bewust af van het huidige `buildShowlist`-gedrag).
5. ~~**§6** — betekenis `created_date`~~ — **beantwoord:** het moment waarop RemoveSky de foto
   heeft **verwerkt** (`processed.processed_at`), niet `createdAt` (lokale sync) en niet
   `capturedAt` (EXIF). Vereist een nieuw veld + backend-uitbreiding, zie §6/§12.
6. ~~**§9** — thumbs-down als filter~~ — **beantwoord:** vervalt volledig, ook uit de app-UI
   (duim-knoppen worden verwijderd) — geen filter, geen sorteercriterium, geen `rating`-veld
   meer nodig op termijn.
7. ~~**§5** — blijft `selectWallpaperPhoto()` apart pad~~ — **beantwoord:** nee, wordt
   volledig verwijderd. Curator-afwijzingen hoeven niet instant vervangen te worden; die komen
   vanzelf naar voren bij de eerstvolgende `getSortedResultlist()`-cyclus.
8. ~~**§10** — waar wordt `sortByRecencyViewsDistance` toegepast~~ — **beantwoord door het
   bijgewerkte schema (§10.2):** precies één keer, op het complete `Resultlist`, ná de
   `>= minimal`-gate. De rotatielus (`getFirstSortedResultlistItem`/`getNextSortedResultlistItem`)
   loopt daarna zelf door die ene vaste sortering — een tussentijdse hersortering elders zou
   de rotatie-index zinloos maken.
9. ~~**§10.1** — hoeveel downloaden, wat met oude cache-bestanden~~ — **beantwoord:** cap op
   `maxCachedPhotosPerLocation` (niet de hele lijst); zodra het totaal daarboven komt, gaan de
   oudste bestanden (op download-tijdstip) direct weg via de bestaande `pruneLocationCache()`,
   meteen na `downloadMissingImages` — niet pas via losstaande, later getriggerde eviction.

## 12. Voorgestelde implementatiestappen

1. Alle punten uit §11 zijn beantwoord — geen blokkerende open vragen meer.
2. **Backend (removesky-service):** `processed_at` toevoegen aan de `/search`-resultaatdict
   in `run_search()` (`app/services/processing.py`, rond regel 907-934) — bestaat al in
   `processed_dao._COLUMNS`, wordt alleen nog niet meegegeven in de JSON-response.
3. **App:** nieuw `processedAt`-veld op `RemoveSkyEnabledPhoto`/`ImagesSyncResult`-parsing in
   `RemoveSkyProvider.kt` (lezen van de nieuwe `processed_at`-sleutel), nieuwe kolom
   `processed_at` in `wallpaper_photos.sq` + `WallpaperPhotoRecord`, opgenomen in
   `upsertDownloaded`/`upsertDataDB`.
3a. Nieuwe kolom `user_banned` in `wallpaper_photos.sq` + `WallpaperPhotoRecord` (§10.3),
    los van het bestaande `disabled`. `WallpaperPhotoManagerActivity`/`setPhotoDisabled` laten
    wijzen naar dit nieuwe veld (`setUserBanned`) in plaats van naar `setDisabled` — de
    bestaande beheer-UI blijft ongewijzigd, alleen het onderliggende veld verandert.
4. `RemoveSkyImageDao.kt` verwijderen; `upsertDataDB`/`deleteRecordsDataDB` herschrijven als
   wrappers rond `WallpaperPhotoRepository` (incl. nieuwe `getIdBySourceUrl`-query in
   `wallpaper_photos.sq`).
5. `minimal` toevoegen aan `WallpaperImageStore` (zelfde patroon als
   `maxCachedPhotosPerLocation`), met een bijbehorend item in `DebugSettingsScreen.kt`
   (niet in de reguliere gebruikersinstellingen) zodat alleen admin/debug-toegang dit kan
   aanpassen.
6. `GetMinimalLocationRecs` bouwen als losse, kleine functies per §8.1, met het
   gegeneraliseerde `sortitem`/`location`-parameterpaar (§8) — kleinste, meest losstaande
   stuk, gebruikt door beide sorteerpaden hieronder.
7. `SortLocationRecsByGPSLocation` bouwen als losse, kleine functies per §7.4.2 (scenario 1,
   afstand-cascade), met `GetMinimalLocationRecs(sortitem=DISTANCE)` als laatste stap.
7a. `SortLocationRecsByLocation(location, minimal, fictief)` bouwen (§7a, scenario 2/3, geen
    cascade) — de `Werklocation`-bepaling (fictief → huidige GPS, anders → de locatie zelf),
    met `GetMinimalLocationRecs(sortitem=CREATED_DATE)` als enige stap.
8. `sortByRecencyViewsDistance` bouwen als losse comparators per §9 (gebruikt het nieuwe
   `processedAt`-veld uit stap 3).
9. `downloadMissingImages` bouwen per §10.1, op basis van bestaande `WallpaperRepository`-cachelogica.
10. `getSortedResultlist(locationKey)` + per-locatie `_currentSortedResultlist`-state bouwen
    (§10.0/§10.0a), inclusief de `selectedLocationType`-vertakking (CURRENT_POSITION vs.
    VASTE_LOCATIE vs. FICTIEVE_LOCATIE, vermoedelijk af te leiden uit bestaande
    `location.isFictional`/`toWallpaperPlaceQuery()`) in `WallpaperPhotoRefreshWorker`/
    `WallpaperRepository`.
10a. De buitenste rotatielus bouwen (§10.2/§10.3), per-locatie rotatie-index, alleen actief
    voor de huidige `shouldActivateLocation`-winnaar: `getFirstSortedResultlistItem`/
    `getNextSortedResultlistItem` + `isBannedByUser`-skip + de "Every x mins change
    background"-trigger die `getSortedResultlist(activeLocationKey)` aanroept en op basis
    van wijziging kiest tussen beide.
10d. Bestaande `pruneDisabledPhotos()`-aanroep in de worker verifiëren en vermoedelijk
    verwijderen (§10.0a) — pas nadat bevestigd is dat `deleteRecordsDataDB` hetzelfde gat
    dekt voor alle gesynchroniseerde locaties, niet alleen de actieve.
10b. `MaterialLiveWallpaperService.kt` aanpassen (§10.4): de rotatielus schrijft het gekozen
    item weg naar `cachedPhotoPath`/`cachedPhotoUrl`, renderer-code zelf blijft ongewijzigd.
10c. Migratiefunctie bouwen (§10.5): bij app-start, als de bestandsgebaseerde cache
    (`WallpaperImageStore.allRecentUrls()`/`cachedPhotoUrl`) nog data bevat die niet als rij
    in `wallpaper_photos` staat — migreren via `upsertDownloaded`; alleen bij een volledig
    geslaagde migratie de bestandsgebaseerde cache-state opruimen, anders ongewijzigd laten
    staan voor een nieuwe poging bij de volgende app-start.
11. Oude paden uitfaseren zodra het nieuwe pad geverifieerd werkt: `buildShowlist()` (vervangen
    door §7-§9) en `selectWallpaperPhoto()` (volledig verwijderd, §5).
12. `rating`/duim-omlaag-omhoog: sorteer-/filtercode al buiten scope (§9), UI-verwijdering
    (duim-knoppen) losstaand oppakken, `rating`-kolom in `wallpaper_photos.sq` pas verwijderen
    zodra de UI daadwerkelijk weg is.

## 13. Unit tests

- `GetMinimalLocationRecs`: elke relaxatiestap apart testen (genoeg bij eerste filter / pas bij
  laatste "geen filter"-stap genoeg / nooit genoeg ondanks alle stappen).
- `SortLocationRecsByGPSLocation`: cascade stopt op de juiste ring; lege locatie geeft lege
  lijst zonder crash; `maxImages`-grens exact op de rand (`== maxImages`, niet alleen `>`/`<`).
- `SortLocationRecsByLocation` (fictief=false/true): geen cascade, direct naar
  `GetMinimalLocationRecs(sortitem=CREATED_DATE)`; `Werklocation` klopt in beide gevallen
  (locatie zelf vs. huidige GPS).
- `sortByRecencyViewsDistance`: het rekenvoorbeeld uit `customsortlogic.md` (5 items,
  verwachte volgorde `102m, 200m(10d), 200m(20d), 111m, 100m`) 1-op-1 overnemen als testcase.
- Regressietest: dezelfde invoerset door zowel het oude (`buildShowlist`) als het nieuwe pad
  halen en verschillen expliciet documenteren (niet per se gelijk verwachten, maar wél
  verklaarbaar vanuit dit document).
- Multi-locatie: rotatie van locatie A raakt nooit de state van locatie B; alleen de
  `shouldActivateLocation`-winnaar draait de rotatielus.

## 14. Definition of done

- De resterende open vragen uit §11 zijn beantwoord en verwerkt in de implementatie.
- `RemoveSkyImageDao.kt` is verwijderd (of expliciet, beargumenteerd behouden).
- `GetMinimalLocationRecs`, `SortLocationRecsByGPSLocation`, `SortLocationRecsByLocation`,
  `sortByRecencyViewsDistance` en `downloadMissingImages` bestaan elk als een set kleine,
  losse, unit-testbare functies per §4a (geen grote inline-lussen/`when`-blokken, geen
  duplicatie in `WallpaperRepository`).
- Het rekenvoorbeeld uit `customsortlogic.md` slaagt als unit test.
- `ANALYSE - Sorteerfunctie wallpaper foto's (huidige werking).md` is bijgewerkt (of
  gearchiveerd met een verwijzing naar dit document) zodra het oude gedrag daadwerkelijk is
  vervangen.
