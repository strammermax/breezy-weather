# ACT-013 - Glassmorphic kaartontwerp voor weergegevens

## Status

- Type: implementatieopdracht (UI/visueel ontwerp)
- Prioriteit: middelhoog
- Omvang: middelgroot
- Risico: laag tot middelgroot, omdat dit vooral bestaande Compose/XML-kaarten van de hoofd-app (forecast, hourly, air quality, comfort level) visueel herstijlt, niet de wallpaper-renderlogica
- Prerequisite: geen harde afhankelijkheid van ACT-001 t/m ACT-012; bij voorkeur na ACT-003 (clouds) en ACT-012 (seizoensgrading) zodat de achtergrond al rijker is wanneer de glaslook erover ligt
- Doelplatform: alle ondersteunde Android-versies; blur-effecten gebruiken `RenderEffect`/`BlurEffect` op Android 12+ met een gedimde-overlay fallback op oudere versies

## 1. Opdracht in een zin

Geef de weergegevens-kaarten (huidige condities, daily/hourly forecast, air quality, comfort level, wind/grafieken) een "glas"-look zoals het YoWindow-achtige referentiebeeld: halftransparante, licht vervaagde kaarten met afgeronde hoeken op een levendige achtergrondfoto, zodat de app aanvoelt als een doorkijk op het weer in plaats van een ondoorzichtige donkerblauwe lijst.

## 2. Waarom deze wijziging nodig is

De huidige UI gebruikt vlakke, ondoorzichtige donkerblauwe kaarten (zie de bestaande schermafbeeldingen: "Hourly forecast", "Air quality", "Comfort level", "Graphs"). Dat werkt functioneel, maar:

- de achtergrondfoto/live wallpaper-sfeer (waar ACT-003 t/m ACT-012 net aan werken) is in de app zelf niet zichtbaar — de kaarten zijn volledig dekkend;
- de app voelt visueel los van de live wallpaper, terwijl YoWindow juist een doorlopende sfeer tussen wallpaper en app-UI heeft;
- het referentiebeeld (New York-voorbeeld) toont hoe een "glas"-kaart met blur, lichte rand en subtiele schaduw de achtergrondfoto laat doorschijnen terwijl tekst leesbaar blijft.

De gewenste situatie: dezelfde informatie, dezelfde databronnen, maar in halftransparante "glas"-kaarten boven een zichtbare achtergrond (foto of, waar toepasselijk, een statische render van de wallpaperscene), met behoud van leesbaarheid en toegankelijkheid (contrast).

## 3. Huidige architectuur

### Belangrijkste bestanden

1. Hoofdscherm-composables/fragmenten voor "Today", "Hourly", "Daily", "Graphs" (de schermen in de meegestuurde screenshots).
   - bevatten de huidige kaartcontainers (`Card`/`Surface` in Compose, of vergelijkbare XML-`CardView`/`MaterialCardView`);
   - gebruiken een vast donkerblauw thema-achtergrondkleur voor zowel de pagina-achtergrond als de kaarten.

2. Theming-bestanden (Material theme / `Color.kt` / `Theme.kt` / stijl-XML).
   - definieren de huidige achtergrond- en kaartkleuren;
   - zijn het startpunt voor nieuwe "glass surface"-kleuren, elevaties en corner radii.

3. Eventuele bestaande achtergrond-/header-afbeelding-componenten (bijvoorbeeld een locatie-foto boven het scherm, vergelijkbaar met de wallpaper-fotopipeline).

### Huidig gedrag

Elk scherm (Today, Hourly, Daily, Graphs, Air quality, Comfort level) rendert op een effen donkerblauwe achtergrond met ondoorzichtige kaarten in een iets lichtere blauwtint. Er is geen blur, transparantie of doorkijk naar een achtergrondbeeld.

## 4. Afbakening

### Wel uitvoeren

- een nieuwe "glass surface"-stijl (kleur, transparantie, corner radius, rand, schaduw) voor kaarten;
- blur of vervaging van de achtergrond achter kaarten waar het platform dit ondersteunt;
- toepassing van deze stijl op: huidige condities, hourly forecast, daily forecast/grafieken, air quality, comfort level, wind;
- een achtergrondafbeelding of -gradient achter de kaarten op de hoofdschermen, consistent met de locatiefoto-sfeer van de wallpaper;
- contrast- en leesbaarheidsregels (minimumcontrast tekst/achtergrond) voor lichte en donkere foto's;
- een lichte/donkere variant van de glaslook (dag versus nacht), aansluitend op de bestaande dag/nacht-logica;
- een visueel voorstel/mockup (sectie 9) ter goedkeuring voordat brede implementatie start;
- screenshots van de geherstijlde schermen naast de huidige schermen.

### Niet uitvoeren

- geen wijziging aan databronnen, API's of berekeningen (temperatuur, AQI, wind, etc.);
- geen wijziging aan de live wallpaper-renderlogica (ACT-001 t/m ACT-012 blijven ongewijzigd; alleen de app-UI verandert);
- geen volledige navigatie- of informatiearchitectuur-herontwerp;
- geen nieuwe schermen of features;
- geen verwijdering van bestaande functionaliteit (instellingen, camera, RemoveSky-koppeling, etc.);
- geen brede refactor van Breezy Weather buiten theming/kaartstijl;
- geen externe UI-kits of gekopieerde closed-source assets.

## 5. Architectuurregel

Dit is een visuele/theming-wijziging in de app-laag, niet in de wallpaper-laag.

Tijdens deze opdracht mag de implementatie:

- bestaande theming-bestanden (kleuren, vormen, elevatie) uitbreiden met "glass"-varianten;
- `RenderEffect`/`BlurEffect` (Android 12+) of een vergelijkbare blur-implementatie gebruiken voor achtergrondvervaging achter kaarten;
- bestaande locatiefoto's (zoals gebruikt door de wallpaper-fotopipeline) hergebruiken als achtergrond in de app, read-only;
- bestaande dag/nacht- en weather-kind-state gebruiken om de glaslook te varieren.

De implementatie mag niet:

- nieuwe netwerk- of fotobronnen toevoegen — hergebruik wat al bestaat (RemoveSky-cache via de bestaande store);
- de wallpaper-renderloop of `WallpaperWeatherEffectRenderer`/`MaterialLiveWallpaperService` wijzigen;
- de leesbaarheid van cijfers/tekst onder de wettelijke/Material-richtlijnen voor contrast laten zakken.

## 6. Prerequisite

Geen harde technische prerequisite. Wel inhoudelijk slim te combineren:

- als ACT-003 (clouds) en ACT-012 (seizoensgrading) al gemerged zijn, is de achtergrondfoto/wallpaperscene visueel rijker, wat de glaslook beter laat uitkomen in screenshots;
- als die nog niet gemerged zijn, gebruik de bestaande locatiefoto zonder die effecten als achtergrond — de glaslook zelf is daar niet van afhankelijk.

Controleer voor aanvang welke theming-bestanden en kaartcomposables daadwerkelijk bestaan; maak geen tweede, parallelle themingstructuur naast de bestaande Material-theme.

## 7. Gewenst visueel model

### Glass surface-stijl

Definieer een herbruikbare stijl/`Modifier`/`Shape`-combinatie, bijvoorbeeld:

```kotlin
data class GlassSurfaceStyle(
    val backgroundColor: Color,   // wit of donker, lage alpha (bv. 0.12f - 0.22f)
    val borderColor: Color,       // subtiele lichte rand, lage alpha
    val cornerRadius: Dp,         // bv. 20-24dp, ruim afgerond
    val blurRadius: Dp,           // bv. 16-24dp waar ondersteund
    val elevationTint: Color,     // zachte schaduw, geen harde drop shadow
)
```

- lichte modus (dag): `backgroundColor` wit met lage alpha (bv. 14-20%), donkere tekst of hoog-contrast witte tekst afhankelijk van de onderliggende foto;
- donkere modus (nacht): `backgroundColor` zwart/donkerblauw met lage alpha (bv. 25-35%), lichte tekst;
- altijd een subtiele 1dp lichte rand (alpha ~20-30%) voor het "glas"-randje;
- afgeronde hoeken consistent over alle kaarten (bv. 20dp).

### Achtergrond

- de pagina-achtergrond is een (vervaagde) locatiefoto of gradient in plaats van een effen kleur;
- waar geen foto beschikbaar is: val terug op een gradient gebaseerd op weather-kind en dag/nacht (consistent met de bestaande wallpaper-sky-gradients), niet op de huidige effen donkerblauwe kleur.

### Blur

- gebruik `RenderEffect.createBlurEffect` (Android 12+) op de achtergrondlaag achter kaarten, of teken kaarten op een laag met `Modifier.blur()` in Compose waar ondersteund;
- op oudere Android-versies: vervang blur door een iets sterkere `backgroundColor`-alpha (meer dekking) zodat contrast behouden blijft zonder blur-API.

## 8. Toepassing per scherm

| Scherm | Huidige stijl | Nieuwe stijl |
|---|---|---|
| Today (hoofdscherm) | effen donkerblauwe achtergrond, geen kaarten zichtbaar voor huidige condities | locatiefoto/gradient als achtergrond; "Weather Tomorrow"-balk en daily-forecast-strook als glaskaarten |
| Hourly forecast | ondoorzichtige donkerblauwe kaart | glaskaart over achtergrondfoto, lijn-/puntgrafiek blijft zoals nu maar met lichtere, semi-transparante gridlines |
| Daily forecast / grafieken | ondoorzichtige donkerblauwe kaart | glaskaart, temperatuur-/windgrafieken behouden kleurcodering (oranje/blauw) voor contrast tegen het glas |
| Air quality | ondoorzichtige donkerblauwe kaart met ringdiagram | glaskaart; ringdiagram en kleurenschaal blijven ongewijzigd qua kleurbetekenis |
| Comfort level | ondoorzichtige donkerblauwe kaart met staafdiagrammen | glaskaart; staafkleuren (oranje heat index, blauw humidity) blijven behouden |

Databorden, schaalverdelingen, kleurcoderingen voor AQI/temperatuur/wind blijven functioneel ongewijzigd — alleen de kaartomlijsting en achtergrond veranderen.

## 9. Visueel voorstel (mockup)

Onderstaande ASCII-mockup toont de gewenste "Today"-indeling, geinspireerd op het meegestuurde New York-referentiebeeld, toegepast op de bestaande Hoofddorp-gegevens:

```
+--------------------------------------------------+
|  (achtergrond: locatiefoto, lichtjes vervaagd)    |
|                                                    |
|  Hoofddorp                              [share][...] |
|                                                    |
|   +--------------------------------------------+  |
|   |  61°F   Partly cloudy                       |  | <- glaskaart, alpha ~0.16
|   |  Feels like 61°F        33% rain  18mph     |  |    blur achtergrond, rand 1dp
|   |                          68% humidity        |  |    radius 22dp
|   +--------------------------------------------+  |
|                                                    |
|   [* Weather Tomorrow ------------------------>]  | <- accentkaart, hogere alpha
|                                                    |
|   +--------------------------------------------+  |
|   | Daily forecast              Foreca.com  [⚙] |  |
|   | SUN  MON  TUE  WED  THU  FRI  SAT            |  | <- glaskaart
|   |  47%  31%  21%  35%   7%  62%  36%           |  |
|   +--------------------------------------------+  |
|                                                    |
|   +--------------------------------------------+  |
|   | Hourly forecast                              |  |
|   | 2PM..8PM  iconen + %  + temperatuurlijn      |  | <- glaskaart
|   +--------------------------------------------+  |
+--------------------------------------------------+
```

Kernverschillen met de huidige schermen:

1. de pagina-achtergrond is de locatiefoto (of gradient-fallback) in plaats van effen donkerblauw;
2. elke kaart krijgt afgeronde hoeken (~22dp), een subtiele lichte rand en een halftransparante vulling (~14-20% alpha) met blur van de achtergrond erachter;
3. tekst en iconen behouden voldoende contrast: lichte tekst op donkere foto's, met een optionele lichte schaduw of een iets hogere kaart-alpha bij zeer lichte foto's;
4. kleurcoderingen in grafieken (AQI-ring, heat index oranje, humidity blauw, temperatuurlijnen rood/blauw) blijven exact zoals nu — alleen de kaartrand en achtergrond veranderen.

### Concrete kleurwaarden en alpha's per variant

Onderstaande waarden zijn startpunten voor `GlassSurfaceStyle`-instanties en moeten na implementatie visueel worden gefinetuned op echte foto's. Alle alpha's zijn op een schaal van 0f..1f (oftewel ARGB-hex `00`..`FF`).

**Dag-variant (lichte/heldere achtergrondfoto, bv. zomerse hemel)**

| Eigenschap | Waarde | Toelichting |
|---|---:|---|
| `backgroundColor` | `#FFFFFF` @ alpha `0.16` (`#29FFFFFF`) | wit glas, laat foto duidelijk doorschijnen |
| `borderColor` | `#FFFFFF` @ alpha `0.35` (`#59FFFFFF`) | iets sterkere rand dan de vulling, geeft "glasrandje" |
| `cornerRadius` | `22dp` | consistent over alle kaarten |
| `blurRadius` | `20dp` | op Android 12+; matig zodat achtergrondkleuren herkenbaar blijven |
| `elevationTint` | `#000000` @ alpha `0.10` | zachte schaduw onder de kaart, geen harde drop shadow |
| Primaire tekstkleur | `#FFFFFF` @ alpha `1.0` met `0.20` zwarte tekstschaduw (radius ~2dp) | lichte tekst blijft leesbaar op lichte foto's dankzij schaduw |
| Secundaire tekstkleur | `#FFFFFF` @ alpha `0.75` | voor labels/subtitels |
| Accentkaart ("Weather Tomorrow") | `backgroundColor` `#FFFFFF` @ alpha `0.28`, of bestaande accentkleur @ alpha `0.85` | hogere alpha dan gewone kaarten, blijft het visuele "call to action" |

**Nacht-variant (donkere achtergrondfoto, sterrenhemel/maanlicht)**

| Eigenschap | Waarde | Toelichting |
|---|---:|---|
| `backgroundColor` | `#0A1430` @ alpha `0.30` (`#4D0A1430`) | donkerblauw glas, sluit aan op bestaande nacht-theming |
| `borderColor` | `#FFFFFF` @ alpha `0.18` | subtieler dan dag-variant, voorkomt "gloeiende rand" op een donkere foto |
| `cornerRadius` | `22dp` | gelijk aan dag-variant |
| `blurRadius` | `24dp` | iets sterker, omdat donkere foto's vaak meer detail/ruis bevatten dat anders door het glas heen "knettert" |
| `elevationTint` | `#000000` @ alpha `0.25` | iets sterkere schaduw voor diepte op een donkere achtergrond |
| Primaire tekstkleur | `#FFFFFF` @ alpha `1.0` | geen schaduw nodig, contrast is van nature hoog |
| Secundaire tekstkleur | `#FFFFFF` @ alpha `0.70` | iets lager dan dag-variant |
| Accentkaart ("Weather Tomorrow") | `backgroundColor` bestaande accentkleur @ alpha `0.90` | blijft duidelijk het meest opvallende element, ook 's nachts |

**Fallback-variant (geen foto / oudere Android zonder `RenderEffect`)**

| Eigenschap | Waarde | Toelichting |
|---|---:|---|
| Achtergrond | gradient gebaseerd op weather-kind + dag/nacht (zelfde paletten als de wallpaper-sky-gradient) | vervangt de huidige effen donkerblauwe paginaondergrond |
| `backgroundColor` (dag) | `#FFFFFF` @ alpha `0.22` | hogere alpha dan de blur-variant (`0.16`) omdat er geen blur is om detail te dempen |
| `backgroundColor` (nacht) | `#0A1430` @ alpha `0.40` | hogere alpha dan de blur-variant (`0.30`) |
| `blurRadius` | n.v.t. (0dp) | blur wordt vervangen door de hogere alpha hierboven |
| Overige waarden (`borderColor`, `cornerRadius`, tekstkleuren) | gelijk aan dag- of nacht-variant | alleen `backgroundColor` en `blurRadius` wijzigen in de fallback |

**Overgang dag naar nacht**

- interpoleer `backgroundColor`-, `borderColor`- en `elevationTint`-alpha's lineair op basis van de bestaande daylightfactor, op dezelfde manier als ACT-002 `contribution` interpoleert tussen renderers;
- vermijd een harde sprong op het moment van sunrise/sunset: de kaartstijl verandert net zo geleidelijk als de achtergrondscene.

## 10. Contrast en toegankelijkheid

- bereken of meet het contrast tussen tekstkleur en de gemiddelde achtergrondkleur achter een kaart;
- als een foto te licht of te wisselend van helderheid is, verhoog de kaart-alpha (minder doorkijk) totdat het contrast voldoende is, in plaats van de tekstkleur per pixel aan te passen;
- definieer een minimumcontrastratio (bijvoorbeeld volgens WCAG AA, ~4.5:1 voor normale tekst) als richtlijn, ook al is strikte WCAG-naleving voor een wallpaper-app niet verplicht;
- test met zowel een lichte (zomerse dag) als donkere (nachtelijke) achtergrondfoto.

## 11. Performance-eisen

- blur wordt alleen herberekend bij wijziging van achtergrond of scrollpositie, niet op elk frame zonder reden;
- geen nieuwe full-screen bitmapkopieen per recompositie/frame;
- op oudere Android-versies (geen `RenderEffect`) wordt blur vervangen door een statische alpha-aanpassing, geen software-blur-loop;
- scroll-performance van Hourly/Daily/Graphs-schermen mag niet meetbaar verslechteren.

## 12. Logging en privacy

- geen nieuwe logging van foto-inhoud of locatiegegevens;
- als debug-logging wordt toegevoegd (bijvoorbeeld "blur fallback actief"), volgt deze de bestaande regels: debug-only, geen secrets, geen exacte locatie, geen fotopaden met persoonsgegevens.

## 13. Voorgestelde implementatiestappen

1. Inventariseer de bestaande theming-bestanden en kaartcomposables/XML voor Today, Hourly, Daily, Graphs, Air quality, Comfort level.
2. Definieer `GlassSurfaceStyle` (of vergelijkbaar) met lichte en donkere variant.
3. Voeg een achtergrondlaag (foto of gradient-fallback) toe aan de hoofdschermen.
4. Implementeer blur achter kaarten op Android 12+ en de alpha-fallback op oudere versies.
5. Pas de glas-stijl toe op de kaarten uit sectie 8, een scherm per keer.
6. Verifieer contrast op een lichte en een donkere referentiefoto.
7. Maak screenshots van elk geherstijld scherm naast de huidige versie.
8. Verzamel feedback op het visuele voorstel (sectie 9) voordat verdere schermen worden aangepakt.
9. Build debug en release.
10. Test op emulator en gekoppelde telefoon, inclusief scroll-performance.
11. Controleer `git diff` op scope en secrets.

## 14. Handmatige testmatrix

| Scenario | Verwachting |
|---|---|
| Lichte locatiefoto, dag | tekst en iconen blijven leesbaar, kaarten ogen als licht glas |
| Donkere locatiefoto, nacht | tekst blijft leesbaar, kaarten ogen als donker glas |
| Geen locatiefoto beschikbaar | gradient-fallback, geen effen donkerblauw meer |
| Scrollen door Hourly/Daily | geen merkbare framerate-daling t.o.v. huidige versie |
| Android < 12 (geen RenderEffect) | alpha-fallback, geen crash, voldoende contrast |
| Air quality / Comfort level | kleurcoderingen (AQI-ring, heat index, humidity) ongewijzigd herkenbaar |

## 15. Acceptatiecriteria

1. Today, Hourly, Daily/Graphs, Air quality en Comfort level gebruiken de nieuwe glaskaart-stijl.
2. De pagina-achtergrond toont een foto of gradient in plaats van een effen donkerblauwe vlakte.
3. Tekst en iconen voldoen aan een gedocumenteerde minimumcontrastratio op zowel lichte als donkere achtergronden.
4. Kleurcoderingen in grafieken en ringdiagrammen blijven functioneel ongewijzigd.
5. Blur wordt gebruikt op Android 12+; oudere versies tonen een alpha-fallback zonder crash.
6. Scroll-performance van bestaande lijsten/grafieken verslechtert niet meetbaar.
7. Geen wijziging aan databronnen, berekeningen of de wallpaper-renderlogica.
8. Er worden geen secrets, fotopaden met persoonsgegevens of exacte locaties gelogd.

## 16. Definition of done

- implementatie is beperkt tot theming-/kaartcomposables en gerichte aanpassingen aan achtergrondlagen;
- debugbuild slaagt;
- releasebuild slaagt;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd, inclusief Android < 12 indien beschikbaar;
- screenshots van alle geherstijlde schermen zijn gemaakt en vergeleken met de huidige versie;
- visueel voorstel (sectie 9) is goedgekeurd voordat de volledige toepassing is afgerond;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-013-bestanden;
- commit en push gebeuren pas nadat de visuele controle is goedgekeurd.

## 17. Samenwerking met andere agents

- lees eerst `git status` en de actuele diff;
- wijzig geen wallpaper-renderbestanden (`WallpaperWeatherEffectRenderer.kt`, `MaterialLiveWallpaperService.kt`, `CloudField.kt`) — deze opdracht raakt alleen de app-UI;
- wijzig geen camera-, RemoveSky-client- of iconpack-bestanden buiten theming/UI;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- stage bestanden expliciet, nooit via `git add .`;
- baseer alle keuzes op de actuele code en thema-structuur, niet uitsluitend op dit document.

## 18. Verwacht eindresultaat

Na ACT-013 voelt de app visueel aan als een doorkijk op het weer: halftransparante "glas"-kaarten met afgeronde hoeken en een subtiele rand liggen over een zichtbare locatiefoto of gradient-achtergrond, in lijn met het meegestuurde referentiebeeld. Alle bestaande gegevens, grafieken en kleurcoderingen blijven functioneel hetzelfde; alleen de kaart- en achtergrondstijl zijn vervangen. De app blijft leesbaar op zowel lichte als donkere achtergrondfoto's en op Android-versies zonder blur-ondersteuning.
