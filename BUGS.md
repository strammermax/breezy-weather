# Bekende bugs

Lijst met openstaande issues die nog niet (volledig) zijn opgelost.

## Live wallpaper parallax doet niets

- **Status:** open
- **Omschrijving:** Met de "Parallax effect" instelling aan (in `LiveWallpaperConfigActivity`) is er geen
  zichtbare verschuiving van de achtergrond/foreground laag bij het swipen tussen homescreens.
- **Vermoedelijke oorzaak:** Getest op telefoon (R5CX80DCFQE, 3 homescreens) met `adb logcat -s LWW`.
  Na een scherm uit/aan-cyclus staat `mParallaxEnabled=true` correct (zie log: `onVisibilityChanged
  visible=true parallax=true`, `layer bounds updated 1080x2340 parallax=true`), maar bij het swipen tussen
  homescreens komt er **geen enkele** `onOffsetsChanged` log binnen
  (`app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt:1423`). De launcher op
  dit toestel roept `onOffsetsChanged` blijkbaar niet aan voor live wallpapers — een bekende beperking bij
  diverse (vooral non-stock) launchers, soms instelbaar via een eigen "wallpaper scroll effect"-optie in de
  launcher.
- **Mogelijke fix:** geen code-fix in onze app mogelijk als de launcher de callback niet aanroept. Eventueel
  documenteren dat parallax alleen werkt op launchers die `onOffsetsChanged` ondersteunen, en/of zoeken naar
  een launcher-instelling die dit aanzet.
- **Eerdere (afgeronde) sub-bevinding:** `mParallaxEnabled` wordt alleen herladen in
  `WeatherEngine.onVisibilityChanged(visible = true)` (regel 1360) — een instellingswijziging vereist dus
  een scherm uit/aan om actief te worden. Dit werkte na de test correct, maar is op zichzelf een minor
  papercut (instellingen worden niet live doorgepusht naar de actieve engine).
