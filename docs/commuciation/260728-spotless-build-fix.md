# 2026-07-28 — Spotless build fix (RadarActivity.kt)

## Aanleiding
Gebruiker was bezig de app te versnellen (aanpassingen in de wallpaper-service en
-layout). Tijdens die sessie liep een GitHub Actions build vast.

## Probleem
CI-build faalde op `:app:spotlessKotlinCheck`:

```
Execution failed for task ':app:spotlessKotlinCheck'
> The following files had format violations:
src/main/kotlin/com/liveweatherwallpaperapp/radar/RadarActivity.kt
```

Oorzaak: de import `androidx.compose.ui.platform.LocalContext` stond niet
alfabetisch gesorteerd binnen het `androidx.compose.ui.*` importblok
(stond na `unit.dp` i.p.v. voor `res.colorResource`).

## Oplossing
Import verplaatst naar de juiste alfabetische positie in
`app/src/main/kotlin/com/liveweatherwallpaperapp/radar/RadarActivity.kt`:

```diff
 import androidx.compose.ui.graphics.Color
+import androidx.compose.ui.platform.LocalContext
 import androidx.compose.ui.res.colorResource
 import androidx.compose.ui.res.stringResource
 import androidx.compose.ui.text.font.FontWeight
 import androidx.compose.ui.unit.dp
-import androidx.compose.ui.platform.LocalContext
 import androidx.compose.ui.viewinterop.AndroidView
```

## Status
- Commit `f9e309598` — "Fix import order in RadarActivity.kt to satisfy spotlessKotlinCheck"
- Gepusht naar `origin/main`.
- Overige openstaande wijzigingen (`MaterialLiveWallpaperService.kt`,
  `WallpaperPhotoLayout.kt`, onderdeel van de snelheids-optimalisatie) zijn
  bewust ongemoeid gelaten — dat is nog werk in uitvoering bij de gebruiker.

## Afbeeldingen
Geen afbeeldingen gedeeld in deze communicatie.
