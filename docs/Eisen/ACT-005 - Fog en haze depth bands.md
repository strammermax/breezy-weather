# ACT-005 - Fog en haze depth bands

## Status

- Type: implementatieopdracht
- Prioriteit: middelhoog
- Omvang: middelgroot
- Risico: middelgroot, omdat dit de foreground weather pass en de leesbaarheid van de locatie-afbeelding raakt
- Prerequisite: ACT-001 - Centrale wallpaper scene state; bij voorkeur ook ACT-002 - Vloeiende state transitions
- Doelplatform: Android 13 en hoger heeft prioriteit; Android 6 tot en met 12 behoudt een Canvas fallback

## 1. Opdracht in een zin

Bouw mist en haze op uit meerdere langzaam bewegende horizontale dieptebanden met verschillende hoogte, alpha en blur-indruk, zodat ze diepte toevoegen zonder een vlakke witte waas over de hele scene en zonder netwerkverkeer vanuit de wallpaper.

## 2. Waarom deze wijziging nodig is

De live wallpaper heeft al eigen foreground-effecten voor mist en haze, maar die ogen nog te veel als een uniforme overlay. Daardoor kan de gebruiker ervaren:

- een vlakke, witte waas die de hele scene egaal bedekt;
- onvoldoende dieptegevoel doordat mist geen lagen of horizon volgt;
- mist en haze die qua kleur en intensiteit nauwelijks van elkaar verschillen;
- een locatie-afbeelding die te sterk wordt overdekt en onherkenbaar wordt;
- effecten die niet reageren op zicht (visibility) of wind.

De gewenste ervaring is mist en haze met herkenbare diepte: lagere, dichtere banden bij de horizon en lichtere banden hoger in beeld, met behoud van de herkenbaarheid van de foto.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperWeatherEffectRenderer.kt`
   - gebruikt AGSL `RuntimeShader` vanaf Android 13;
   - gebruikt een Canvas fallback op oudere Android-versies;
   - bevat background-, foreground- en glass-passes;
   - tekent mist en haze in de foreground weather pass.

2. `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
   - beheert `WallpaperService.Engine` en de renderthread;
   - levert weather kind, windfactor en daytime aan de renderer;
   - start en stopt rendering op basis van wallpaper visibility.

3. De door ACT-001 toegevoegde scene-statebestanden.
   - lever fog/haze intensity, visibility, windfactor en daylightfactor via de scene state;
   - gebruik de namen en locatie die ACT-001 daadwerkelijk heeft geintroduceerd;
   - maak geen tweede concurrerend scene-state-model.

4. De door ACT-002 toegevoegde transition-/contributionlogica.
   - als ACT-002 is gemerged, moeten de dieptebanden de bestaande contribution/transitionAlpha respecteren;
   - mist en haze moeten via dezelfde overgangsbijdrage kunnen in- en uitfaden.

### Huidige laagvolgorde

1. hemel / achtergrondgradient;
2. zon of maan;
3. background weather pass, voornamelijk wolken;
4. transparante locatie-afbeelding;
5. bestaande Material Weather-effecten;
6. foreground weather pass: regen, sneeuw, hagel, mist, haze en bliksem;
7. glass rain drops;
8. rotating testlabel.

Deze volgorde moet intact blijven. Mist en haze horen in de foreground weather pass (laag 6).

### Huidig gedrag

Mist en haze worden momenteel als foreground-effect getekend, maar niet als afzonderlijke dieptebanden met eigen hoogte, alpha en snelheid. Daardoor is er weinig diepte en is het onderscheid tussen mist en haze beperkt.

## 4. Afbakening

### Wel uitvoeren

- meerdere horizontale dieptebanden voor mist en haze;
- verschillende hoogte, alpha, snelheid en blur-indruk per band;
- onderscheid tussen koele, dichtere mist en warmere, subtielere haze;
- koppeling aan visibility, fog/haze intensity en wind uit de scene state;
- behoud van de herkenbaarheid van de locatie-afbeelding;
- respecteren van bestaande adaptive quality en, indien aanwezig, ACT-002-contribution;
- AGSL- en Canvas-implementatie;
- behoud van een werkende fallback;
- unit tests voor pure band- en intensiteitslogica;
- handmatige/emulatortests voor visuele scenario's.

### Niet uitvoeren

- geen nieuwe weather families;
- geen nieuwe wolkenvormen: dat is ACT-003;
- geen nieuwe snow/hail particle pool: dat is ACT-004;
- geen nieuwe rain-on-glasskwaliteit: dat is ACT-006;
- geen aparte gebruikersinstelling voor mist-intensiteit in deze opdracht: dat hoort bij ACT-007 quality profiles;
- geen automatische foto-download: dat is ACT-010;
- geen wijziging aan Meteo-, GPS- of RemoveSky-clients;
- geen OpenGL-migratie;
- geen UI-herontwerp;
- geen brede refactor van Breezy Weather;
- geen externe shader-, texture- of asset-bestanden uit YoWindow kopieren.

## 5. Architectuurregel

De wallpaper is een read-only consumer van de centrale lokale datalaag.

Tijdens deze opdracht mag de renderer:

- fog/haze intensity, visibility, windfactor en daylightfactor uit de scene state lezen;
- het actieve quality profile uit de scene state of bestaande adaptive quality lezen;
- lokale wallpaperconfiguratie lezen.

De wallpaper mag niet:

- zelf GPS starten;
- een weather provider aanroepen;
- RemoveSky aanroepen;
- HTTP-requests uitvoeren;
- een eigen tweede weather cache introduceren;
- per frame nieuwe Paint-, Path-, Rect-, Shader- of Bitmap-objecten aanmaken.

## 6. Prerequisite ACT-001 en ACT-002

ACT-005 moet voortbouwen op de centrale immutable scene state uit ACT-001. Controleer voor aanvang welke class en velden ACT-001 daadwerkelijk heeft toegevoegd.

De state moet voor ACT-005 minimaal kunnen leveren:

- fog intensity en haze intensity als genormaliseerde `0f..1f` factoren;
- visibility of voldoende informatie om dichtheid af te leiden;
- windfactor en bij voorkeur windrichting;
- daylightfactor voor band-kleur en alpha;
- quality profile of voldoende informatie voor de bestaande adaptive quality.

Als ACT-002 al is gemerged, moeten de banden de bestaande contribution/transitionAlpha gebruiken. Is ACT-002 nog niet aanwezig, dan moeten de banden toch een globale intensity-/alphafactor accepteren zodat ACT-002 die later kan koppelen.

Als ACT-001 nog niet is gemerged, stop dan en rapporteer deze dependency. Voeg niet stilzwijgend een tweede scene-state-architectuur toe.

## 7. Gewenst depth band-model

Introduceer een kleine set vooraf gedefinieerde dieptebanden die bij initialisatie worden gereserveerd en daarna niet opnieuw worden gealloceerd.

Conceptueel:

```kotlin
data class FogBand(
    val verticalCenter: Float,   // 0f = boven, 1f = onder/horizon
    val height: Float,           // relatieve hoogte van de band
    val baseAlpha: Float,        // basisdoorzichtigheid
    val speedFactor: Float,      // horizontale driftsnelheid
    val blurStrength: Float,     // blur-indruk
    var offset: Float,           // huidige horizontale offset, hergebruikt per frame
)
```

Aanbevolen 3 tot 5 banden:

- lagere banden bij de horizon zijn dichter, hoger en langzamer;
- hogere banden zijn lichter, dunner en iets sneller;
- elke band drift horizontaal met een eigen snelheid;
- de banden mengen samen tot een diepte-indruk in plaats van een vlakke laag.

Volg voor `verticalCenter`/snelheid hetzelfde depth-concept als `CloudLayer.depth` uit ACT-003 (0f = ver/boven, 1f = dichtbij/horizon) en bouw `offset` per frame bij via windrichting plus een monotone timer, nooit via een per-frame allocatie of `Random`-aanroep. Zie ook `docs/ACT-000 - Conventies en gedeelde patronen.md`.

## 8. Mist versus haze

| Aspect | Mist (fog) | Haze |
|---|---|---|
| Kleur | koeler, neutraal wit/grijs | warmer, licht geel/bruin |
| Dichtheid | hoger, vooral bij horizon | lager, gelijkmatiger |
| Hoogteverdeling | sterk gelaagd, dicht onderaan | subtiel, hoger reikend |
| Effect op foto | foto vager, vooral onderin | lichte sluier, foto blijft duidelijk |
| Snelheid | langzaam | langzaam tot zeer langzaam |

Mist is lokaal dichter en koeler; haze is warmer en subtieler. De locatie-afbeelding moet in beide gevallen herkenbaar blijven.

## 9. Wind en beweging

- windsnelheid schaalt de horizontale drift van de banden;
- windrichting bepaalt de richting van de drift;
- gebruik continue, langzame beweging zonder zichtbare herhaling;
- voorkom dat een windverandering een abrupte sprong in de offset veroorzaakt; pas de snelheid geleidelijk aan;
- gebruik bij voorkeur windrichting via de kortste hoek, consistent met ACT-002.

## 10. Rendererstrategie

### Aanbevolen oplossing

- reserveer de banden en hun Paint/Shader-objecten bij initialisatie;
- werk per frame alleen de offset bij;
- teken de banden van achter naar voren binnen de foreground pass;
- vermijd een full-screen overlay met uniforme alpha;
- gebruik geen offscreen full-screen bitmap per frame.

### AGSL

- benader de banden procedureel via verticale gradient-maskers en lichte noise;
- vermenigvuldig de uiteindelijke alpha met contribution/transitionAlpha en intensity:

```text
effectiveAlpha = bandAlpha * sceneIntensity * contribution
```

- laat de horizontale offset en tijd doorlopen, ook tijdens een overgang;
- gebruik geen harde `if (intensity > 0.5)`.

### Canvas fallback

- gebruik bestaande Paint-instanties met een `LinearGradient`-shader per band;
- vermenigvuldig de Paint-alpha met contribution en intensity;
- maak geen nieuwe Paint, Shader, Path, Rect of Bitmap per frame;
- gebruik een lichte blur-indruk via gradient-randen in plaats van dure blurfilters per frame.

## 11. Leesbaarheid van de foto

- de banden mogen de locatie-afbeelding niet onleesbaar maken;
- begrens de gecombineerde maximale alpha zodat de foto herkenbaar blijft;
- leg de dichtste mist bij de horizon, niet over het hele beeld;
- test dat bij maximale fog intensity de foto nog herkenbaar is;
- vermijd een full-screen witte overlay.

## 12. Lifecycle

### Visibility

Wanneer `onVisibilityChanged(false)` wordt aangeroepen:

- stop framecallbacks zoals nu;
- behoud de gereserveerde banden en Paint/Shader-objecten;
- voer geen offset-updates uit terwijl onzichtbaar.

Wanneer de wallpaper opnieuw zichtbaar wordt:

- hervat de beweging vanaf de bestaande offsets;
- clamp de eerste delta na hervatten om een grote sprong te voorkomen.

### Surface recreation

Bij surface resize of recreation:

- herbereken band-posities en hoogtes op basis van de nieuwe bounds;
- herbouw gradient-shaders alleen als de afmetingen wijzigen;
- behoud de huidige offsets waar mogelijk.

### Animaties uit

Als animaties uitgeschakeld zijn:

- plan geen offset-updateframes;
- teken een enkel statisch representatief frame met de juiste intensiteit.

## 13. Adaptive quality en quality profiles

- koppel het aantal banden en de blur-indruk aan het quality profile;
- behoud de bestaande adaptive quality op basis van frametijd;
- gebruik hysterese zodat het aantal banden niet snel heen en weer schakelt;
- definieer indicatieve budgetten, die in ACT-007 verder worden vastgelegd:

| Profiel | Indicatief aantal banden | Blur-indruk |
|---|---:|---|
| Battery saver | laag (2) | minimaal |
| Balanced | middel (3-4) | matig |
| High | hoog (5) | rijker |

De exacte aantallen moeten op echte toestellen worden gemeten en gemotiveerd. Als ACT-007 nog niet is gemerged, gebruik lokaal `LOW`/`BALANCED`/`HIGH` op basis van de bestaande adaptive-quality-meting; ACT-007 consolideert dit later in `QualityBudget` zonder gedragsverandering.

## 14. Data-gedrag

ACT-005 is geen nieuwe refreshoplossing. De banden reageren uitsluitend op de lokale scene state:

- bij wallpaper visibility/restart;
- na een lokale data-invalidation;
- na configwijziging;
- tijdens rotating testmodus;
- via een toekomstige centrale wallpaper-update-notificatie.

Voeg geen periodieke netwerkpoll of eigen datacache toe.

## 15. Performance-eisen

- maximaal 30 FPS;
- geen objectallocaties in de fog/haze-update- en draw-paden;
- geen nieuwe Paint, Shader, Path, Rect of Bitmap per frame;
- banden en shaders worden eenmaal gereserveerd en hergebruikt;
- uniforms en gradients alleen bijwerken als nodig;
- bestaande adaptive quality behouden;
- update stopt wanneer de wallpaper onzichtbaar is;
- geen logging per frame.

Meet minimaal:

- stabiele frametijd bij lichte mist;
- frametijd bij dichte mist op High;
- frametijd bij haze;
- Fog naar Clear en Haze naar Rain tijdens een transition;
- Canvas fallback op een pre-Android 13 emulator indien beschikbaar.

## 16. Logging en privacy

Alle nieuwe logging is debug-only en niet per frame.

Toegestane informatie:

- gekozen quality profile;
- aantal actieve banden;
- fog/haze intensity bucket;
- fallback naar Canvas door shaderfout.

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- lokale fotopaden als die persoonsgegevens kunnen bevatten.

## 17. Voorgestelde implementatiestappen

1. Controleer en documenteer de ACT-001 scene-state API en, indien aanwezig, de ACT-002 contribution.
2. Voeg een pure band-definitie en offset-update toe zonder Android-afhankelijkheid in de logica.
3. Definieer 3 tot 5 banden met hoogte, alpha, snelheid en blur-indruk.
4. Reserveer Paint/Shader-objecten eenmalig bij initialisatie.
5. Koppel band-parameters aan fog/haze intensity, visibility en wind.
6. Implementeer het kleur- en dichtheidsonderscheid tussen mist en haze.
7. Voeg de AGSL-draw met contribution/intensity toe.
8. Voeg de Canvas-fallbackdraw met gradient-Paint toe.
9. Begrens de gecombineerde alpha voor leesbaarheid van de foto.
10. Koppel het aantal banden aan quality profile en adaptive quality met hysterese.
11. Handel visibility, surface recreation en animations disabled af.
12. Voeg pure unit tests toe.
13. Build debug en release.
14. Test op emulator en gekoppelde telefoon.
15. Maak screenshots van de vereiste scenario's.
16. Controleer `git diff` op scope en secrets.

## 18. Unit tests

Voeg tests toe voor pure logica, zonder echte Surface of WallpaperService waar dat niet nodig is.

Minimale testgevallen:

1. het aantal banden komt overeen met het quality profile;
2. de offset loopt continu en wraparound veroorzaakt geen sprong;
3. lagere banden hebben hogere alpha dan hogere banden;
4. fog gebruikt koelere kleur dan haze;
5. haze gebruikt lagere maximale alpha dan fog;
6. windsnelheid schaalt de driftsnelheid;
7. windrichting bepaalt het teken van de drift;
8. windrichting 350 naar 10 graden gebruikt de korte route;
9. intensity 0 levert geen zichtbare banden;
10. de gecombineerde alpha blijft onder de leesbaarheidsgrens;
11. contribution/transitionAlpha schaalt de effectieve alpha lineair;
12. een grotere delta na hervatten wordt geclamped;
13. quality profile bepaalt het aantal banden met hysterese;
14. snel uit- en weer aanzetten van fog/haze (intensity 0 -> >0 -> 0 binnen een korte transitie) levert geen zichtbare pop of harde alpha-sprong op, consistent met de ACT-002-contribution.

Gebruik een injecteerbare clock of geef delta's als parameters door. Tests mogen niet afhankelijk zijn van de echte huidige tijd of een echte Surface.

## 19. Handmatige testmatrix

### Scenario's

| Scenario | Verwachting |
|---|---|
| Lichte mist | dunne, lage band bij de horizon, foto goed leesbaar |
| Dichte mist | meerdere koele banden, dichter onderin, foto herkenbaar |
| Lichte haze | warme, subtiele sluier over de scene |
| Dichte haze | warmere sluier, foto blijft duidelijk |
| Mist met wind | banden driften zichtbaar maar rustig |
| Fog naar Clear | mist lost geleidelijk op zonder pop |
| Haze naar Rain | warme haze verdwijnt terwijl stormlaag opkomt |

### Modi

- Auto weather + Auto day/night;
- geforceerde Fog;
- geforceerde Haze;
- Rotating;
- animations disabled;
- verschillende quality profiles;
- photo background enabled/disabled;
- wallpaper preview en werkelijk ingestelde wallpaper.

### Platforms

- Android 13 of hoger op gekoppelde telefoon;
- actieve emulator;
- waar mogelijk Android 12 of lager voor Canvas fallback.

## 20. Screenshotset

Maak minimaal screenshots van:

1. lichte mist overdag;
2. dichte mist overdag;
3. mist bij nacht;
4. lichte haze;
5. dichte haze;
6. mist met wind;
7. Fog naar Clear op ongeveer 50%;
8. Canvas fallback met mist indien beschikbaar.

Screenshots moeten weather family, quality profile en platform in de bestandsnaam krijgen.

## 21. Acceptatiecriteria

1. Mist en haze bestaan uit meerdere horizontale dieptebanden.
2. Banden verschillen in hoogte, alpha, snelheid en blur-indruk.
3. Mist is lokaal dichter en koeler dan haze.
4. Haze is warmer en subtieler dan mist.
5. De locatie-afbeelding blijft herkenbaar, ook bij maximale intensity.
6. Er is geen full-screen witte overlay.
7. Windrichting en windsnelheid beinvloeden de drift.
8. Het effect schaalt met fog/haze intensity en visibility.
9. De fallback via Canvas blijft beschikbaar en crasht niet.
10. AGSL compileert zonder fouten op Android 13+.
11. De bestaande adaptive quality blijft werken, met hysterese.
12. Indien ACT-002 aanwezig is, faden mist en haze via de contribution mee in en uit.
13. De wallpaper blijft begrensd op maximaal 30 FPS.
14. De wallpaper stopt updates wanneer hij niet zichtbaar is.
15. Er zijn geen objectallocaties in de fog/haze-update- en draw-paden.
16. Er zijn geen netwerk-, GPS- of RemoveSky-calls vanuit de wallpaper toegevoegd.
17. Er worden geen secrets of exacte locaties gelogd.
18. Bestaande regen-, sneeuw-, hagel-, wolken- en rain-on-glassfunctionaliteit blijft werken.
19. De laagvolgorde blijft intact en mist/haze blijven in de foreground weather pass.

## 22. Definition of done

- implementatie is beperkt tot de wallpaper fog/haze-/rendercode en gerichte tests;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- screenshotset is gemaakt;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-005-bestanden;
- commit en push gebeuren pas nadat de visuele controle is goedgekeurd.

## 23. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, RemoveSky-, iconpack- of andere niet- ACT-005-bestanden;
- neem bestaande wijzigingen in gedeelde wallpaperbestanden als uitgangspunt;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg nieuwe classes bij voorkeur in een klein eigen fog/haze-bestand toe;
- houd wijzigingen in `WallpaperWeatherEffectRenderer.kt` en `MaterialLiveWallpaperService.kt` zo lokaal mogelijk;
- stage bestanden expliciet, nooit via `git add .`;
- meld conflicten met ACT-001 of ACT-002 in plaats van een tweede architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 24. Verwacht eindresultaat

Na ACT-005 voegen mist en haze diepte toe in plaats van een vlakke waas. Lagere, dichtere banden hangen bij de horizon en lichtere banden zweven hoger in beeld, met koele mist en warme haze. De banden driften rustig met de wind, de locatie-afbeelding blijft herkenbaar, en het effect schaalt voorspelbaar met intensity en quality profile. De renderer blijft compact, lokaal, maximaal 30 FPS en compatibel met zowel AGSL als Canvas.