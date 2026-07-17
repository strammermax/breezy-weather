# ACT-020 - Batterij- en prestatie-optimalisatie (resultatenrapport)

## Status

- Type: resultatenrapport (afgeronde opdracht, geen implementatie-opdracht zoals de andere ACT-documenten)
- Uitgevoerd: juli 2026
- Omvang: 3 modules (live wallpaper, caching/achtergrondtaken, macrobenchmark-tooling)
- Gerelateerd: ACT-001 (scene state), ACT-007 (quality profiles), ACT-009 (frame-/lifecycletelemetrie), ACT-010 (foto-refresh), ACT-018 (renderpijplijn)

## 1. Opdracht in een zin

Onderzoek en verhelp onnodig batterij-/CPU-/GPU-verbruik in de live wallpaper, de foto-caching en de achtergrondtaken, en bewijs elke wijziging met echte gemeten waarden op fysieke hardware in plaats van aannames.

## 2. Aanpak

Drie modules, stap voor stap, elk pas gestart na goedkeuring van de vorige:

1. **Module 1 — Live wallpaper service**: audit van de bestaande renderlus, visibility-handling en telemetrie.
2. **Module 2 — Caching en achtergrondtaken**: bitmap-decoding, in-memory caching, WorkManager-constraints, low-RAM-handling.
3. **Module 3 — Macrobenchmark-tooling**: een herbruikbare, on-device meetopstelling zodat toekomstige claims ("dit is sneller/zuiniger") altijd verifieerbaar zijn, niet alleen deze ronde.

Elke wijziging is gebouwd, geïnstalleerd en getest op een fysiek toestel (Samsung SM-S928B), niet alleen op de emulator — `dumpsys gfxinfo` bleek voor de wallpaper-surface onbetrouwbaar (zie 3.2), dus waar mogelijk is gemeten via de app's eigen `LWW`-logcat-telemetrie of Macrobenchmark in plaats van generieke Android-tooling.

## 3. Onderzoeksbevindingen

### 3.1 Module 1 — Live wallpaper service (`MaterialLiveWallpaperService.kt`)

De kernrenderer bleek **al correct geïmplementeerd**:

- `onVisibilityChanged(false)` annuleert de render-interval, ruimt handlers op en unregistert sensors — geen achtergrond-rendering wanneer de wallpaper niet zichtbaar is.
- De render-loop is al hard gecapt op 30 fps (`screenRefreshRate.let { if (it > 30f) 30f else it }`), onafhankelijk van het native paneel-refreshrate.
- Geen per-frame allocaties in het hot path; bestaande adaptive-quality-governor (ACT-007) degradeert al bij aanhoudende jank.

Geen wijzigingen nodig aan de wallpaper-engine zelf. Dit is destijds apart vastgelegd in een artifact ("Module 1 — Live Wallpaper Performance Audit") met de ruwe telemetrie als bewijs.

### 3.2 Ontdekt tijdens de audit: de in-app frosted achtergrond was niet gecapt

Buiten de oorspronkelijke scope viel op dat `WallpaperEffectView` — de geblurde achtergrondlaag die zichtbaar is **wanneer de app zelf open staat** (niet het startscherm) — zijn Choreographer-loop op elke vsync liet vuren, dus tot 90-120 fps op high-refresh-rate panelen, terwijl deze laag achter een `RenderEffect`-blur zit die bij elke `invalidate()` opnieuw moet composieten.

**Fix**: `Choreographer.postFrameCallbackDelayed(this, 17L)` capt dit expliciet op ~60 fps, in overleg gekozen (15/30/60 fps afgewogen; 60 fps gekozen als goede balans tussen vloeiendheid en besparing).

### 3.3 Module 2 — Caching en achtergrondtaken (`WallpaperRepository.kt` e.a.)

Audit van image-loading, WorkManager-jobs en low-RAM-handling bracht aan het licht:

- **Geen Glide/Coil**, alles handmatige `BitmapFactory`-decodes.
- `loadCachedBitmap()`/`loadCachedDepthBitmap()` decodeerden **bij elke aanroep opnieuw vanaf disk** — geen in-memory cache — terwijl dit bij elke wallpaper-redraw, elk openen van Details/Radar-scherm, etc. gebeurt.
- Geen downsampling (`inSampleSize`): bronfoto's werden altijd op volledige resolutie gedecodeerd, ook als het scherm veel kleiner is.
- 4 van de 7 achtergrondtaken (WorkManager) hadden **geen constraints** — konden op elk moment vuren, ook op metered data of bij bijna lege batterij.
- Geen `ActivityManager.isLowRamDevice()`-detectie; alle bitmaps altijd `ARGB_8888` (2x geheugen t.o.v. `RGB_565`).

## 4. Wat is gefixt

| # | Wijziging | Bestand | Commit |
|---|---|---|---|
| 1 | Frosted in-app achtergrond gecapt op 60 fps | `WallpaperEffectView.kt` | `d11fd60af` |
| 2 | Debug-only fps-telemetrie toegevoegd aan die view (om de cap te kunnen verifiëren) | `WallpaperEffectView.kt` | `47e0f2277` |
| 3 | In-memory bitmap-cache (path+lastModified-keyed) voor foto en depth-map | `WallpaperRepository.kt` | `69628f79d` |
| 4 | Downsampling (`inSampleSize`) op ~1.5x langste schermzijde bij decode | `WallpaperRepository.kt` | `69628f79d` |
| 5 | Constraints (netwerk/batterij) op 4 voorheen ongeconstrainede WorkManager-jobs | `WallpaperPhotoRefreshWorker.kt`, `WeatherUpdateJob.kt` | `69628f79d` |
| 6 | `RGB_565` voor de achtergrondfoto op low-RAM-toestellen (nooit de depth-map) | `WallpaperRepository.kt` | `11d121c50` |
| 7 | Nieuwe `:benchmark`-module (Macrobenchmark) met cold-start- en frame-timing-tests | `benchmark/`, `app/build.gradle.kts`, `buildSrc/` | `95342222d` |

Bewust **niet** aangepast: `TodayForecastNotificationJob`/`TomorrowForecastNotificationJob` — die doen alleen een lokale DB-read (geen netwerk), dus een constraint zou de dagelijkse notificatie kunnen laten missen zonder enige batterijwinst.

## 5. Resultaten (gemeten, niet aangenomen)

- **Frosted achtergrond, 60Hz paneel**: `dumpsys gfxinfo` → 490 frames / 8.0s ≈ **61.25 fps** (bevestigt cap werkt, geen regressie).
- **Frosted achtergrond, 120Hz paneel** (na expliciet omschakelen van het toestel naar 120Hz): app-eigen `LWW`-telemetrie → **~40 fps**, ruim onder de 120 fps die een ongecapte loop daar zou geven. (`dumpsys gfxinfo` bleek op deze surface onbetrouwbaar — twee metingen ~200ms na elkaar gaven 331 en 8492 "frames", fysiek onmogelijk. Vandaar de eigen `LWW`-telemetrie als betrouwbare bron; zie ook ACT-009.)
- **Macrobenchmark, cold startup** (5 iteraties, fysiek toestel SM-S928B, Android 16): `timeToInitialDisplayMs` mediaan **438.5 ms** (range 404-489 ms, coefficient of variation ≈ 0.09). Dit is de eerste keer dat deze waarde meetbaar en reproduceerbaar is vastgelegd — er was voorheen geen benchmark-infrastructuur.
- Module 2-wijzigingen (bitmap-cache, downsampling, RGB_565, WorkManager-constraints) zijn functioneel geverifieerd (geen crashes, foto rendert nog correct/scherp) maar **niet apart met voor/na-metingen bewezen** — zie open eindjes.

## 6. Wat kunnen we in de toekomst beter doen

1. **Voor/na-metingen bij elke wijziging, niet alleen achteraf.** Bij Module 1 en de frosted-achtergrond-fix is dat goed gelukt (voor/na-fps op twee refresh rates). Bij Module 2 ontbreekt dat — de bitmap-cache-hitrate en het geheugen-effect van RGB_565 zijn nooit met een concreet getal aangetoond, alleen beargumenteerd vanuit de code.
2. **`dumpsys gfxinfo` structureel vermijden voor `WallpaperService`-surfaces.** Het is twee keer deze sessie aantoonbaar onbetrouwbaar gebleken. De app-eigen `LWW`-telemetrie (ACT-009) of Macrobenchmark (nu beschikbaar dankzij Module 3) zijn de betrouwbare alternatieven — dat mag een vaste afspraak worden in plaats van iets wat we per keer herontdekken.
3. **Macrobenchmark-tests uitbreiden nu de infrastructuur er is.** Er is nu precies 1 cold-start-test en 1 (nog niet apart bevestigde) frame-timing-test. Voor de hand liggende vervolgstappen: een macrobenchmark voor het wisselen van locatie/foto (triggert de hele decode-cache-pipeline uit Module 2), en een `CompilationMode`-vergelijking (baseline profile aanwezig of niet) voor de app-startup.
4. **Device-eigenaardigheden (Samsung/One UI) kosten onevenredig veel tijd.** Zowel de refresh-rate-instelling (geen adb-toggle beschikbaar, moest handmatig via Instellingen) als de "spooky" package-signature-registratie die een niet-geïnstalleerd pakket toch leek te blokkeren, kostten aanzienlijke tijd t.o.v. de daadwerkelijke code-wijziging. Een tweede testtoestel (of consistente emulator-flow) zou dit type frictie voor toekomstig werk verminderen.
5. **RGB_565-pad is ongetest op echte low-RAM hardware.** Het test-toestel (SM-S928B) is geen low-RAM-toestel, dus `ActivityManager.isLowRamDevice()` is daar altijd `false` — de nieuwe code-tak is nooit daadwerkelijk doorlopen op dit toestel.

## 7. Open eindjes

- **Module 2 mist eigen metingen** (zie punt 6.1) — geheugengebruik voor/na RGB_565, cache-hitrate voor/na de in-memory bitmap-cache.
- **RGB_565-pad nooit uitgevoerd** op een echt low-RAM-toestel (zie punt 6.5) — alleen door codelezing geverifieerd dat de logica correct is (depth-map expliciet uitgesloten van kwaliteitsverlies).
- **Macrobenchmark dekt nog niet** de scenario's uit Module 2 (foto-refresh/decode-cache) of de frosted-achtergrond-fps direct (de `mainScreenFrameTiming`-test bestaat, maar het resultaat is nog niet apart geanalyseerd/gerapporteerd).
- **Telefoon staat momenteel op de `benchmark`-buildvariant** (debug-signed, R8-geminifieerd, non-debuggable) onder `com.liveweatherwallpaperapp`, niet de normale release- of debugbuild — op uitdrukkelijk verzoek zo gelaten. Moet op enig moment teruggezet worden naar een normale build.
- **Root cause van de "phantom package signature"-blokkade** op het testtoestel is nooit gevonden (vermoedelijk Samsung's auto-archive-functie); opgelost door de app handmatig te verwijderen i.p.v. via adb, maar niet begrepen waaróm adb het niet kon.
