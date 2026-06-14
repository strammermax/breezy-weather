# ACT-002 - Vloeiende state transitions

## Status

- Type: implementatieopdracht
- Prioriteit: hoog
- Omvang: middelgroot
- Risico: middelgroot, omdat dit de centrale wallpaper-renderlus raakt
- Prerequisite: ACT-001 - Centrale wallpaper scene state
- Doelplatform: Android 13 en hoger heeft prioriteit; Android 6 tot en met 12 behoudt een Canvas fallback

## 1. Opdracht in een zin

Laat de live wallpaper geleidelijk mengen van de huidige visuele toestand naar een nieuwe weer- of dag/nachttoestand, zonder abrupte kleur-, wolken-, neerslag-, celestial- of fototintsprongen en zonder netwerkverkeer vanuit de wallpaper.

## 2. Waarom deze wijziging nodig is

De live wallpaper heeft al een duidelijke laagopbouw en een gedeeltelijk vloeiende dag/nachtcyclus. Een verandering van weather family vervangt de effectrenderer momenteel echter direct. Ook enkele dag/nachteigenschappen zijn nog binair.

De gebruiker ziet daardoor mogelijk:

- wolken die in een frame verdwijnen of verschijnen;
- regen, sneeuw, hagel, mist of haze die abrupt start of stopt;
- een directe omschakeling tussen lichte en donkere effectkleuren;
- een abrupte nachtkleuring van de transparante locatie-afbeelding;
- een nieuwe particleverdeling die plotseling op het scherm staat;
- verschillende overgangslogica voor hemel, zon/maan, foto en weereffecten.

De gewenste ervaring is een scene die doorloopt en geleidelijk van karakter verandert.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
   - beheert `WallpaperService.Engine` en de renderthread;
   - leest lokale locatie- en weerdata;
   - bepaalt weather kind en day/night;
   - tekent hemel, celestial body, wolken, locatie-afbeelding en voorgrondeffecten;
   - start en stopt rendering op basis van wallpaper visibility.

2. `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperWeatherEffectRenderer.kt`
   - gebruikt AGSL `RuntimeShader` vanaf Android 13;
   - gebruikt een Canvas fallback op oudere Android-versies;
   - bevat background-, foreground- en glass-passes;
   - weather kind, daytime en windfactor zijn momenteel constructorwaarden;
   - ondersteunt adaptieve sneeuw- en hagelkwaliteit.

3. `app/src/main/kotlin/org/breezyweather/wallpaper/LiveWallpaperConfigManager.kt`
   - bewaart geforceerde of automatische weather/day-night-keuzes;
   - bevat geen overgangsinstellingen.

4. `app/src/main/kotlin/org/breezyweather/wallpaper/LiveWallpaperConfigActivity.kt`
   - bevat de wallpaperinstellingen en rotating testmodus;
   - hoeft voor de eerste versie van ACT-002 niet te worden aangepast.

5. De door ACT-001 toegevoegde scene-statebestanden.
   - gebruik de namen en locatie die ACT-001 daadwerkelijk heeft geintroduceerd;
   - maak geen tweede concurrerend scene-state-model.

### Huidige laagvolgorde

1. hemel / achtergrondgradient;
2. zon of maan;
3. background weather pass, voornamelijk wolken;
4. transparante locatie-afbeelding;
5. bestaande Material Weather-effecten;
6. foreground weather pass: regen, sneeuw, hagel, mist, haze en bliksem;
7. glass rain drops;
8. rotating testlabel.

Deze volgorde moet intact blijven.

### Huidig abrupt gedrag

`MaterialLiveWallpaperService.setWeatherImplementor()` maakt een nieuwe `WallpaperWeatherEffectRenderer` voor het actuele weather kind. De vorige renderer wordt direct vervangen.

`refreshAutomaticDayNight()` verandert `mDaytime` en bouwt daarna renderer en achtergrond opnieuw op. De hemelgradient en zon/maan hebben al tijdgebaseerde menging, maar niet alle overige lagen volgen dezelfde continue factor.

De foto-identiteit bevat `mDaytime`. Bij een dag/nachtwisseling wordt de foto opnieuw opgebouwd met of zonder vaste `ColorMatrixColorFilter`. Dit is een binaire omschakeling en moet worden losgekoppeld van de bitmapcache.

## 4. Afbakening

### Wel uitvoeren

- vloeiende overgang tussen twee weather scene states;
- vloeiende overgang van weereffectintensiteiten;
- vloeiende dag/nachtkleur voor effecten en locatie-afbeelding;
- bestaande hemel- en celestial-overgang integreren met de scene state;
- stabiele animatietijd tijdens een overgang;
- overgang afbreken of herstarten wanneer tijdens de overgang een nieuw target binnenkomt;
- AGSL- en Canvas-implementatie;
- unit tests voor overgangsberekeningen;
- handmatige/emulatortests voor visuele scenario's.

### Niet uitvoeren

- geen nieuwe weather families;
- geen nieuwe wolkenvormen: dat is ACT-003;
- geen nieuwe snow/hail particle pool: dat is ACT-004;
- geen nieuwe fog/haze-uitwerking: dat is ACT-005;
- geen nieuwe rain-on-glasskwaliteit: dat is ACT-006;
- geen automatische foto-download: dat is ACT-010;
- geen wijziging aan Meteo-, GPS- of RemoveSky-clients;
- geen OpenGL-migratie;
- geen UI-herontwerp;
- geen brede refactor van Breezy Weather;
- geen externe shadercode kopieren.

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

## 6. Prerequisite ACT-001

ACT-002 moet voortbouwen op de centrale immutable scene state uit ACT-001. Controleer voor aanvang welke class en velden ACT-001 daadwerkelijk heeft toegevoegd.

De state moet voor ACT-002 minimaal een renderbare snapshot kunnen leveren met:

- weather family/kind;
- day/night- of daylightfactor;
- cloudparameters;
- precipitation type en intensity;
- fog/haze intensity;
- thunder/lightning intensity;
- glass-rain intensity;
- windfactor en bij voorkeur windrichting;
- sunrise en sunset;
- zon- en maanpositie of voldoende informatie om die te berekenen;
- hemelkleuren of genormaliseerde dawn/day/dusk/nightfactoren;
- fotonachttint;
- quality profile.

Als ACT-001 nog niet is gemerged, stop dan en rapporteer deze dependency. Voeg niet stilzwijgend een tweede scene-state-architectuur toe.

## 7. Gewenst overgangsmodel

Introduceer een kleine transition controller die drie begrippen beheert:

```kotlin
data class WallpaperSceneTransition(
    val from: WallpaperSceneState,
    val to: WallpaperSceneState,
    val startedAtMillis: Long,
    val durationMillis: Long,
)
```

De exacte naam mag aansluiten bij ACT-001, maar de verantwoordelijkheid moet hetzelfde blijven.

De engine bewaart conceptueel:

```kotlin
private var stableSceneState: WallpaperSceneState? = null
private var activeTransition: WallpaperSceneTransition? = null
```

Per frame wordt een render state bepaald:

```kotlin
val progress = transitionProgress(now, transition)
val easedProgress = smoothStep(progress)
val renderState = interpolate(transition.from, transition.to, easedProgress)
```

Aanbevolen easing:

```kotlin
private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
```

Gebruik monotonic time voor overgangsduur, bijvoorbeeld `SystemClock.elapsedRealtime()`. Gebruik wall-clock time alleen voor sunrise/sunset en astronomische positie.

## 8. Overgangsduren

Gebruik vaste, centraal gedefinieerde defaults. Voeg in deze opdracht nog geen gebruikersinstelling toe.

Aanbevolen waarden:

| Situatie | Duur | Gedrag |
|---|---:|---|
| Normale weather family-wijziging | 60 seconden | rustig mengen |
| Start/stop neerslag | 45 seconden | geleidelijke density |
| Naar/van Thunderstorm | 30 seconden | sneller maar niet abrupt |
| Lokale data wordt na lange offlineperiode vervangen | 60 seconden | normale weather-overgang |
| Auto dag/nacht | continu vanuit echte tijd | geen losse binaire transition nodig |
| Geforceerde Day/Night-keuze in instellingen | 3 seconden | snelle visuele bevestiging |
| Rotating testmodus | 2 seconden | grootste deel van 20 seconden blijft zichtbaar |
| Animaties uitgeschakeld | 0 seconden | direct stabiel frame |
| Eerste frame na service-start | 0 seconden | geen fade vanuit zwart of ongedefinieerde state |

De overgangsduur moet als pure functie worden gekozen, bijvoorbeeld:

```kotlin
fun transitionDurationMillis(
    from: WallpaperSceneState,
    to: WallpaperSceneState,
    mode: WallpaperMode,
    animationsEnabled: Boolean,
): Long
```

## 9. Wat moet worden geinterpoleerd

### Continue waarden

Lineair mengen na easing:

- daylightfactor;
- sky top- en bottomkleur;
- sun alpha;
- moon alpha;
- celestial glow alpha;
- cloud density;
- cloud darkness;
- cloud alpha;
- cloud speedfactor;
- precipitation intensity;
- fog intensity;
- haze intensity;
- lightning probability/intensity;
- glass-rain intensity;
- photo night tint;
- algemene stormdarkening.

Kleuren moeten per ARGB-kanaal of in een geschikte kleurruimte worden gemengd. Gebruik geen directe integerinterpolatie op een packed color.

### Hoeken en richtingen

Windrichting moet via de kortste hoek worden gemengd. Een overgang van 350 naar 10 graden moet via 0 graden lopen, niet via 180 graden.

### Discrete waarden

Weather family zelf is discreet en mag niet halverwege omschakelen als dat een effectpop veroorzaakt. Gebruik hiervoor twee bijdragen:

- outgoing family met alpha/intensity `1 - progress`;
- incoming family met alpha/intensity `progress`.

Hetzelfde geldt voor precipitation type als bijvoorbeeld Rain naar Snow verandert.

### Niet interpoleren

- bitmapidentiteit of fotopad;
- surfaceafmetingen;
- parallax aan/uit;
- quality profile tijdens een actief frame, tenzij de bestaande adaptive quality dit vereist;
- secrets, configkeys of repositoryobjecten.

## 10. Rendererstrategie

### Aanbevolen oplossing

Houd tijdens een weather family-overgang tijdelijk twee effectrendererinstanties in leven:

- `outgoingRenderer` voor de vorige family;
- `incomingRenderer` voor de nieuwe family.

Beide renderers:

- behouden hun eigen particle state;
- worden per frame geupdatet zolang hun bijdrage groter dan nul is;
- tekenen dezelfde drie passes in dezelfde laagvolgorde;
- ontvangen een globale contribution/alpha;
- worden na voltooiing teruggebracht tot alleen de incoming renderer.

Conceptueel:

```kotlin
drawSky(interpolatedState)
drawCelestial(interpolatedState)

outgoingRenderer?.drawBackgroundWeatherPass(canvas, 1f - progress)
incomingRenderer?.drawBackgroundWeatherPass(canvas, progress)

drawLocationImage(canvas, interpolatedState.photoNightTint)

outgoingRenderer?.drawForegroundWeatherPass(canvas, 1f - progress)
incomingRenderer?.drawForegroundWeatherPass(canvas, progress)

outgoingRenderer?.drawGlassRainDrops(canvas, 1f - progress)
incomingRenderer?.drawGlassRainDrops(canvas, progress)
```

Maak geen offscreen full-screen bitmap per frame. Dat veroorzaakt geheugenbandbreedte, allocaties en mogelijk haperingen.

### AGSL

Voeg een globale uniforme contribution toe, bijvoorbeeld:

```glsl
uniform float transitionAlpha;
```

Vermenigvuldig de uiteindelijke alpha van ieder effect met deze waarde. Laat geometrie, particlepositie en tijd doorlopen.

Voor effecten die al een intensiteitsuniform hebben:

```text
effectiveIntensity = sceneIntensity * transitionAlpha
```

Gebruik niet alleen een harde `if (transitionAlpha > 0.5)`.

### Canvas fallback

De Canvas renderer moet dezelfde contribution ontvangen. Vermenigvuldig de alpha van de hergebruikte `Paint`-objecten met contribution.

Gebruik:

- bestaande Paint-instanties;
- geen nieuwe Paint, Rect, Path, Random of particleobjecten per frame;
- `saveLayerAlpha()` alleen als een specifiek effect niet op een andere manier correct kan mengen en nadat performance is gemeten.

### Bestaande Material Weather implementor

Controleer of alle twaalf huidige families via `WallpaperWeatherEffectRenderer.supports()` lopen. Voor een eventueel resterend pad via `WeatherImplementorFactory` zijn er twee opties:

1. voeg het ontbrekende effect minimaal toe aan `WallpaperWeatherEffectRenderer`; of
2. laat het oude implementorpad direct wisselen en documenteer dit als tijdelijke beperking.

Maak voor ACT-002 geen brede aanpassing aan de algemene Breezy Weather-animatiebibliotheek.

## 11. Hemel, zon en maan

### Bestaand gedrag behouden

De code bevat al:

- 45 minuten blending rond sunrise en sunset;
- 25 minuten crossfade voor zonzichtbaarheid;
- een natuurlijke boog voor zon en maan;
- echte dagelijkse astrodata met lokale fallbackberekening;
- hemelgradientcache per minuut.

Deze logica niet vervangen door een vaste klok zoals 06:00/18:00.

### Integratie

Maak de resulterende daylight- en skyfactor onderdeel van de scene state. De renderer moet dezelfde factor gebruiken voor:

- hemel;
- zon/maan;
- wolkenkleur;
- neerslagkleur;
- mist/haze;
- fotonachttint.

`mDaytime` mag niet langer de enige visuele bron zijn voor een binaire dag/nachtkleur. Het mag blijven bestaan als semantische status voor condition mapping, maar visuele kleur moet een `0f..1f` factor gebruiken.

### Pooldagen en poolnachten

Houd rekening met ontbrekende of ongewone rise/set-data:

- sunrise ontbreekt en sunset ontbreekt;
- sunrise is na sunset door intervalnormalisatie;
- de zon komt niet op of gaat niet onder;
- providerdata is leeg en de astronomische fallback wordt gebruikt.

Gebruik de bestaande fallbackfuncties. Introduceer geen nieuwe externe astrobron.

## 12. Locatie-afbeelding

De locatiebitmap mag niet opnieuw worden gedecodeerd uitsluitend omdat de daylightfactor verandert.

Verwijder daarom uiteindelijk `mDaytime` uit de foreground cache key, tenzij een andere bitmaptransformatie dit nog strikt vereist.

Aanbevolen gedrag:

- cache een neutrale transparante locatiebitmap;
- pas nachttint tijdens tekenen toe met een herbruikte Paint/ColorFilter of een lichte overlay;
- meng `photoNightTint` continu van 0 naar 1;
- voorkom full-screen bitmapkopieen per frame;
- behoud de huidige 52% schermhoogte en parallaxbounds.

De precieze tint mag bij de bestaande nachtkleuring aansluiten, maar moet via een continue factor worden toegepast.

## 13. Nieuwe target tijdens actieve overgang

Een nieuwe lokale scene state kan binnenkomen voordat de vorige overgang klaar is.

Gebruik dan niet opnieuw de oorspronkelijke `from`-state. Neem de op dat moment zichtbare geinterpoleerde state als nieuw vertrekpunt:

```kotlin
val visibleNow = currentInterpolatedState(now)
activeTransition = WallpaperSceneTransition(
    from = visibleNow,
    to = newTarget,
    startedAtMillis = elapsedRealtime,
    durationMillis = selectedDuration,
)
```

Voor discrete rendererfamilies is het niet wenselijk onbeperkt renderers te stapelen. Beperk het tot maximaal twee actieve renderers.

Aanbevolen regel bij een derde target:

- kies de renderer met de grootste huidige bijdrage als outgoing;
- verwijder de andere renderer;
- maak een nieuwe incoming renderer voor het nieuwste target;
- normaliseer de bijdragen zodat geen helderheidspiek ontstaat.

## 14. Lifecycle

### Visibility

Wanneer `onVisibilityChanged(false)` wordt aangeroepen:

- stop framecallbacks zoals nu;
- vernietig de transition state niet noodzakelijk;
- bewaar target state en transition-eindtijd;
- voer geen frames uit terwijl onzichtbaar.

Wanneer de wallpaper opnieuw zichtbaar wordt:

- lees de nieuwste lokale snapshot;
- als de oude overgang inmiddels verstreken is, toon direct de target state;
- als de lokale state is gewijzigd, start een normale overgang vanaf de laatst zichtbare of stabiele state;
- start niet vanuit een lege/clear default als een geldige state bekend is.

### Surface recreation

Bij surface resize of recreation:

- behoud transition progress;
- pas alleen bounds en resolutie-uniforms aan;
- maak niet zonder noodzaak nieuwe transition timing;
- voorkom een dubbel aangemaakte incoming renderer.

### Animaties uit

Als animaties uitgeschakeld zijn:

- geen transitionframes plannen;
- target state direct toepassen;
- exact een geldig frame tekenen;
- celestial positie en hemel mogen bij een volgend noodzakelijk redrawmoment actualiseren.

## 15. Rotating testmodus

Rotating wisselt iedere 20 seconden door twaalf weather families en toont de familienaam.

Voor deze modus:

- gebruik een transition van ongeveer 2 seconden;
- laat het label direct de incoming family tonen, eventueel als `Rotating: Rain`;
- voorkom dat een 60 seconden overgang nooit voltooit;
- behoud de huidige volgorde van families;
- gebruik echte lokale wind- en astrodata voor snelheid en celestial positie;
- laat rotating geen repositorydata of instellingen overschrijven.

## 16. Data-updategedrag

ACT-002 is geen nieuwe refreshoplossing. Een transition start wanneer de engine een nieuwe lokale target state ontvangt, bijvoorbeeld:

- bij wallpaper visibility/restart;
- na een bestaande lokale data-invalidation;
- na configwijziging;
- tijdens rotating testmodus;
- via een toekomstige centrale wallpaper-update-notificatie.

Als er nog geen observer of invalidatiesignaal voor lokale weather updates bestaat, mag ACT-002 een kleine lokale invalidatie-ingang toevoegen. Voeg geen periodieke netwerkpoll toe.

Een eenvoudige API is voldoende:

```kotlin
private fun submitSceneTarget(
    target: WallpaperSceneState,
    reason: SceneTransitionReason,
)
```

Mogelijke redenen:

```kotlin
enum class SceneTransitionReason {
    INITIAL,
    WEATHER_DATA_CHANGED,
    AUTO_DAY_NIGHT,
    USER_FORCED_MODE,
    ROTATING_TEST,
    SURFACE_RECREATED,
}
```

## 17. Performance-eisen

- maximaal 30 FPS;
- geen full-screen bitmapallocatie per frame;
- geen nieuwe renderer per frame;
- maximaal twee weather effectrenderers tijdens een overgang;
- na overgang exact een renderer bewaren;
- geen objectallocaties in `drawBackgroundWeatherPass`, `drawForegroundWeatherPass` en `drawGlassRainDrops`;
- uniforms alleen bijwerken als nodig;
- bestaande gradientcache per minuut behouden;
- bestaande adaptive snow/hail quality behouden;
- rendering stopt wanneer wallpaper onzichtbaar is;
- geen logging per frame.

Meet minimaal:

- stabiele frametijd zonder overgang;
- frametijd met twee actieve renderers;
- Rain naar Snow;
- Clear naar Thunderstorm;
- dag naar nacht met fotolaag;
- Canvas fallback op een pre-Android 13 emulator indien beschikbaar.

Als twee volledige rendererpasses op een toestel structureel te zwaar zijn, verlaag tijdens de transition tijdelijk het particlebudget. Verlaag niet de schermresolutie zonder expliciete meting en motivatie.

## 18. Logging en privacy

Alle nieuwe logging is debug-only en niet per frame.

Toegestane informatie:

- transition reason;
- outgoing en incoming weather family;
- gekozen duur;
- begin, voltooiing of annulering;
- gekozen quality profile;
- fallback naar Canvas door shaderfout.

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- lokale fotopaden als die persoonsgegevens kunnen bevatten.

## 19. Voorgestelde implementatiestappen

1. Controleer en documenteer de ACT-001 scene-state API.
2. Voeg pure interpolation helpers toe voor float, color en angle.
3. Voeg een transition controller toe zonder Android Canvas-afhankelijkheid.
4. Vervang directe statevervanging door `submitSceneTarget()`.
5. Laat eerste initialisatie zonder fade verlopen.
6. Voeg contribution/transitionAlpha toe aan AGSL.
7. Voeg dezelfde contribution toe aan Canvas Paint-alpha's.
8. Beheer outgoing en incoming renderer met maximaal twee instanties.
9. Koppel sky, celestial en effect day/nightkleur aan dezelfde daylightfactor.
10. Maak de locatiebitmap neutraal en pas nachttint tijdens draw toe.
11. Voeg speciale duren toe voor rotating, forced mode en animations disabled.
12. Handel een nieuw target tijdens een actieve transition af.
13. Voeg pure unit tests toe.
14. Build debug en release.
15. Test op emulator en gekoppelde telefoon.
16. Maak screenshots van de vereiste scenario's.
17. Controleer `git diff` op scope en secrets.

## 20. Unit tests

Voeg tests toe voor pure logica, zonder echte Surface of WallpaperService waar dat niet nodig is.

Minimale testgevallen:

1. progress is 0 voor starttijd;
2. progress is 1 na eindtijd;
3. duration 0 resulteert direct in target;
4. smoothStep blijft tussen 0 en 1 en is monotonic;
5. floatinterpolatie geeft correcte begin-, midden- en eindwaarde;
6. kleurinterpolatie mengt alle kanalen afzonderlijk;
7. hoekinterpolatie 350 naar 10 gebruikt de korte route;
8. eerste scene state maakt geen overgang;
9. animations disabled gebruikt duur 0;
10. rotating gebruikt korte duur;
11. normale weather update gebruikt standaardduur;
12. Thunderstorm gebruikt begrensde snellere duur;
13. nieuw target tijdens transition gebruikt zichtbare tussenstate;
14. voltooide transition ruimt outgoing state op;
15. daylightfactor blijft continu rond sunrise en sunset;
16. ontbrekende astrodata levert een geldige fallback en geen NaN;
17. identieke target state start geen nieuwe transition;
18. stateverschillen door alleen timestamp starten niet onnodig een weather transition.

Gebruik een injecteerbare clock of geef tijden als parameters door. Tests mogen niet afhankelijk zijn van de echte huidige tijd.

## 21. Handmatige testmatrix

### Weather-overgangen

| Van | Naar | Verwachting |
|---|---|---|
| Clear | Cloud | wolken faden rustig in achter de foto |
| Cloud | Cloudy | dichtheid neemt toe zonder pop |
| Cloudy | Rain | wolken worden donkerder, regen bouwt op |
| Rain | Thunderstorm | stormdarkening en bliksem nemen sneller toe |
| Rain | Snow | regen neemt af terwijl sneeuw opbouwt |
| Snow | Hail | sneeuw verdwijnt terwijl compactere, snellere hagel toeneemt |
| Fog | Clear | mist lost geleidelijk op |
| Haze | Rain | warme haze verdwijnt terwijl stormlaag opkomt |
| Wind | Partly cloudy | snelheid en dichtheid normaliseren zonder resetpop |
| Thunderstorm | Clear | regen, druppels en donkere wolken bouwen rustig af |

### Tijdscenario's

- 30 minuten voor sunrise;
- exact rond sunrise;
- 30 minuten na sunrise;
- middag;
- 30 minuten voor sunset;
- exact rond sunset;
- 30 minuten na sunset;
- nacht;
- ontbrekende provider-astrodata met lokale fallback.

### Modi

- Auto weather + Auto day/night;
- geforceerde weather family;
- geforceerde Day;
- geforceerde Night;
- Rotating;
- animations disabled;
- photo background disabled;
- photo background enabled;
- parallax disabled/enabled;
- wallpaper preview en werkelijk ingestelde wallpaper.

### Platforms

- Android 13 of hoger op gekoppelde telefoon;
- actieve emulator;
- waar mogelijk Android 12 of lager voor Canvas fallback.

## 22. Screenshotset

Maak minimaal screenshots van:

1. Clear naar Cloud op ongeveer 50%;
2. Cloudy naar Rain op ongeveer 50%;
3. Rain naar Snow op ongeveer 50%;
4. Clear naar Thunderstorm op ongeveer 50%;
5. sunrise;
6. sunset;
7. night met maan;
8. dag/nacht-fototint tijdens de overgang;
9. rotating label met inkomende weather family.

Screenshots moeten weather family, testtijd en platform in de bestandsnaam krijgen.

## 23. Acceptatiecriteria

1. Een normale weather family-wijziging veroorzaakt geen zichtbare one-frame pop.
2. Outgoing en incoming wolken/effecten mengen gedurende de gekozen overgangsduur.
3. Neerslag start en stopt via een geleidelijke intensityfactor.
4. Rain naar Snow en Snow naar Hail kunnen mengen zonder crash of volledig leeg frame.
5. Hemel, zon/maan, effectkleuren en fototint gebruiken een consistente daylightfactor.
6. Sunrise en sunset blijven gebaseerd op lokale repositorydata of bestaande astronomische fallback.
7. De locatiebitmap wordt niet iedere minuut of ieder frame opnieuw gedecodeerd.
8. De nachttint van de foto schakelt niet meer abrupt.
9. Rotating blijft iedere 20 seconden doorgaan en gebruikt een korte overgang.
10. Geforceerde testmodi reageren binnen enkele seconden.
11. Animations disabled tekent direct een stabiel frame zonder transitionloop.
12. Een nieuw target tijdens een actieve overgang veroorzaakt geen helderheidspiek of rendererketen.
13. Er zijn maximaal twee effectrendererinstanties tegelijk actief.
14. De wallpaper blijft begrensd op maximaal 30 FPS.
15. De wallpaper stopt rendering en sensorgebruik wanneer hij niet zichtbaar is.
16. Android 13+ AGSL compileert zonder fouten.
17. Canvas fallback crasht niet en toont een acceptabele overgang.
18. Er zijn geen netwerk-, GPS- of RemoveSky-calls vanuit de wallpaper toegevoegd.
19. Er worden geen secrets of exacte locaties gelogd.
20. Bestaande foto-, cache-, parallax- en rotatingfunctionaliteit blijft werken.

## 24. Definition of done

- implementatie is beperkt tot wallpaper scene/transitioncode en gerichte tests;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- screenshotset is gemaakt;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-002-bestanden;
- commit en push gebeuren pas nadat de visuele controle is goedgekeurd.

## 25. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, RemoveSky-, iconpack- of andere niet- ACT-002-bestanden;
- neem bestaande wijzigingen in gedeelde wallpaperbestanden als uitgangspunt;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg nieuwe classes bij voorkeur in een klein eigen transitionbestand toe;
- houd wijzigingen in `MaterialLiveWallpaperService.kt` en `WallpaperWeatherEffectRenderer.kt` zo lokaal mogelijk;
- stage bestanden expliciet, nooit via `git add .`;
- meld conflicten met ACT-001 in plaats van een tweede architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 26. Verwacht eindresultaat

Na ACT-002 voelt de wallpaper als een doorlopende scene. Nieuwe lokale weerdata verandert de scene geleidelijk, sunrise en sunset blijven natuurlijk verlopen, de foto wordt rustig donkerder of lichter en particles verschijnen of verdwijnen zonder abrupte reset. De renderer blijft compact, lokaal, maximaal 30 FPS en compatibel met zowel AGSL als Canvas.
