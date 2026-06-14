# ACT-003 - Wolken met meerdere massa-lagen

## Status

- Type: implementatieopdracht
- Prioriteit: middelhoog
- Omvang: middelgroot
- Risico: middelgroot, omdat dit de background weather pass van de wallpaper-renderlus raakt
- Prerequisite: ACT-001 - Centrale wallpaper scene state
- Aanbevolen volgorde: bij voorkeur na ACT-002 - Vloeiende state transitions, zodat wolkenovergangen meteen meeliften op de transitielogica
- Doelplatform: Android 13 en hoger heeft prioriteit; Android 6 tot en met 12 behoudt een Canvas fallback

## 1. Opdracht in een zin

Vervang de huidige uniforme procedurele wolkenlaag door twee of drie eigen wolken-massalagen met verschillende schaal, offset, snelheid, alpha en donkerte, zodat Cloud, Cloudy, Rain, Thunderstorm en Wind visueel duidelijk van elkaar te onderscheiden zijn, zonder YoWindow-assets te kopieren en zonder netwerkverkeer vanuit de wallpaper.

## 2. Waarom deze wijziging nodig is

De live wallpaper tekent wolken nu als een grotendeels uniforme procedurele ruis- of dichtheidslaag achter de transparante locatie-afbeelding. Daardoor ogen verschillende bewolkte weather families te veel hetzelfde en mist de lucht herkenbare massa en diepte.

De gebruiker ziet daardoor mogelijk:

- een egale waas in plaats van herkenbare wolkenpartijen;
- weinig verschil tussen Partly cloudy, Cloudy en Overcast;
- Rain en Thunderstorm die qua wolkendek nauwelijks donkerder of zwaarder ogen;
- Wind die geen duidelijk snellere, langgerekte beweging laat zien;
- weinig dieptegevoel doordat alle wolken op dezelfde snelheid en schaal bewegen.

De gewenste ervaring is een lucht met meerdere lagen die samen een natuurlijke massa en parallax-achtige diepte vormen, passend bij de actuele weather family.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
   - beheert `WallpaperService.Engine` en de renderthread;
   - leest lokale locatie- en weerdata;
   - bouwt de scene state op (ACT-001) en bepaalt weather kind en day/night;
   - roept de background weather pass aan voor wolken, voor de locatie-afbeelding.

2. `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperWeatherEffectRenderer.kt`
   - gebruikt AGSL `RuntimeShader` vanaf Android 13;
   - gebruikt een Canvas fallback op oudere Android-versies;
   - bevat `drawBackgroundWeatherPass()` waar de wolken worden getekend;
   - ontvangt cloudparameters via de scene state of constructorwaarden.

3. De door ACT-001 toegevoegde scene-statebestanden.
   - lever `cloudDensity`, `cloudDarkness` en, indien aanwezig, `cloudAlpha`, `cloudSpeedfactor` en `windDirectionDegrees`;
   - gebruik de namen en velden die ACT-001 daadwerkelijk heeft geintroduceerd;
   - maak geen tweede concurrerend cloudmodel.

4. De door ACT-002 toegevoegde transitielogica, indien gemerged.
   - cloudparameters moeten al via interpolatie kunnen mengen;
   - voeg geen tweede, afwijkende cloudtransition toe.

### Huidige laagvolgorde

1. hemel / achtergrondgradient;
2. zon of maan;
3. background weather pass, voornamelijk wolken;
4. transparante locatie-afbeelding;
5. bestaande Material Weather-effecten;
6. foreground weather pass: regen, sneeuw, hagel, mist, haze en bliksem;
7. glass rain drops;
8. rotating testlabel.

Deze volgorde moet intact blijven. Wolken blijven altijd achter de locatie-afbeelding (laag 3).

### Huidig wolkengedrag

De background pass tekent op dit moment conceptueel een enkele dichtheidsgestuurde wolkenlaag met een uniforme beweging. Schaal, snelheid en donkerte zijn niet duidelijk per laag onderscheiden. Hierdoor ontbreekt dieptewerking en is het verschil tussen bewolkte families subtiel.

## 4. Afbakening

### Wel uitvoeren

- twee of drie eigen procedurele wolken-massalagen;
- per laag eigen schaal, offset, snelheid, alpha en donkerte;
- koppeling van laagparameters aan de scene state (density, darkness, windfactor, windrichting);
- duidelijk visueel onderscheid tussen Clear, Partly cloudy, Cloudy, Rain, Thunderstorm en Wind;
- AGSL- en Canvas-implementatie van de gelaagde wolken;
- behoud van de bestaande laagvolgorde, met wolken achter de foto;
- compatibiliteit met de ACT-002-transitiebijdrage (contribution/alpha) indien aanwezig;
- unit tests voor pure cloud-parameterberekeningen;
- handmatige/emulatortests voor visuele scenario's;
- screenshotset van de bewolkte families.

### Niet uitvoeren

- geen nieuwe weather families;
- geen nieuwe snow/hail particle pool: dat is ACT-004;
- geen nieuwe fog/haze-uitwerking: dat is ACT-005;
- geen nieuwe rain-on-glasskwaliteit: dat is ACT-006;
- geen renderer quality profiles als nieuw raamwerk: dat is ACT-007 (gebruik wel de bestaande adaptive quality indien aanwezig);
- geen automatische foto-download: dat is ACT-010;
- geen wijziging aan Meteo-, GPS- of RemoveSky-clients;
- geen OpenGL-migratie;
- geen UI-herontwerp of nieuwe gebruikersinstelling in deze opdracht;
- geen brede refactor van Breezy Weather;
- geen externe shadercode, textures of assets uit YoWindow of andere bronnen kopieren.

## 5. Architectuurregel

De wallpaper is een read-only consumer van de centrale lokale datalaag.

Tijdens deze opdracht mag `MaterialLiveWallpaperService`:

- lokale `LocationRepository`-data lezen;
- lokale `WeatherRepository`-data lezen;
- de lokale fotocache lezen;
- lokale wallpaperconfiguratie lezen.

De wallpaper mag niet:

- zelf GPS starten;
- een weather provider aanroepen;
- RemoveSky aanroepen;
- HTTP-requests uitvoeren;
- een eigen tweede weather cache introduceren.

Wolken worden uitsluitend procedureel gegenereerd. Er worden geen bitmaps, textures of shaders uit externe of gedecompileerde bronnen toegevoegd.

## 6. Prerequisite ACT-001 en relatie met ACT-002

ACT-003 bouwt voort op de centrale immutable scene state uit ACT-001. Controleer voor aanvang welke class en velden ACT-001 daadwerkelijk heeft toegevoegd.

De state moet voor ACT-003 minimaal leveren:

- `weatherFamily` of `weatherKind`;
- `cloudDensity` (`0f..1f`);
- `cloudDarkness` (`0f..1f`);
- `windFactor`;
- `windDirectionDegrees` (mag null zijn);
- `daylight` of een vergelijkbare dag/nachtfactor voor wolkenkleur.

Als ACT-001 nog niet is gemerged, stop dan en rapporteer deze dependency. Voeg niet stilzwijgend een tweede scene-state-architectuur of een eigen cloudmodel toe.

Als ACT-002 al is gemerged:

- meng de cloudparameters via de bestaande interpolatie;
- ontvang de bestaande transition contribution/alpha en vermenigvuldig daar de uiteindelijke wolken-alpha mee;
- voeg geen losse cloudtransitie toe.

Als ACT-002 nog niet is gemerged:

- zorg dat de gelaagde wolken alsnog correct werken op een stabiele scene state;
- houd de implementatie zo dat ACT-002 later de cloudparameters kan interpoleren zonder herontwerp.

## 7. Gewenst wolkenmodel

Introduceer een klein, pure model dat per laag de renderparameters beschrijft. De exacte naam mag aansluiten bij ACT-001/ACT-002, maar de verantwoordelijkheid blijft hetzelfde.

```kotlin
data class CloudLayer(
    val depth: Float,        // 0f = ver/achter, 1f = dichtbij/voor
    val scale: Float,        // relatieve celgrootte van de wolkenvormen
    val speedFactor: Float,  // relatieve horizontale snelheid
    val alpha: Float,        // dekking van deze laag
    val darkness: Float,     // 0f = licht, 1f = donker
    val verticalOffset: Float,
)

data class CloudFieldParams(
    val layers: List<CloudLayer>,
    val directionDegrees: Float,
)
```

Aanbevolen aantal lagen:

- minimaal twee, bij voorkeur drie;
- een achterste, langzame, grote en lichte laag voor diepte;
- een middenlaag met gemiddelde schaal en snelheid;
- optioneel een voorste, kleinere, snellere en iets donkerdere laag voor detail en parallax-indruk.

Lagen verschillen altijd in minstens schaal en snelheid, zodat geen herhalend, egaal patroon ontstaat.

## 8. Laagparameters per family

De cloud field params worden afgeleid uit de scene state als pure functie:

```kotlin
fun cloudFieldParams(
    family: WallpaperWeatherFamily,
    cloudDensity: Float,
    cloudDarkness: Float,
    windFactor: Float,
    windDirectionDegrees: Float?,
): CloudFieldParams
```

Richtlijnen voor de verhoudingen (renderdefaults, geen meteorologische claims):

| Family | Aantal zichtbare lagen | Totale dekking | Donkerte | Snelheidskarakter |
|---|---:|---:|---:|---|
| Clear | 0 | ~0.00 | laag | n.v.t. |
| Partly cloudy | 2 | laag-midden | laag | rustig |
| Cloudy | 3 | hoog | midden | rustig tot gemiddeld |
| Rain | 3 | hoog | hoog | gemiddeld |
| Snow | 3 | hoog | midden | rustig |
| Sleet | 3 | hoog | midden-hoog | gemiddeld |
| Hail | 3 | zeer hoog | hoog | gemiddeld |
| Fog | 2 | midden | laag-midden | zeer rustig |
| Haze | 2 | laag-midden | laag | zeer rustig |
| Thunder | 3 | hoog | hoog | gemiddeld |
| Thunderstorm | 3 | zeer hoog | zeer hoog | snel |
| Wind | 2-3 | midden | laag-midden | snel en langgerekt |

Belangrijke regels:

- de totale dekking en donkerte worden gestuurd door `cloudDensity` en `cloudDarkness` uit de scene state;
- Wind verhoogt de `speedFactor` en bij voorkeur de horizontale uitrekking van de wolkenvormen;
- Thunderstorm combineert hoge dekking, hoge donkerte en hoge snelheid;
- bij `cloudDensity` rond 0 worden lagen volledig transparant en is er effectief geen zichtbare wolk;
- de overgang van weinig naar veel lagen mag niet als harde pop verschijnen; gebruik alpha in plaats van het abrupt toevoegen of verwijderen van een laag.

## 9. Wolkenvorm en beweging

### Vorm

- gebruik eigen procedurele vormen, bijvoorbeeld gestapelde of gemengde ruis (waarde-/gradientruis) of soft-blob velden;
- vermijd een herkenbaar herhalend tegelpatroon door per laag schaal en offset te varieren;
- houd randen zacht; harde randen ogen onnatuurlijk;
- donkerte mag onderin een wolkenmassa iets sterker zijn dan bovenin voor volume-indruk.

### Beweging

- elke laag beweegt horizontaal op basis van `speedFactor`, `windFactor` en monotonic time;
- gebruik windrichting om de bewegingsrichting te bepalen; bij ontbrekende richting gebruik een vaste, rustige standaardrichting;
- diepere (achterste) lagen bewegen langzamer dan voorste lagen, voor parallax-indruk;
- gebruik monotonic time, bijvoorbeeld `SystemClock.elapsedRealtime()`, niet wall-clock time.

### Kleur

- wolkenkleur volgt `daylight`: lichter en warmer overdag, donkerder en koeler 's nachts;
- `cloudDarkness` verlaagt de helderheid voor zware en stormachtige families;
- gebruik dezelfde daylightfactor als hemel en celestial body, zodat de lucht consistent oogt;
- meng kleuren per kanaal of in een geschikte kleurruimte; geen directe integerinterpolatie op een packed color.

## 10. Rendererstrategie

### AGSL

- breid de bestaande wolken-shader uit zodat meerdere lagen in een enkele pass worden gemengd, of voeg een beperkt aantal samples per laag toe;
- geef per laag uniforms door voor scale, speed, alpha, darkness en offset;
- houd het aantal noise-octaves en samples begrensd om binnen het frame budget te blijven;
- vermenigvuldig de uiteindelijke alpha met de scene-/transition contribution indien ACT-002 aanwezig is;
- log een shaderfout eenmalig en schakel direct over op de Canvas fallback.

Voorbeeld van uniform-opzet (conceptueel):

```glsl
uniform float layerCount;
uniform float layerScale[3];
uniform float layerSpeed[3];
uniform float layerAlpha[3];
uniform float layerDarkness[3];
uniform float windDirection;
uniform float daylight;
uniform float transitionAlpha;
```

### Canvas fallback

- teken twee of drie wolkenlagen met hergebruikte `Paint`-objecten en vooraf opgebouwde vormen of `Shader`/`BitmapShader`-tegels die eenmalig worden aangemaakt;
- verschuif de lagen per frame via een offset, niet door nieuwe objecten te alloceren;
- gebruik geen nieuwe `Paint`, `Path`, `Bitmap`, `Random` of laagobjecten per frame;
- gebruik `saveLayerAlpha()` alleen wanneer een laag niet anders correct kan mengen en nadat performance is gemeten;
- het Canvas-resultaat hoeft niet identiek te zijn aan AGSL, maar moet hetzelfde aantal lagen en hetzelfde karakter per family tonen.

### Gedeelde regels

- maak geen offscreen full-screen bitmap per frame;
- bouw cloud field params alleen opnieuw op bij wijziging van de scene state, niet per frame;
- houd de wolken strikt achter de locatie-afbeelding.

## 11. Performance-eisen

- maximaal 30 FPS;
- geen full-screen bitmapallocatie per frame;
- geen objectallocaties in `drawBackgroundWeatherPass`;
- cloud field params worden gecachet en alleen bij stateverandering herberekend;
- begrens het aantal noise-octaves/samples per laag;
- bestaande gradientcache per minuut en bestaande adaptive quality blijven behouden;
- als de bestaande adaptive quality (uit eerdere acts) aanwezig is, mag het aantal wolkenlagen of de noise-complexiteit bij trage frames tijdelijk worden verlaagd, met hysterese om snel schakelen te voorkomen;
- rendering stopt wanneer de wallpaper onzichtbaar is;
- geen logging per frame.

Meet minimaal:

- stabiele frametijd met drie wolkenlagen op Clear, Cloudy en Thunderstorm;
- frametijd op Android 13+ AGSL en op de Canvas fallback;
- frametijd tijdens een weather-overgang met ACT-002 actief (twee renderers plus gelaagde wolken);
- geheugengedrag bij langdurig zichtbaar blijven van de wallpaper.

Als drie volledige wolkenlagen op een toestel structureel te zwaar zijn, val terug naar twee lagen. Verlaag niet de schermresolutie zonder expliciete meting en motivatie.

## 12. Logging en privacy

Alle nieuwe logging is debug-only en niet per frame.

Toegestane informatie:

- weather family;
- aantal actieve wolkenlagen;
- gekozen quality-degradatie indien van toepassing;
- fallback naar Canvas door shaderfout.

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- lokale fotopaden als die persoonsgegevens kunnen bevatten.

## 13. Voorgestelde implementatiestappen

1. Controleer en documenteer de ACT-001 cloudvelden en, indien aanwezig, de ACT-002 transitiebijdrage.
2. Voeg pure helpers toe voor `cloudFieldParams()` en per-layer parameters.
3. Definieer de standaard laagconfiguraties per family (twee tot drie lagen).
4. Breid de AGSL-wolkenshader uit naar meerdere gemengde lagen met uniforms.
5. Implementeer de Canvas fallback met hergebruikte Paint/Shader-objecten en per-frame offsets.
6. Koppel wolkenkleur aan dezelfde daylightfactor als hemel en celestial body.
7. Koppel snelheid en richting aan windfactor en windrichting.
8. Vermenigvuldig de wolken-alpha met de transition contribution indien ACT-002 aanwezig is.
9. Cache cloud field params en herbouw ze alleen bij stateverandering.
10. Voeg eventueel een optionele laagreductie toe via bestaande adaptive quality.
11. Voeg pure unit tests toe.
12. Build debug en release.
13. Test op emulator en gekoppelde telefoon.
14. Maak de screenshotset.
15. Controleer `git diff` op scope en secrets.

## 14. Unit tests

Voeg tests toe voor pure logica, zonder echte Surface of WallpaperService waar dat niet nodig is.

Minimale testgevallen:

1. Clear levert geen of volledig transparante wolkenlagen op;
2. Partly cloudy levert minder zichtbare dekking dan Cloudy;
3. Cloudy, Rain en Thunderstorm leveren drie zichtbare lagen op;
4. Thunderstorm heeft hogere donkerte dan Cloudy;
5. Thunderstorm heeft een hogere totale snelheid dan Partly cloudy;
6. Wind verhoogt de speedFactor ten opzichte van dezelfde dekking zonder wind;
7. hogere `cloudDensity` verhoogt de totale dekking monotoon;
8. hogere `cloudDarkness` verhoogt de donkerte monotoon;
9. alle layer-alpha- en darkness-waarden blijven binnen `0f..1f`;
10. achterste laag heeft een lagere snelheid dan de voorste laag;
11. ontbrekende windrichting levert een geldige standaardrichting en geen NaN;
12. windrichting wordt genormaliseerd naar `0..<360`;
13. twee identieke inputs leveren gelijke `CloudFieldParams`;
14. NaN of infinity in density, darkness of windfactor levert geen ongeldige parameters op;
15. overgang van Clear naar Cloudy verandert dekking via alpha en niet via een hard laag-aantal-sprong.

Gebruik geen afhankelijkheid van de echte huidige tijd. Geef tijd of fasewaarden als parameter door waar beweging wordt getest.

## 15. Handmatige testmatrix

### Families

| Family | Verwachting |
|---|---|
| Clear | nagenoeg geen wolken; heldere lucht |
| Partly cloudy | enkele lichte wolkenpartijen met diepte |
| Cloudy | duidelijk gelaagd, vrijwel gesloten dek |
| Rain | dichter, donkerder wolkendek dan Cloudy |
| Snow | gesloten maar zachter en lichter dek, rustige beweging |
| Sleet | dichter en iets donkerder dan Snow |
| Hail | zeer dicht en donker wolkendek |
| Fog | laag, rustig, weinig contrast |
| Haze | dunne, lichte sluier met zeer trage beweging |
| Thunder | donker, zwaar wolkendek |
| Thunderstorm | zeer donker en snel bewegend wolkendek |
| Wind | langgerekte, duidelijk snel bewegende wolken |

### Tijdscenario's

- middag met lichte wolken;
- middag met zwaar wolkendek;
- rond sunrise en sunset (wolkenkleur volgt hemel);
- nacht met donkere wolken en zichtbare maan tussen de wolken.

### Modi

- Auto weather + Auto day/night;
- geforceerde bewolkte family;
- Rotating door alle families;
- animations disabled (stabiel frame zonder beweging);
- photo background enabled en disabled;
- parallax enabled en disabled;
- wallpaper preview en werkelijk ingestelde wallpaper.

### Platforms

- Android 13 of hoger op gekoppelde telefoon (AGSL);
- actieve emulator;
- waar mogelijk Android 12 of lager voor Canvas fallback.

## 16. Screenshotset

Maak minimaal screenshots van:

1. Clear day;
2. Partly cloudy day;
3. Cloudy day;
4. Rain;
5. Thunderstorm;
6. Wind;
7. Cloudy bij sunrise of sunset;
8. nacht met wolken en maan;
9. indien ACT-002 aanwezig: Cloud naar Cloudy op ongeveer 50% overgang.

Screenshots krijgen weather family, testtijd en platform in de bestandsnaam.

## 17. Acceptatiecriteria

1. De wolken bestaan uit minimaal twee, bij voorkeur drie eigen procedurele lagen met verschillende schaal, snelheid, alpha en donkerte.
2. Cloud, Cloudy, Rain, Thunderstorm en Wind zijn visueel duidelijk van elkaar te onderscheiden.
3. Wolken blijven altijd achter de transparante locatie-afbeelding.
4. Wolkenkleur volgt dezelfde daylightfactor als hemel en celestial body.
5. Wind beinvloedt zichtbaar snelheid en richting van de wolken.
6. `cloudDensity` en `cloudDarkness` uit de scene state sturen dekking en donkerte.
7. Er is geen herkenbaar herhalend, egaal tegelpatroon.
8. Het aantal lagen verandert niet als zichtbare harde pop; overgangen verlopen via alpha.
9. Indien ACT-002 aanwezig is, mengen wolkenparameters mee in de bestaande transitions.
10. De wallpaper blijft begrensd op maximaal 30 FPS.
11. Er zijn geen objectallocaties per frame in de background weather pass.
12. Android 13+ AGSL compileert zonder fouten.
13. Canvas fallback crasht niet en toont hetzelfde aantal lagen en karakter per family.
14. De wallpaper stopt rendering wanneer hij niet zichtbaar is.
15. Er zijn geen netwerk-, GPS- of RemoveSky-calls vanuit de wallpaper toegevoegd.
16. Er worden geen secrets of exacte locaties gelogd.
17. Er zijn geen externe shaders, textures of assets gekopieerd.
18. Bestaande foto-, cache-, parallax- en rotatingfunctionaliteit blijft werken.

## 18. Definition of done

- implementatie is beperkt tot wallpaper cloud-/rendercode en gerichte tests;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- screenshotset is gemaakt;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-003-bestanden;
- commit en push gebeuren pas nadat de visuele controle is goedgekeurd.

## 19. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, RemoveSky-, iconpack- of andere niet-ACT-003-bestanden;
- neem bestaande wijzigingen in gedeelde wallpaperbestanden als uitgangspunt;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg nieuwe classes bij voorkeur in een klein eigen cloudbestand toe;
- houd wijzigingen in `MaterialLiveWallpaperService.kt` en `WallpaperWeatherEffectRenderer.kt` zo lokaal mogelijk;
- stage bestanden expliciet, nooit via `git add .`;
- meld conflicten met ACT-001 of ACT-002 in plaats van een tweede architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 20. Verwacht eindresultaat

Na ACT-003 heeft de wallpaper een lucht met meerdere wolken-massalagen die diepte en beweging tonen. Bewolkte families zijn duidelijk van elkaar te onderscheiden: Partly cloudy is licht en open, Cloudy is gelaagd en vrijwel gesloten, Rain en Thunderstorm zijn donker en zwaar, en Wind laat snelle, langgerekte wolken zien. De wolken blijven achter de locatie-afbeelding, volgen de dag/nachtkleur van de lucht, blijven begrensd op maximaal 30 FPS en werken zowel met AGSL als met de Canvas fallback, zonder externe assets en zonder netwerkverkeer vanuit de wallpaper.