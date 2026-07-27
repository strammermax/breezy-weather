# ACT-014 - Live wallpaper als achtergrond achter de glaskaarten

## Status

- Vervolg op ACT-013 (glassmorphic kaartontwerp), die al is geimplementeerd in `AbstractMainCardViewHolder` (semi-transparante kaarten, afgeronde hoeken, lichte rand).
- Probleem: de glaskaarten zijn transparant, maar de achtergrond erachter is een vlakke gradient (themakleuren), niet de live wallpaper-scene (zon/maan/wolken). Daardoor ontbreekt het "glas"-gevoel uit de ACT-013 mockup (`docs/prototypes/act-013-glass-mockup.html`), waar de kaarten over een levendige hemel-scene liggen.
- Dit document beschrijft hoe de live-wallpaper-scene als achtergrond van het hoofdscherm getoond kan worden, zodat ACT-013 zijn beoogde effect bereikt.

## 1. Opdracht in een zin

Render de bestaande live-wallpaperscene (zon/maan/wolken/etc. uit `WallpaperWeatherEffectRenderer`) als achtergrond van `MainActivity`, zodat de ACT-013-glaskaarten er transparant overheen liggen, zoals in de ACT-013-mockup — zonder de bestaande systeem-live-wallpaper-functionaliteit (`MaterialLiveWallpaperService`) te breken.

## 2. Huidige architectuur

- `MaterialLiveWallpaperService` (een `WallpaperService.Engine`) draait de scene-rendering (`WallpaperWeatherEffectRenderer`, `WallpaperSceneState`, `CloudField`, particles, etc.) als systeem-wallpaper, achter de launcher en alle apps.
- `MainActivity` is een gewone activity met een eigen `windowBackground` (gradient op basis van `md_theme_*`-kleuren, dag/nacht via `values`/`values-night`).
- De glaskaarten (ACT-013) zijn transparant/semi-transparant en tonen daardoor nu deze vlakke gradient erdoorheen, niet de scene.

Belangrijk: de systeem-wallpaper-service en de activity-achtergrond zijn vandaag **twee gescheiden render-paden**. Deze opdracht voegt een derde rendercontext toe (binnen de activity) zonder de andere twee te verwijderen.

## 3. Doel / gewenst eindbeeld

- Bij het openen van de app ziet de gebruiker de live-wallpaperscene (hemelgradient, zon/maan, wolken, neerslag-effecten passend bij het huidige weer/dagdeel) als achtergrond van het hoofdscherm.
- De ACT-013-glaskaarten liggen hier transparant overheen, zoals in de mockup: je ziet de scene vervaagd doorschemeren door de kaarten.
- De systeem-live-wallpaper (los van de app, op het startscherm) blijft ongewijzigd werken.
- Scrollen, tab-wisselen, dag/nacht-thema en bestaande functionaliteit van het hoofdscherm blijven intact.

## 4. Opties

### Optie A — Activity-window transparant + wallpaper doorschijnen (`FLAG_SHOW_WALLPAPER`)

- `MainActivity`'s theme krijgt `windowBackground="@android:color/transparent"` en `WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER`.
- Toont de **systeem-wallpaper** (wat de gebruiker ook op zijn startscherm ziet) achter de activity.
- Voordeel: bijna geen nieuwe rendercode, hergebruikt het bestaande systeem.
- Nadeel: toont alleen de systeem-wallpaper als de gebruiker deze live wallpaper ook daadwerkelijk heeft ingesteld als systeem-wallpaper. Als dat niet zo is (bijv. gebruiker heeft een andere/standaard wallpaper), klopt de achtergrond niet met de weersdata in de app. Geen controle over synchronisatie tussen scene-state in de app en in de wallpaper-service (twee aparte `WallpaperSceneState`-instanties).

### Optie B — Eigen renderview binnen de activity (gedeelde renderer, los van de systeem-service)

- Voeg een `View` (bijv. een `Canvas`-based custom view of `GLSurfaceView`, naar smaak van de bestaande renderer) toe als onderste laag van `activity_main.xml`, die `WallpaperWeatherEffectRenderer` + `WallpaperSceneState` hergebruikt om dezelfde scene te tekenen — onafhankelijk van of de gebruiker de live wallpaper ook als systeemwallpaper heeft ingesteld.
- Voordeel: altijd consistent met de weersdata die de app al toont, werkt voor elke gebruiker.
- Nadeel: de renderer draait dan mogelijk **twee keer** (systeem-wallpaper-service + in-app view) als de gebruiker de live wallpaper ook als systeemwallpaper gebruikt — extra CPU/GPU-load. Vereist hergebruik van bestaande rendercode op een manier die niet aan de `WallpaperService.Engine`-lifecycle gebonden is.

### Optie C — Hybride: Optie A als basis, met fallback

- Toon de systeem-wallpaper via `FLAG_SHOW_WALLPAPER` (Optie A).
- Als de actieve systeem-wallpaper niet de eigen live wallpaper is (te detecteren via `WallpaperManager`), render dan een statische of lichte eigen achtergrondgradient (huidige gedrag) als fallback — geen volledige effect-renderer in de activity nodig.
- Voordeel: laag risico, geen dubbele renderer, werkt "mooi" voor de doelgroep die de live wallpaper ook gebruikt (de primaire use case van deze app).
- Nadeel: voor gebruikers zonder deze live wallpaper als systeemwallpaper blijft het oude vlakke effect.

## 5. Aanbevolen aanpak

**Optie C** als eerste stap: lichtste wijziging, laagste performance-risico, en past bij de aard van deze app (een live-wallpaper-app, waarbij de meeste gebruikers de wallpaper ook actief gebruiken). Optie B kan later als losstaande opdracht (ACT-015?) worden uitgewerkt als blijkt dat veel gebruikers de wallpaper niet instellen maar wel het glaseffect willen.

Niet kiezen voor Optie A zonder fallback: dat zou voor een deel van de gebruikers een onbedoeld kale/transparante achtergrond geven (afhankelijk van hun systeem-wallpaper), wat een functionaliteitsverlies t.o.v. de huidige vlakke gradient is.

## 6. Implementatiestappen (Optie C)

1. In `MainActivity`'s theme: `windowBackground` transparant maken en `FLAG_SHOW_WALLPAPER` zetten op het window.
2. Detecteer via `WallpaperManager.getWallpaperInfo()` of de actieve live wallpaper de eigen `MaterialLiveWallpaperService` is.
3. Indien ja: laat het transparante window staan (systeem-wallpaper schijnt door de glaskaarten).
4. Indien nee: behoud (of toon opnieuw) de huidige vlakke gradient-achtergrond als `windowBackground`, zodat het gedrag niet verslechtert t.o.v. nu.
5. Test dag/nacht-overgang: de systeem-wallpaper-scene en de activity-thema-kleuren (voor tekst/iconen-contrast) moeten beide correct overschakelen.
6. Controleer contrast/leesbaarheid van tekst in de glaskaarten boven op de levendige scene (vooral bij heldere lucht / felle zon) — eventueel de kaartachtergrond-alpha uit ACT-013 sectie 9 licht verhogen indien nodig, maar alleen na visuele test.

## 7. Performance

- Optie C voegt geen nieuwe renderer toe; de bestaande systeem-wallpaper-service blijft de enige plek die de scene tekent.
- Enige toegevoegde kosten: één `WallpaperManager`-lookup bij activity-start (niet per frame).

## 8. Testmatrix

| Scenario | Verwacht resultaat |
|---|---|
| Eigen live wallpaper actief, dag | Scene (zon/wolken) zichtbaar achter glaskaarten, zoals mockup dag-variant |
| Eigen live wallpaper actief, nacht | Scene (maan/sterren) zichtbaar achter glaskaarten, zoals mockup nacht-variant |
| Andere/geen wallpaper actief | Huidige vlakke gradient-achtergrond, geen regressie |
| Dag→nacht overgang terwijl app open is | Activity-thema en systeem-wallpaper-scene wisselen consistent |
| Scrollen door kaarten | Geen performance-regressie, geen flikkering van de achtergrond |

## 9. Acceptatiecriteria

- Met de eigen live wallpaper actief toont het hoofdscherm de scene achter de transparante glaskaarten, vergelijkbaar met de ACT-013-mockup.
- Zonder de eigen live wallpaper actief blijft het hoofdscherm minstens zo leesbaar/bruikbaar als vandaag (geen kale of contrastloze achtergrond).
- Geen bestaande functionaliteit (navigatie, kaarten, dag/nacht-thema, systeem-wallpaper-service) raakt kapot.
- Build slaagt (`:app:assembleBasicDebug`); visuele check via emulator-screenshot tegen de mockup.

## 10. Definition of done

- Implementatie volgens optie C, gecommit op een eigen feature-branch.
- Screenshots (dag + nacht, met en zonder eigen wallpaper actief) ter vergelijking met de ACT-013-mockup.
- Geen regressie in bestaande tests/build.

## 11. Samenwerking

- Wijzig alleen `MainActivity`, het bijbehorende theme/`windowBackground`, en eventueel `activity_main.xml` voor deze opdracht.
- Raak `MaterialLiveWallpaperService`, `WallpaperWeatherEffectRenderer`, `CloudField` en de particle-/scene-code uit ACT-001 t/m ACT-012 niet aan — die blijven ongewijzigd herbruikt.
- Bij conflict met lopende ACT-opdrachten (scene state, transitions): meld dit, bouw geen tweede scene-architectuur.
