# ACT-006 - Rain-on-glass kwaliteitslagen

## Status

- Type: implementatieopdracht
- Prioriteit: middelhoog
- Omvang: middelgroot
- Risico: middelgroot, omdat dit de bovenste glass-pass en de batterijbelasting van de wallpaper raakt
- Prerequisite: ACT-001 - Centrale wallpaper scene state; bij voorkeur ook ACT-002 - Vloeiende state transitions
- Doelplatform: Android 13 en hoger heeft prioriteit; Android 6 tot en met 12 behoudt een Canvas fallback

## 1. Opdracht in een zin

Definieer Low-, Balanced- en High-varianten voor druppelaantal, trails, highlights en refractie-indruk van rain-on-glass, zodat het effect schaalbaar en geloofwaardig blijft zonder een kunstmatige uitstraling en zonder netwerkverkeer vanuit de wallpaper.

## 2. Waarom deze wijziging nodig is

De live wallpaper heeft al een glass-rain-overlay als bovenste laag, maar het effect heeft nog geen expliciete kwaliteitslagen en kan kunstmatig ogen. Daardoor kan de gebruiker of het toestel ervaren:

- een vast druppelaantal dat op zwakke toestellen te zwaar of op sterke toestellen te schaars is;
- druppels die er te uniform of te synthetisch uitzien;
- ontbrekende trails of highlights waardoor diepte mist;
- een effect dat ook verschijnt bij weersituaties zonder regen;
- onvoldoende afstemming op het quality profile.

De gewenste ervaring is een geloofwaardig rain-on-glass-effect dat per kwaliteitsniveau voorspelbaar afschaalt en alleen bij relevante weersituaties verschijnt.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperWeatherEffectRenderer.kt`
   - gebruikt AGSL `RuntimeShader` vanaf Android 13;
   - gebruikt een Canvas fallback op oudere Android-versies;
   - bevat background-, foreground- en glass-passes;
   - tekent rain-on-glass via `drawGlassRainDrops()` als bovenste laag.

2. `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
   - beheert `WallpaperService.Engine` en de renderthread;
   - levert weather kind, windfactor en daytime aan de renderer;
   - start en stopt rendering op basis van wallpaper visibility.

3. De door ACT-001 toegevoegde scene-statebestanden.
   - lever precipitation type, glass-rain intensity, windfactor en quality profile via de scene state;
   - gebruik de namen en locatie die ACT-001 daadwerkelijk heeft geintroduceerd;
   - maak geen tweede concurrerend scene-state-model.

4. De door ACT-002 toegevoegde transition-/contributionlogica.
   - als ACT-002 is gemerged, moeten de druppels de bestaande contribution/transitionAlpha respecteren;
   - glass-rain moet via dezelfde overgangsbijdrage kunnen in- en uitfaden.

### Huidige laagvolgorde

1. hemel / achtergrondgradient;
2. zon of maan;
3. background weather pass, voornamelijk wolken;
4. transparante locatie-afbeelding;
5. bestaande Material Weather-effecten;
6. foreground weather pass: regen, sneeuw, hagel, mist, haze en bliksem;
7. glass rain drops;
8. rotating testlabel.

Deze volgorde moet intact blijven. Rain-on-glass is laag 7, de bovenste effectlaag.

### Huidig gedrag

`drawGlassRainDrops()` tekent druppels op het glas zonder expliciete Low/Balanced/High-profielen en zonder duidelijke scheiding tussen statische en zakkende druppels. Trails, highlights en refractie zijn beperkt of uniform.

## 4. Afbakening

### Wel uitvoeren

- Low-, Balanced- en High-kwaliteitsvarianten voor rain-on-glass;
- gedifferentieerd druppelaantal per profiel;
- statische en zakkende druppels met verschillend gedrag;
- trails achter zakkende druppels;
- highlights en een refractie-indruk;
- alleen tonen bij Rain, Sleet en Thunderstorm;
- koppeling aan glass-rain intensity en wind uit de scene state;
- respecteren van bestaande adaptive quality en, indien aanwezig, ACT-002-contribution;
- AGSL- en Canvas-implementatie;
- behoud van een acceptabele Canvas fallback;
- unit tests voor pure druppel- en profiellogica;
- handmatige/emulatortests voor visuele scenario's.

### Niet uitvoeren

- geen nieuwe weather families;
- geen nieuwe wolkenvormen: dat is ACT-003;
- geen nieuwe snow/hail particle pool: dat is ACT-004;
- geen nieuwe fog/haze-uitwerking: dat is ACT-005;
- geen volledige quality profile-architectuur voor de hele renderer: dat is ACT-007;
- geen automatische foto-download: dat is ACT-010;
- geen wijziging aan Meteo-, GPS- of RemoveSky-clients;
- geen OpenGL-migratie;
- geen UI-herontwerp;
- geen brede refactor van Breezy Weather;
- geen externe shader-, texture- of asset-bestanden uit YoWindow kopieren.

## 5. Architectuurregel

De wallpaper is een read-only consumer van de centrale lokale datalaag.

Tijdens deze opdracht mag de renderer:

- precipitation type, glass-rain intensity, windfactor en quality profile uit de scene state lezen;
- de bestaande adaptive quality lezen;
- lokale wallpaperconfiguratie lezen.

De wallpaper mag niet:

- zelf GPS starten;
- een weather provider aanroepen;
- RemoveSky aanroepen;
- HTTP-requests uitvoeren;
- een eigen tweede weather cache introduceren;
- per frame nieuwe druppel-, Paint-, Path-, Rect-, Random- of Bitmap-objecten aanmaken.

## 6. Prerequisite ACT-001 en ACT-002

ACT-006 moet voortbouwen op de centrale immutable scene state uit ACT-001. Controleer voor aanvang welke class en velden ACT-001 daadwerkelijk heeft toegevoegd.

De state moet voor ACT-006 minimaal kunnen leveren:

- precipitation type, minimaal het onderscheid tussen Rain, Sleet en Thunderstorm;
- glass-rain intensity als genormaliseerde `0f..1f` factor;
- windfactor en bij voorkeur windrichting;
- quality profile of voldoende informatie voor de bestaande adaptive quality.

Als ACT-002 al is gemerged, moeten de druppels de bestaande contribution/transitionAlpha gebruiken. Is ACT-002 nog niet aanwezig, dan moet de glass-rainlaag toch een globale intensity-/alphafactor accepteren zodat ACT-002 die later kan koppelen.

Als ACT-001 nog niet is gemerged, stop dan en rapporteer deze dependency. Voeg niet stilzwijgend een tweede scene-state-architectuur toe.

## 7. Gewenst kwaliteitslagen-model

Definieer drie expliciete profielen voor rain-on-glass. Reserveer de buffers en Paint-objecten bij initialisatie en alloceer niet per frame.

| Profiel | Druppelaantal | Trails | Highlights | Refractie-indruk |
|---|---:|---|---|---|
| Low | laag | minimaal of geen | basaal | geen of zeer licht |
| Balanced | middel | korte trails | duidelijke highlight | lichte refractie |
| High | hoog | langere trails | rijke highlights | duidelijke refractie |

Conceptueel:

```kotlin
data class GlassRainProfile(
    val maxDrops: Int,
    val trailLength: Float,
    val highlightStrength: Float,
    val refractionStrength: Float,
)
```

De druppels zelf gebruiken een vaste buffer (structure-of-arrays) met positie, grootte, snelheid, type (statisch of zakkend) en alpha.

Volg voor de profiel-/buffergedachte hetzelfde patroon als `CloudFieldFactory` uit ACT-003: een pure, testbare `GlassRainProfile`/druppel-parameterlaag, los van Canvas/AGSL. Zie ook `docs/ACT-000 - Conventies en gedeelde patronen.md`.

## 8. Statische versus zakkende druppels

| Aspect | Statische druppel | Zakkende druppel |
|---|---|---|
| Beweging | blijft staan, trilt licht | zakt naar beneden, versnelt |
| Trail | geen | laat een trail/spoor achter |
| Levensduur | langer, kan samensmelten | korter, recyclet na onderrand |
| Grootte | gevarieerd, vaak klein | groeit voor het zakken |

Een deel van de druppels is statisch en blijft op het glas staan; een ander deel zakt en laat een trail achter. De verhouding hangt af van glass-rain intensity en wind.

Indicatieve verhouding statisch/zakkend per intensity, te verfijnen op echte toestellen:

| Glass-rain intensity | Statisch | Zakkend |
|---|---:|---:|
| Licht (Sleet, lichte regen) | ongeveer 70% | ongeveer 30% |
| Zwaar (Thunderstorm, zware regen) | ongeveer 40% | ongeveer 60% |

## 9. Wind en beweging

- windsnelheid en -richting beinvloeden de hoek van zakkende trails;
- bij meer wind lopen trails schuiner;
- gebruik bij voorkeur windrichting via de kortste hoek, consistent met ACT-002;
- voorkom dat een windverandering een abrupte herpositionering veroorzaakt; pas geleidelijk aan.

## 10. Rendererstrategie

### Aanbevolen oplossing

- reserveer druppelbuffers en Paint-objecten bij initialisatie;
- update en teken alleen actieve druppels;
- recycle zakkende druppels die de onderrand bereiken;
- gebruik geen `Random`-allocatie per frame; gebruik een herbruikbare, geseede randombron;
- koppel het profiel aan het quality profile en de bestaande adaptive quality.

### AGSL

- benader druppels, trails, highlights en refractie procedureel in de shader;
- de refractie-indruk mag de onderliggende foto licht vervormen, niet volledig herschilderen;
- vermenigvuldig de uiteindelijke alpha met contribution/transitionAlpha en intensity:

```text
effectiveAlpha = dropAlpha * sceneIntensity * contribution
```

- laat geometrie, positie en tijd doorlopen, ook tijdens een overgang;
- gebruik geen harde `if (intensity > 0.5)`.

### Canvas fallback

- gebruik bestaande Paint-instanties;
- teken druppels als cirkels/ovalen met een lichte highlight via gradient;
- refractie mag op Canvas worden benaderd of weggelaten; de fallback moet acceptabel blijven;
- maak geen nieuwe Paint, Path, Rect, Bitmap of Random per frame;
- gebruik `saveLayerAlpha()` alleen na meting.

## 11. Tonen alleen bij relevante weersituaties

- druppels verschijnen alleen bij Rain, Sleet en Thunderstorm;
- bij andere weather families is glass-rain intensity 0 en worden geen druppels getekend;
- bij een overgang (ACT-002) faden druppels via de contribution in en uit;
- voorkom een pop bij het starten of stoppen van regen door bestaande druppels te laten uitlopen;
- bij een Rain -> Clear-transitie blijven bestaande druppels nog even op het glas staan en lopen ze uit terwijl de contribution naar 0 gaat, in plaats van direct te worden verwijderd.

## 12. Lifecycle

### Visibility

Wanneer `onVisibilityChanged(false)` wordt aangeroepen:

- stop framecallbacks zoals nu;
- behoud de gereserveerde druppelbuffers en Paint-objecten;
- voer geen druppel-updates uit terwijl onzichtbaar.

Wanneer de wallpaper opnieuw zichtbaar wordt:

- hervat de update vanaf de bestaande druppel state;
- clamp de eerste delta na hervatten om een grote sprong te voorkomen.

### Surface recreation

Bij surface resize of recreation:

- herbereken bounds en recyclegrenzen;
- herverdeel druppelposities binnen de nieuwe bounds zonder de buffers opnieuw te alloceren als de capaciteit gelijk blijft.

### Animaties uit

Als animaties uitgeschakeld zijn:

- plan geen druppel-updateframes;
- teken een enkel statisch representatief frame of geen druppels, consistent met het bestaande gedrag.

## 13. Adaptive quality en quality profiles

- koppel het profiel aan het quality profile en de bestaande adaptive quality;
- gebruik hysterese zodat het profiel niet snel heen en weer schakelt;
- het profiel bepaalt druppelaantal, trails, highlights en refractie-indruk;
- de exacte budgetten worden in ACT-007 verder vastgelegd en op echte toestellen gemeten.

## 14. Data-gedrag

ACT-006 is geen nieuwe refreshoplossing. De glass-rainlaag reageert uitsluitend op de lokale scene state:

- bij wallpaper visibility/restart;
- na een lokale data-invalidation;
- na configwijziging;
- tijdens rotating testmodus;
- via een toekomstige centrale wallpaper-update-notificatie.

Voeg geen periodieke netwerkpoll of eigen datacache toe.

## 15. Performance-eisen

- maximaal 30 FPS;
- geen objectallocaties in de glass-rain-update- en draw-paden;
- geen nieuwe Paint, Path, Rect, Bitmap of Random per frame;
- vaste buffers worden eenmaal gereserveerd en hergebruikt;
- uniforms alleen bijwerken als nodig;
- bestaande adaptive quality behouden;
- update stopt wanneer de wallpaper onzichtbaar is;
- geen logging per frame.

Meet minimaal:

- stabiele frametijd bij Balanced;
- frametijd bij High met veel druppels en refractie;
- frametijd bij Low;
- start/stop regen tijdens een transition;
- Canvas fallback op een pre-Android 13 emulator indien beschikbaar.

## 16. Logging en privacy

Alle nieuwe logging is debug-only en niet per frame.

Toegestane informatie:

- gekozen quality profile en glass-rainprofiel;
- gereserveerd en actief aantal druppels;
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
2. Voeg een pure druppel-pool en profielmodel toe zonder Android-afhankelijkheid in de logica.
3. Definieer de Low-, Balanced- en High-profielen.
4. Reserveer buffers en Paint-objecten eenmalig bij initialisatie.
5. Implementeer statische en zakkende druppels met trails.
6. Voeg highlights en een refractie-indruk toe.
7. Koppel het effect aan glass-rain intensity, precipitation type en wind.
8. Voeg de AGSL-draw met contribution/intensity toe.
9. Voeg de Canvas-fallbackdraw toe met acceptabele benadering.
10. Toon druppels alleen bij Rain, Sleet en Thunderstorm.
11. Koppel het profiel aan quality profile en adaptive quality met hysterese.
12. Handel visibility, surface recreation en animations disabled af.
13. Voeg pure unit tests toe.
14. Build debug en release.
15. Test op emulator en gekoppelde telefoon.
16. Maak screenshots van de vereiste scenario's.
17. Controleer `git diff` op scope en secrets.

## 18. Unit tests

Voeg tests toe voor pure logica, zonder echte Surface of WallpaperService waar dat niet nodig is.

Minimale testgevallen:

1. elk profiel heeft een verwacht maximaal druppelaantal;
2. Low heeft minder druppels dan Balanced en High;
3. een zakkende druppel die de onderrand bereikt wordt gerecycled;
4. recycling kent nieuwe eigenschappen toe zonder nieuwe objectallocatie;
5. statische en zakkende druppels hebben verschillend gedrag;
6. zakkende druppels genereren trails, statische niet;
7. windrichting beinvloedt de trail-hoek;
8. windrichting 350 naar 10 graden gebruikt de korte route;
9. druppels verschijnen alleen bij Rain, Sleet en Thunderstorm;
10. intensity 0 laat druppels uitlopen in plaats van direct te wissen;
11. contribution/transitionAlpha schaalt de effectieve alpha lineair;
12. een grotere delta na hervatten wordt geclamped;
13. quality profile bepaalt het profiel met hysterese;
14. een profielwissel tijdens actieve regen (bijvoorbeeld door ACT-007-degradatie van High naar Balanced) verandert het druppelaantal geleidelijk, zonder een abrupte pop van alle druppels in een frame.

Gebruik een injecteerbare clock of geef delta's als parameters door. Tests mogen niet afhankelijk zijn van de echte huidige tijd of een echte Surface.

## 19. Handmatige testmatrix

### Scenario's

| Scenario | Verwachting |
|---|---|
| Lichte regen Balanced | weinig druppels, enkele trails |
| Zware regen High | veel druppels, lange trails, refractie |
| Regen Low | weinig druppels, minimale trails |
| Thunderstorm | dichte druppels, sterk effect |
| Sleet | gemengd, druppels aanwezig |
| Regen met wind | trails lopen schuiner |
| Start regen | druppels bouwen geleidelijk op |
| Stop regen | druppels lopen uit zonder pop |
| Clear/Snow | geen druppels |

### Modi

- Auto weather + Auto day/night;
- geforceerde Rain;
- geforceerde Thunderstorm;
- geforceerde Sleet;
- Rotating;
- animations disabled;
- Low, Balanced en High profiel;
- wallpaper preview en werkelijk ingestelde wallpaper.

### Platforms

- Android 13 of hoger op gekoppelde telefoon;
- actieve emulator;
- waar mogelijk Android 12 of lager voor Canvas fallback.

## 20. Screenshotset

Maak minimaal screenshots van:

1. regen Low;
2. regen Balanced;
3. regen High;
4. thunderstorm met druppels;
5. regen met wind en schuine trails;
6. start regen op ongeveer 50%;
7. stop regen op ongeveer 50%;
8. Canvas fallback met druppels indien beschikbaar.

Screenshots moeten weather family, profiel en platform in de bestandsnaam krijgen.

## 21. Acceptatiecriteria

1. Er zijn expliciete Low-, Balanced- en High-varianten voor rain-on-glass.
2. Het druppelaantal verschilt aantoonbaar per profiel.
3. Statische en zakkende druppels verschillen in gedrag.
4. Zakkende druppels tonen trails; statische niet.
5. Er zijn highlights en een refractie-indruk.
6. Druppels verschijnen alleen bij Rain, Sleet en Thunderstorm.
7. Wind beinvloedt de trail-hoek.
8. Lagere profielen verminderen dichtheid en effecten.
9. De Canvas fallback blijft acceptabel en crasht niet.
10. AGSL compileert zonder fouten op Android 13+.
11. De bestaande adaptive quality blijft werken, met hysterese.
12. Indien ACT-002 aanwezig is, faden druppels via de contribution mee in en uit.
13. De wallpaper blijft begrensd op maximaal 30 FPS.
14. De wallpaper stopt updates wanneer hij niet zichtbaar is.
15. Er zijn geen objectallocaties in de glass-rain-update- en draw-paden.
16. Er zijn geen netwerk-, GPS- of RemoveSky-calls vanuit de wallpaper toegevoegd.
17. Er worden geen secrets of exacte locaties gelogd.
18. Bestaande regen-, sneeuw-, hagel-, mist- en wolkenfunctionaliteit blijft werken.
19. De laagvolgorde blijft intact en rain-on-glass blijft de bovenste effectlaag.

## 22. Definition of done

- implementatie is beperkt tot de wallpaper glass-rain-/rendercode en gerichte tests;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- screenshotset is gemaakt;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-006-bestanden;
- commit en push gebeuren pas nadat de visuele controle is goedgekeurd.

## 23. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, RemoveSky-, iconpack- of andere niet- ACT-006-bestanden;
- neem bestaande wijzigingen in gedeelde wallpaperbestanden als uitgangspunt;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg nieuwe classes bij voorkeur in een klein eigen glass-rain-bestand toe;
- houd wijzigingen in `WallpaperWeatherEffectRenderer.kt` en `MaterialLiveWallpaperService.kt` zo lokaal mogelijk;
- stage bestanden expliciet, nooit via `git add .`;
- meld conflicten met ACT-001 of ACT-002 in plaats van een tweede architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 24. Verwacht eindresultaat

Na ACT-006 oogt rain-on-glass geloofwaardiger en schaalt het voorspelbaar. Statische druppels staan op het glas en zakkende druppels laten trails achter, met highlights en een lichte refractie. Het effect verschijnt alleen bij regen, sleet en onweer, schaalt netjes per Low-, Balanced- en High-profiel, faedt mee in overgangen en blijft begrensd op maximaal 30 FPS en compatibel met zowel AGSL als Canvas.