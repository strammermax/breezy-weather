# Visuele effecten per weertype

Overzicht van wat er visueel gebeurt in de live wallpaper en in-app schermen
per weertype. Alle effecten worden procedureel gegenereerd (geen afbeeldingen).

---

## Effectentabel

| Weertype | Hemelkleur | Wolken | Neerslag | Glas-druppels | Mist / Nevel | Bliksem | Sterren | Fotodimming |
|---|---|---|---|---|---|---|---|---|
| **Onbewolkt** | Helder blauw (dag) / diepblauw (nacht) | — | — | — | — | — | ✓ nacht | geen |
| **Licht bewolkt** | Lichtblauw | Licht, 1–2 losse wolken | — | — | — | — | ✓ nacht | weinig |
| **Bewolkt** | Iets grijzer blauw | Dikker wolkendek | — | — | — | — | — | matig |
| **Wind** | Lichtblauw-grijs | Matige wolken, snel bewegend | — | — | — | — | — | licht |
| **Nevel** | Wazig blauw | Lichte wolken | — | — | ✓ Nevel | — | — | licht |
| **Mist** | Grijs-wit | Matige wolken | — | — | ✓✓ Dikke mist | — | — | matig |
| **Regen** | Donkergrijs | Vol wolkendek, zwaar | ✓ Regenstrepen | ✓ Druppels | — | — | — | sterk |
| **Natte sneeuw** | Donkergrijs | Vol wolkendek | ✓ Gemengd | ✓ Licht | — | — | — | sterk |
| **Sneeuw** | Lichtgrijs-wit | Dik wolkendek | ✓ Sneeuwvlokken | — | — | — | — | matig |
| **Hagel** | Donkergrijs | Volledig bedekt | ✓ Hagelstenen | — | — | — | — | zeer sterk |
| **Onweer** | Zwaar donkergrijs | Bijna volledig, erg donker | — | — | — | ✓ Bliksem | — | zeer sterk |
| **Onweersbui** | Zwart-grijs | Volledig bedekt, pikzwart | ✓ Zware regen | ✓✓ Veel | — | ✓✓ Veel bliksem | — | maximaal |

---

## Effect-parameters per weertype

Onderliggende waarden uit `WallpaperSceneStateFactory.effectProfile()`.

| Weertype | cloudDensity | cloudDarkness | precipitatie | fogIntensity | hazeIntensity | thunderIntensity | glassRain |
|---|---|---|---|---|---|---|---|
| Onbewolkt | 0.00 | 0.00 | — | — | — | — | — |
| Licht bewolkt | 0.35 | 0.05 | — | — | — | — | — |
| Bewolkt | 0.85 | 0.25 | — | — | — | — | — |
| Wind | 0.55 | 0.15 | — | — | — | — | — |
| Nevel | 0.35 | 0.10 | — | — | 0.65 | — | — |
| Mist | 0.65 | 0.35 | — | 0.85 | — | — | — |
| Regen | 0.95 | 0.55 | 0.75 | — | — | — | 0.70 |
| Natte sneeuw | 0.95 | 0.50 | 0.80 | — | — | — | 0.55 |
| Sneeuw | 0.90 | 0.45 | 0.75 | — | — | — | — |
| Hagel | 1.00 | 0.70 | 0.90 | — | — | — | — |
| Onweer | 0.95 | 0.70 | — | — | — | 0.55 | — |
| Onweersbui | 1.00 | 0.85 | 1.00 | — | — | 1.00 | 0.90 |

Alle waarden zijn 0.0–1.0. Bij neerslaggerichte typen worden deze waarden
geschaald door de werkelijke mm/u uit de weerdata (`precipFactor`), zodat
een motregen er anders uitziet dan een stortbui.

---

## Uitleg van de effecten

### Wolken
Bewegende wolkvormen via AGSL-shader (`cloudShape` + `driftingCloud`).
`cloudDensity` bepaalt hoe vol de hemel is; `cloudDarkness` hoe grijs.
Bij hogere dichtheid overlappen wolken elkaar en vormen ze een aaneengesloten dek.

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
