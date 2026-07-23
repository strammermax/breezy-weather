# Analyse: sorteerfunctie wallpaper-foto's (huidige werking)

Doel van dit document: beschrijven hoe de sortering/selectie van wallpaper-foto's *nu* werkt,
als basis voor een gesprek over wat er beter kan. Er is nog niets aangepast.

## Waar het zit

Alle sorteerlogica staat in één bestand, volledig on-device (geen backend-sortering):

- [`WallpaperPhotoPriority.kt`](../app/src/main/kotlin/com/liveweatherwallpaperapp/wallpaper/photo/WallpaperPhotoPriority.kt)
  — bevat de twee sorteeralgoritmes.
- [`WallpaperRepository.kt`](../app/src/main/kotlin/com/liveweatherwallpaperapp/wallpaper/photo/WallpaperRepository.kt)
  — roept ze aan vanuit `refreshFor()` (regel 255), `purgeUrls()` (regel 479) en
  `prefetchShowlist()` (regel 717).
- [`WallpaperPhotoRefreshWorker.kt`](../app/src/main/kotlin/com/liveweatherwallpaperapp/wallpaper/photo/WallpaperPhotoRefreshWorker.kt)
  — periodieke achtergrondtaak die deze flow triggert.
- [`WallpaperPhotoRecord`](../../data/src/main/kotlin/livewallpaperweather/data/wallpaper/WallpaperPhotoRepository.kt)
  (regels 13-71) — de datavelden die als sorteercriteria dienen: `rating`, `viewCount`,
  `createdAt`, `updatedAt`, `lastShownAt`, `dayPeriod`, `season`, `weather`,
  `exifLat/Lon`, `resolvedLat/Lon`.
- [`docs/UpdateFLow.md`](UpdateFLow.md) regels 52-67 en 105 beschrijven waar dit in de
  ververs-flow past.
- [`ACT-019 - Testpagina sorteervolgorde (admin)`](ACT-019%20-%20Testpagina%20sorteervolgorde%20%28admin%29.md)
  is een (nog niet gebouwd) voorstel voor een simulatiepagina — het bewijst dat dit onderwerp
  al eerder als lastig te doorgronden is bestempeld.

Er zijn **twee verschillende sorteeralgoritmes** die op dezelfde data werken, met net andere regels.

## A) `selectWallpaperPhoto()` — kiest direct één vervangende foto

Gebruikt wanneer een getoonde foto server-side wordt verwijderd (`purgeUrls`) en er meteen een
vervanger nodig is. Strikt lexicografisch: elke stap breekt alleen een gelijkspel van de vorige
stap — geen optelsom van scores.

Volgorde (`WallpaperPhotoPriority.kt:62-71`):

1. **Niet thumbs-down** (`rating != -1`) — een afgekeurde foto wint alleen als *alle* kandidaten zijn afgekeurd.
2. **Seizoen-match** (2 = huidig seizoen, 1 = onbekend seizoen, 0 = ander, bekend seizoen).
3. **Dag/nacht-match** — onbekende `dayPeriod` telt als "dag".
4. **GPS-afstand** (haversine) — dichterbij wint; onbekende locatie = altijd laatste in deze tier.
5. **Thumbs-up** (`rating == 1`).
6. **Minste `viewCount`.**
7. **`lastShownAt`** oplopend — nooit-getoond (`null` → kleinst mogelijke waarde) wint.
8. **`createdAt`** oplopend, als allerlaatste tiebreak.

## B) `buildShowlist()` — bouwt een lijst van ≥ 6 kandidaten (standaard `minSize`)

Gebruikt bij elke normale ververs-cyclus (`refreshFor`) om uit de al-gecachete foto's een
prioriteitenlijst te bouwen, vóórdat nieuwe foto's gedownload worden. Werkt via een **escalerende
fallback-ladder**: pas als een stap niet genoeg kandidaten oplevert, wordt de volgende, ruimere
stap geprobeerd.

Sortering **binnen elke stap** (`WallpaperPhotoPriority.kt:157-161`) is korter dan bij A:

1. Niet thumbs-down
2. **`createdAt` aflopend** (nieuwste eerst)
3. Minste `viewCount`

Seizoen, dag/nacht, GPS-afstand en weer worden hier niet als tiebreak gebruikt, maar als
**filter per ladderstap**:

1. Alleen als het om de "huidige positie" gaat (GPS volgt de gebruiker echt): radius 1/2/5 km,
   met filtercombinaties seizoen+weer → alleen weer → alleen seizoen → geen van beide
   (12 pogingen).
2. Hele locatie zonder afstandsgrens, dezelfde 4 filtercombinaties (altijd de enige stap voor
   een vaste, handmatig gekozen locatie — die heeft geen zinvolle "afstand tot mij").
3. Laatste redmiddel: ook dag/nacht-filter laten vallen; alleen "niet uitgeschakeld/uitgesloten" blijft over.

`gpsDistanceKmOrWorst()` gebruikt eerst de EXIF-GPS van de foto zelf, valt terug op RemoveSky's
`resolvedLat/Lon` (plaats-centrum), en anders `Double.MAX_VALUE`.

## Waar dit precies wordt gebruikt

- **`refreshFor()`** (elke normale achtergrond-ververs): probeert eerst `buildShowlist()` op
  reeds-gecachete foto's, vóór een nieuwe download.
- **`purgeUrls()`**: als de curator (RemoveSky) een *op dit moment getoonde* foto verwijdert,
  kiest `selectWallpaperPhoto()` direct een vervanger uit de resterende cache.
- **`prefetchShowlist()`**: downloadt tot 4 van de best-gerangschikte nog-niet-gecachete
  kandidaten, met tijdelijke placeholder-records zodat nieuwe en gecachete foto's eerlijk
  tegen elkaar worden afgewogen.
- **`WallpaperPhotoRefreshWorker`** triggert dit periodiek op de achtergrond.

## Opvallende inconsistenties / mogelijke oorzaken van klachten

1. **Twee algoritmes, verschillende criteria-volgorde voor "dezelfde" taak.** `selectWallpaperPhoto`
   gebruikt 8 tiers (incl. seizoen, dag/nacht, GPS, thumbs-up, `lastShownAt`); `buildShowlist`
   gebruikt seizoen/dag-nacht/GPS/weer alleen als filter, niet als tiebreak, en sorteert
   verder alleen op rating/`createdAt`/`viewCount`. Welke foto bovenaan komt, hangt dus af van
   *welk codepad* toevallig actief was (normale refresh vs. purge-vervanging) — een
   waarschijnlijke bron van "het klopt niet"-gevoel bij gebruikers.

2. **`lastShownAt` wordt genegeerd in `buildShowlist`.** Bij A telt "nooit getoond" mee als
   voordeel; bij B helemaal niet. Een net getoonde foto kan in de showlist zo weer bovenaan
   komen, puur omdat hij recenter is aangemaakt.

3. **Tegengestelde `createdAt`-richting.** In B is nieuwer = beter (aflopend); in A is
   `createdAt` juist een allerlaatste, zwakke tiebreak in oplopende richting. Klachten als
   "steeds dezelfde nieuwe foto's" of "steeds oude foto's" kunnen dus per codepad een ander
   antwoord hebben.

4. **GPS-tier is in de praktijk vaak een no-op.** De meeste gecureerde landschapsfoto's hebben
   geen EXIF-GPS, dus valt de vergelijking terug op het plaats-centrum — identiek voor alle
   foto's van diezelfde plaats. Sortering valt dan direct door naar thumbs-up/`viewCount`.

5. **Vaste locatie slaat radius-ladder volledig over** (bewuste keuze, maar wel iets waar
   gebruikers met een handmatig gekozen locatie tegenaan kunnen lopen: geen GPS-verfijning bij hen).

6. **Season/dag-nacht zijn ruwe strings**, afkomstig van een externe bron (RemoveSky). Een
   afwijkend label of nieuw seizoenswoord van de provider valt stilzwijgend terug op "onbekend",
   zonder foutmelding — kan verkeerde sortering geven zonder dat dit ergens zichtbaar wordt.

7. **Sommige weercodes (mist, hagelslag, onweer, ijzel) mappen naar `null`**, waardoor de
   weer-filter voor die condities stilzwijgend wordt overgeslagen.

8. **`updatedAt` wordt nergens gebruikt** in de sortering, alleen `createdAt`. Als een bestaand
   foto-record achteraf wordt bijgewerkt, verandert de sorteerpositie niet mee.

9. **Seizoen/dag-nacht hangt af van de systeem-tijdzone van het toestel** (`ZoneId.systemDefault()`).
   Bij reizen of een gewijzigde tijdzone-instelling kan het seizoen/dag-nacht-resultaat
   veranderen zonder dat de foto-cache daarop is voorbereid.

10. **Geen debug-/simulatiemogelijkheid**: er bestaat nog geen manier om te reproduceren welke
    foto waarom bovenaan kwam (zie ACT-019), wat het triagen van klachten lastig maakt.

## Vervolg

Dit document is bedoeld als gespreksbasis. Onderwerpen om samen te bekijken:
- Eén uniforme sorteerlogica voor beide paden (A en B), of bewust verschillend houden?
- Moet `lastShownAt` ook in `buildShowlist` meetellen?
- Is "nieuwste eerst" in de showlist gewenst, of leidt dat tot te veel herhaling van net
  toegevoegde foto's?
- Hoe zwaar moet GPS-afstand wegen gezien de meeste foto's geen EXIF hebben?
