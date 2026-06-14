# ACT-004 - Particle pool voor sneeuw en hagel

## Status

- Type: implementatieopdracht
- Prioriteit: middelhoog
- Omvang: middelgroot
- Risico: middelgroot, omdat dit de neerslag-renderpaden en de adaptive quality van de wallpaper raakt
- Prerequisite: ACT-001 - Centrale wallpaper scene state; bij voorkeur ook ACT-002 - Vloeiende state transitions
- Doelplatform: Android 13 en hoger heeft prioriteit; Android 6 tot en met 12 behoudt een Canvas fallback

## 1. Opdracht in een zin

Vervang dynamische of herhaalde particleberekeningen voor sneeuw en hagel door vooraf gereserveerde buffers en een kleine eigen sprite-atlas, zodat de neerslag meer diepte en variatie krijgt zonder objectallocaties per frame en zonder netwerkverkeer vanuit de wallpaper.

## 2. Waarom deze wijziging nodig is

De live wallpaper heeft al adaptieve meerlaagse sneeuw en hagel, maar de particles worden nog grotendeels per frame of per laag berekend en hergebruiken geen vaste buffers of gedeelde sprite-atlas.

Daardoor kan de gebruiker of het toestel het volgende ervaren:

- zichtbare allocaties of garbage collection-pieken tijdens zware neerslag;
- weinig variatie tussen individuele vlokken of korrels, waardoor het beeld als een uniforme ruislaag oogt;
- onvoldoende dieptegevoel doordat alle particles dezelfde grootte, snelheid en alpha hebben;
- sneeuw en hagel die qua val- en windgedrag te veel op elkaar lijken;
- een effect dat bij lagere kwaliteitsprofielen niet voorspelbaar afschaalt.

De gewenste ervaring is neerslag met herkenbare diepte, natuurlijke variatie en een stabiele framecadans op alle ondersteunde toestellen.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperWeatherEffectRenderer.kt`
   - gebruikt AGSL `RuntimeShader` vanaf Android 13;
   - gebruikt een Canvas fallback op oudere Android-versies;
   - bevat background-, foreground- en glass-passes;
   - tekent neerslag in de foreground weather pass;
   - ondersteunt adaptieve sneeuw- en hagelkwaliteit op basis van gemeten frametijd.

2. `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
   - beheert `WallpaperService.Engine` en de renderthread;
   - levert weather kind, windfactor en daytime aan de renderer;
   - start en stopt rendering op basis van wallpaper visibility.

3. De door ACT-001 toegevoegde scene-statebestanden.
   - lever precipitation type, intensity, windfactor en windrichting via de scene state;
   - gebruik de namen en locatie die ACT-001 daadwerkelijk heeft geintroduceerd;
   - maak geen tweede concurrerend scene-state-model.

4. De door ACT-002 toegevoegde transition-/contributionlogica.
   - als ACT-002 is gemerged, moet de particle pool de bestaande contribution/transitionAlpha respecteren;
   - sneeuw en hagel moeten via dezelfde overgangsbijdrage kunnen in- en uitfaden.

5. `app/src/main/kotlin/org/breezyweather/wallpaper/LiveWallpaperConfigManager.kt`
   - bewaart geforceerde of automatische weather/day-night-keuzes;
   - bevat geen aparte particle-instellingen en hoeft voor de eerste versie niet te worden aangepast.

### Huidige laagvolgorde

1. hemel / achtergrondgradient;
2. zon of maan;
3. background weather pass, voornamelijk wolken;
4. transparante locatie-afbeelding;
5. bestaande Material Weather-effecten;
6. foreground weather pass: regen, sneeuw, hagel, mist, haze en bliksem;
7. glass rain drops;
8. rotating testlabel.

Deze volgorde moet intact blijven. Sneeuw en hagel horen in de foreground weather pass (laag 6).

### Huidig gedrag

De renderer bepaalt het aantal sneeuw- en hagellagen adaptief op basis van frametijd. De particles worden echter niet uit een vooraf gereserveerde, stabiele buffer getekend en gebruiken geen gedeelde sprite-atlas. Daardoor ontstaat per frame meer rekenwerk en mogelijk meer allocatie dan nodig, en is de variatie tussen particles beperkt.

## 4. Afbakening

### Wel uitvoeren

- vooraf gereserveerde particle buffers voor sneeuw en hagel;
- een kleine eigen sprite-atlas met meerdere vlok- en korrelvormen;
- effectieve diepte via meerdere lagen of een depthfactor per particle;
- windrichting en windsnelheid die het traject van particles beinvloeden;
- onderscheidend valgedrag tussen sneeuw en hagel;
- integratie met precipitation type en intensity uit de scene state;
- respecteren van bestaande adaptive quality en, indien aanwezig, ACT-002-contribution;
- AGSL- en Canvas-implementatie;
- behoud van een werkende fallback;
- unit tests voor pure particle- en bufferlogica;
- handmatige/emulatortests voor visuele scenario's.

### Niet uitvoeren

- geen nieuwe weather families;
- geen nieuwe wolkenvormen: dat is ACT-003;
- geen volledige rewrite van de dag/nachtkleur: dat is onderdeel van ACT-002;
- geen nieuwe fog/haze-uitwerking: dat is ACT-005;
- geen nieuwe rain-on-glasskwaliteit: dat is ACT-006;
- geen aparte gebruikersinstelling voor particle-aantallen in deze opdracht: dat hoort bij ACT-007 quality profiles;
- geen automatische foto-download: dat is ACT-010;
- geen wijziging aan Meteo-, GPS- of RemoveSky-clients;
- geen OpenGL-migratie;
- geen UI-herontwerp;
- geen brede refactor van Breezy Weather;
- geen externe shader-, texture- of asset-bestanden uit YoWindow kopieren.

## 5. Architectuurregel

De wallpaper is een read-only consumer van de centrale lokale datalaag.

Tijdens deze opdracht mag de renderer:

- precipitation type, intensity, windfactor en windrichting uit de scene state lezen;
- het actieve quality profile uit de scene state of bestaande adaptive quality lezen;
- lokale wallpaperconfiguratie lezen.

De wallpaper mag niet:

- zelf GPS starten;
- een weather provider aanroepen;
- RemoveSky aanroepen;
- HTTP-requests uitvoeren;
- een eigen tweede weather cache introduceren;
- per frame nieuwe particle-, Paint-, Path-, Rect-, Random- of Bitmap-objecten aanmaken.

## 6. Prerequisite ACT-001 en ACT-002

ACT-004 moet voortbouwen op de centrale immutable scene state uit ACT-001. Controleer voor aanvang welke class en velden ACT-001 daadwerkelijk heeft toegevoegd.

De state moet voor ACT-004 minimaal kunnen leveren:

- precipitation type, minimaal het onderscheid tussen regen, sneeuw, hagel en sleet;
- precipitation intensity als genormaliseerde `0f..1f` factor;
- windfactor en bij voorkeur windrichting;
- quality profile of voldoende informatie voor de bestaande adaptive quality;
- day/night- of daylightfactor voor particlekleur en alpha.

Als ACT-002 al is gemerged, moet de particle pool de bestaande contribution/transitionAlpha gebruiken zodat sneeuw en hagel netjes mee in- en uitfaden tijdens een overgang. Is ACT-002 nog niet aanwezig, dan moet de particle pool toch een globale intensity-/alphafactor accepteren zodat ACT-002 die later kan koppelen.

Als ACT-001 nog niet is gemerged, stop dan en rapporteer deze dependency. Voeg niet stilzwijgend een tweede scene-state-architectuur toe.

## 7. Gewenst particle pool-model

Introduceer een kleine, herbruikbare particle pool die bij rendererinitialisatie of bij eerste gebruik wordt gereserveerd en daarna niet opnieuw wordt gealloceerd.

Conceptueel:

```kotlin
class PrecipitationParticlePool(
    val maxParticles: Int,
) {
    // Structure-of-arrays voor cache-efficientie en geen per-particle objecten.
    val x = FloatArray(maxParticles)
    val y = FloatArray(maxParticles)
    val velocityX = FloatArray(maxParticles)
    val velocityY = FloatArray(maxParticles)
    val depth = FloatArray(maxParticles)      // 0f = ver weg/klein, 1f = dichtbij/groot
    val scale = FloatArray(maxParticles)
    val rotation = FloatArray(maxParticles)
    val spriteIndex = IntArray(maxParticles)
    val alpha = FloatArray(maxParticles)
    var activeCount: Int = 0
}
```

De exacte naam en plaatsing mogen aansluiten bij de bestaande codeconventies, maar de verantwoordelijkheid moet hetzelfde blijven: vaste buffers, geen allocatie per frame, hergebruik van particle-slots via recycling wanneer een particle uit beeld valt.

Volg voor naamgeving, allocatiestijl en het depth-concept (0f = ver/klein, 1f = dichtbij/groot) hetzelfde patroon als `CloudLayer`/`CloudFieldFactory` uit ACT-003 (`app/src/main/kotlin/org/breezyweather/wallpaper/CloudField.kt`): een pure, testbare factory/params-laag die losstaat van Canvas/AGSL, plus een los renderdeel dat die parameters consumeert. Zie ook `docs/ACT-000 - Conventies en gedeelde patronen.md`.

Per frame:

```kotlin
fun update(deltaSeconds: Float, scene: WallpaperRenderState)
fun draw(canvas: Canvas, atlas: ParticleAtlas, contribution: Float)
```

Gebruik monotonic time of een vaste delta voor de update, geen wall-clock per particle.

## 8. Sprite-atlas

Bouw een kleine, eigen sprite-atlas met meerdere vlok- en korrelvormen. De atlas mag procedureel worden gegenereerd in een offscreen bitmap bij initialisatie, of uit eigen vectordrawables worden opgebouwd. Kopieer geen YoWindow-assets.

Aanbevolen inhoud:

| Type | Aantal varianten | Karakter |
|---|---:|---|
| Sneeuwvlok | 3 tot 5 | zacht, rond, lichte ster- of stipvorm, hogere transparantie |
| Hagelkorrel | 2 tot 3 | compacter, harder contour, lichte highlight, minder transparant |

Eisen:

- de atlas wordt eenmaal opgebouwd en hergebruikt;
- particles kiezen bij (her)initialisatie een vaste `spriteIndex`;
- de atlas blijft klein, bijvoorbeeld maximaal enkele honderden pixels per as;
- bij AGSL kan de atlas als sampler/`shader` worden aangeboden of kan de particle vorm procedureel in de shader worden benaderd;
- bij Canvas wordt de atlas via `drawBitmap` met source-rects per sprite getekend.

## 9. Wat onderscheidt sneeuw en hagel

| Aspect | Sneeuw | Hagel |
|---|---|---|
| Valsnelheid | langzaam | sneller en compacter |
| Horizontale beweging | meer drift en lichte sinusoide | rechter, minder drift |
| Windgevoeligheid | hoog | lager |
| Grootte | gevarieerd, zachte randen | kleiner tot middelgroot, harde randen |
| Alpha | hoger doorzichtig, zachte fade | minder transparant |
| Rotatie | lichte continue rotatie | weinig tot geen rotatie |
| Aantal bij gelijke intensity | hoger | lager |

Sleet kan worden benaderd als een mengsel: een deel sneeuwparticles en een deel hagelparticles binnen dezelfde pool, gestuurd door de scene state.

## 10. Diepte en lagen

Gebruik effectieve diepte van 10 tot 20 lagen of een equivalente continue depthfactor per particle.

- `depth` bepaalt `scale`, `alpha` en `velocityY`;
- particles dichterbij zijn groter, helderder en vallen sneller;
- particles ver weg zijn kleiner, vager en bewegen trager;
- de depthverdeling moet stabiel zijn en niet per frame opnieuw gerandomiseerd worden;
- vermijd een zichtbare gelaagde banding; gebruik een vloeiende verdeling.

## 11. Wind

Windrichting en windsnelheid uit de scene state beinvloeden de particle-trajecten.

- windsnelheid schaalt `velocityX`;
- windrichting bepaalt het teken en de hoek van de horizontale beweging;
- sneeuw reageert sterker op wind dan hagel;
- gebruik bij voorkeur windrichting via de kortste hoek, consistent met ACT-002;
- voorkom dat een windverandering een abrupte herpositionering van alle particles veroorzaakt; pas de versnelling geleidelijk toe.

## 12. Rendererstrategie

### Aanbevolen oplossing

- reserveer de buffers en de atlas bij initialisatie of bij de eerste keer dat sneeuw of hagel actief wordt;
- update en teken alleen de actieve particles (`activeCount`);
- recycle particles die buiten beeld vallen door ze bovenaan opnieuw te positioneren met nieuwe willekeurige eigenschappen uit een vooraf gevulde randombron;
- gebruik geen `Random`-allocatie per frame; gebruik een herbruikbare, geseede randomgenerator of een vooraf gevulde noise-tabel;
- koppel het maximale aantal particles aan het quality profile en de bestaande adaptive quality.

### AGSL

- voer de positie-update bij voorkeur op de CPU uit op de buffers en teken de sprites, of benader de particles procedureel in de shader;
- vermenigvuldig de uiteindelijke alpha met de globale contribution/transitionAlpha en met de precipitation intensity:

```text
effectiveAlpha = particleAlpha * sceneIntensity * contribution
```

- laat geometrie, particlepositie en tijd doorlopen, ook tijdens een overgang;
- gebruik geen harde `if (intensity > 0.5)`.

### Canvas fallback

- gebruik bestaande Paint-instanties;
- teken via `drawBitmap` met source-rects uit de atlas, of via een herbruikt `Path`;
- vermenigvuldig de Paint-alpha met contribution en intensity;
- maak geen nieuwe Paint, Bitmap, Path, Rect of Random per frame;
- gebruik `saveLayerAlpha()` alleen als een effect niet anders correct kan mengen en nadat performance is gemeten.

## 13. Integratie met intensity en transition

- het aantal actieve particles schaalt met precipitation intensity;
- bij een lage intensity zijn er minder en zwakkere particles, niet alleen lagere alpha;
- bij een overgang (ACT-002) faden particles via de contribution in en uit;
- bij Rain naar Snow of Snow naar Hail mag de pool tegelijk sneeuw- en hagelparticles bevatten, met afzonderlijke bijdragen;
- voorkom een lege of poppende frame wanneer intensity naar 0 gaat: laat bestaande particles uitvallen in plaats van ze direct te wissen.

## 14. Lifecycle

### Visibility

Wanneer `onVisibilityChanged(false)` wordt aangeroepen:

- stop framecallbacks zoals nu;
- behoud de gereserveerde buffers en atlas;
- voer geen particle-updates uit terwijl onzichtbaar.

Wanneer de wallpaper opnieuw zichtbaar wordt:

- hervat de update vanaf de bestaande particle state;
- voorkom een grote tijdsprong door een onbegrensde delta; clamp de eerste delta na hervatten.

### Surface recreation

Bij surface resize of recreation:

- herbereken bounds en spawn-/recyclegrenzen;
- herverdeel particleposities binnen de nieuwe bounds zonder de buffers opnieuw te alloceren als de capaciteit gelijk blijft;
- bouw de atlas alleen opnieuw op als de schaal dat strikt vereist.

### Animaties uit

Als animaties uitgeschakeld zijn:

- plan geen particle-updateframes;
- teken eventueel een enkel statisch representatief frame of geen particles, consistent met het bestaande gedrag van de andere effecten.

## 15. Adaptive quality en quality profiles

- behoud de bestaande adaptive snow/hail quality op basis van frametijd;
- koppel het maximale aantal particles en de atlas-detailgraad aan het quality profile;
- gebruik hysterese zodat het aantal particles niet snel heen en weer schakelt;
- definieer indicatieve budgetten, die in ACT-007 verder worden vastgelegd:

| Profiel | Indicatief max particles (sneeuw) | Indicatief max particles (hagel) |
|---|---:|---:|
| Battery saver | laag | laag |
| Balanced | middel | middel |
| High | hoog | hoog |

De exacte aantallen moeten op echte toestellen worden gemeten en gemotiveerd.

Als ACT-007 nog niet is gemerged op het moment van implementatie, definieer dan een lokale `enum class QualityLevel { LOW, BALANCED, HIGH }` (of vergelijkbaar) binnen het particle-pool-bestand, gevoed door de bestaande adaptive-quality-meting. Wacht niet op ACT-007. Zodra ACT-007 is gemerged, consolideert die opdracht dit naar het centrale `QualityBudget`-model zonder gedragsverandering voor ACT-004.

## 16. Data-gedrag

ACT-004 is geen nieuwe refreshoplossing. De particle pool reageert uitsluitend op de lokale scene state:

- bij wallpaper visibility/restart;
- na een lokale data-invalidation;
- na configwijziging;
- tijdens rotating testmodus;
- via een toekomstige centrale wallpaper-update-notificatie.

Voeg geen periodieke netwerkpoll of eigen datacache toe.

## 17. Performance-eisen

- maximaal 30 FPS;
- geen objectallocaties in de particle-update- en draw-paden;
- geen nieuwe Paint, Bitmap, Path, Rect of Random per frame;
- vaste buffers worden eenmaal gereserveerd en hergebruikt;
- de atlas wordt eenmaal opgebouwd en hergebruikt;
- uniforms en source-rects alleen bijwerken als nodig;
- bestaande adaptive snow/hail quality behouden;
- particle-update stopt wanneer de wallpaper onzichtbaar is;
- geen logging per frame.

Meet minimaal:

- stabiele frametijd bij lichte sneeuw;
- frametijd bij zware sneeuw op High;
- frametijd bij zware hagel;
- Rain naar Snow en Snow naar Hail tijdens een transition;
- Canvas fallback op een pre-Android 13 emulator indien beschikbaar.

Als het volledige particlebudget op een toestel structureel te zwaar is, verlaag het aantal particles via adaptive quality. Verlaag niet de schermresolutie zonder expliciete meting en motivatie.

## 18. Logging en privacy

Alle nieuwe logging is debug-only en niet per frame.

Toegestane informatie:

- gekozen quality profile;
- gereserveerd en actief aantal particles;
- atlas-initialisatie en hergebruik;
- fallback naar Canvas door shaderfout.

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- lokale fotopaden als die persoonsgegevens kunnen bevatten.

## 19. Voorgestelde implementatiestappen

1. Controleer en documenteer de ACT-001 scene-state API en, indien aanwezig, de ACT-002 contribution.
2. Voeg een pure particle pool-class toe met structure-of-arrays en zonder Android-afhankelijkheid in de logica.
3. Voeg pure update-logica toe voor positie, depth, wind en recycling.
4. Bouw een kleine sprite-atlas, procedureel of uit eigen vectordrawables.
5. Reserveer buffers en atlas eenmalig bij initialisatie of eerste gebruik.
6. Koppel sneeuw- en hagelparameters aan precipitation type, intensity en wind.
7. Implementeer onderscheidend val- en windgedrag tussen sneeuw en hagel.
8. Voeg de AGSL-draw met contribution/intensity toe.
9. Voeg de Canvas-fallbackdraw met Paint-alpha toe.
10. Koppel het maximale aantal particles aan quality profile en adaptive quality met hysterese.
11. Handel sleet af als mengsel van sneeuw en hagel.
12. Handel visibility, surface recreation en animations disabled af.
13. Voeg pure unit tests toe.
14. Build debug en release.
15. Test op emulator en gekoppelde telefoon.
16. Maak screenshots van de vereiste scenario's.
17. Controleer `git diff` op scope en secrets.

## 20. Unit tests

Voeg tests toe voor pure logica, zonder echte Surface of WallpaperService waar dat niet nodig is.

Minimale testgevallen:

1. een pool reserveert exact `maxParticles` slots en allocaties gebeuren niet opnieuw bij update;
2. `activeCount` schaalt monotoon met intensity;
3. een particle die onder de onderrand valt wordt gerecycled naar boven;
4. recycling kent nieuwe eigenschappen toe zonder nieuwe objectallocatie;
5. depth bepaalt scale, alpha en valsnelheid binnen verwachte grenzen;
6. sneeuw heeft gemiddeld lagere valsnelheid dan hagel bij gelijke intensity;
7. sneeuw reageert sterker op windfactor dan hagel;
8. windrichting beinvloedt het teken van de horizontale snelheid;
9. windrichting 350 naar 10 graden gebruikt de korte route;
10. intensity 0 leidt tot uitvallende particles, niet tot een directe wis;
11. contribution/transitionAlpha schaalt de effectieve alpha lineair;
12. sleet bevat zowel sneeuw- als hagelparticles in de verwachte verhouding;
13. een grotere delta na hervatten wordt geclamped;
14. quality profile bepaalt het maximale aantal particles met hysterese;
15. geseede randombron geeft reproduceerbare verdeling voor tests.

Gebruik een injecteerbare clock of geef delta's als parameters door. Tests mogen niet afhankelijk zijn van de echte huidige tijd of een echte Surface.

## 21. Handmatige testmatrix

### Neerslagscenario's

| Scenario | Verwachting |
|---|---|
| Lichte sneeuw | weinig, langzaam dwarrelende vlokken met duidelijke diepte |
| Zware sneeuw | veel vlokken, variatie in grootte en snelheid, stabiele FPS |
| Lichte hagel | weinig, snellere en compactere korrels |
| Zware hagel | veel korrels, recht vallend, weinig drift |
| Sneeuw met veel wind | sterke zijwaartse drift en sinusoide |
| Hagel met veel wind | beperkte drift, blijft vrij recht |
| Sleet | mengsel van vlokken en korrels |
| Rain naar Snow | regen neemt af terwijl sneeuw opbouwt zonder pop |
| Snow naar Hail | sneeuw verdwijnt terwijl compactere hagel toeneemt |
| Intensity naar 0 | particles vallen uit in plaats van direct te verdwijnen |

### Modi

- Auto weather + Auto day/night;
- geforceerde Snow;
- geforceerde Hail;
- geforceerde Sleet;
- Rotating;
- animations disabled;
- verschillende quality profiles;
- wallpaper preview en werkelijk ingestelde wallpaper.

### Platforms

- Android 13 of hoger op gekoppelde telefoon;
- actieve emulator;
- waar mogelijk Android 12 of lager voor Canvas fallback.

## 22. Screenshotset

Maak minimaal screenshots van:

1. lichte sneeuw overdag;
2. zware sneeuw overdag;
3. sneeuw bij nacht;
4. lichte hagel;
5. zware hagel;
6. sneeuw met sterke wind;
7. sleet;
8. Rain naar Snow op ongeveer 50%;
9. Snow naar Hail op ongeveer 50%;
10. Canvas fallback met sneeuw indien beschikbaar.

Screenshots moeten weather family, quality profile en platform in de bestandsnaam krijgen.

## 23. Acceptatiecriteria

1. Er is een vooraf gereserveerde particle pool die niet per frame alloceert.
2. Er is een kleine eigen sprite-atlas met meerdere vlok- en korrelvormen.
3. De neerslag toont effectief 10 tot 20 lagen of een equivalente continue diepte.
4. Windrichting en windsnelheid beinvloeden aantoonbaar de trajecten.
5. Hagel valt sneller en compacter dan sneeuw en reageert minder op wind.
6. Sleet wordt als mengsel van sneeuw en hagel weergegeven.
7. Het aantal particles schaalt met precipitation intensity.
8. De fallback via Canvas blijft beschikbaar en crasht niet.
9. AGSL compileert zonder fouten op Android 13+.
10. De bestaande adaptive snow/hail quality blijft werken, met hysterese.
11. Er treedt geen zichtbare pop op bij Rain naar Snow of Snow naar Hail.
12. Intensity naar 0 laat particles uitvallen in plaats van direct te wissen.
13. Indien ACT-002 aanwezig is, faden particles via de contribution mee in en uit.
14. De wallpaper blijft begrensd op maximaal 30 FPS.
15. De wallpaper stopt particle-updates wanneer hij niet zichtbaar is.
16. Er zijn geen objectallocaties in de particle-update- en draw-paden.
17. Er zijn geen netwerk-, GPS- of RemoveSky-calls vanuit de wallpaper toegevoegd.
18. Er worden geen secrets of exacte locaties gelogd.
19. Bestaande regen-, mist-, haze-, wolken- en rain-on-glassfunctionaliteit blijft werken.
20. De laagvolgorde blijft intact en sneeuw/hagel blijven in de foreground weather pass.

## 24. Definition of done

- implementatie is beperkt tot de wallpaper particle-/rendercode en gerichte tests;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- screenshotset is gemaakt;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-004-bestanden;
- commit en push gebeuren pas nadat de visuele controle is goedgekeurd.

## 25. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, RemoveSky-, iconpack- of andere niet- ACT-004-bestanden;
- neem bestaande wijzigingen in gedeelde wallpaperbestanden als uitgangspunt;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg nieuwe classes bij voorkeur in een klein eigen particle-/atlasbestand toe;
- houd wijzigingen in `WallpaperWeatherEffectRenderer.kt` en `MaterialLiveWallpaperService.kt` zo lokaal mogelijk;
- stage bestanden expliciet, nooit via `git add .`;
- meld conflicten met ACT-001 of ACT-002 in plaats van een tweede architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 26. Verwacht eindresultaat

Na ACT-004 tonen sneeuw en hagel herkenbare diepte en natuurlijke variatie. Vlokken dwarrelen met de wind, hagel valt sneller en compacter, en sleet combineert beide. De particles komen uit vaste buffers en een kleine eigen sprite-atlas, zonder allocaties per frame. Het effect schaalt voorspelbaar met intensity en quality profile, faedt netjes mee in overgangen, blijft begrensd op maximaal 30 FPS en blijft compatibel met zowel AGSL als Canvas.