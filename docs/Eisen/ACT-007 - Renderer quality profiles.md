# ACT-007 - Renderer quality profiles

## Status

- Type: implementatieopdracht
- Prioriteit: hoog
- Omvang: middelgroot
- Risico: middelgroot, omdat dit alle effectbudgetten en de centrale renderlus raakt
- Prerequisite: ACT-001 - Centrale wallpaper scene state
- Doelplatform: Android 13 en hoger heeft prioriteit; Android 6 tot en met 12 behoudt een Canvas fallback

## 1. Opdracht in een zin

Voeg expliciete Battery saver-, Balanced- en High-kwaliteitsprofielen toe met gedocumenteerde particle-, cloud- en dropletbudgetten en automatische tijdelijke degradatie bij trage frames, zodat de wallpaper voorspelbaar presteert zonder netwerkverkeer vanuit de wallpaper.

## 2. Waarom deze wijziging nodig is

De live wallpaper reageert nu vooral adaptief op gemeten frametijd, maar kent geen expliciete, gedocumenteerde kwaliteitsprofielen. Daardoor kan de gebruiker of het toestel ervaren:

- onvoorspelbaar effectgedrag dat alleen op CPU-belasting reageert;
- geen duidelijke keuze tussen batterijbesparing en visuele rijkdom;
- verschillende effecten die elk hun eigen, niet-gecoordineerde adaptieve logica gebruiken;
- snel heen en weer schakelen tussen kwaliteitsniveaus zonder hysterese;
- geen centrale plek om budgetten voor particles, wolken en druppels te beheren.

De gewenste ervaring is een set expliciete profielen die alle effectbudgetten centraal bepalen, met automatische tijdelijke degradatie als een toestel het niet bijhoudt.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperWeatherEffectRenderer.kt`
   - gebruikt AGSL `RuntimeShader` vanaf Android 13;
   - gebruikt een Canvas fallback op oudere Android-versies;
   - bevat background-, foreground- en glass-passes;
   - ondersteunt adaptieve sneeuw- en hagelkwaliteit op basis van gemeten frametijd.

2. `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
   - beheert `WallpaperService.Engine` en de renderthread;
   - meet frametijd en beheert de renderlus;
   - start en stopt rendering op basis van wallpaper visibility.

3. `app/src/main/kotlin/org/breezyweather/wallpaper/LiveWallpaperConfigManager.kt`
   - bewaart wallpaperinstellingen;
   - bevat nog geen quality profile-keuze.

4. `app/src/main/kotlin/org/breezyweather/wallpaper/LiveWallpaperConfigActivity.kt`
   - bevat de wallpaperinstellingen en rotating testmodus;
   - kan een keuze voor quality profile tonen.

5. De door ACT-001 toegevoegde scene-statebestanden.
   - de scene state bevat een quality profile-veld;
   - gebruik de namen en locatie die ACT-001 daadwerkelijk heeft geintroduceerd;
   - maak geen tweede concurrerend scene-state-model.

### Huidig gedrag

De renderer past de sneeuw- en hagelkwaliteit adaptief aan op basis van frametijd, maar er is geen centraal profielmodel dat ook wolken, mist/haze en rain-on-glass aanstuurt. Effecten kunnen onafhankelijk van elkaar schalen.

## 4. Afbakening

### Wel uitvoeren

- een centraal quality profile-model met Battery saver, Balanced en High;
- gedocumenteerde budgetten voor particles, wolken, fog/haze-banden en druppels;
- automatische tijdelijke degradatie bij structureel trage frames;
- automatische herstel naar het gekozen profiel als frames weer ruim binnen budget vallen;
- hysterese om snel schakelen te voorkomen;
- lokaal opslaan van de profielkeuze;
- integratie met bestaande adaptive snow/hail quality;
- optioneel een instelling in de configuratie-UI;
- unit tests voor pure profiel- en degradatielogica;
- handmatige/emulatortests voor visuele en performance-scenario's.

### Niet uitvoeren

- geen nieuwe weather families;
- geen nieuwe effect-implementaties zelf: clouds is ACT-003, snow/hail is ACT-004, fog/haze is ACT-005, rain-on-glass is ACT-006;
- geen telemetrie-export: dat is ACT-009;
- geen automatische foto-download: dat is ACT-010;
- geen wijziging aan Meteo-, GPS- of RemoveSky-clients;
- geen OpenGL-migratie;
- geen brede UI-herontwerp;
- geen brede refactor van Breezy Weather;
- geen externe shader- of asset-bestanden uit YoWindow kopieren.

## 5. Architectuurregel

De wallpaper is een read-only consumer van de centrale lokale datalaag.

Tijdens deze opdracht mag de renderer:

- het gekozen quality profile uit de lokale config lezen;
- de gemeten frametijd gebruiken voor automatische degradatie;
- het profiel aan de scene state koppelen.

De wallpaper mag niet:

- zelf GPS starten;
- een weather provider aanroepen;
- RemoveSky aanroepen;
- HTTP-requests uitvoeren;
- een eigen tweede weather cache introduceren;
- per frame nieuwe profiel- of bufferobjecten aanmaken.

## 6. Prerequisite ACT-001

ACT-007 moet voortbouwen op de centrale immutable scene state uit ACT-001. Controleer voor aanvang welke class en velden ACT-001 daadwerkelijk heeft toegevoegd, met name het quality profile-veld.

Als ACT-001 nog niet is gemerged, stop dan en rapporteer deze dependency. Voeg niet stilzwijgend een tweede scene-state-architectuur toe.

## 7. Gewenst quality profile-model

Definieer drie expliciete profielen. Elk profiel bepaalt de budgetten voor alle effecten centraal.

```kotlin
enum class WallpaperQualityProfile {
    BATTERY_SAVER,
    BALANCED,
    HIGH,
}

data class QualityBudget(
    val maxSnowParticles: Int,
    val maxHailParticles: Int,
    val cloudLayers: Int,
    val fogBands: Int,
    val maxGlassDrops: Int,
    val blurStrength: Float,
    val effectUpdateHz: Int,
)
```

Indicatieve budgetten, op echte toestellen te meten en te motiveren:

| Budget | Battery saver | Balanced | High |
|---|---:|---:|---:|
| Snow particles | laag | middel | hoog |
| Hail particles | laag | middel | hoog |
| Cloud layers | 2 | 3 | 3 |
| Fog bands | laag | middel | hoog |
| Glass drops | laag | middel | hoog |
| Blur-indruk | minimaal | matig | rijker |
| Effect update Hz | lager | normaal | normaal |

De maximale FPS blijft 30 in alle profielen.

Dit model is de centrale, leidende plek voor effectbudgetten. ACT-003 t/m ACT-006 mogen tijdelijk lokale enums/budgetten hebben gebruikt totdat ACT-007 is gemerged; na het mergen van ACT-007 lezen die effecten hun budgetten uit `QualityBudget` in plaats van uit een eigen lokaal model. ACT-007 mag de bestaande velden/aantallen uit ACT-003 t/m ACT-006 overnemen als startwaarden in plaats van ze te herontwerpen.

## 8. Automatische tijdelijke degradatie

- meet de gemiddelde en hoge-percentiel-frametijd over een venster;
- als de frametijd structureel boven een drempel komt, degradeer tijdelijk een niveau;
- als de frametijd weer ruim binnen budget valt, herstel geleidelijk;
- gebruik hysterese: degradeer en herstel niet op dezelfde drempel;
- degradatie is tijdelijk en overschrijft de door de gebruiker gekozen profielwaarde niet permanent;
- log een degradatie- of herstelgebeurtenis alleen in debug, niet per frame.

Indicatieve drempels, op echte toestellen te verfijnen:

- venstergrootte: 30 frames (ongeveer 1 seconde bij 30 FPS);
- degradeer als de p95-frametijd over dat venster > 40ms is op 10 of meer van de 30 frames;
- herstel pas als de gemiddelde frametijd over een venster van 10 seconden < 35ms blijft;
- degradeer maximaal 1 niveau per stap (High -> Balanced -> Battery saver), niet direct 2 niveaus.

Conceptueel:

```kotlin
fun resolveEffectiveProfile(
    chosen: WallpaperQualityProfile,
    recentFrameTimeMillis: Float,
    currentlyDegraded: Boolean,
): WallpaperQualityProfile
```

## 9. Hysterese

- definieer aparte drempels voor degraderen en herstellen;
- degradeer pas na meerdere achtereenvolgende trage frames;
- herstel pas na een aanhoudende periode van ruime frametijd;
- voorkom oscillatie tussen twee profielen;
- maak de drempels en venstergroottes constanten die centraal gedocumenteerd zijn.

## 10. Integratie met effecten

- clouds (ACT-003) gebruikt `cloudLayers`;
- snow/hail (ACT-004) gebruikt `maxSnowParticles` en `maxHailParticles`;
- fog/haze (ACT-005) gebruikt `fogBands` en `blurStrength`;
- rain-on-glass (ACT-006) gebruikt `maxGlassDrops` en effectsterktes;
- elk effect leest het effectieve profiel uit de scene state of via een gedeelde provider;
- de bestaande adaptive snow/hail quality moet binnen dit profielmodel passen, niet ernaast.

Overzicht van welk budgetveld door welke opdracht wordt gelezen:

| Budgetveld | ACT-003 | ACT-004 | ACT-005 | ACT-006 |
|---|---|---|---|---|
| `cloudLayers` | gebruikt | - | - | - |
| `maxSnowParticles` | - | gebruikt | - | - |
| `maxHailParticles` | - | gebruikt | - | - |
| `fogBands` | - | - | gebruikt | - |
| `blurStrength` | - | - | gebruikt | - |
| `maxGlassDrops` | - | - | - | gebruikt |
| `effectUpdateHz` | gebruikt | gebruikt | gebruikt | gebruikt |

Als sommige van deze effecten nog niet zijn gemerged, definieer dan het budgetveld alvast en documenteer dat het effect het later kan consumeren.

## 11. Configuratie-UI

Optioneel binnen deze opdracht:

- voeg een keuze toe voor Battery saver, Balanced en High;
- de standaardwaarde mag Balanced zijn;
- bewaar de keuze in `LiveWallpaperConfigManager`;
- toon geen losse sliders per effect; het profiel is de centrale knop;
- de keuze moet zowel in preview als in de echte wallpaper werken.

Als de UI-wijziging buiten scope blijft, lever dan minimaal de configopslag en een sensibele default.

## 12. Lifecycle

### Visibility

- de profielkeuze blijft bewaard wanneer de wallpaper onzichtbaar is;
- tijdelijke degradatie kan worden gereset bij het opnieuw zichtbaar worden, zodat een toestel weer op het gekozen profiel start.

### Surface recreation

- behoud de profielkeuze;
- herbereken budgetten alleen als de resolutie wezenlijk wijzigt.

### Animaties uit

- als animaties uit zijn, geldt nog steeds een profiel voor het enkele statische frame;
- plan geen degradatie-loop als er geen animatieframes zijn.

## 13. Data-gedrag

ACT-007 is geen nieuwe refreshoplossing. Het profiel reageert op:

- de lokale configkeuze;
- de gemeten frametijd;
- wallpaper visibility/restart.

Voeg geen periodieke netwerkpoll of eigen datacache toe.

## 14. Performance-eisen

- maximaal 30 FPS in alle profielen;
- geen objectallocaties in de profiel-resolutie per frame;
- budgetten worden eenmaal per profielwijziging berekend en hergebruikt;
- degradatie en herstel gebruiken hysterese;
- geen logging per frame;
- rendering stopt wanneer de wallpaper onzichtbaar is.

Meet minimaal:

- frametijd per profiel bij zware neerslag;
- gedrag van automatische degradatie op een zwak toestel of emulator;
- herstel naar het gekozen profiel na een rustige periode;
- Canvas fallback per profiel op een pre-Android 13 emulator indien beschikbaar.

## 15. Logging en privacy

Alle nieuwe logging is debug-only en niet per frame.

Toegestane informatie:

- gekozen profiel;
- effectief profiel na degradatie;
- degradatie- en herstelgebeurtenissen;
- berekende budgetten.

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- lokale fotopaden als die persoonsgegevens kunnen bevatten.

## 16. Voorgestelde implementatiestappen

1. Controleer en documenteer de ACT-001 scene-state API en het quality profile-veld.
2. Definieer het `WallpaperQualityProfile`-enum en het `QualityBudget`-model.
3. Vul indicatieve budgetten per profiel in.
4. Voeg een pure functie toe voor effectief profiel met hysterese.
5. Koppel de bestaande adaptive snow/hail quality aan dit model.
6. Lever de budgetten aan de effecten via de scene state of een gedeelde provider.
7. Voeg configopslag voor de profielkeuze toe.
8. Voeg optioneel een UI-keuze toe.
9. Reset tijdelijke degradatie bij opnieuw zichtbaar worden.
10. Voeg pure unit tests toe.
11. Build debug en release.
12. Test op emulator en gekoppelde telefoon.
13. Maak metingen en eventuele screenshots.
14. Controleer `git diff` op scope en secrets.

## 17. Unit tests

Voeg tests toe voor pure logica, zonder echte Surface of WallpaperService waar dat niet nodig is.

Minimale testgevallen:

1. elk profiel levert de verwachte budgetten;
2. budgetten verschillen aantoonbaar tussen Battery saver, Balanced en High;
3. een trage frametijd boven de degradatiedrempel degradeert na het ingestelde aantal frames;
4. een snelle frametijd herstelt na het ingestelde aantal frames;
5. degradatie en herstel gebruiken verschillende drempels (hysterese);
6. degradatie overschrijft de gekozen profielwaarde niet permanent;
7. degradatie reset bij opnieuw zichtbaar worden indien zo geconfigureerd;
8. de maximale FPS blijft 30 in elk profiel;
9. een onbekende of ontbrekende profielkeuze valt terug op Balanced;
10. de profiel-resolutie alloceert geen objecten in de hot path.

Gebruik een injecteerbare clock of geef frametijden als parameters door. Tests mogen niet afhankelijk zijn van de echte huidige tijd of een echte Surface.

## 18. Handmatige testmatrix

### Scenario's

| Scenario | Verwachting |
|---|---|
| Battery saver, zware sneeuw | weinig particles, stabiele FPS, lage belasting |
| Balanced, zware regen | middelmatige dichtheid, vloeiend |
| High, thunderstorm | rijke effecten, FPS blijft begrensd |
| Zwak toestel, High | automatische degradatie naar lager profiel |
| Herstel na degradatie | terug naar gekozen profiel na rustige periode |
| Profielwissel in instellingen | effecten schalen direct mee |

### Modi

- Auto weather + Auto day/night;
- Battery saver, Balanced en High;
- Rotating;
- animations disabled;
- wallpaper preview en werkelijk ingestelde wallpaper.

### Platforms

- Android 13 of hoger op gekoppelde telefoon;
- actieve emulator;
- waar mogelijk Android 12 of lager voor Canvas fallback;
- indien beschikbaar een zwakker toestel om degradatie te testen.

## 19. Acceptatiecriteria

1. Er zijn expliciete Battery saver-, Balanced- en High-profielen.
2. Elk profiel heeft gedocumenteerde particle-, cloud-, fog- en dropletbudgetten.
3. De budgetten verschillen aantoonbaar tussen profielen.
4. Automatische tijdelijke degradatie treedt op bij structureel trage frames.
5. Herstel naar het gekozen profiel gebeurt na een rustige periode.
6. Hysterese voorkomt snel heen en weer schakelen.
7. De profielkeuze wordt lokaal opgeslagen.
8. De bestaande adaptive snow/hail quality past binnen dit model.
9. De maximale FPS blijft 30 in elk profiel.
10. De effecten (clouds, snow/hail, fog/haze, glass-rain) consumeren de budgetten via de scene state.
11. Er zijn geen objectallocaties in de profiel-resolutie per frame.
12. De wallpaper stopt rendering wanneer hij niet zichtbaar is.
13. Er zijn geen netwerk-, GPS- of RemoveSky-calls vanuit de wallpaper toegevoegd.
14. Er worden geen secrets of exacte locaties gelogd.
15. Bestaande effect- en cachefunctionaliteit blijft werken.

## 20. Definition of done

- implementatie is beperkt tot de wallpaper quality-/config-/rendercode en gerichte tests;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- metingen en eventuele screenshots zijn gemaakt;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-007-bestanden;
- commit en push gebeuren pas nadat de controle is goedgekeurd.

## 21. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, RemoveSky-, iconpack- of andere niet- ACT-007-bestanden;
- neem bestaande wijzigingen in gedeelde wallpaperbestanden als uitgangspunt;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg het profielmodel bij voorkeur in een klein eigen quality-bestand toe;
- houd wijzigingen in `WallpaperWeatherEffectRenderer.kt` en `MaterialLiveWallpaperService.kt` zo lokaal mogelijk;
- stem budgetvelden af met ACT-003 t/m ACT-006 in plaats van losse adaptieve logica te dupliceren;
- stage bestanden expliciet, nooit via `git add .`;
- meld conflicten met ACT-001 in plaats van een tweede architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 22. Verwacht eindresultaat

Na ACT-007 heeft de wallpaper drie heldere kwaliteitsprofielen die alle effectbudgetten centraal bepalen. De gebruiker kan kiezen tussen batterijbesparing en visuele rijkdom, terwijl het toestel bij trage frames tijdelijk en met hysterese terugschakelt en daarna herstelt. De renderer blijft begrensd op maximaal 30 FPS, lokaal, en compatibel met zowel AGSL als Canvas.