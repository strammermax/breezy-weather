# ACT-000 - Conventies en gedeelde patronen

## Status

- Type: referentiedocument, geen losse implementatieopdracht
- Doel: gedeelde conventies vastleggen die door ACT-003 en hoger worden gebruikt, zodat effecten onderling consistent blijven zonder dat elk document dezelfde afspraken opnieuw moet uitleggen

## 1. Doel van dit document

Dit document is geen opdracht die op zichzelf wordt geimplementeerd. Het legt patronen vast die al impliciet door meerdere ACT-documenten worden gebruikt of veronderstald, zodat:

- nieuwe effecten dezelfde structuur volgen als bestaande effecten (zoals `CloudField.kt` uit ACT-003);
- overgangen tussen renderers consistent blijven (ACT-002);
- effectbudgetten via een centraal model lopen (ACT-007) zonder dat eerdere opdrachten daar tijdelijk omheen moeten werken;
- tests reproduceerbaar zijn over ACT-004, ACT-006 en ACT-008 heen.

Bij twijfel of conflict tussen dit document en een specifiek ACT-document, is het specifieke ACT-document leidend voor zijn eigen scope; dit document beschrijft de gedeelde basislijn.

## 2. Depth- en laagconcept (ACT-003-patroon)

`app/src/main/kotlin/org/breezyweather/wallpaper/CloudField.kt` is de canonieke referentie voor gelaagde of particle-gebaseerde effecten.

Kernpunten:

- een `depth: Float` van `0f` (ver/klein/traag) tot `1f` (dichtbij/groot/snel);
- een pure, testbare factory (bijvoorbeeld `CloudFieldFactory.cloudFieldParams()`) die parameters/lagen/particles berekent zonder Canvas-, AGSL- of Android-afhankelijkheden;
- een los renderdeel dat deze parameters consumeert en tekent, zonder zelf de parameterberekening te doen.

Effecten die dit patroon volgen of zouden moeten volgen:

- ACT-003 clouds (`CloudLayer`/`CloudFieldFactory`, reeds geimplementeerd);
- ACT-004 particle pool voor sneeuw en hagel (`depth` per particle);
- ACT-005 fog/haze depth bands (`verticalCenter`/snelheid per band);
- ACT-006 rain-on-glass (druppel-/profielparameters via een `GlassRainProfile`-achtige laag).

Nieuwe effecten met meerdere lagen of particles volgen dit patroon: pure parameterlaag + apart renderdeel, met `depth` 0f..1f als gedeelde conventie voor "ver" versus "dichtbij".

## 3. Transition-/contribution-conventie (ACT-002-patroon)

ACT-002 introduceert `TransitionManager` met `transitionProgress()` en een `contribution`/`transitionAlpha`-mechanisme voor het cross-faden tussen een uitgaande en een inkomende renderer.

Conventie voor alle effecten die hiermee te maken hebben:

- de uitgaande renderer gebruikt `contribution = 1f - transitionProgress`;
- de inkomende renderer gebruikt `contribution = transitionProgress`;
- `contribution` wordt vermenigvuldigd in de effect-alpha (bijvoorbeeld druppelalpha, gradingalpha, bandalpha), niet gebruikt om het effect helemaal aan of uit te zetten met een harde sprong;
- effecten met eigen aan/uit-overgangen (bijvoorbeeld een experimentele flag zoals ACT-012, of fog/haze die van 0 naar >0 gaat zoals ACT-005) volgen hetzelfde idee: een vloeiende alpha-factor in plaats van een binaire schakelaar.

Documenten die hier al expliciet op aansluiten: ACT-002 (bron), ACT-005, ACT-006, ACT-008 (testgeval met `transitionProgress = 0.5`), ACT-011 (timestamp-consistentie tijdens transitie), ACT-012 (flag-toggle tijdens transitie).

Als ACT-002 nog niet gemerged is op het moment van implementatie, mag een effect een kleine eigen fade gebruiken (zoals ACT-012 sectie 6 toestaat), maar nooit een tweede, concurrerende transition-architectuur.

## 4. Quality budget-conventie (ACT-007-patroon)

ACT-007 introduceert `WallpaperQualityProfile` (`BATTERY_SAVER`, `BALANCED`, `HIGH`) en een centraal `QualityBudget`-model met velden zoals `cloudLayers`, `maxSnowParticles`, `maxHailParticles`, `fogBands`, `blurStrength`, `maxGlassDrops` en `effectUpdateHz`.

Conventie:

- `QualityBudget` is de centrale, leidende plek voor effectbudgetten zodra ACT-007 is gemerged;
- als ACT-007 nog niet gemerged is, mag een effect (ACT-004, ACT-005, ACT-006) een lokale `enum class QualityLevel { LOW, BALANCED, HIGH }` (of vergelijkbaar) gebruiken, gevoed door de bestaande adaptive-quality-meting;
- na het mergen van ACT-007 lezen die effecten hun budget uit `QualityBudget` in plaats van uit het lokale model, zonder gedragsverandering;
- ACT-007 mag bestaande velden/aantallen uit ACT-003 t/m ACT-006 overnemen als startwaarden in plaats van ze te herontwerpen.

Zie de ownership-tabel in ACT-007 sectie 10 voor welk budgetveld door welke opdracht wordt gelezen.

## 5. Seeded random / reproduceerbaarheid

Effecten met particles, druppels of andere stochastische verdeling (ACT-004, ACT-006) en de visuele regressietests (ACT-008) volgen dezelfde conventie:

- gebruik een geinjecteerde `Random`-instantie of expliciete `seed: Long`, nooit een impliciete `Random()` of `Math.random()` in productiecode die getest moet worden;
- dezelfde seed en input leveren dezelfde particle-/druppelverdeling op;
- een andere seed verandert de verdeling deterministisch maar voorspelbaar;
- tests injecteren een vaste seed en vaste klok/tijd, zodat ze niet afhankelijk zijn van de echte huidige tijd.

## 6. Render-pass interface (aanbeveling)

Voor nieuwe foreground-/background-effecten is een gedeelde signatuur aan te raden, in lijn met hoe `WallpaperWeatherEffectRenderer` bestaande passes aanroept:

```kotlin
fun drawEffect(
    canvas: Canvas,
    scene: WallpaperSceneState,
    contribution: Float,
)
```

Dit is een aanbeveling, geen harde eis: bestaande passes hoeven niet te worden geherstructureerd om hieraan te voldoen. Nieuwe effecten die toch al een vergelijkbare signatuur nodig hebben, gebruiken bij voorkeur deze vorm zodat ACT-002-`contribution` en de scene state consistent worden doorgegeven.

## 7. Laagvolgorde (referentie)

De volledige laagvolgorde, zoals herhaald in ACT-003 t/m ACT-012, is:

1. hemel / achtergrondgradient;
2. zon of maan;
3. background weather pass (voornamelijk wolken, ACT-003);
4. transparante locatie-afbeelding;
5. bestaande Material Weather-effecten;
6. foreground weather pass: regen, sneeuw, hagel, mist, haze en bliksem (ACT-004, ACT-005);
7. glass rain drops (ACT-006);
8. optionele seizoensgrading als laatste kleurlaag (ACT-012);
9. rotating testlabel.

Deze volgorde moet door alle effect-opdrachten intact worden gelaten. Een nieuw effect dat zich hier niet in laat passen, meldt dit als scope-conflict in plaats van de volgorde stilzwijgend te wijzigen.

## 8. Samenwerkingsregels (herhaling)

Deze regels staan al in elk ACT-document maar gelden als gedeelde basislijn:

- lees eerst `git status` en de actuele diff;
- wijzig geen bestanden buiten de eigen ACT-scope (camera, RemoveSky, iconpack, etc.);
- revert nooit wijzigingen die niet door de eigen opdracht zijn gemaakt;
- stage bestanden expliciet, nooit via `git add .`;
- meld architectuurconflicten met andere ACT-opdrachten in plaats van een tweede, concurrerende architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op documentatie.
