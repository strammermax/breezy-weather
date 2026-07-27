# ACT-012 - Seizoensgrading als experiment

## Status

- Type: experimentele implementatieopdracht
- Prioriteit: laag
- Omvang: klein tot middelgroot
- Risico: laag tot middelgroot, omdat het de visuele kleur van de hele scene raakt maar achter een uitschakelbare flag staat
- Prerequisite: ACT-001 - Centrale wallpaper scene state; bij voorkeur ook ACT-002 - Vloeiende state transitions
- Doelplatform: Android 13 en hoger heeft prioriteit; Android 6 tot en met 12 behoudt een Canvas fallback

## 1. Opdracht in een zin

Onderzoek en bouw een optionele, subtiele kleur- en lichtgrading op basis van het seizoen die als laatste sfeerlaag op de scene wordt toegepast, zonder objecten, zonder volledige landschappen, zonder wijziging aan de bronfoto en zonder extra netwerkverkeer vanuit de wallpaper.

## 2. Waarom deze wijziging nodig is

YoWindow geeft een seizoensgebonden sfeer mee aan zijn scenes. Een winterscene voelt koeler en blauwer, een zomerscene warmer, en herfst en lente hebben elk hun eigen tint. De live wallpaper van deze app gebruikt echter een vaste fotobron en weergestuurde effecten zonder seizoensgevoel.

Een volledige YoWindow-aanpak met getekende landschappen, bomen en seizoensobjecten valt buiten scope en buiten de architectuur van deze app. Dit experiment neemt alleen het bruikbare sfeerprincipe over: een subtiele kleurgrading die het seizoen voelbaar maakt met beperkte complexiteit.

Zonder deze grading:

- voelt de scene neutraal en seizoensloos;
- ontbreekt het warme of koele karakter dat een seizoen herkenbaar maakt;
- mist de wallpaper een goedkope manier om sfeer toe te voegen zonder nieuwe assets.

De gewenste ervaring is een scene die in de winter net iets koeler en blauwer aanvoelt en in de zomer net iets warmer, terwijl de gebruiker dit volledig kan uitschakelen.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
   - beheert `WallpaperService.Engine` en de renderthread;
   - leest lokale locatie- en weerdata;
   - bepaalt weather kind en day/night;
   - tekent hemel, celestial body, wolken, locatie-afbeelding en voorgrondeffecten;
   - is het natuurlijke punt waar een laatste gradinglaag wordt toegevoegd.

2. `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperWeatherEffectRenderer.kt`
   - gebruikt AGSL `RuntimeShader` vanaf Android 13;
   - gebruikt een Canvas fallback op oudere Android-versies;
   - bevat background-, foreground- en glass-passes.

3. `app/src/main/kotlin/org/breezyweather/wallpaper/LiveWallpaperConfigManager.kt`
   - bewaart geforceerde of automatische weather/day-night-keuzes;
   - is de plek voor de experimentele seizoensgrading-flag.

4. `app/src/main/kotlin/org/breezyweather/wallpaper/LiveWallpaperConfigActivity.kt`
   - bevat de wallpaperinstellingen en rotating testmodus;
   - krijgt een toggle voor het experiment.

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

Deze volgorde moet intact blijven. De seizoensgrading komt conceptueel als optionele laatste laag boven de scene maar onder het rotating testlabel.

### Huidig gedrag

De scene heeft geen enkel besef van seizoen. Kleuren worden bepaald door daylightfactor en weather kind. Er is geen mechanisme dat de algehele kleurtemperatuur of helderheid op basis van de kalendermaand of het seizoen aanpast.

## 4. Afbakening

### Wel uitvoeren

- een experimentele, uitschakelbare seizoensgrading;
- een subtiele kleurtemperatuur- en lichtverschuiving per seizoen;
- een implementatie als laatste gradinglaag op de gerenderde scene;
- AGSL- en Canvas-implementatie;
- een experimentele flag in de configuratie, standaard uit;
- pure unit tests voor de seizoensbepaling en gradingfactoren;
- handmatige/emulatortests voor de vier seizoenen.

### Niet uitvoeren

- geen seizoensobjecten, bomen, sneeuwbergen of getekende landschappen;
- geen wijziging aan de bronfoto of fotopipeline;
- geen permanente bewerking van de bitmapcache;
- geen nieuwe weather families;
- geen automatische foto-download: dat is ACT-010;
- geen wijziging aan Meteo-, GPS- of RemoveSky-clients;
- geen OpenGL-migratie;
- geen UI-herontwerp buiten een enkele experimentele toggle;
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
- een eigen tweede weather cache introduceren;
- extra netwerkdata ophalen voor seizoensbepaling.

Het seizoen moet worden afgeleid uit reeds beschikbare lokale data: de scene state, de lokale datum en bij voorkeur de breedtegraad van de locatie voor het noordelijk of zuidelijk halfrond.

## 6. Prerequisite ACT-001 en ACT-002

ACT-012 moet voortbouwen op de centrale immutable scene state uit ACT-001. Controleer voor aanvang welke class en velden ACT-001 daadwerkelijk heeft toegevoegd.

De seizoensgrading heeft minimaal nodig:

- een betrouwbare lokale datum/tijd;
- bij voorkeur de breedtegraad van de actieve locatie om halfrond en seizoen correct te bepalen;
- de daylightfactor zodat de grading dag en nacht respecteert.

Als ACT-002 beschikbaar is, moet de gradingfactor mee-interpoleren met andere scene-overgangen zodat een seizoenswissel of het in- en uitschakelen van het experiment niet abrupt is. Als ACT-002 nog niet gemerged is, mag ACT-012 zelf een kleine eigen fade gebruiken, maar geen tweede transition-architectuur opzetten.

Als ACT-001 nog niet is gemerged, stop dan en rapporteer deze dependency. Voeg niet stilzwijgend een tweede scene-state-architectuur toe.

## 7. Gewenst seizoensmodel

### Seizoensbepaling

Bepaal het seizoen als pure functie, zonder Android-afhankelijkheid waar mogelijk:

```kotlin
enum class WallpaperSeason {
    WINTER,
    SPRING,
    SUMMER,
    AUTUMN,
}

fun seasonFor(
    date: LocalDate,
    latitude: Double?,
): WallpaperSeason
```

Regels:

- gebruik de kalendermaand als basis;
- keer het seizoen om voor het zuidelijk halfrond als de breedtegraad negatief is;
- als de breedtegraad onbekend is, neem het noordelijk halfrond aan als veilige default;
- bepaal het seizoen niet op basis van temperatuur, om sprongen door weersextremen te voorkomen.

### Seizoensgrading

Iedere `WallpaperSeason` levert een kleine, genormaliseerde gradingbeschrijving:

```kotlin
data class SeasonGrading(
    val warmthShift: Float,      // -1f koeler .. +1f warmer
    val tint: Int,               // subtiele ARGB-overlaykleur
    val tintStrength: Float,     // 0f..1f, klein gehouden
    val brightnessShift: Float,  // -1f donkerder .. +1f lichter
    val saturationShift: Float,  // -1f minder .. +1f meer verzadiging
)
```

Aanbevolen richting per seizoen, allemaal subtiel:

| Seizoen | Karakter | warmthShift | brightnessShift | saturationShift |
|---|---|---:|---:|---:|
| Winter | koel, blauwig, iets bleker | -0.25 | -0.05 | -0.10 |
| Lente | fris, licht, iets groener | +0.05 | +0.05 | +0.10 |
| Zomer | warm, helder, verzadigd | +0.20 | +0.05 | +0.10 |
| Herfst | warm-amber, iets gedempt | +0.15 | -0.05 | +0.05 |

Alle waarden zijn richtwaarden. De uiteindelijke sterkte moet centraal begrensd worden zodat het effect altijd subtiel blijft.

### Globale sterktebegrenzing

Definieer een centrale maximale gradingsterkte zodat het experiment nooit te sterk wordt:

```kotlin
const val MAX_SEASON_GRADING_STRENGTH = 0.35f
```

De effectieve grading is altijd `baseGrading * userOrDefaultStrength`, begrensd op `MAX_SEASON_GRADING_STRENGTH`.

## 8. Toepassing als laatste gradinglaag

### Plaatsing

De seizoensgrading is geen objectlaag maar een kleurbewerking op de reeds gerenderde scene. Conceptueel:

```kotlin
drawScene(canvas, interpolatedState) // hemel t/m glass rain
if (seasonGradingEnabled) {
    applySeasonGrading(canvas, effectiveGrading, daylightFactor)
}
drawRotatingTestLabel(canvas)
```

**Concrete plaatsing**: de grading wordt toegepast na alle effecten uit de laagvolgorde in sectie 3 (hemel t/m glass rain drops) en voor het rotating testlabel. Op AGSL is dit een uitbreiding van de bestaande eindshader (geen extra renderpass); op Canvas is dit de laatste `ColorMatrixColorFilter`/tint-overlay op de `Paint` waarmee de scene als geheel is getekend, voordat het testlabel met een eigen, ongefilterde `Paint` wordt getekend.

De grading mag de bronfoto, de bitmapcache of de fotopipeline niet wijzigen. Het effect bestaat alleen tijdens het tekenen.

### Dag/nacht-respect

De grading moet de daylightfactor respecteren:

- pas de warmte- en helderheidsverschuiving overdag iets sterker toe;
- houd de grading 's nachts terughoudend zodat de bestaande nachtkleuring leidend blijft;
- voorkom dat een zomerse warmtegrading een nachtelijke scene onnatuurlijk oranje maakt.

### Geen fotobewerking

Belangrijk en expliciet: de grading wijzigt nooit de opgeslagen of gedecodeerde bronfoto. Het is een tijdelijke kleuroverlay of kleurmatrix tijdens draw. Bij het uitschakelen van het experiment verdwijnt het effect volledig en blijft de oorspronkelijke scene over.

## 9. Rendererstrategie

### AGSL

Voeg uniforms toe voor de gradingparameters, bijvoorbeeld:

```glsl
uniform float seasonWarmth;
uniform float seasonBrightness;
uniform float seasonSaturation;
uniform float seasonTintStrength;
uniform float4 seasonTint;
uniform float seasonGradingAlpha;
```

Pas de grading toe op de uiteindelijke kleur:

```text
gradedColor = applyWarmth(color, seasonWarmth)
gradedColor = applyBrightness(gradedColor, seasonBrightness)
gradedColor = applySaturation(gradedColor, seasonSaturation)
gradedColor = mix(gradedColor, seasonTint.rgb, seasonTintStrength)
finalColor = mix(color, gradedColor, seasonGradingAlpha)
```

`seasonGradingAlpha` is 0 wanneer het experiment uit staat en faden tijdens een overgang.

### Canvas fallback

De Canvas renderer past dezelfde grading toe met een herbruikt `ColorMatrixColorFilter` en eventueel een lichte tintoverlay:

- bereken de `ColorMatrix` voor warmte, helderheid en saturatie eenmalig per gradingwijziging;
- hergebruik de `Paint` en `ColorMatrixColorFilter`-instanties;
- gebruik geen nieuwe Paint, ColorMatrix of Bitmap per frame;
- pas de tint toe met een herbruikte overlay-Paint met lage alpha;
- gebruik `saveLayer`/`saveLayerAlpha` alleen als het niet anders kan en nadat performance is gemeten.

### Geen offscreen kopie per frame

Maak geen full-screen offscreen bitmap per frame om de grading toe te passen. Werk waar mogelijk met een color filter op de bestaande draw, niet met een dure compositiestap.

## 10. Experimentele flag

### Configuratie

Voeg in `LiveWallpaperConfigManager` een experimentele instelling toe:

```kotlin
var seasonGradingExperimentEnabled: Boolean // default false
var seasonGradingStrength: Float             // default 0.5f, bereik 0f..1f
```

Concrete defaults en opslag:

- `seasonGradingExperimentEnabled` default `false`, opgeslagen als `LiveWallpaperConfigManager.seasonGradingEnabled` (of de exacte naam die aansluit op bestaande boolean-instellingen in dat bestand);
- `seasonGradingStrength` default `0.5f`, geklemd op `0f..1f` voordat deze met `MAX_SEASON_GRADING_STRENGTH` wordt vermenigvuldigd tot de effectieve sterkte.

De flag staat standaard uit. De grading verschijnt alleen als de gebruiker het experiment expliciet aanzet.

### UI

Voeg in `LiveWallpaperConfigActivity` een enkele, duidelijk gelabelde experimentele toggle toe, bijvoorbeeld onder een sectie "Experimenteel". Houd de UI minimaal:

- een schakelaar "Seizoensgrading (experimenteel)";
- optioneel een sterkteregelaar met conservatief bereik;
- een korte uitleg dat het effect subtiel is en de bronfoto niet wijzigt.

Voeg geen uitgebreide nieuwe instellingenpagina toe.

## 11. Overgang en in-/uitschakelen

Het in- en uitschakelen van het experiment en een seizoenswissel mogen niet abrupt zijn.

- als ACT-002 beschikbaar is, laat `seasonGradingAlpha` en de gradingparameters mee-interpoleren via de bestaande transition controller;
- bij een seizoenswissel rond middernacht op de seizoensgrens moet de grading geleidelijk over enkele seconden of via de bestaande scene-overgang wisselen;
- bij het uitzetten van de flag faden de gradingparameters naar neutraal voordat de laag volledig wordt overgeslagen;
- een directe harde sprong is alleen acceptabel als animaties zijn uitgeschakeld.

## 12. Lifecycle

### Visibility

Wanneer de wallpaper onzichtbaar wordt, stoppen de framecallbacks zoals nu. De gradingstate blijft bewaard zodat bij terugkeer geen herberekening of pop optreedt.

### Surface recreation

Bij surface resize of recreation:

- behoud de huidige seizoens- en gradingstate;
- herbereken alleen de eventuele resolutie-uniforms;
- maak geen nieuwe ColorMatrix of Paint zonder noodzaak.

### Animaties uit

Als animaties uitgeschakeld zijn:

- pas de grading direct toe zonder fade;
- teken exact een geldig frame;
- plan geen losse gradinganimatieloop.

## 13. Performance-eisen

- maximaal 30 FPS;
- geen full-screen bitmapallocatie per frame;
- geen nieuwe Paint, ColorMatrix, ColorFilter of Bitmap per frame;
- gradingparameters alleen herberekenen bij een seizoens-, flag- of sterktewijziging;
- uniforms alleen bijwerken als nodig;
- rendering stopt wanneer de wallpaper onzichtbaar is;
- geen logging per frame;
- wanneer de flag uit staat mag er geen meetbare extra kost zijn ten opzichte van de huidige scene.

Meet minimaal:

- frametijd met grading uit, ter controle dat er geen overhead is;
- frametijd met grading aan op AGSL;
- frametijd met grading aan op Canvas fallback;
- effect van een seizoenswissel met actieve ACT-002-overgang.

## 14. Logging en privacy

Alle nieuwe logging is debug-only en niet per frame.

Toegestane informatie:

- bepaald seizoen;
- gebruikt halfrond;
- of het experiment aan of uit staat;
- gekozen gradingsterkte;
- fallback naar Canvas door shaderfout.

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten of exacte breedtegraad;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- lokale fotopaden als die persoonsgegevens kunnen bevatten.

Log de breedtegraad niet exact; log hooguit het afgeleide halfrond.

## 15. Voorgestelde implementatiestappen

1. Controleer en documenteer de ACT-001 scene-state API en de beschikbaarheid van datum en breedtegraad.
2. Voeg een pure `seasonFor()`-functie toe met halfrondcorrectie.
3. Voeg een pure mapping van seizoen naar `SeasonGrading` toe met centrale begrenzing.
4. Voeg de experimentele flag en sterkte toe aan `LiveWallpaperConfigManager`, standaard uit.
5. Voeg een minimale experimentele toggle toe aan `LiveWallpaperConfigActivity`.
6. Implementeer de grading als laatste gradinglaag in AGSL.
7. Implementeer dezelfde grading in de Canvas fallback met herbruikte ColorMatrix en Paint.
8. Respecteer de daylightfactor in de gradingsterkte.
9. Laat de grading mee-interpoleren via ACT-002 of via een kleine eigen fade.
10. Zorg dat uitschakelen het effect volledig en zonder restbeeld verwijdert.
11. Voeg pure unit tests toe.
12. Build debug en release.
13. Test op emulator en gekoppelde telefoon.
14. Maak screenshots van de vier seizoenen met grading aan en uit.
15. Controleer `git diff` op scope en secrets.

## 16. Unit tests

Voeg tests toe voor pure logica, zonder echte Surface of WallpaperService waar dat niet nodig is.

Minimale testgevallen:

1. december, januari en februari geven WINTER op het noordelijk halfrond;
2. juni, juli en augustus geven SUMMER op het noordelijk halfrond;
3. dezelfde maanden geven het tegenovergestelde seizoen bij negatieve breedtegraad;
4. onbekende breedtegraad valt terug op het noordelijk halfrond;
5. iedere maand levert exact een geldig seizoen op;
6. de gradingmapping levert voor ieder seizoen waarden binnen het toegestane bereik;
7. de effectieve gradingsterkte wordt begrensd op `MAX_SEASON_GRADING_STRENGTH`;
8. gradingsterkte 0 levert een neutrale grading op die de kleur niet wijzigt;
9. de flag uit levert `seasonGradingAlpha` 0 op;
10. de daylightfactor verlaagt de gradingsterkte 's nachts;
11. een seizoenswissel produceert een continue gradingovergang en geen sprong;
12. de seizoensbepaling is deterministisch voor een gegeven datum en breedtegraad;
13. het in- of uitschakelen van `seasonGradingExperimentEnabled` tijdens een actieve ACT-002-transitie laat `seasonGradingAlpha` geleidelijk naar de nieuwe waarde lopen in plaats van direct te springen.

Gebruik een injecteerbare datum en breedtegraad. Tests mogen niet afhankelijk zijn van de echte huidige datum.

## 17. Handmatige testmatrix

### Seizoenen

| Seizoen | Verwachting |
|---|---|
| Winter | scene voelt iets koeler en blauwer, subtiel |
| Lente | scene voelt fris en licht, subtiel groener |
| Zomer | scene voelt warmer en iets verzadigder |
| Herfst | scene voelt warm-amber en iets gedempt |

### Modi

- experiment uit: scene identiek aan huidige versie;
- experiment aan op standaardsterkte;
- experiment aan op lage sterkte;
- experiment aan overdag;
- experiment aan 's nachts, grading blijft terughoudend;
- noordelijk halfrond;
- zuidelijk halfrond;
- onbekende breedtegraad;
- in- en uitschakelen tijdens een actieve scene;
- animations disabled;
- photo background enabled en disabled.

### Platforms

- Android 13 of hoger op gekoppelde telefoon;
- actieve emulator;
- waar mogelijk Android 12 of lager voor Canvas fallback.

## 18. Screenshotset

Maak minimaal screenshots van:

1. winter met grading aan;
2. zomer met grading aan;
3. herfst met grading aan;
4. lente met grading aan;
5. zomer met grading uit ter vergelijking;
6. nachtscene met grading aan om subtiliteit te tonen;
7. zuidelijk halfrond in dezelfde maand als noordelijke winter;
8. Canvas fallback met grading aan.

Screenshots moeten seizoen, halfrond, grading aan/uit en platform in de bestandsnaam krijgen.

## 19. Acceptatiecriteria

1. Het experiment staat standaard uit en kan via een duidelijke toggle worden ingeschakeld.
2. Met het experiment uit is de scene visueel en qua performance gelijk aan de huidige versie.
3. Het seizoen wordt correct bepaald op basis van datum en halfrond.
4. De grading is altijd subtiel en blijft binnen de centrale sterktebegrenzing.
5. De grading wijzigt nooit de bronfoto, de bitmapcache of de fotopipeline.
6. Uitschakelen verwijdert het effect volledig zonder restbeeld.
7. De grading respecteert de daylightfactor en blijft 's nachts terughoudend.
8. Een seizoenswissel en het in-/uitschakelen verlopen zonder abrupte sprong, behalve als animaties uit staan.
9. Android 13+ AGSL compileert zonder fouten.
10. Canvas fallback crasht niet en toont een vergelijkbare subtiele grading.
11. Er worden geen objecten, bomen of landschappen toegevoegd.
12. Er zijn geen netwerk-, GPS- of RemoveSky-calls vanuit de wallpaper toegevoegd.
13. Er worden geen secrets of exacte locaties gelogd.
14. De wallpaper blijft begrensd op maximaal 30 FPS.
15. Bestaande foto-, cache-, parallax- en rotatingfunctionaliteit blijft werken.

## 20. Definition of done

- implementatie is beperkt tot wallpaper grading-, scene- en configuratiecode en gerichte tests;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- screenshotset is gemaakt;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-012-bestanden;
- commit en push gebeuren pas nadat de visuele controle is goedgekeurd.

## 21. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, RemoveSky-, iconpack- of andere niet- ACT-012-bestanden;
- neem bestaande wijzigingen in gedeelde wallpaperbestanden als uitgangspunt;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg nieuwe classes bij voorkeur in een klein eigen seizoensgradingbestand toe;
- houd wijzigingen in `MaterialLiveWallpaperService.kt` en `WallpaperWeatherEffectRenderer.kt` zo lokaal mogelijk;
- stage bestanden expliciet, nooit via `git add .`;
- meld conflicten met ACT-001 of ACT-002 in plaats van een tweede architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 22. Verwacht eindresultaat

Na ACT-012 kan de gebruiker een experimentele seizoensgrading inschakelen die de scene in de winter net iets koeler en blauwer maakt en in de zomer net iets warmer, met passende lente- en herfsttinten. Het effect is subtiel, respecteert dag en nacht, laat de bronfoto volledig ongemoeid en is op ieder moment uit te schakelen. De grading werkt op zowel AGSL als Canvas, voegt geen netwerkverkeer toe, blijft begrensd op maximaal 30 FPS en heeft geen meetbare kost wanneer hij uit staat.