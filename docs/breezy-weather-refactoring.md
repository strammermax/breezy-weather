# Breezy Weather → Live-Wallpaper-Weather-App: refactorplan

Onderzoek van 2026-07-13 naar alle plekken waar "Breezy"/"Breezy Weather" nog voorkomt in code, resources, build-configuratie en GitHub, met een stappenplan om dit te vervangen door een eigen "Live-Wallpaper-Weather-App"-identiteit.

## Samenvatting van de scan

Het package (`com.liveweatherwallpaperapp`) en de meeste class-namen zijn al omgedoopt. Wat overblijft, valt in 7 categorieën:

| # | Categorie | Omvang | Voorbeeld |
|---|-----------|--------|-----------|
| 1 | Licentie-/copyright-headers in bronbestanden | ~1200 `.kt`-bestanden | `This file is part of Breezy Weather.` |
| 2 | Build-configuratie & Gradle convention plugins | `app/build.gradle.kts`, `buildSrc/src/main/kotlin/breezy/buildlogic`, `settings.gradle.kts`, `gradle/libs.versions.toml` | plugin-ids `breezy.android.application`, `Config.isBreezy`, dependency `com.github.breezy-weather:...` |
| 3 | Broncode-referenties (klassen, resource-mappen, strings) | `BreezyWeather.kt`, `BreezyActivity`, `BreezyWeatherTheme`, `res_breezy/`, ~72 `strings.xml`-bestanden | `android:theme="@style/BreezyWeatherTheme"`, `<string name="breezy_weather">` |
| 4 | Manifest & icon-provider-interop | `AndroidManifest.xml` | `.BreezyWeather` als Application-class, `org.breezyweather.ICON_PROVIDER`-action (interop met externe icon packs — zie Risico's) |
| 5 | GitHub-metadata / CI | `.github/workflows/push.yml`, `scripts/release.ps1` | repo-check `github.repository == 'breezy-weather/breezy-weather'`, artefactnamen `breezy-weather-*.apk` |
| 6 | GitHub-repository zelf | `git remote -v` | origin = `strammermax/breezy-weather`, upstream = `breezy-weather/breezy-weather` |
| 7 | Documentatie | `README.md` | titel, quick-start, clone-URL's, badges, licentie-links |

## 1. Licentie-headers — het lastigste punt (lees dit eerst)

Breezy Weather is gelicenseerd onder **LGPL-3.0**. De headers ("This file is part of Breezy Weather...") zijn geen branding, maar een **copyright-/licentievermelding** van de oorspronkelijke auteurs. Dit mag je:

- **Niet** verwijderen uit bestanden die grotendeels ongewijzigde Breezy-code bevatten — dat is een licentieschending (LGPL vereist behoud van copyright- en licentievermeldingen).
- **Wel** aanvullen: voeg een eigen copyright-regel toe voor de bestanden die je zelf hebt geschreven of substantieel gewijzigd (bv. `wallpaper/`-package, `photo/`-package), zonder de originele Breezy-vermelding te verwijderen.
- Voor bestanden die je **volledig zelf** hebt geschreven (nieuwe wallpaper-engine, foto-features e.d. — geen Breezy-origine) is een eigen header met eigen naam/project correct.

**Risico:** het klakkeloos zoek-en-vervangen van "Breezy Weather" → "Live-Wallpaper-Weather-App" in alle 1200 headers verwijdert attributie die de LGPL vereist, en kan de repo in licentie-overtreding brengen. Dit moet **handmatig per bestand(-groep)** beoordeeld worden: onderscheid "vrijwel ongewijzigde Breezy-bron" vs. "eigen code".

**Aanbeveling:** behoud de headers zoals ze zijn (ze verwijzen naar het project, niet naar jouw appnaam) en voeg in plaats daarvan een `NOTICE`/`THIRD-PARTY-NOTICES.md` toe die duidelijk maakt: "Live-Wallpaper-Weather-App is a fork of Breezy Weather (LGPL-3.0)". Dat is zowel netjes als juridisch correct — en scheelt duizenden onnodige regel-diffs.

> **Beslissing (2026-07-13):** licentie-headers blijven staan, ongewijzigd. Dit is definitief gekozen, mede omdat de repo in de toekomst publiekelijk gemaakt wordt — zie §1a hieronder voor wat dat extra vereist.

### 1a. Extra aandacht bij het publiek maken van de repo

Zodra de repo van privé naar publiek gaat, tellen de LGPL-verplichtingen zwaarder mee omdat iedereen ze dan kan controleren:

- **`LICENSE`-bestand aanwezig en ongewijzigd houden** (staat al goed volgens §"Wat NIET aan te raken").
- **Volledige, correcte attributie in README** is dan niet optioneel meer maar het eerste wat bezoekers/reviewers zien — zorg dat de "fork van Breezy Weather"-vermelding (§7) prominent en compleet blijft, niet alleen in een voetnoot.
- **`THIRD-PARTY-NOTICES.md` toevoegen vóór het publiek maken**, niet erna — dit is de duidelijkste plek om te laten zien dat aan de LGPL-voorwaarden is voldaan (koppeling naar broncode van gewijzigde bestanden, vermelding van originele auteurs/project).
- **Broncode-beschikbaarheid**: LGPL-3.0 vereist dat de volledige broncode (inclusief jouw wijzigingen) beschikbaar is voor eenieder die de gecompileerde app gebruikt. Een publieke GitHub-repo voldoet hieraan vanzelf, maar controleer dat er geen submodules/private dependencies zijn die dit doorbreken (bv. het `breezy-weather-data-sharing-lib`-jitpack-package, dat zelf ook publiek toegankelijk moet blijven).
- **Secrets/keys**: voordat de repo publiek gaat, dubbelcheck dat er geen API-keys per ongeluk in `local.properties`, commit-historie, of `.github/workflows/push.yml` terecht zijn gekomen (de `breezy.*.key`-properties horen alleen via GitHub Secrets binnen te komen, nooit hardcoded — zie §5). Scan ook de **git-historie**, niet alleen de huidige bestanden.
- **CI-guard `github.repository == 'breezy-weather/breezy-weather'`** (§5) blijft relevant: zorg dat release-/signing-stappen niet per ongeluk gaan draaien op forks van jóúw (dan publieke) repo, op dezelfde manier als upstream dat nu voor zichzelf afschermt.

## 2. Build-configuratie

Bestanden: `app/build.gradle.kts`, `buildSrc/src/main/kotlin/breezy/buildlogic/*`, `settings.gradle.kts`, `gradle/libs.versions.toml`

Acties:
- Hernoem het package `breezy.buildlogic` (in `buildSrc/src/main/kotlin/breezy/buildlogic`) naar bv. `lww.buildlogic`, inclusief de plugin-ids `breezy.android.application` / `breezy.android.application.compose` die in `app/build.gradle.kts` en waarschijnlijk andere modules (`data`, `domain`, etc.) toegepast worden.
- `Config.isBreezy` / de `-Pbreezy`-gradle-property stuurt een aantal **functionele** dingen aan (welke resource-map wordt gebruikt, welke links in-app getoond worden, licentietekst voor report-issue/source-code/privacy-policy). Dit is geen cosmetische vlag — **niet blind hernoemen**. Beslis eerst of jouw fork deze modus nog nodig heeft (waarschijnlijk niet, aangezien dit al een losstaande app is) en overweeg 'm helemaal te laten vervallen in plaats van te hernoemen.
- `gradle.properties`/`local.properties`-sleutels beginnen met `breezy.` (bv. `breezy.accu.key`, `breezy.github.org`). Hernoemen betekent dat **alle CI-secrets en lokale dev-`local.properties`-bestanden** mee moeten, anders breekt de build stil (lege string i.p.v. API-key).
- Dependency `com.github.breezy-weather:breezy-weather-data-sharing-lib` (in `gradle/libs.versions.toml` en `settings.gradle.kts`) is een **externe library van een ander GitHub-account** (jitpack coordinate). Die kun je niet zomaar hernoemen — die blijft `breezy-weather/...` tenzij je zelf een fork van die lib publiceert.
- `res_breezy/`-resourcemap (icons e.d., gebruikt wanneer `Config.isBreezy` aanstaat) — vervalt waarschijnlijk mee als je punt hierboven opruimt.

## 3. Broncode: klassen, thema's, strings

- `app/src/main/kotlin/com/liveweatherwallpaperapp/BreezyWeather.kt` → hernoem class `BreezyWeather` (Application-subclass) naar bv. `LiveWallpaperWeatherApp`.
- `BreezyActivity` (basis-activity, common/activities) → hernoem naar bv. `AppActivity`/`BaseActivity`.
- Thema `BreezyWeatherTheme` (en varianten `.Main`, `.Search`) komt in **tientallen plekken** in `AndroidManifest.xml` en waarschijnlijk `styles.xml` voor → hernoemen naar bv. `AppTheme`. Dit is een mechanische maar brede find-and-replace; laat de style-**definitie** en alle **referenties** synchroon lopen.
- `strings.xml` (72 taalbestanden!): sleutel `breezy_weather` en de letterlijke tekst "Breezy Weather" in user-facing strings (about-scherm, notificatie-broadcast-namen, content-provider-permissie-omschrijvingen). Let op:
  - `about_fork_description` / `about_fork_source`: deze strings verwijzen **inhoudelijk terecht** naar Breezy Weather als bron-project (attributievereiste, zie §1) — pas de tekst aan zodat die klopt met de nieuwe naam ("Live-Wallpaper-Weather-App is a fork of Breezy Weather…") maar verwijder de vermelding niet.
  - `content_provider_permission_label`/`_description` ("Read Breezy Weather data") beschrijft een **interop-permissie met andere apps** (zie §4) — als je de contentprovider-authority hernoemt, moet de string mee, maar wees je bewust dat externe apps (bv. Gadgetbridge) mogelijk op de oude naam/authority zoeken.
  - `nws_weather_text_wind_breezy` ("Breezy" als windconditie-tekst) is **geen branding** maar een weerterm — met rust laten.
  - Aangezien dit 72 taalbestanden zijn (vertaald via bv. Weblate/crowdin), overweeg of vertalingen opnieuw ingevoerd moeten worden na tekstwijziging, of dat je alleen de Engelse/Nederlandse bronstring aanpast en de rest later bijwerkt.
- `res_breezy`, `res_fork` mappen: `res_fork` lijkt al de bedoelde plek voor fork-specifieke branding (README verwijst ernaar) — controleer of jouw app-icoon/naam al daar staan i.p.v. in `res_breezy`.

## 4. AndroidManifest — interop-risico

`org.breezyweather.ICON_PROVIDER` is een **intent-action die externe icon-pack-apps gebruiken om zich te registreren bij Breezy Weather-achtige apps**. Als je deze action-string hernoemt:

- Bestaande icon-pack-apps die gebruikers al geïnstalleerd hebben, werken niet meer met jouw app totdat die icon-packs ook geüpdatet worden naar de nieuwe action-naam.
- Dit is een bewuste **breaking change voor gebruikers**, geen puur cosmetische rename. Beslis expliciet: action-naam behouden (compatibiliteit) vs. hernoemen (branding-consistentie, maar breekt bestaande icon-pack-koppelingen).

`android:name=".BreezyWeather"` (Application-class-referentie) moet uiteraard meeveranderen zodra de class hernoemd wordt.

## 5. CI/CD en release-tooling

`.github/workflows/push.yml`:
- `if: github.repository == 'breezy-weather/breezy-weather'` — deze guard zorgt dat release-steps (signing, GitHub release aanmaken) **alleen** draaien op het officiële upstream-repo. Op jouw fork (`strammermax/breezy-weather` of straks `strammermax/live-wallpaper-weather-app`) slaat deze guard de release-stappen momenteel al over — dus dit is mogelijk dode code voor jouw fork, tenzij je 'm bewust hebt aangepast elders. **Controleer of er al een fork-specifieke guard bestaat** (mogelijk verderop in het bestand, niet in de gescande fragmenten) voordat je deze regels aanpast.
- Artefactnamen `breezy-weather-*.apk`, workflow-titel "Breezy Weather push CI", en de `-Pbreezy`-property-vlag horen bij §2.
- `gradle.properties`-sleutels in de workflow (`breezy.accu.key=...` etc.) moeten in lockstap met de sleutel-rename uit §2 aangepast worden, **inclusief de GitHub Actions repo-secrets zelf** (secret-namen zoals `ACCU_WEATHER_KEY` blijven waarschijnlijk hetzelfde, alleen de property-key ervoor verandert).

`scripts/release.ps1`: `--repo strammermax/breezy-weather` moet naar de nieuwe repo-naam wijzen zodra je die hernoemt (zie §6).

### 5a. Actions & secrets — wat er echt gebeurt bij hernoemen/publiek maken

- **Secrets zelf hoeven niet opnieuw ingevoerd te worden.** GitHub-repo-secrets (`SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`, `GOOGLE_SERVICES_JSON`, alle weer-API-keys) blijven gewoon aan de repo gekoppeld na een rename. Alleen de *property-namen ervoor* (`breezy.accu.key=...` etc., regel 72-87) moeten mee als je die hernoemt (zie §2/§5).
- **Geen fork-PR-risico.** De workflow triggert alleen op `push` naar `main`/tags, niet op `pull_request` of `pull_request_target`. Dat betekent dat forks van je (straks publieke) repo geen pull request kunnen openen die met jouw secrets meedraait — het risico dat normaal het grootste punt van zorg is bij "repo publiek maken + Actions", speelt hier dus niet. Blijf hier wel op letten als er ooit een `pull_request`-getriggerde workflow bijkomt.
- **Bestaande bug: de repository-guard klopt al niet met de huidige repo-naam.** Alle `if: ... && github.repository == 'breezy-weather/breezy-weather'`-checks (regel 34, 38, 50, 56, 65, 69, 118, 128, 140, 161) verwijzen naar het **upstream**-repo, niet naar `strammermax/breezy-weather`. Dat betekent dat signing, release-aanmaken en de freenet-build via déze workflow **nu al nooit draaien** op dit fork — ongeacht of je hernoemt. Als je releases via deze workflow wil laten lopen, moet de guard sowieso bijgewerkt worden naar je eigen (toekomstige) repo-naam, los van de vraag of je publiek gaat.
- **Acuut risico, los van naamgeving: `-Pbreezy` op de basic-releasebuild staat níet achter de repository-guard.** Regel 115 (`./gradlew assembleBasicRelease -Pbreezy`) draait onvoorwaardelijk, ook op dit fork, ook nu al. De inline-comment waarschuwt expliciet: *"You're NOT allowed to redistribute modified APKs with the breezy config enabled, see license terms"*. Controleer of dít de build is die naar de Play Store interne testtrack gepubliceerd wordt — zo ja, is dat mogelijk een licentieschending die losstaat van de rest van deze refactor en voorrang verdient.

## 6. GitHub repository

Huidige situatie:
```
origin    https://github.com/strammermax/breezy-weather.git
upstream  https://github.com/breezy-weather/breezy-weather.git
```

- **origin hernoemen** (GitHub repo-instellingen → Rename): `strammermax/breezy-weather` → bv. `strammermax/live-wallpaper-weather-app`. GitHub redirect't automatisch oude URL's, maar:
  - Lokale remote-URL moet je zelf updaten (`git remote set-url origin ...`).
  - Externe links (Play Store-vermeldingen, forum-posts, oude release-assets, obtainium/F-Droid-config die naar de repo-URL verwijst) blijven op de oude naam wijzen totdat je ze bijwerkt — GitHub's redirect vangt dit op zolang je de oude naam niet aan een ander repo hergeeft.
- **upstream** (`breezy-weather/breezy-weather`) is het **echte bovenstroomse project** waar je ooit van geforkt bent — dit **moet blijven staan** zoals het is; dit is geen branding van jouw kant maar een git-remote om toekomstige upstream-updates te kunnen pullen. Niet aanraken.
- GitHub's "forked from" badge op de repo-pagina blijft "forked from breezy-weather/breezy-weather" tonen zolang het een GitHub-fork is (niet een losstaande repo) — dat is inherent aan hoe GitHub forks toont en is niet weg te refactoren zonder de fork-relatie te verbreken (GitHub Support-verzoek, zie Risico's).

## 7. Documentatie (README.md)

Rechttoe-rechtaan te herschrijven, maar wel bewust:
- Titel, quick-start, clone-URL, badges (license/version/f-droid) → naar nieuwe naam/URL's.
- Zinnen die naar Breezy Weather verwijzen als **bron-project** (regel 7, 10, 64, 90, 159, 201, 206-210) horen bij de LGPL-attributievereiste (§1) — herschrijven qua bewoording mag, maar de vermelding "gebaseerd op Breezy Weather" moet feitelijk correct en zichtbaar blijven.
- `org.breezyweather` package-verwijzingen in F-Droid/IzzyOnDroid-badge-URL's (regel 55-59) horen bij het **officiële Breezy Weather-project op die stores**, niet bij jouw fork — die badges zijn sowieso niet van toepassing op jouw fork en kunnen weg (jouw fork staat daar niet).

## Risico's — overzicht

| Risico | Ernst | Toelichting |
|---|---|---|
| Licentieschending door verwijderen copyright-headers | Hoog | LGPL-3.0 vereist attributie; zie §1 |
| Icon-pack-interop breekt | Middel | `org.breezyweather.ICON_PROVIDER`-action hernoemen breekt bestaande externe icon-pack-koppelingen bij gebruikers, zie §4 |
| Stille CI-breuk door API-keys | Middel-Hoog | `breezy.*`-property-namen hernoemen zonder alle secrets/`local.properties` mee te nemen → build slaagt maar features werken niet (lege API-key) |
| Externe dependency niet onder jouw controle | Laag-Middel | `com.github.breezy-weather:breezy-weather-data-sharing-lib` blijft die naam tenzij je zelf een fork van die losse lib publiceert |
| Vertalingen raken uit sync | Laag | 72 taalbestanden bevatten "Breezy Weather"-tekst; alleen bronstring aanpassen laat vertalingen tijdelijk verouderd/inconsistent achter |
| GitHub "forked from"-badge blijft zichtbaar | Laag | Inherent aan GitHub-forkrelatie; alleen weg te krijgen door fork-status bij GitHub Support te laten verbreken (kan niet via code) |
| Brede mechanische rename (theme/class) raakt build | Middel | `BreezyWeatherTheme` komt tientallen keren voor in manifest + styles; gemiste referentie geeft resource-not-found crash bij build/runtime |
| Grote diff-omvang bemoeilijkt review | Laag | ~1200 bestanden met headers, 72 strings.xml — als je toch besluit te hernoemen, doe dit in aparte, behapbare commits per categorie |

## Plan van aanpak (volgorde van uitvoering)

1. **Beslissing vooraf**: bepaal het beleid voor licentie-headers (behouden + NOTICE-bestand toevoegen, aanbevolen — zie §1) en voor de icon-pack-interop-action (behouden voor compatibiliteit vs. bewust breken).
2. **Documentatie & attributie eerst**: voeg `THIRD-PARTY-NOTICES.md` toe, herschrijf `README.md` met correcte attributie-taal. Dit is risicoloos en legt de "regels" vast voor de rest van de refactor.
3. **Build-logic hernoemen** (`buildSrc/src/main/kotlin/breezy/buildlogic` → eigen package, plugin-ids): geïsoleerd te testen met een lokale build (`./gradlew assembleDebug`) voordat je verder gaat.
4. **`Config.isBreezy`-mechanisme opruimen of hernoemen**: eerst beslissen of de flag nog nut heeft voor jouw losse app; zo niet, verwijderen (scheelt een hele categorie werk in `build.gradle.kts`, CI en `res_breezy`).
5. **`breezy.*`-property-sleutels hernoemen** in `build.gradle.kts`, `.github/workflows/push.yml`, en lokaal — in dezelfde commit/PR, met een checklist van alle CI-secrets die moeten meeveranderen. Test een CI-run op een branch voordat je naar main merget.
6. **Klassen & thema's hernomen** (`BreezyWeather` → app-class, `BreezyActivity`, `BreezyWeatherTheme`): mechanische maar brede rename; laat de IDE (Android Studio "Rename" refactor) dit doen i.p.v. handmatige sed, om referenties in manifest/styles/kotlin synchroon te houden.
7. **strings.xml**: pas eerst bron-taal (`values/strings.xml`) aan met zorgvuldige tekst voor de fork-attributie-strings; commit apart van de klassen-rename zodat vertalingen los bijgewerkt kunnen worden.
8. **AndroidManifest interop-beslissing uitvoeren**: action-naam behouden of bewust hernoemen (met release-notes-vermelding dat oude icon-packs niet meer werken, als je voor hernoemen kiest).
9. **Licentie-headers**: NIET generiek vervangen. Optioneel: voeg alleen aan bestanden die je zelf substantieel herschreven hebt een aanvullende copyrightregel toe. Laat de rest staan.
10. **GitHub-repo hernoemen** (laatste stap, want dit raakt CI-URL's, README-links, `release.ps1`, en externe verwijzingen): rename via GitHub-instellingen, dan `git remote set-url origin`, dan `scripts/release.ps1` en overgebleven hardcoded URL's in één opruim-commit bijwerken.
11. **Volledige build + release-dry-run**: `./gradlew assembleDebug` en `assembleBasicRelease` (zonder `-Pbreezy`) lokaal draaien, en een test-tag pushen naar een testbranch om de CI-workflow end-to-end te controleren voordat je een echte release tagt.

## 8. Extra: versioning-schema aanpassen naar `yyyy.MM.dd.buildnr`

Niet direct Breezy-gerelateerd, maar meegenomen in dezelfde refactor-ronde omdat het dezelfde build-logic bestanden raakt.

**Huidige situatie** (`app/build.gradle.kts:33-34`, functie in `buildSrc/src/main/kotlin/breezy/buildlogic/Commands.kt:19`):
```
versionCode = 60200 + getLwwPatch()          // 60200 = Breezy-basiswaarde + patch-telling
versionName = "1.1.${getLwwPatch()}"         // bv. "1.1.123"
```
`getLwwPatch()` telt het aantal commits sinds het fork-punt (commit `795e85b`) — dat getal loopt dus vanzelf op bij elke commit.

**Gewenst:** `versionName` in de vorm `yyyy.MM.dd.buildnr`, bv. `2026.07.13.842`.

**Belangrijk onderscheid:** `versionCode` (het interne, door Google Play verplichte getal waarmee updates herkend worden) en `versionName` (de tekst die gebruikers zien) zijn losse velden. Alleen `versionName` hoeft te veranderen — `versionCode` moet **ongewijzigd qua logica** blijven:
- Play Store accepteert alleen een **strikt oplopend integer** per package; er zijn al gepubliceerde releases met codes vanaf `60200 + N`. Een nieuwe versionCode moet daar altijd boven blijven, anders weigert de Play Console de upload.
- Datum in de versionCode verwerken (bv. `20260713001` als int) is af te raden: geen garantie dat dit hoger blijft dan bestaande codes, risico op overflow (Play Store-limiet is 2.100.000.000), en geeft geen meerwaarde t.o.v. de huidige, al werkende, altijd-oplopende commit-teller.
- **Aanbeveling:** laat `versionCode = 60200 + getLwwPatch()` (of een vergelijkbare monotone teller) intact, en verander alleen `versionName` naar het datumformaat.

**Voorstel voor de nieuwe `versionName`:**
```kotlin
// buildSrc/.../Commands.kt
fun Project.getBuildDate(): String {
    return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))
}
```
```kotlin
// app/build.gradle.kts
versionName = "${getBuildDate()}.${getLwwPatch()}"   // bv. "2026.07.13.842"
```
Het build-nummer (laatste segment) blijft de bestaande commit-teller — dat garandeert uniciteit ook bij meerdere builds op dezelfde dag, zonder dat je losse state moet bijhouden (zoals een "build-teller die per dag reset" wel zou vereisen).

**Wat hier verder nog geraakt wordt:**
- `scripts/release.ps1:86` (`$tag = "v$versionName" + ...`) leest `versionName` uit de build-output en gebruikt die letterlijk als git-tag — werkt ongewijzigd met het nieuwe formaat (tag wordt dan bv. `v2026.07.13.842`).
- Bestaande, al gepubliceerde git-tags/releases in de vorm `v1.1.xxx` blijven gewoon staan; alleen nieuwe releases krijgen het nieuwe formaat. Geen historische tags hernoemen.
- Play Console toont `versionName` aan gebruikers in de listing-historie — een sprong van `1.1.123` naar `2026.07.13.842` is voor eindgebruikers een cosmetische wijziging, geen probleem, mits `versionCode` intact blijft (zie boven) zodat updates gewoon herkend worden.
- Het comment bij `versionCode` in `build.gradle.kts` ("versionCode stays above the Breezy base") kan bijgewerkt worden zodra je punt 3/4 uit het plan van aanpak (build-logic hernoemen / `isBreezy` opruimen) uitvoert — puur een tekst-update, geen functionele wijziging.

**Risico:** als `getLwwPatch()` ooit vervangen wordt door iets dat niet meer monotoon oploopt (bv. per-dag reset), moet `versionCode` daar los van blijven — anders breekt de Play Store-upload. Houd `versionCode`'s bron altijd gescheiden van het cosmetische `versionName`-formaat.

## Wat NIET aan te raken

- `upstream`-remote (`breezy-weather/breezy-weather`) — nodig om updates te blijven pullen.
- De LGPL-3.0-licentietekst zelf (`LICENSE`-bestand) — niet vervangen, alleen aanvullen met eigen copyright waar van toepassing.
- `com.github.breezy-weather:breezy-weather-data-sharing-lib`-coordinate — extern, niet van jou.
- `nws_weather_text_wind_breezy` en vergelijkbare strings die "breezy" als **weerterm** gebruiken (niet als merknaam).
