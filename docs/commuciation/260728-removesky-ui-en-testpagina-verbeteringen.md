# RemoveSky: locatie-autocomplete, tabel-scrollbar, kaarthoogte, test-pagina model-selector

**Datum:** 2026-07-28
**Onderwerp:** vier losse verbeterverzoeken aan `removesky-service` (admin-UI en de `/test` refinement-vergelijkingspagina), na elkaar afgehandeld in één sessie.

---

## 1. Locatie-filter: dropdown → autocomplete invoerveld

**Melding gebruiker:** de "Locatie"-dropdown op de kaart-pagina was inmiddels zo lang geworden (tientallen plaatsnamen, alfabetisch, geen zoekfunctie) dat je moest scrollen om iets te vinden — zie screenshot van de opengeklapte lijst op de Beheren-pagina. Vraag: kan dit een autocomplete-invoerveld worden, en dat geldt dan voor Beheren, Kaart én Weetjes.

**Bevindingen:** Weetjes had al een vrij-tekst filter, geen dropdown. Alleen Beheren (`manageLocation`) en Kaart (`mapLocation`) hadden de lange `<select>`.

**Uitgevoerd** (`removesky-service/static/index.html`):
- Beide `<select>`-dropdowns vervangen door `<input list="…" autocomplete="off">` + een bijbehorende `<datalist>`, gevuld vanuit dezelfde bestaande `/manage/locations`-call.
- Debounced `input`-listener (400ms) toegevoegd naast de bestaande `change`-listener, zodat filteren al gebeurt terwijl je typt, niet alleen na het kiezen van een suggestie.
- Op de Kaart-pagina riep het reverse-GPS-lookup-pad (`setLocationFromGpsPoint`) voorheen `ensureSelectOption()` aan om een stad aan de dropdown toe te voegen vóór selectie — bij een vrij invoerveld is dat niet meer nodig, dus die aanroep verwijderd (de functie zelf blijft bestaan voor `mapCountry`, dat wél een `<select>` blijft).
- Bestaande "Filters wissen"-logica (`TableUX.resetFilters`) werkte al generiek voor niet-`<select>`-velden, dus geen wijziging nodig.

---

## 2. Tabellen: horizontale scrollbar pas zichtbaar na helemaal naar beneden scrollen

**Melding gebruiker:** bij lange tabellen (bv. Cities met 50 rijen op een pagina) moest je eerst de hele pagina naar beneden scrollen voordat de horizontale scrollbar van de tabel in beeld kwam — onhandig bij brede tabellen.

**Oorzaak:** `.manage-table-wrap` had `overflow-x: auto` zonder hoogtebeperking, waardoor de scrollbar aan de onderkant van de (zeer lange) tabel zat in plaats van ergens binnen het zichtbare kader.

**Fix:** `.manage-table-wrap` krijgt `max-height: 65vh` met `overflow: auto` (beide richtingen), zodat de tabel in een vast kader scrolt met altijd-bereikbare scrollbars. De bestaande sticky `<thead>` (`position: sticky; top: 0`) werkt hierdoor automatisch binnen dat kader in plaats van binnen de hele pagina. Eén CSS-klasse, dus meteen van toepassing op Beheren, Kaart, Weetjes én Cities.

---

## 3. Kaart: hoogte aanpasbaar maken

**Melding gebruiker:** wilde de hoogte van de Leaflet-kaart op de Kaart-pagina kunnen aanpassen, het liefst door rechtsonder aan de kaart te trekken (net als bij een `<textarea>`).

**Uitgevoerd:**
- `.map-leaflet` kreeg `resize: vertical` (native browser-slepgreep rechtsonder) plus `min-height: 240px` en `overflow: hidden`.
- Een `ResizeObserver` op het kaart-element roept bij elke resize `leafletMap.invalidateSize()` aan (anders blijven tiles/markers op de oude afmeting getekend) en onthoudt de gekozen hoogte in `localStorage` (`removesky_map_height`), zodat die bij het volgende bezoek wordt hersteld.

---

## 4. `/test`-vergelijkingspagina: model-selector en live parameter-sliders

**Context:** de gebruiker deelde een voorbeeld-Python-script (OpenCV-sliders voor Threshold/Morphological-closing op een losse BiRefNet-run) en een lijst van vijf parameters die randjes/dunne structuren (molenwieken) beïnvloeden: `input_size`, threshold/sigmoid-drempel, normalize-waarden, morphological-kernelgrootte, en de modelvariant zelf. Vraag: een dropdown toevoegen om niet steeds alle vier refiners te hoeven draaien (Alle / BRIA RMBG-2.0 / SAM2 / ViTMatte), parameters live instelbaar maken, én het geheel zo bouwen dat nieuwe modellen straks makkelijk toe te voegen zijn.

**Uitgevoerd** (`removesky-service/app/api/v1/compare.py`, volledig herschreven):
- **Model-selector:** dropdown (Alle/BiRefNet/BRIA RMBG-2.0/SAM2/ViTMatte) — alleen de gekozen refiner(s) draaien, in plaats van altijd alle vier. De lijst met modellen komt nu rechtstreeks uit `app.services.refiners._REGISTRY` (bestond al, was tot nu toe alleen intern gebruikt) in plaats van hardcoded per-model blokken in `compare.py`. **Nieuw model toevoegen om te testen = alleen registreren in `refiners/__init__.py`**; het verschijnt dan automatisch in de dropdown, wordt geladen en krijgt een resultaatkaart, zonder dat `compare.py` aangepast hoeft te worden.
- **Parameter-sliders:** vier sliders die de bestaande, tot nu toe alleen via environment-variabelen instelbare tuning-waarden uit `app/config.py` blootleggen:
  - *Marge (fg/bg)* → `SKY_MASK_HYBRID_MARGIN` (komt overeen met de "threshold" uit het voorbeeldscript — lager = dunne structuren zoals wieken blijven eerder behouden)
  - *Sky-core max* → `SKY_MASK_HYBRID_SKY_CORE_MAX`
  - *Erode fractie* → `SKY_MASK_HYBRID_ERODE_FRACTION` (komt overeen met de "kernel size"/morphological-closing uit het voorbeeldscript)
  - *Overlap-drempel* → `SAM2_SKY_OVERLAP_MIN` (SAM2/ViTMatte-sanity-check)

  Deze worden per request als query-param meegegeven en tijdelijk over `app.config` heen gezet (`_override_config`, met een `finally` die alles terugzet) — veilig omdat vergelijkingen toch al één-voor-tegelijk draaien achter een bestaande lock.
- **Itereren zonder terug te gaan naar het formulier:** de resultaatpagina toont dezelfde model-dropdown + sliders opnieuw, met een "↻ Opnieuw vergelijken"-knop die met de aangepaste waarden herlaadt en de pagina in-place vervangt (zelfde `fetch` + `document.write`-patroon als het bestaande formulier) — vergelijkbaar met het live-sliders-idee uit het gedeelde voorbeeldscript, maar in de browser i.p.v. een lokaal OpenCV-venster.
- Dode code opgeruimd tijdens de herschrijving: ongebruikte `_label`/`_diff_image`-helpers en de `ImageDraw`/`ImageFont`-imports die daarvoor nodig waren (werden nergens meer aangeroepen).

**Niet uitgevoerd:** `input_size`, de normalize-waarden en het wisselen naar een andere modelvariant (bv. `BiRefNet-general-HRSOD`/`BiRefNet-matting`) uit de gedeelde vijf-parameter-lijst zijn niet blootgesteld als sliders — dat vereist een nieuwe voorwaartse pass door het model (kost tijd/VRAM per wijziging) in plaats van een goedkope her-berekening op een al bestaand masker, dus dat past niet in hetzelfde "live sleepbalk"-patroon als de andere vier parameters.

**Validatie:** kon niet end-to-end getest worden vanuit deze sessie (de lokale `.venv` mist `numpy`/`torch`); gecontroleerd via `py_compile` en losse format-string/placeholder-tests. Live geverifieerd na deploy: de GitHub Actions-run op commit `5143207` (inclusief deze wijziging en latere vervolgcommits) is `success`, met geslaagde `/health`-check.

---

## Commits (removesky-service, `main`)

| Commit | Omschrijving |
|---|---|
| `461872b` | Autocomplete voor locatiefilters, tabellen tonen scrollbar zonder scrollen, resizable kaarthoogte |
| `cac6903` | Model-selector en live parameter-sliders op `/test` refinement-vergelijking |

## Afbeeldingen

De gebruiker deelde tijdens dit gesprek drie schermafbeeldingen: (1) de opengeklapte, lange Locatie-dropdown op de Beheren-pagina die aanleiding gaf tot punt 1, (2) de Cities-tabel met de laat-zichtbare horizontale scrollbar (punt 2), en (3) de 2×2 `/test`-vergelijkingspagina met de molen "De Vlijt" als voorbeeldafbeelding (punt 4). Deze zijn inline in de chatgeschiedenis gedeeld en niet als los bestand beschikbaar, dus niet opgenomen als los beeldbestand in deze map.
