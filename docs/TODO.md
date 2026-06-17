# Todo — volgende sessie

## 1. Zon-gloed verbeteren
De zon ziet er nu uit als een kleine witte schijf met een strak halo.
Doel: grote zachte stralende gloed (zie `D:\Project\_voorbeelden\Weer\zonnig.png`).

- Vergroot `glowRadius` van ~0.345× naar ~0.55–0.65× kortste zijde
- Meer gradient-stops met langzamere falloff in de buitenste 60–100%
- Verwijder de code-duplicatie: `drawSun()` staat nu zowel in
  `MaterialLiveWallpaperService.kt` als `WallpaperSceneSnapshot.kt` —
  samenvoegen in een nieuw `CelestialGlow.kt` object

## 2. Wolkvariatie per bewolkingsgraad
Nu ziet elke bewolkingsgraad er grofweg hetzelfde uit (dezelfde blob-vorm, alleen dichter).
Doel: zichtbaar verschillende looks:

| Graad | Huidig | Doel |
|---|---|---|
| Half bewolkt (0.35) | 1–2 kleine blobs | 1–2 grote, losse, fluffy cumulus; veel blauwe lucht zichtbaar |
| Meer bewolkt (0.85) | dichtere blobs | grotere massa's, overlappend, enkele gaten |
| Vol bewolkt (0.95–1.0) | bijna aaneengesloten | dikke grijze plafondlaag, geen blauw |

Aanpak: spacing/overlap-factor afleiden uit `layerAlpha[i]` in `driftingCloud`,
zodat de bestaande uniform-set volstaat (geen nieuwe uniforms nodig).

## 3. Achtergrond-hemel bij zwaar onweer
Bij regen/onweer is de hemelkleur in de gaten tussen wolken nu gewoon de foto
(die er soms te licht uitziet). Overweeg een donkere sfeer-overlay die in de
gaten tussen wolken de broeinoerig-grijze achtergrondlucht simuleert.

---

*Plan al uitgewerkt in: `C:\Users\camiel\.claude\plans\fuzzy-questing-otter.md`*
