# Mergerapport: sync-upstream (14 juli 2026)

## Onderzochte merge

- Merge-commit: `81879f3ad` — "Merge branch 'sync-upstream'"
- Parent 1 (onze branch vóór de merge): `23537328a` — "Update app information [skip ci]"
- Parent 2 (sync-branch): `b58c27075` — "Merge upstream Breezy Weather via graft to shared fork-point ancestor"
  - Op zijn beurt parents: `23b89daff` (onze code) en `093873ce8` (upstream Breezy Weather, "Fix #2870 - NCEI parsing error")

Methode: `git diff --stat 23537328a 81879f3ad` en `git diff --diff-filter=D --name-status` om verwijderde bestanden en regels te vinden, aangevuld met een volledige diff van alle Kotlin-bestanden.

## Resultaat: wat is er in deze merge veranderd

In totaal 38 bestanden gewijzigd (458 toevoegingen, 121 verwijderingen). Het overgrote deel betreft:

- Vertalingen (`app/src/main/res/values-*/strings.xml`, `plurals.xml`) — ca, es, fa, lv, pt-rBR, ro, sk, sv, ta, tr, vi
- Fastlane store-metadata (beschrijvingen/changelogs voor cs, es-ES, sk, sv-SE, ta-IN)
- Build-configuratie: `gradle/libs.versions.toml` (versie-bumps), `gradle/wrapper/gradle-wrapper.properties`, GitHub Actions workflows
- Documentatie: `README.md`, `CONTRIBUTE.md`, `CHANGELOG.md`, `3RD_PARTY.md`, `docs/RADAR.md`, `docs/TECHNICAL.md`

### Enige verwijderde broncodebestand

`app/src/src_nonfreenet/com/liveweatherwallpaperapp/sources/ncei/json/NceiStationsCentroid.kt` (24 regels) is verwijderd. Dit is een upstream data-klasse van de NCEI-weerbron (`@Serializable data class NceiStationsCentroid(val point: List<Double>)`), hoort bij de upstream-fix "Fix #2870 - NCEI parsing error". Niet gerelateerd aan eigen (wallpaper/camera) functionaliteit en nergens anders meer gerefereerd — een schone upstream-opruiming.

### Enige functionele wijziging in eigen/UI-code

`app/src/main/kotlin/com/liveweatherwallpaperapp/ui/settings/activities/DependenciesActivity.kt`:

```diff
             LibrariesContainer(
                 libraries,
-                Modifier.padding(it),
-                showLicenseBadges = true
+                Modifier.padding(it)
             )
```

De licentie-badges op het "Dependencies"-scherm (Instellingen → Over → Dependencies) worden niet meer getoond. Dit is de enige, kleine, zichtbare functionaliteitsverandering die direct uit deze merge komt.

## Conclusie

Deze specifieke `sync-upstream`-merge heeft **geen eigen wallpaper-, camera- of andere kernfunctionaliteit verwijderd**. De wallpaper- en camera-modules (`app/src/main/kotlin/com/liveweatherwallpaperapp/wallpaper/*`, `.../ui/camera/*`) komen niet voor in de diff en staan nog volledig in de repository op de huidige `HEAD` (`d53487c90`).

De enige waarneembare functieverandering is het verdwijnen van de licentie-badges op het Dependencies-scherm.

## Aanbeveling voor verder onderzoek

Als er toch functionaliteit mist die niet in deze merge zit, komt dat waarschijnlijk uit een andere bron:

1. **Niet-gecommitte wijzigingen die verloren zijn gegaan** vóór de merge (bijv. door een `git checkout`/`reset`/stash die niet is teruggezet). Controleer `git reflog` en eventuele stashes (`git stash list`).
2. **Een eerdere upstream-merge**, verder terug in de geschiedenis dan deze sync — de graft-merge (`b58c27075`) heeft mogelijk een "shared fork-point ancestor" gebruikt die zelf al ouder/beperkter was dan de werkelijke gemeenschappelijke basis, waardoor eerdere eigen commits buiten beeld vielen. Waard om te controleren: `git log --all --oneline -- app/src/main/kotlin/com/liveweatherwallpaperapp/wallpaper` en hetzelfde voor `ui/camera`, om te zien of er commits "verweesd" zijn geraakt (niet meer bereikbaar vanaf `HEAD`).
3. **Runtime-/configuratieregressie** zonder codeverwijdering: functionaliteit die er nog wel staat in de broncode, maar niet meer wordt geactiveerd door een gewijzigde instelling, feature flag, of build-variant (`libs.versions.toml`-wijzigingen kunnen bijvoorbeeld een library-downgrade hebben veroorzaakt die `showLicenseBadges` niet meer ondersteunt).

Zodra de bug-lijst die je aan het opstellen bent klaar is, kan per gemelde regressie gericht met `git log -p --follow -- <bestand>` of `git bisect` worden uitgezocht in welke commit die specifieke functionaliteit is verdwenen.
