# Visuele effecten per weertype

Overzicht van wat er visueel gebeurt in de live wallpaper per weertype. De
weereffecten worden procedureel gegenereerd; er worden geen PNG-wolken gebruikt.
Op Android 13 en hoger rendert een AGSL-shader de effecten. Oudere apparaten
gebruiken de Canvas-fallback met dezelfde sceneparameters.

---

## Effectentabel

| Weertype | Hemelkleur | Wolken | Neerslag | Glas-druppels | Mist / Nevel | Bliksem | Sterren | Fotodimming |
|---|---|---|---|---|---|---|---|---|
| **Onbewolkt** | Helder blauw (dag) / diepblauw (nacht) | — | — | — | — | — | ✓ nacht | geen |
| **Licht bewolkt** | Lichtblauw | Drie verspreide dieptelagen | — | — | — | — | ✓ nacht | weinig |
| **Bewolkt** | Iets grijzer blauw | Vijf overlappende lagen | — | — | — | — | — | matig |
| **Wind** | Lichtblauw-grijs | Matige wolken, snel bewegend | — | — | — | — | — | licht |
| **Nevel** | Wazig blauw | Lichte wolken | — | — | ✓ Nevel | — | — | licht |
| **Mist** | Grijs-wit | Matige wolken | — | — | ✓✓ Dikke mist | — | — | matig |
| **Regen** | Donkergrijs | Vijf lagen en volledig dekkend regenplafond | ✓ Regenstrepen | ✓ Druppels | — | — | — | sterk |
| **Natte sneeuw** | Donkergrijs | Vol wolkendek | ✓ Gemengd | ✓ Licht | — | — | — | sterk |
| **Sneeuw** | Lichtgrijs-wit | Dik wolkendek | ✓ Sneeuwvlokken | — | — | — | — | matig |
| **Hagel** | Donkergrijs | Volledig bedekt | ✓ Hagelstenen | — | — | — | — | zeer sterk |
| **Onweer** | Zwaar donkergrijs | Vijf lagen en volledig dekkend donker plafond | — | — | — | ✓ Bliksem | — | zeer sterk |
| **Onweersbui** | Zwart-grijs | Vijf lagen, volledig bedekt en zeer donker | ✓ Zware regen | ✓✓ Veel | — | ✓✓ Veel bliksem | — | maximaal |

---

## Samengesteld weermodel

`WallpaperSceneStateFactory` maakt niet langer één ondeelbaar effectprofiel per
weeromschrijving. Een `WallpaperEffectCondition` bestaat uit onafhankelijke
assen die gelijktijdig kunnen worden gerenderd:

| As | Waarden |
|---|---|
| Hemel | `CLEAR`, `FAIR`, `PARTLY_CLOUDY`, `MOSTLY_CLOUDY`, `OVERCAST` |
| Neerslag | `NONE`, `DRIZZLE`, `RAIN`, `SLEET`, `SNOW`, `HAIL` |
| Intensiteit | `NONE`, `LIGHT`, `MODERATE`, `HEAVY` |
| Zicht | `CLEAR`, `HAZE`, `FOG`, `DENSE_FOG` |
| Onweer | continue waarde van 0.0 tot 1.0 |
| Wind | rustig of winderig, plus de bestaande continue `windFactor` |

De grove `WeatherCode` levert de basis. In automatische modus verfijnen echte
meetwaarden het resultaat:

- `cloudCover` kiest de hemelklasse bij 10%, 30%, 60% en 85%;
- mm/u onderscheidt motregen, normale en zware neerslag;
- zicht onder 10 km voegt nevel toe, onder 5 km mist en onder 1 km dichte mist;
- wind of windstoten vanaf 8 m/s markeren de scene als winderig;
- een neerslagcode garandeert minimaal een volledig bewolkte hemel, ook als een
  provider tegelijk een onwaarschijnlijk lage bewolkingsgraad meldt.

Hierdoor zijn combinaties mogelijk zoals **zware regen + dichte mist** of
**zware regen + onweer**, zonder daarvoor een nieuwe rendererklasse te maken.
De neerslagintensiteit bepaalt nu ook rechtstreeks hoeveel shader-/deeltjeslagen
worden gebruikt. In handmatige en roterende testmodus worden actuele bewolking
en zicht bewust genegeerd, zodat het gekozen testtype herkenbaar blijft.

Regen, onweer en onweersbui krijgen in `CloudFieldFactory` daarnaast een
dekkingsfactor van `1.15`, zodat het donkere wolkenplafond duidelijk zichtbaar is.

---

## Uitleg van de effecten

### Wolken
Bewegende wolkvormen via AGSL-shader (`cloudShape` + `driftingCloud`).
`cloudDensity` bepaalt hoe vol de hemel is; `cloudDarkness` hoe grijs.
Bij hogere dichtheid overlappen wolken elkaar en vormen ze een aaneengesloten dek.

Iedere scene bevat vijf vaste dieptelagen, van achter naar voren:

| Laag | Diepte | Schaal | Relatieve snelheid | Alpha-aandeel | Verticale offset |
|---|---:|---:|---:|---:|---:|
| Achtergrond | 0.00 | 0.55 | 0.35 | 0.38 | -0.06 |
| Ver | 0.25 | 0.70 | 0.50 | 0.50 | -0.03 |
| Midden | 0.50 | 0.88 | 0.65 | 0.64 | 0.00 |
| Dichtbij | 0.75 | 1.08 | 0.82 | 0.78 | +0.03 |
| Voorgrond | 1.00 | 1.30 | 1.00 | 0.92 | +0.06 |

Verre wolken zijn kleiner, lichter en langzamer. Wolken dichtbij zijn groter,
donkerder en bewegen sneller. Dit levert parallax op: de gebruiker ervaart de
langzame lagen als verder weg. De vormen krijgen per instantie een andere seed,
extra lobben en geanimeerde ruis, waardoor wolken niet exact op elkaar lijken.

Onbewolkt maakt alle vijf lagen transparant. Licht bewolkt, mist en nevel tonen
laag 1, 3 en 5. De overige weertypen kunnen alle vijf lagen gebruiken. Bij hoge
dichtheid worden extra wolkmassa's toegevoegd. Regen, onweer en onweersbui
vullen bovendien de resterende openingen met een donker wolkenplafond.

### Hemelkleur
De hemelgradient loopt van nacht (diepblauw) → dageraad (paars/oranje) →
dag (blauw) → schemering (oranje/paars) → nacht. Bij stijgende `cloudDarkness`
verschuift de kleur richting overcast-grijs zodat een onweershemel er niet
blauw uitziet.

### Neerslag
Valt schuin bij wind — windrichting + windkracht (Beaufort) bepalen de
hellingshoek automatisch. Typen:
- **Regen** — smalle blauwe strepen
- **Sneeuw** — witte vlokken (vier lagen, elk formaat)
- **Hagel** — ronde bollen, groter en sneller dan regen
- **Natte sneeuw** — mix van regen en sneeuw

### Glas-druppels
Druppels die over het schermoppervlak glijden (zoals regen op een ruit).
Los van de neerslag in de lucht. Zichtbaar bij regen, natte sneeuw en onweersbui.
Gewoon onweer heeft zelf geen glasdruppels. In de roterende test kan tijdens de
crossfade wel kort een druppellaag van de vorige of volgende scene zichtbaar zijn.

### Mist vs. Nevel
- **Mist** (`fogIntensity`) — dikke witte sluier die het zicht sterk beperkt
- **Nevel** (`hazeIntensity`) — lichtere, bruinachtige waas (smog/dunst)

### Bliksem
Willekeurige witte flitsen over het scherm (onweer + onweersbui).
Bij onweersbui zijn ze frequenter en intenser.

### Sterren
Zichtbaar bij onbewolkt en licht bewolkt, alleen 's nachts (`daylight < 0.5`).
Positie is gekoppeld aan de locatie-seed zodat ze per locatie anders staan.

### Fotodimming
De locatiefoto wordt donkerder gecombineerd uit:
- **Nachtdimming** — hoe donkerder de hemel (daylight → 0), hoe meer dimming
- **Wolkdimming** — `cloudDensity × cloudDarkness × 1.3`, geclampd op 1.0

Formule: `photoDimming = 1 − (1 − nacht) × (1 − wolk)`

Resultaat: een zonnige zomerfoto ziet er onder zware regenbuien of 's nachts
donker en realistisch uit in plaats van vrolijk en zonnig.

---

## Renderkwaliteit

De gekozen kwaliteit bepaalt hoeveel van de vijf wolkenlagen werkelijk worden
gerenderd. Bij een lager budget blijven een verre en een nabije laag behouden,
zodat het parallax-effect niet volledig verdwijnt.

| Profiel | Wolkenlagen | Selectie |
|---|---:|---|
| Battery saver | 2 | verste en voorste laag |
| Balanced | 4 | alle lagen behalve de middelste |
| High | 5 | alle lagen |

De renderer kan tijdelijk naar een lager profiel schakelen wanneer frames te
lang duren. De maximale rendersnelheid blijft in alle profielen 30 FPS.

---

## Roterende test

De optie **Rotating test (10 seconds)** kiest elke 10 seconden het volgende
scenario. De volgorde is:

`Onbewolkt → Helder → Licht bewolkt → Meest bewolkt → Zwaar bewolkt →`
`Motregen → Regen → Zware regen → Lichte sneeuw → Sneeuw → Zware sneeuw →`
`Natte sneeuw → Hagel → Nevel → Mist → Dichte mist → Onweer →`
`Onweersbui → Wind → Regen + dichte mist`

Daarmee komen alle genormaliseerde hemelklassen, neerslagsoorten,
intensiteitsklassen en zichtklassen voor. De laatste scene controleert expliciet
dat twee effecten tegelijk kunnen worden samengesteld.

Tussen twee testscenes loopt een crossfade van 2 seconden. Tijdens die overgang
worden de uitgaande en inkomende achtergrondeffecten, neerslag en glasdruppels
samen getekend. Het label toont al de nieuwe scene; daardoor kan bijvoorbeeld
aan het begin van Sneeuw nog kort de druppellaag van Regen zichtbaar zijn.

---

## Windeffect op wolksnelheid

Windsnelheid (m/s en windstoten) verhogen de `windFactor` die wolken sneller
laat bewegen en neerslag meer laat schuin vallen:

| Windsnelheid | windFactor |
|---|---|
| < 8 m/s | 1.0 (rustig) |
| 8–14 m/s | 1.0 → 2.8 (matig) |
| > 14 m/s | 3.6 (storm) |
| Onweer (minimum) | 2.8 |
| Wind-type (minimum) | 4.2 |
