# ACT-008 - Visuele regressietests

## Status

- Type: implementatieopdracht
- Prioriteit: hoog
- Omvang: middelgroot
- Risico: laag tot middelgroot, omdat dit testinfrastructuur toevoegt zonder de productie-renderlus te wijzigen
- Prerequisite: ACT-001 - Centrale wallpaper scene state; bij voorkeur ook ACT-002 - Vloeiende state transitions
- Doelplatform: Android 13 en hoger heeft prioriteit; Android 6 tot en met 12 behoudt een Canvas fallback

## 1. Opdracht in een zin

Maak reproduceerbare screenshots voor Clear day, Sunrise, Sunset, Night, Cloud, Cloudy, Rain, Thunderstorm, Wind, Snow, Sleet en Fog met een vaste surfacegrootte, tijd, weather data en seed, zodat visuele wijzigingen objectief vergelijkbaar zijn en zonder netwerkverkeer vanuit de test.

## 2. Waarom deze wijziging nodig is

De live wallpaper krijgt steeds meer visuele effecten, maar er is geen objectieve manier om visuele wijzigingen te vergelijken. Daardoor:

- zijn regressies in laagvolgorde, kleur of effecten moeilijk te detecteren;
- is het lastig te bevestigen of een wijziging een effect verbetert of breekt;
- ontbreekt een vaste set referentiebeelden per weather family en tijdstip;
- kan een shaderfallback ongemerkt afwijken van het AGSL-resultaat;
- is handmatig screenshotten tijdrovend en niet reproduceerbaar.

De gewenste situatie is een reproduceerbare screenshotset die op de emulator kan worden herhaald met vaste input.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperWeatherEffectRenderer.kt`
   - bevat de effect-rendering die getest moet worden;
   - gebruikt AGSL vanaf Android 13 en Canvas-fallback daaronder.

2. `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
   - bevat de compositie en laagvolgorde;
   - leest lokale data en bouwt de scene op.

3. De door ACT-001 toegevoegde scene-statebestanden.
   - leveren een deterministische, injecteerbare scene state;
   - gebruik de namen en locatie die ACT-001 daadwerkelijk heeft geintroduceerd;
   - maak geen tweede concurrerend scene-state-model.

4. Bestaande testmappen en test-artifacts.
   - `test-artifacts/` bevat al wallpaper-weather-screenshots;
   - sluit aan bij de bestaande mapstructuur waar mogelijk.

### Huidig gedrag

Er bestaan al handmatige wallpaper-weather-screenshots in `test-artifacts/`, maar geen geautomatiseerde, reproduceerbare generatie met vaste input en seed.

## 4. Afbakening

### Wel uitvoeren

- een testbare renderpad die een scene op een offscreen surface of bitmap tekent;
- een vaste surfacegrootte, tijd, weather data en seed per scenario;
- screenshotgeneratie voor de twaalf gevraagde scenario's;
- controle van laagvolgorde en shaderfallback;
- een herhaalbare opzet die op emulator kan draaien;
- een manier om referentiebeelden te vergelijken of op te slaan;
- documentatie van hoe de tests worden gedraaid;
- handmatige verificatie van de gegenereerde beelden.

### Niet uitvoeren

- geen wijziging aan de productie-renderlogica zelf;
- geen nieuwe effecten: clouds is ACT-003, snow/hail is ACT-004, fog/haze is ACT-005, glass-rain is ACT-006;
- geen telemetrie-export: dat is ACT-009;
- geen automatische foto-download: dat is ACT-010;
- geen wijziging aan Meteo-, GPS- of RemoveSky-clients;
- geen OpenGL-migratie;
- geen brede UI-herontwerp;
- geen brede refactor van Breezy Weather;
- geen externe shader- of asset-bestanden uit YoWindow kopieren;
- geen echte netwerk- of GPS-calls in de test.

## 5. Architectuurregel

De test mag uitsluitend deterministische, lokale input gebruiken.

Tijdens deze opdracht mag de test:

- een vaste, injecteerbare scene state opbouwen;
- een vaste seed voor random gebruiken;
- een vaste surfacegrootte en tijd instellen;
- de renderer aanroepen op een offscreen surface of bitmap.

De test mag niet:

- GPS starten;
- een weather provider aanroepen;
- RemoveSky aanroepen;
- HTTP-requests uitvoeren;
- afhankelijk zijn van de echte huidige tijd of echte locatie;
- afhankelijk zijn van persoonlijke fotodata.

## 6. Prerequisite ACT-001 en ACT-002

ACT-008 moet de centrale immutable scene state uit ACT-001 gebruiken als deterministische input. Controleer voor aanvang welke class en velden ACT-001 daadwerkelijk heeft toegevoegd.

Als ACT-002 is gemerged, moet de testopzet een transition op een vaste progress (bijvoorbeeld 0.5) kunnen renderen om overgangsbeelden vast te leggen.

Als ACT-001 nog niet is gemerged, stop dan en rapporteer deze dependency. Voeg niet stilzwijgend een tweede scene-state-architectuur toe.

## 7. Gewenste testopzet

Maak een renderpad die los van de echte `WallpaperService.Engine` een frame kan tekenen.

Conceptueel:

```kotlin
fun renderSceneToBitmap(
    state: WallpaperSceneState,
    width: Int,
    height: Int,
    seed: Long,
    transitionProgress: Float = 1f,
): Bitmap
```

- de surfacegrootte is vast per scenario, bijvoorbeeld een standaard telefoongrootte;
- de seed maakt particle- en druppelverdeling deterministisch;
- de tijd wordt expliciet meegegeven, niet uit de systeemklok gelezen;
- de weather data wordt expliciet opgebouwd, niet uit een repository geladen.

Voor AGSL kan een `HardwareRenderer`/`ImageReader`-pad of een Canvas-pad worden gebruikt, afhankelijk van wat op de emulator betrouwbaar is. Documenteer de gekozen aanpak.

**Gekozen aanpak (uitgangspunt, mag worden bijgesteld na technische verkenning)**: bouw `renderSceneToBitmap()` als een nieuwe, kleine wrapper-functie/klasse buiten `WallpaperService.Engine`, die de bestaande `WallpaperWeatherEffectRenderer` en de Canvas-/AGSL-tekenmethoden hergebruikt zonder de productie-`Engine`-lifecycle aan te roepen. Voeg alleen toe wat nodig is om de renderer vanuit een test te kunnen instantieren en aan te roepen (bijvoorbeeld een bestaande methode `public`/`internal` maken); voer geen bredere refactor van `WallpaperWeatherEffectRenderer` uit. Gebruik op API 29+ `HardwareRenderer` + `ImageReader` voor het AGSL-pad en een directe `Canvas`-naar-`Bitmap` voor het fallback-pad. Als dit tijdens implementatie een grotere refactor blijkt te vereisen, stop en rapporteer dat als scope-conflict in plaats van de refactor stilzwijgend uit te voeren.

## 8. Scenario's

Genereer minimaal de volgende twaalf scenario's:

| Scenario | Belangrijkste input |
|---|---|
| Clear day | clear, midden van de dag |
| Sunrise | clear/cloud, rond sunrise |
| Sunset | clear/cloud, rond sunset |
| Night | clear, nacht met maan |
| Cloud | lichte bewolking, dag |
| Cloudy | zware bewolking, dag |
| Rain | regen met druppels, dag |
| Thunderstorm | onweer met bliksem en stormdarkening |
| Wind | wind met snellere wolken |
| Snow | sneeuw met particles |
| Sleet | mengsel van sneeuw en hagel/regen |
| Fog | mist met dieptebanden |

Elk scenario gebruikt een vaste tijd, vaste weather data en vaste seed.

## 9. Vaste input

- surfacegrootte: een vaste resolutie, gedocumenteerd in de test;
- tijd: een vaste wall-clock per scenario voor sunrise/sunset/night/day;
- sunrise/sunset: vaste waarden zodat celestial positie deterministisch is;
- seed: een vaste `Long` per scenario;
- windrichting en -snelheid: vaste waarden;
- quality profile: een vast profiel, bij voorkeur Balanced, met optioneel extra runs per profiel.

Documenteer alle vaste waarden zodat een andere ontwikkelaar exact dezelfde beelden kan reproduceren.

## 10. Laagvolgorde en fallback controleren

- controleer dat de laagvolgorde overeenkomt met de gedocumenteerde volgorde;
- genereer indien mogelijk zowel een AGSL- als een Canvas-fallbackbeeld voor minimaal enkele scenario's;
- vergelijk dat de fallback acceptabel overeenkomt met het AGSL-resultaat;
- markeer grote afwijkingen als testfout of als handmatig te beoordelen verschil.

## 11. Referentiebeelden en vergelijking

**Gekozen aanpak**: referentiebeelden opslaan in een testmap (bijvoorbeeld `test-artifacts/regression-baseline/`) en perceptueel vergelijken met een tolerantie.

- gebruik een eenvoudige perceptuele maat (bijvoorbeeld gemiddeld pixelverschil of SSIM) met een tolerantiedrempel, bijvoorbeeld "gelijkenis >= 0.95 is Pass";
- sla bij een mismatch een diff-beeld op naast het gegenereerde beeld;
- bij een bewuste visuele wijziging: vervang de referentiebeelden handmatig na visuele controle (geen automatische "update baseline"-stap zonder review);
- als er nog geen referentiebeelden bestaan voor een scenario, genereer en sla het beeld op als nieuwe baseline en meld dit expliciet in de testoutput in plaats van de test te laten slagen of falen.

## 12. Uitvoering

- de tests moeten op een emulator kunnen draaien;
- documenteer het exacte commando en de vereiste emulator-API-niveaus;
- de tests mogen geen echte wallpaper hoeven in te stellen;
- de tests mogen geen netwerk of GPS gebruiken;
- de gegenereerde beelden worden in een gedocumenteerde map geplaatst.

## 13. Performance- en betrouwbaarheidseisen

- de testopzet mag de productie-renderlus niet wijzigen;
- de generatie moet deterministisch zijn bij gelijke input en seed;
- vermijd flaky tests door geen echte tijd of echte sensoren te gebruiken;
- houd de testduur redelijk;
- geen logging van secrets of locaties.

## 14. Logging en privacy

Alle nieuwe logging is debug-/testniveau.

Toegestane informatie:

- scenario-naam;
- gebruikte seed, surfacegrootte en tijd;
- pad naar gegenereerd beeld;
- mismatch met referentie en diff-pad.

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- persoonlijke fotopaden.

## 15. Voorgestelde implementatiestappen

1. Controleer en documenteer de ACT-001 scene-state API en, indien aanwezig, de ACT-002 transition-API.
2. Maak een deterministische scene state-builder voor testinput.
3. Voeg een offscreen renderpad toe dat een scene naar een bitmap tekent.
4. Maak een geseede randombron beschikbaar voor particles en druppels.
5. Definieer de twaalf scenario's met vaste input.
6. Genereer screenshots per scenario.
7. Voeg indien mogelijk AGSL- en Canvas-varianten toe.
8. Kies en implementeer de vergelijkings- of beoordelingsstrategie.
9. Documenteer het draaicommando en de mapstructuur.
10. Verifieer de gegenereerde beelden handmatig.
11. Build debug.
12. Draai de tests op de emulator.
13. Controleer `git diff` op scope en secrets.

## 16. Testgevallen

Minimale gevallen:

1. elk van de twaalf scenario's genereert een beeld zonder crash;
2. dezelfde input en seed leveren een identiek of binnen tolerantie gelijk beeld;
3. een andere seed verandert de particle-/druppelverdeling deterministisch;
4. de laagvolgorde in het beeld komt overeen met de gedocumenteerde volgorde;
5. sunrise en sunset tonen de juiste celestial positie bij de vaste tijd;
6. night toont de maan in plaats van de zon;
7. Rain en Thunderstorm tonen druppels respectievelijk bliksem;
8. Snow en Sleet tonen de juiste particles;
9. Fog toont dieptebanden zonder full-screen witte overlay;
10. indien beschikbaar wijkt de Canvas-fallback niet onacceptabel af van AGSL;
11. een scenario met een actieve ACT-002-transitie op `transitionProgress = 0.5` (bijvoorbeeld Cloud naar Cloudy) rendert beide renderers correct over elkaar zonder dat de test crasht of een leeg beeld oplevert.

## 17. Handmatige verificatie

- bekijk elk gegenereerd beeld op juiste kleur, laagvolgorde en effecten;
- vergelijk met de bestaande `test-artifacts/`-beelden waar relevant;
- controleer dat geen persoonlijke fotodata in de testbeelden zit;
- bevestig dat een herhaalde run dezelfde beelden oplevert.

## 18. Platforms

- Android 13 of hoger op emulator voor AGSL;
- waar mogelijk Android 12 of lager voor Canvas fallback;
- documenteer welke API-niveaus zijn gebruikt.

## 19. Acceptatiecriteria

1. Er is een reproduceerbare screenshotgeneratie voor de twaalf gevraagde scenario's.
2. De surfacegrootte, tijd, weather data en seed zijn vast en gedocumenteerd.
3. Dezelfde input levert hetzelfde of binnen tolerantie gelijke beeld op.
4. De laagvolgorde wordt gecontroleerd.
5. De shaderfallback wordt waar mogelijk gecontroleerd.
6. De tests kunnen op een emulator worden herhaald.
7. De tests gebruiken geen echte tijd, GPS, netwerk of persoonlijke fotodata.
8. De productie-renderlus is niet gewijzigd buiten wat nodig is om testbaar te zijn.
9. Er is documentatie voor het draaien van de tests en het bijwerken van referenties.
10. Er worden geen secrets of exacte locaties gelogd.

## 20. Definition of done

- implementatie is beperkt tot testinfrastructuur en minimale testbaarheidshaken;
- debugbuild slaagt;
- de visuele regressietests draaien op de emulator;
- de twaalf scenario's genereren beelden;
- de gegenereerde beelden zijn handmatig geverifieerd;
- documentatie voor uitvoering is toegevoegd;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-008-bestanden;
- commit en push gebeuren pas nadat de controle is goedgekeurd.

## 21. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, RemoveSky-, iconpack- of andere niet- ACT-008-bestanden;
- raak de productie-renderlogica zo min mogelijk aan; voeg alleen testbaarheidshaken toe;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg testcode bij voorkeur in een eigen testmap toe;
- stage bestanden expliciet, nooit via `git add .`;
- meld conflicten met ACT-001 of ACT-002 in plaats van een tweede architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 22. Verwacht eindresultaat

Na ACT-008 bestaat er een reproduceerbare set visuele regressietests die de twaalf belangrijkste scenario's met vaste input en seed genereert. Visuele wijzigingen kunnen objectief worden vergeleken, de laagvolgorde en shaderfallback worden gecontroleerd, en de tests draaien deterministisch op de emulator zonder netwerk, GPS of persoonlijke data.