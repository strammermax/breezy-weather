# YoWindow 2.58.30 versus LiveWeatherApp

## 1. Samenvatting

YoWindow en LiveWeatherApp lossen een vergelijkbaar zichtbaar probleem op, maar vanuit een andere architectuur.

- **YoWindow** is een complete geanimeerde scene-engine. De applicatie combineert OpenGL ES 2.0, shaders, sprite-atlassen, GPU-particles, seizoensvarianten, geluiden en meerdere landschapspakketten. Het landschap zelf is een uitgebreide wereld met objecten en animaties.
- **LiveWeatherApp** bouwt voort op Breezy Weather en gebruikt een centrale lokale weer- en locatiedatabase. De live wallpaper is een relatief compacte compositor met een hemel, zon of maan, wolken, een transparante lokale foto en voorgrondeffecten. Android 13 en hoger gebruikt AGSL; oudere Android-versies gebruiken Canvas.

De belangrijkste conclusie is dat LiveWeatherApp niet moet proberen de volledige YoWindow-engine na te bouwen. De bruikbare lessen zitten vooral in:

1. een expliciete scene state met vloeiende overgangen;
2. duidelijke kwaliteitsprofielen en effectbudgetten;
3. herbruikbare particle pools en sprite-atlassen;
4. betere synchronisatie tussen tijd, weer, seizoen en animatie;
5. instellingen voor effectintensiteit zonder de rendererarchitectuur open te breken.

De bestaande keuze dat de live wallpaper uitsluitend uit de centrale lokale datalaag leest, is goed en moet behouden blijven. Externe weer-, GPS- en RemoveSky-opvragingen horen bij de app- en backgroundlaag, niet bij de wallpaper-renderlus.

## 2. Onderzochte codebases en beperkingen

### YoWindow

Onderzocht pad:

`D:\Project\YoWindow v2.58.30\YoWindowv2.58.30_cfr`

De map is geen oorspronkelijke, complete ontwikkelrepository. Het gaat om een gedecompileerde en gereconstrueerde Android-build:

- veel applicatieklassen hebben geobfusceerde namen zoals `a`, `B0` en `G0`;
- de manifestnamen, zoals `yo.wallpaper.Wallpaper`, zijn niet betrouwbaar terug te koppelen naar leesbare bronbestanden;
- er staan zowel gedecompileerde Java-bestanden als smali-bestanden in de boom;
- de aanwezige `app/build.gradle` is een generiek reconstructiebestand en spreekt het manifest tegen;
- comments, oorspronkelijke modulegrenzen, symbolen en een deel van de type-informatie ontbreken;
- native `.so`-bibliotheken kunnen niet inhoudelijk worden beoordeeld zonder verdere reverse engineering.

Daarom gelden in dit document drie niveaus:

- **Bevestigd:** rechtstreeks zichtbaar in manifest, resources, instellingen of shaders.
- **Sterke inferentie:** volgt logisch uit meerdere samenhangende assets en shadercontracten.
- **Onbekend:** niet betrouwbaar vast te stellen door obfuscatie of ontbrekende bron.

Voor Android-versies is het manifest leidend: YoWindow declareert `minSdkVersion 23`, `targetSdkVersion 36` en verplicht OpenGL ES 2.0. De waarden uit het gereconstrueerde Gradle-bestand zijn niet betrouwbaar.

### LiveWeatherApp

Onderzocht pad:

`D:\Project\LiveWeatherApp`

De relevante onderdelen zijn onder andere:

- `MaterialLiveWallpaperService.kt`: wallpaper lifecycle, compositie en lokale data-inlezing;
- `WallpaperWeatherEffectRenderer.kt`: AGSL- en Canvas-weereffecten;
- `LiveWallpaperConfigActivity.kt`: instellingen, preview en handmatige foto-refresh;
- `LiveWallpaperConfigManager.kt`: bewaarde wallpaperinstellingen;
- `WallpaperRepository.kt` en `WallpaperImageStore.kt`: foto-ophalen en lokale fotocache;
- `LocationRepository.kt` en `WeatherRepository.kt`: centrale lokale datalaag;
- `RefreshHelper.kt`, `SourceManager.kt` en `AndroidLocationService.kt`: externe databronnen;
- `WeatherUpdateJob.kt`: periodieke achtergrondverversing via WorkManager.

De werkboom bevatte tijdens dit onderzoek bestaande, niet aan deze opdracht gerelateerde wijzigingen in de camerafunctie. Die zijn niet aangepast of meegenomen.

## 3. Functionele vergelijking

| Onderdeel | YoWindow 2.58.30 | LiveWeatherApp | Advies |
|---|---|---|---|
| Dag/nacht | Uitgebreide scenevariatie; sterren- en gradientshaders aanwezig | Gradient, zon/maan en echte sunrise/sunset-data | LiveWeather-aanpak behouden; overgang verder interpoleren |
| Zon en maan | Onderdeel van complete scene/tijdlijn | Procedureel getekend, positie uit lokale astrodata | Scene state per minuut cachen en booglogica testen |
| Wolken | Dubbele cloud-textuurlagen in shader, bewegende offsets | Procedurele AGSL/Canvas-wolken achter de foto | Wolkenvormen verbeteren zonder YoWindow-assets te kopieren |
| Regen | Regen-spriteshader, geluid en particles aanwezig | Regen voor de foto plus glass-rain-overlay | Intensiteit en particle budget aan actuele data koppelen |
| Sneeuw | Eigen snow-sheetshader en scene-assets | Adaptieve meerlaagse sneeuw/hagel | Particle pool en quality profile toevoegen |
| Mist/haze | Niet volledig traceerbaar; scene/shaderinfrastructuur kan overlays dragen | Eigen foreground-effecten | Verder verfijnen met dieptebanden en lage snelheid |
| Onweer | Meerdere dondergeluiden en weereffecten | Donkere wolken, regen en bliksem | Timing randomiseren en audio voorlopig achterwege laten |
| Wind | Scene-objecten en effecten kunnen reageren; exacte logica onbekend | Windfactor beinvloedt wolken en neerslag | Windrichting naast windsnelheid gebruiken |
| Seizoenen | Bevestigde winter-, lente-, zomer- en herfstassets | Geen volledige seizoensscenes | Alleen subtiele kleurgrading overwegen |
| Landschap | Meerdere uitgebreide scene- en fotolandschappen | Transparante lokale RemoveSky-foto | Dit is juist het onderscheidende LiveWeather-concept |
| Animatieobjecten | Mensen, dieren, voertuigen, water en andere scene-objecten | Niet aanwezig | Niet overnemen; te groot en niet nodig voor het doel |
| Geluid | Regen, donder, landschap en objectgeluiden | Niet aanwezig | Lage prioriteit wegens batterij en wallpaperverwachtingen |
| Parallax | Instelbaar voor app en wallpaper | Instelbaar, verschillende laagfactoren | Behouden en begrenzen zoals nu |
| Wallpaper | Eigen service en uitgebreide instellingen | Eigen service en compacte instellingen | LiveWeather-instellingen gericht houden |
| Screensaver | Daydream-service aanwezig | Niet aanwezig | Niet relevant voor huidige roadmap |
| Widgets/notificaties | Veel widgets, alarmen en weerwaarschuwingen | Breezy Weather heeft uitgebreide widgets/notificaties | Geen YoWindow-werk nodig |
| Offline gebruik | Lokale assets, databasecomponenten en caches aanwezig | Lokale SQLDelight-data en fotocache | LiveWeather is architectonisch al sterk op dit punt |
| Testmodus | Niet betrouwbaar vast te stellen | Rotating weather, iedere 20 seconden met label | Behouden; uitbreiden met vaste tijdscenario's |

## 4. Renderingarchitectuur

### YoWindow

Bevestigde technische bouwstenen:

- OpenGL ES 2.0 is verplicht in het manifest.
- Er zijn 29 shaderbestanden en ongeveer 589 assetbestanden, samen circa 31 MiB.
- `render_batch.glsl` wijst op gebatchte rendering.
- `double_bitmap_cloud_sheet.glsl` mengt twee bewegende cloud-textuurlagen.
- `rain_sheet.glsl` en `snow_sheet.glsl` verplaatsen sprites op basis van tijd en per-particle attributen.
- de `particles`-shaders bewaren positie, snelheid, levensduur, schaal en rotatie in textures en werken die op de GPU bij;
- aparte shaders bestaan voor sterren, regenboog, foto-sprites, golven, water, ijs, rook en gebouwramen;
- landschapspakketten bevatten vooraf opgebouwde `.bin`- en texture-assets.

Sterke inferentie: YoWindow gebruikt een scene graph of vergelijkbare renderlijst met gebatchte sprite-elementen, gespecialiseerde shaderpasses en een gedeelde tijdlijn. De app, live wallpaper en daydream lijken dezelfde scene-assets te benutten. Door de geobfusceerde code is niet te bevestigen hoe de rendererobjecten exact zijn verdeeld of hoe frame scheduling wordt gedaan.

### LiveWeatherApp

De compositie is expliciet en goed leesbaar:

1. hemelachtergrond;
2. zon of maan;
3. `drawBackgroundWeatherPass()` voor wolken;
4. transparante locatie-afbeelding;
5. bestaande Material Weather-effecten;
6. `drawForegroundWeatherPass()` voor neerslag, mist en bliksem;
7. `drawGlassRainDrops()` als bovenste laag;
8. optioneel testlabel voor rotating weather.

De renderer gebruikt:

- een hardware canvas waar mogelijk;
- AGSL `RuntimeShader` vanaf Android 13;
- Canvas fallback op oudere toestellen;
- maximaal 30 FPS;
- adaptieve effectkwaliteit op basis van gemeten frametijd;
- een aparte renderthread;
- pauzeren van callbacks en sensoren wanneer de wallpaper niet zichtbaar is.

Deze architectuur is kleiner en eenvoudiger te onderhouden. Een volledige overstap naar OpenGL alleen om YoWindow te benaderen zou een grote refactor, extra lifecycle-risico en dubbele fallbacklogica opleveren. Dat is niet aanbevolen.

## 5. Laagopbouw en scene state

### Verschil in model

YoWindow lijkt een volledige wereld te modelleren. Weer, tijd, seizoen, objecten, water, geluid en landschap zijn onderdelen van dezelfde scene. LiveWeatherApp modelleert een vaste compositor rond een echte foto.

De LiveWeather-laagvolgorde is voor het huidige product passend:

1. hemel en dag/nacht-gradient;
2. zon of maan;
3. wolken;
4. transparante locatie-afbeelding;
5. regen, sneeuw, hagel, mist, haze en bliksem;
6. druppels op glas.

### Wat ontbreekt in LiveWeatherApp

Het grootste verschil is niet het aantal shaders, maar het ontbreken van een centraal, geinterpoleerd **scene state**-object. Momenteel worden meerdere waarden direct uit de huidige weather kind, tijd en instellingen afgeleid. Een compacte scene state kan bevatten:

- genormaliseerde dagvoortgang;
- dawn/day/dusk/night-mengfactoren;
- zon- of maanpositie en zichtbaarheid;
- cloud density, darkness, speed en direction;
- precipitation type en intensity;
- visibility/fog/haze density;
- wind speed, gusts en direction;
- transition progress tussen vorige en nieuwe weersituatie;
- gekozen quality profile.

Zo'n object hoeft geen grote rendererrefactor te zijn. Het kan eenmaal per minuut of bij een datawijziging worden berekend en daarna per frame alleen worden geinterpoleerd.

## 6. Weerdata, locatie, refresh, cache en offline

### YoWindow

Bevestigd:

- instellingen maken onderscheid tussen een current weather provider en forecast provider;
- locatie-, achtergrondlocatie- en netwerkpermissies zijn aanwezig;
- WorkManager, Room, Picasso, widgets, boot receivers en een foreground location service zijn in de build aanwezig;
- er bestaan lokale landschapspaden, importpaden en caches;
- widgets hebben eigen update-intervallen;
- remote-configwaarden bevatten onder andere een achtergrondweerlimiet en job-update-interval.

Niet betrouwbaar vast te stellen:

- welke specifieke database-entiteiten voor weer en locatie worden gebruikt;
- of de wallpaper rechtstreeks netwerkverkeer start;
- de exacte stale-data- en retryregels;
- welke provider voor welke regio wordt gekozen;
- de precieze cache eviction-strategie.

### LiveWeatherApp

LiveWeatherApp heeft een duidelijke scheiding:

- `SourceManager`, `RefreshHelper` en locatiebronnen halen externe data op;
- `WeatherUpdateJob` plant periodieke updates met netwerk- en batterijconstraints;
- `LocationRepository` en `WeatherRepository` zijn de centrale lokale waarheid;
- de wallpaper leest de eerste locatie plus lokale dagelijkse en huidige weerdata;
- sunrise, sunset, wind en weather code komen uit dezelfde lokale weather snapshot;
- de wallpaper leest alleen de lokaal gecachete foto;
- de instellingenpagina mag bij een expliciete gebruikersactie een nieuwe RemoveSky-foto ophalen;
- `WallpaperImageStore` beheert lokale WebP-cache, historie per locatie en cachelimiet.

Dit sluit aan bij de gewenste architectuur: de wallpaper zelf hoort geen Meteo-, GPS- of RemoveSky-opvraging te starten.

### Aandachtspunt

De weerdata wordt centraal automatisch bijgewerkt, maar de fotoverversing is nog vooral gekoppeld aan de handmatige refresh in de instellingen. Als foto's later automatisch moeten wisselen, hoort dat in een app/background-worker die de cache bijwerkt en daarna de wallpaper invalideert. Het hoort niet in `WallpaperService.Engine`.

Offline gedrag is hierdoor voorspelbaar: de laatst opgeslagen weerdata en laatst gecachete locatie-afbeelding blijven bruikbaar. Voeg wel zichtbare of diagnostische informatie toe over ouderdom van de lokale snapshot.

## 7. UI en instellingen

### YoWindow-instellingen

Bevestigde wallpaperopties zijn onder andere:

- animaties aan/uit;
- landschap kiezen;
- parallax;
- dark glass;
- donkere statusbarachtergrond;
- centreren en positie vastzetten;
- fullscreen;
- geluid;
- weer tonen.

Daarnaast zijn er losse instellingen voor units, notificaties, weather providers, daydream, widgets, alarmen, geavanceerde opties en debugfuncties.

### LiveWeather-instellingen

De huidige LiveWeather-wallpaperinstellingen zijn doelgerichter:

- automatische of geforceerde weather family;
- rotating testmodus;
- automatische of geforceerde dag/nachtstand;
- animaties;
- parallax;
- lokale fotoachtergrond;
- cachelimiet;
- maximum aantal foto's per locatie;
- preview, handmatige refresh en opslaan.

### Advies

Voeg alleen instellingen toe die direct visueel of technisch nut hebben:

- effectkwaliteit: Battery saver, Balanced, High;
- neerslagintensiteit: Auto, Subtle, Normal;
- wolkendichtheid: Auto met eventueel een debug override;
- testtijd: Auto, Sunrise, Noon, Sunset, Night, uitsluitend in debug/testmodus;
- toon diagnostiek: weather family, FPS, quality profile en data age, uitsluitend debug.

Neem YoWindow-opties zoals landscape shop, soundscape, objectinteractie, daydream en scene-objecten niet over.

## 8. Performance, batterij en compatibiliteit

### YoWindow

Sterke punten:

- GPU-rendering en batching zijn geschikt voor veel sprites;
- particles worden op de GPU bijgewerkt;
- assets zijn vooraf opgebouwd;
- gespecialiseerde shaders voorkomen algemene, dure renderpaden;
- dezelfde assets kunnen vermoedelijk in app, wallpaper en daydream worden hergebruikt.

Risico's:

- OpenGL ES lifecycle en contextverlies maken wallpapercode complexer;
- veel scene-assets vergroten APK en geheugendruk;
- geluid, locatie, sensoren en continue animatie kunnen batterij kosten;
- de precieze throttling en visibility handling zijn door obfuscatie niet controleerbaar;
- native libraries en binaire scene-assets zijn lastig te onderhouden of auditen.

### LiveWeatherApp

Sterke punten:

- harde bovengrens van 30 FPS;
- callbacks, orientation listener en gravity sensor stoppen wanneer onzichtbaar;
- AGSL wordt alleen gebruikt waar beschikbaar;
- Canvas fallback ondersteunt Android 6 tot en met 12;
- adaptieve sneeuw/hagelkwaliteit reageert op frametijd;
- de lokale foto wordt alleen opnieuw gedecodeerd als identiteit, afmetingen of relevante instelling wijzigt;
- hemel- en celestial state kunnen per minuut worden gecachet;
- geen netwerkwerk in de renderlus.

Risico's en verbeterpunten:

- zowel de bestaande Material Weather-implementor als de nieuwe effectrenderer tekenen effecten; bewaak dubbel werk;
- RuntimeShader-fouten moeten eenmalig worden gelogd en direct op fallback overschakelen;
- grote transparante foto's kunnen veel bitmapgeheugen gebruiken;
- adaptieve kwaliteit moet hysterese houden om heen-en-weer schakelen te voorkomen;
- test op 60/90/120 Hz-schermen of de 30 FPS-cadans stabiel blijft;
- meet frame time, allocations en batterij met echte wallpaper visibility transitions.

### Compatibiliteitsadvies

Behoud het huidige hybride model. Android 13 en hoger heeft prioriteit en kan AGSL gebruiken. Android 6 tot en met 12 krijgt een eenvoudiger Canvas-resultaat. Een OpenGL-migratie is pas gerechtvaardigd als metingen aantonen dat AGSL/Canvas structureel onvoldoende is voor de gewenste effecten.

## 9. Interessante YoWindow-ideeen

### Direct bruikbaar

1. **Dubbele cloud layers**
   Twee cloudvelden met verschillende schaal, snelheid en offset voorkomen een egale ruislaag. Bouw dit met eigen procedurele vormen of eigen assets.

2. **Sprite-atlas voor particles**
   Een kleine atlas met meerdere sneeuwvlokken, regendruppels of mistvormen geeft variatie zonder losse bitmaps per particle.

3. **Vaste particle buffers**
   Reserveer arrays of buffers bij rendererinitialisatie en hergebruik ze. Geen objectallocaties per frame.

4. **Scene state en tijdlijn**
   Interpoleer weer, hemel en celestial state over tijd in plaats van direct van conditie te wisselen.

5. **Kwaliteitsprofielen**
   Laat elk profiel aantallen, shadercomplexiteit, blur en updatefrequentie bepalen. Combineer profiel met automatische tijdelijke degradatie bij trage frames.

6. **Seizoensgrading**
   Een subtiele winter-, lente-, zomer- of herfsttint kan sfeer toevoegen zonder volledige seizoenslandschappen.

7. **Effect-specifieke parameters**
   Regen, sneeuw, mist en wolken verdienen aparte density, depth, width, speed en alpha-parameters die vanuit dezelfde scene state worden gevoed.

8. **Gedeelde previewrenderer**
   Gebruik exact dezelfde compositor voor instellingenpreview en wallpaper, met alleen een andere surface adapter.

### Alleen als inspiratie

De shaders en assets uit de gedecompileerde YoWindow-build mogen niet worden gekopieerd. Licentie, auteursrecht en herkomst zijn niet vastgesteld. Gebruik uitsluitend de algemene visuele en architecturale principes en schrijf een eigen implementatie.

## 10. Niet relevant of niet aanbevolen

De volgende YoWindow-onderdelen passen niet bij het huidige doel:

- volledige interactieve landschappen met mensen, dieren en voertuigen;
- binaire sceneformaten en een eigen landschap-editor;
- alarmklok, daydream en extra widgetvarianten;
- in-app landschapcatalogus, abonnementen en advertenties;
- soundscape en continue wallpaperaudio;
- eigen OpenGL-engine alleen om visuele overeenkomst te bereiken;
- native libraries zonder bron;
- kopieren van gedecompileerde Java, smali, shaders, textures of geluiden;
- exacte nabootsing van de YoWindow-interface.

Ook technisch is een grote rendererwissel ongewenst. De huidige compositor is voldoende modulair voor de geplande verbeteringen.

## 11. Aanbevolen vervolgstappen

### Prioriteit 1: betrouwbaarheid en meetbaarheid

1. Voeg een compacte immutable `WallpaperSceneState` toe die uitsluitend uit lokale repositories en config wordt opgebouwd.
2. Voeg debugtelemetrie toe voor FPS, gemiddelde frame time, quality profile, weather family en ouderdom van data.
3. Maak geautomatiseerde screenshotscenario's voor de twaalf bestaande weather families plus sunrise, sunset en night.
4. Controleer dat de wallpaper nergens externe data ophaalt; borg dit met modulegrenzen of tests.

### Prioriteit 2: visuele verfijning

1. Maak wolken uit twee of drie herkenbare zachte massa-lagen met verschillende snelheid en schaal.
2. Interpoleer de scene state bij weather- en dag/nachtwissels gedurende bijvoorbeeld 30 tot 120 seconden.
3. Verbeter snow, hail, fog en haze met vaste pools en parameters uit wind, zicht en neerslag.
4. Maak glass rain droplets subtieler en verlaag de dichtheid op lagere kwaliteitsprofielen.

### Prioriteit 3: beheer en automatische fotoverversing

1. Voeg een centrale background-worker toe voor geplande fotoverversing per locatie.
2. Laat de worker cachelimiet, maximum per locatie en recente URL-historie respecteren.
3. Laat de worker de wallpaper alleen signaleren dat nieuwe lokale data beschikbaar is.
4. Toon in instellingen de huidige cachegrootte, data age en laatste refreshstatus.

## 12. Concrete backlog

### ACT-001 - Centrale wallpaper scene state

**Doel:** introduceer een immutable state met tijd, celestial positie, weather family, wind, neerslag, zicht, cloud parameters en quality profile.

**Waarom:** voorkomt verspreide berekeningen en maakt vloeiende overgangen en tests eenvoudiger.

**Acceptatie:** de renderer ontvangt per frame alleen scene state plus elapsed time; externe bronnen worden niet aangesproken; bestaande twaalf families blijven werken.

### ACT-002 - Vloeiende state transitions

**Doel:** meng vorige en nieuwe scene state over een begrensde overgangsduur.

**Waarom:** weer- en dag/nachtwissels voelen nu sneller abrupt dan in een volledige scene-engine.

**Acceptatie:** geen directe kleur- of effectsprong bij nieuwe weather data; sunrise en sunset volgen lokale tijden; geforceerde testmodi blijven onmiddellijk of configureerbaar.

### ACT-003 - Wolken met meerdere massa-lagen

**Doel:** render twee of drie eigen cloud layers met verschillende schaal, offset, snelheid, alpha en donkerte.

**Waarom:** herkenbare massa's ogen natuurlijker dan een uniforme ruislaag.

**Acceptatie:** Cloud, Overcast, Rain/Thunderstorm en Wind zijn visueel onderscheidbaar; wolken blijven achter de locatie-afbeelding; 30 FPS blijft haalbaar.

### ACT-004 - Particle pool voor sneeuw en hagel

**Doel:** vervang dynamische of herhaalde particleberekeningen door vooraf gereserveerde buffers en een kleine eigen sprite-atlas.

**Waarom:** meer diepte en variatie zonder allocaties per frame.

**Acceptatie:** 10 tot 20 effectieve lagen of equivalente diepte; windrichting en snelheid beinvloeden trajecten; hagel valt sneller en compacter dan sneeuw; fallback blijft beschikbaar.

### ACT-005 - Fog en haze depth bands

**Doel:** bouw meerdere langzaam bewegende horizontale banden met verschillende hoogte, alpha en blur-indruk.

**Waarom:** mist en haze moeten diepte toevoegen zonder witte waas over de hele scene.

**Acceptatie:** Fog is lokaal dichter en koeler; Haze is warmer en subtieler; locatie-afbeelding blijft herkenbaar; geen full-screen witte overlay.

### ACT-006 - Rain-on-glass kwaliteitslagen

**Doel:** definieer Low, Balanced en High varianten voor druppelaantal, trails, highlights en refractie-indruk.

**Waarom:** het effect is visueel belangrijk maar relatief duur en gevoelig voor een kunstmatige uitstraling.

**Acceptatie:** druppels verschijnen alleen bij Rain, Sleet en Thunderstorm; statische en zakkende druppels verschillen; lagere profielen verminderen dichtheid; Canvas fallback blijft acceptabel.

### ACT-007 - Renderer quality profiles

**Doel:** voeg Battery saver, Balanced en High toe, met automatische tijdelijke degradatie bij trage frames.

**Waarom:** expliciete profielen zijn voorspelbaarder dan uitsluitend reageren op CPU-belasting.

**Acceptatie:** elk profiel heeft gedocumenteerde particle-, cloud- en dropletbudgetten; hysterese voorkomt snel schakelen; keuze wordt lokaal opgeslagen.

### ACT-008 - Visuele regressietests

**Doel:** maak reproduceerbare screenshots voor Clear day, Sunrise, Sunset, Night, Cloud, Cloudy, Rain, Thunderstorm, Wind, Snow, Sleet en Fog.

**Waarom:** visuele wijzigingen zijn anders moeilijk objectief te vergelijken.

**Acceptatie:** vaste surfacegrootte, tijd, weather data en seed; screenshots kunnen op emulator worden herhaald; laagvolgorde en shaderfallback worden gecontroleerd.

### ACT-009 - Frame- en lifecycletelemetrie

**Doel:** meet gemiddelde en hoge-percentiel-frametijd, dropped frames, actieve quality profile en visibility lifecycle.

**Waarom:** vastlopen en batterijproblemen moeten meetbaar zijn in plaats van alleen visueel beoordeeld.

**Acceptatie:** alleen debuglogging; geen secrets of locaties in logs; geen logregel per frame; renderer stopt aantoonbaar wanneer wallpaper onzichtbaar is.

### ACT-010 - Centrale automatische foto-refresh

**Doel:** laat een app/background-worker periodiek een nieuwe RemoveSky-foto ophalen en lokaal opslaan.

**Waarom:** de wallpaper moet lokaal en read-only blijven, terwijl foto's toch automatisch kunnen wisselen.

**Acceptatie:** worker respecteert netwerk- en batterijconstraints, cachelimiet, maximum per locatie en recente historie; wallpaper doet zelf geen HTTP- of GPS-call.

### ACT-011 - Data freshness en offline status

**Doel:** leg refresh time, photo refresh time en stale status vast in de centrale lokale datalaag.

**Waarom:** zonstand, locatie en weer moeten aantoonbaar uit dezelfde actuele snapshot komen.

**Acceptatie:** instellingen/debugpreview tonen data age; offline blijft de laatst geldige scene beschikbaar; ontbrekende data heeft een gedocumenteerde fallback.

### ACT-012 - Seizoensgrading als experiment

**Doel:** onderzoek een subtiele kleur- en lichtgrading op basis van seizoen, zonder objecten of volledige landschappen toe te voegen.

**Waarom:** dit neemt een bruikbaar sfeerprincipe uit YoWindow over met beperkte complexiteit.

**Acceptatie:** experimentele flag; geen wijziging aan de bronfoto zelf; effect is subtiel en uit te schakelen; geen extra netwerkdata nodig.

## Slotadvies

YoWindow is waardevol als referentie voor scene-compositie, GPU-particles, batching en geleidelijke toestandsovergangen. Het is geen geschikte bron om rechtstreeks code of assets uit over te nemen, en de gedecompileerde build is onvoldoende betrouwbaar om architectuurdetails exact te reconstrueren.

LiveWeatherApp heeft al de juiste basis voor het eigen product: centrale lokale weerdata, een read-only wallpaper, een onderscheidende transparante locatie-afbeelding, expliciete renderpasses, 30 FPS-begrenzing en een Android 13+-shaderpad met fallback. De beste volgende stap is daarom geen nieuwe engine, maar een kleine centrale scene state, meetbare kwaliteitsprofielen en gerichte verfijning van clouds, snow/hail, fog/haze en rain-on-glass.
