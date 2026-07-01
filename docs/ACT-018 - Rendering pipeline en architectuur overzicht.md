# ACT-018 — Rendering pipeline en architectuur overzicht

> Gegenereerd door codebase-exploratie — 2026-07-01

---

## 1. Projectstructuur

**Modules:**

| Module | Doel |
|---|---|
| `app/` | Hoofd-app module (wallpaper engine, UI) |
| `data/` | Data-laag / repository implementaties |
| `domain/` | Domeinmodellen en businesslogica |
| `ui-weather-view/` | Herbruikbare weerweergave-component |
| `weather-unit/` | Eenhedenconversie |
| `maps-utils/` | Kaart-utilities |
| `buildSrc/` | Custom Gradle tasks |

**Relevante directories:**

```
app/src/main/
├── kotlin/org/breezyweather/
│   ├── wallpaper/                  # Live wallpaper engine (42 .kt bestanden)
│   │   └── photo/                  # Fotoverwerking en sky removal
│   └── ui/
│       └── theme/weatherView/materialWeatherView/
│           └── implementor/        # Animatie-implementaties
├── res/drawable-nodpi/             # Wallpaper assets
└── assets/
    └── sky_segmentation_ade20k.tflite  # ML-model voor sky-detectie
```

---

## 2. Layer & compositing systeem

De render-pipeline gebruikt **multi-layer compositing** met AGSL hardware shaders (Android 13+) en een Canvas fallback.

### Volgorde van lagen (achter → voor)

| # | Laag | Parallax factor |
|---|---|---|
| 1 | Sky gradient (achtergrond) | 5% |
| 2 | Zon / Maan met glow | 2% |
| 3 | Weather effects — background pass (wolken, sterren, fog) | — |
| 4 | Voorgrond foto (RGBA, transparante lucht) | 15% |
| 5 | Weather effects — foreground pass | — |
| 6 | Glass rain refractie (optioneel) | — |
| 7 | Seizoensgrading (optioneel, kleur/helderheid) | — |

### Parallax systeem

```kotlin
PARALLAX_BG_FACTOR        = 0.05f   // Achtergrond verschuift 5% van schermbreedte
PARALLAX_CELESTIAL_FACTOR = 0.02f
PARALLAX_FG_FACTOR        = 0.15f   // Foto verschuift 15% van schermbreedte
```

Aangestuurd door de oriëntatiessensor. Laaggrenzen worden vergroot om de verschuiving op te vangen.

---

## 3. Sky removal & voorgrond RGBA laden

### On-device: SkySegmenter

**Bestand:** `wallpaper/photo/SkySegmenter.kt` (202 regels)

**Model:** `sky_segmentation_ade20k.tflite`
- Architectuur: DeepLab met MobileNetV3 backbone
- Input: 512×512 RGB uint8, genormaliseerd naar [-1, 1]
- Output: 512×512 float32 per-pixel klasse-ID (Cityscapes)
- Sky klasse-ID: 3

**Verwerkingsstroom:**

```kotlin
fun eraseSky(source: Bitmap): Bitmap? {
    // 1. Schaal naar 512×512
    val scaled = Bitmap.createScaledBitmap(source, 512, 512, true)
    // 2. Normaliseer naar [-1, 1] float32
    // 3. Voer TFLite-interpreter uit
    val labels = output[0]  // [512][512] float32
    // 4. Valideer lucht (>8%, bovenaan, centroid Y<0.4, luchtkleur)
    // 5. Masker: alpha=0 voor luchtpixels
    applyMask(source, labels)
}
```

**Validatieregels:**
- Minimum luchtfractie: 8%
- Bovenste 15% van afbeelding moet ≥ 30% lucht zijn
- Lucht-centroid Y < 0.4 (geconcentreerd bovenaan)
- Onderste band < 15% lucht
- Minimaal 60% van gedetecteerde lucht moet luchtkleur hebben (blauw/helder)

### Server-side: RemoveSkyProvider

**Bestand:** `wallpaper/photo/RemoveSkyProvider.kt` (350+ regels)

- Verbindt met `removesky.vanburik.info` voor server-side verwerking
- Geeft vooraf verwerkte transparante PNGs terug
- On-device `SkySegmenter` als fallback
- Metadata: `dayPeriod`, `country`, `season`, EXIF GPS

### Download pipeline (`WallpaperRepository.refreshFor()`)

```
1. resolveImage(lat, lon, place)  →  ImageResult met URL
2. downloadSkyBitmap(url, alreadyProcessed)
   ├─ RemoveSky al verwerkt: gebruik direct
   └─ Anders: voer SkySegmenter.eraseSky() uit
3. Cache als WebP met sha256-prefix
4. Activeer voor weergave
```

---

## 4. Wolk-animatie implementatie

### AGSL RuntimeShader (Android 13+ / API 33+)

**Bestand:** `wallpaper/WallpaperWeatherEffectRenderer.kt` (1400+ regels)

- Shader source: `SHADER_SOURCE` constante (regels 1064–1400+)
- Uniforms per frame (regels 272–331):
  - `time` — verstreken seconden
  - `windFactor` — wolksnelheid multiplier
  - `layerScale[5]`, `layerSpeed[5]`, `layerAlpha[5]` — per laag
  - `windDirection` — graden voor windrichting

### Canvas fallback (pre-Android 13)

Klasse `WallpaperWeatherEffectRenderer.CanvasRenderer`:
- `drawClouds(canvas)`
- `drawStars(canvas)`
- `drawFog(canvas)`
- `update()` — animeer deeltjesposities

### CloudField — 5 procedurele lagen

**Bestand:** `wallpaper/CloudField.kt` (116 regels)

```kotlin
data class CloudLayer(
    val depth: Float,          // 0f = ver, 1f = dichtbij (voor parallax)
    val scale: Float,          // Relatieve celgrootte
    val speedFactor: Float,    // Bewegingssnelheid
    val alpha: Float,          // Dekking 0..1
    val darkness: Float,       // 0f = licht, 1f = donker
    val verticalOffset: Float, // Y-positie
)
```

**Basissnelheden (achter → voor):** `[0.35f, 0.50f, 0.65f, 0.82f, 1.00f]`

Snelheid wordt ook beïnvloed door `windFactor`: `speedMultiplier = 0.3f + windFactor`

### Shader-assets

- `wallpaper_cloud_atlas.png` — wolk-textureatlas
- `wallpaper_overcast_mask.png` — dekking masker
- Geladen als `BitmapShader` met lineaire filtering

---

## 5. Render-loop per frame

**Bestand:** `MaterialLiveWallpaperService.kt` (2400+ regels), regels 315–410

```
1.  Lock canvas            → hardware canvas (Android 8+) of software
2.  Detecteer grootte      → oriëntatiewijzigingen, parallax-grenzen
3.  Teken achtergrond      → sky gradient (met parallax offset -bgOffset)
4.  Teken hemellichaam     → zon/maan met glow (met -celestialOffset)
5.  Weather bg pass        → wolken, fog, sterren (AGSL of Canvas)
6.  Teken voorgrond foto   → ARGB_8888 RGBA (met parallax offset -fgOffset)
7.  Weather fg pass        → voorgrond neerslag/effecten (optioneel)
8.  Glass rain refractie   → druppel-overlay met scene-texture sampling
9.  Seizoensgrading        → kleur/helderheid aanpassing (optioneel)
```

---

## 6. Klasse-relaties

```
MaterialLiveWallpaperService
└── WeatherEngine
    ├── WallpaperWeatherEffectRenderer
    │   ├── CloudFieldParams        (5 lagen)
    │   ├── FogFieldParams
    │   ├── StarFieldParams
    │   ├── RuntimeShader / CanvasRenderer
    │   └── BitmapShader (cloudAtlas, overcastMask)
    ├── WallpaperRepository
    │   ├── SkySegmenter (lazy)
    │   ├── RemoveSkyProvider
    │   └── WallpaperImageStore
    ├── WallpaperSceneState         (immutable render snapshot)
    ├── Drawable mBackground        (sky gradient)
    └── Drawable mForeground        (RGBA foto)
```

---

## 7. Sleutelbestanden

| Bestand | Regels | Doel |
|---|---|---|
| `MaterialLiveWallpaperService.kt` | 2400+ | Hoofd render-loop, laagbeheer |
| `WallpaperWeatherEffectRenderer.kt` | 1400+ | AGSL shader + canvas fallback |
| `WallpaperPhotoLayout.kt` | 86 | Foto positie (onderkant, 52% hoogte) |
| `SkySegmenter.kt` | 202 | TFLite sky-detectie & maskering |
| `RemoveSkyProvider.kt` | 350+ | Server-side sky removal, zoeken |
| `WallpaperRepository.kt` | 400+ | Afbeeldingsresolutie, download, cache |
| `CloudField.kt` | 116 | 5-laags procedurele wolk-definitie |
| `WallpaperSceneState.kt` | 350+ | Immutable weather render state |

---

## 8. Kwaliteitsprofielen & adaptieve degradatie

**Profielen** (`WallpaperQualityProfile.kt`):
- `HIGH` — maximale kwaliteit
- `BALANCED` — standaard (midrange)
- `BATTERY_SAVER` — minder deeltjes/lagen

**Automatische degradatie:**
- Monitort frame-tijd per frame
- Vermindert neerslag-lagen als gemiddelde frame > 16.7ms
- Verhoogt lagen weer als stabiel < 12ms gedurende 10 seconden

---

## 9. Uitbreidingspunt: diepte-gebaseerde wolk-doorvlucht

Om wolken *door* de voorgrond te laten vliegen (wolken achter verre gebouwen, maar voor dichtbije bomen) moet de voorgrond-foto worden opgesplitst op basis van een dieptekaart:

```
[sky gradient]
[cloud laag 1–3]               ← verre wolken
[foreground RGBA — ver]        ← gebouwen op afstand  (nieuw)
[cloud laag 4–5]               ← dichtbije wolken
[foreground RGBA — dichtbij]   ← bomen/hekken voorgrond (nieuw)
[foreground weather pass]
[glass rain]
```

**Benodigde server-uitbreiding:** de RemoveSky API geeft naast de RGBA ook een dieptekaart terug (gegenereerd met `depth-anything-v2`). `RemoveSkyProvider.kt` haalt dit als tweede afbeelding op en de app splits de voorgrond-bitmap in twee lagen op basis van de dieptedrempel.

> Zie ook: [RemoveSky integratie - getest schema.md](RemoveSky%20integratie%20-%20getest%20schema.md)
