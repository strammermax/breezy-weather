# Camera-improvement - upload- en validatieflow

## Status

- Type: onderzoek + advies + implementatieplan, nog niet uitgevoerd
- Scope: `CameraActivity.kt` en de directe afhankelijkheden `WallpaperRepository.uploadCameraPhoto()` / `checkUploadedPhoto()` en `RemoveSkyProvider.uploadFile()` / `checkImage()`
- Aanleiding: gebruikersfeedback dat de upload-/beoordelingsflow onduidelijk aanvoelt (geen voortgang, dubbele foto, ontbrekende opslaan/sluiten-knoppen)

## 1. Aanleiding

Bij het testen van de camera-flow (foto van de lucht maken en laten beoordelen) kwamen de volgende knelpunten naar voren:

1. Na het indrukken van de sluiterknop is er geen zichtbare voortgang - alleen de statische tekst "Foto uploaden..." met een indeterminate spinner, terwijl het proces (locatie ophalen, uploaden, laten controleren) een tijd duurt.
2. Er is geen moment om de zojuist gemaakte foto te beoordelen vóórdat deze wordt geüpload - de upload start automatisch zodra de foto is genomen.
3. Op het resultaatscherm wordt de originele foto twee keer getoond (once tijdens de vorige preview-stap, nogmaals als thumbnail op de resultaatkaart), wat verwarrend oogt.
4. Na een succesvolle upload van één foto (niet via de galerij-flow) verschijnt er geen "Opslaan"- of "Sluiten"-knop - alleen "Herkansing" is zichtbaar.

## 2. Bevindingen in de huidige code

Alle referenties zijn relatief aan `D:\Project\LiveWeatherApp`.

### 2.1 Camera-scherm

- `app/src/main/kotlin/org/breezyweather/ui/camera/CameraActivity.kt` - één Activity, geen Fragments/Compose, gebruikt CameraX (`Preview`, `ImageCapture`).
- Layout: `app/src/main/res/layout/activity_camera.xml`. De horizon-tekst ("Houd de lucht boven deze lijn", `R.string.camera_horizon_guide`) is `horizonGuideLabel` (regel 35-48); `horizonGuideLine` is een 2dp-lijn op verticale bias 0.3.
- `takePhoto()` (`CameraActivity.kt:278-309`) verwerkt de sluiterklik (`captureButton`, layout regel 50-65). Galerij-import loopt via `galleryButton` / `pickGalleryPhotos` (regel 77-106, 174-198).

### 2.2 "Foto uploaden..."-scherm

- `showCapturedPhotoAndUpload()` (`CameraActivity.kt:394-419`) decodeert de bitmap en roept direct `showResultView()` (240-251) aan.
- `showResultView()` zet `resultTextView.text = getString(R.string.camera_uploading)` (regel 400) en toont een indeterminate `ProgressBar` (layout regel 127-133, geen percentage).
- Er is geen koppeling tussen deze indicator en losse processtappen (locatie, upload, controle) - alles valt onder één statische tekst.

### 2.3 Uploadlogica

- Volgorde: `takePhoto()` -> `showCapturedPhotoAndUpload()` -> `fetchLocation{}` -> `uploadImage(file, bitmap, location)` (`CameraActivity.kt:473-538`).
- Dit loopt op `cameraExecutor` (een single-thread `ExecutorService`, geen coroutine scope); de netwerkcall zelf wordt aangeroepen via `runBlocking { wallpaperRepository.uploadCameraPhoto(...) }` (regel 485-491).
- Er is geen WorkManager betrokken bij deze upload (WorkManager wordt alleen gebruikt voor de achtergrond-wallpaperverversing, zie 2.5).
- `WallpaperRepository.uploadCameraPhoto()` (`WallpaperRepository.kt:231-274`) roept `RemoveSkyProvider.uploadFile()` aan (`RemoveSkyProvider.kt:155-209`), een synchrone OkHttp-call (`client.newCall(request).execute()`). Er bestaat nergens in de netwerklaag een progress-callback of streaming `RequestBody` - dus een percentage tijdens uploaden is met de huidige laag niet mogelijk zonder aanpassing.
- De galerij-flow (`uploadGalleryPhotos()`, regel 580-664) werkt sequentieel per foto en toont wél een lopend logboek (`galleryLog`, `appendGalleryLog()`, regel 666-680) - dat patroon bestaat dus al, maar wordt niet voor de losse camera-upload gebruikt.

### 2.4 Resultaat-/validatiescherm

- Na upload roept `renderResultCards()` (`CameraActivity.kt:697-711`) `wallpaperRepository.checkUploadedPhoto(result.processedUrl)` aan, wat uitkomt bij `RemoveSkyProvider.checkImage()` (`RemoveSkyProvider.kt:107-141`, ook synchrone OkHttp-call). Resultaat: `RemoveSkyCheckResult` / `RemoveSkyChecks` (hasSkyTop, isOutdoor, hasColor, hasGps, hasDate, isNightVisual, seasonVisual).
- Kaarten worden opgebouwd in `buildResultCard()` (regel 713-768) met layout `item_camera_upload_result.xml`: toont de thumbnail van de zojuist gemaakte/geselecteerde foto (`data.thumbnail` - dit is de originele bitmap, niet de door de server verwerkte transparante PNG), bron/locatietekst, een oordeelregel ("✓ Geschikt" / "✗ <reden>", regel 730-747, string `camera_check_ok` = "Geschikt"), en een `ChipGroup` met checkbadges via `addCheckChip()` (770-780: Lucht/Buiten/Kleur/GPS/Datum, groen/rood/grijs) plus dag/seizoen-badges via `addBadgeChip()` (782-787).
- Knoppen:
  - `retakeButton` ("Herkansing", `showCameraView()` regel 200-202) is altijd zichtbaar zodra `showResultView()` actief is (regel 249).
  - `setLiveWallpaperButton` wordt alleen getoond op een succespad; in de galerij-flow expliciet bij `successCount > 0` (regel 658), maar bij een losse camera-upload wordt deze knop nergens expliciet zichtbaar gemaakt - blijft dus op `GONE`.
  - `closeButton` staat standaard op `GONE` en wordt alleen `VISIBLE` op falen-/afwijzenpaden (regel 514, 530, 659).
  - Netto: na een succesvolle upload van één foto via de camera is alleen "Herkansing" zichtbaar - geen "Opslaan" en geen "Sluiten".
- De "dubbele foto" die in de screenshots te zien is, komt door twee losse renderstappen die na elkaar dezelfde bitmap tonen: eenmaal in de uploadfase (`showResultView()` met de net gemaakte bitmap) en eenmaal als thumbnail op de resultaatkaart (`buildResultCard()`); er wordt geen aparte, door de server verwerkte afbeelding getoond.

### 2.5 State-beheer

- Er is geen ViewModel; `CameraActivity` houdt alle state zelf bij als velden: `captureInProgress: Boolean` (regel 69), `galleryLog: StringBuilder` (regel 70), en zichtbaarheids-toggles in `showCameraView()` / `showResultView()`.
- Geen `StateFlow` / `LiveData`, geen Hilt ViewModel in deze flow.

### 2.6 Niet-gerelateerde bestanden

`WallpaperImageStore`, `WallpaperPhotoRefreshWorker.kt`, `MaterialLiveWallpaperService.kt` worden niet door `CameraActivity.kt` aangeroepen en horen bij de achtergrond-wallpapercache/-rotatie, niet bij deze flow. Enige indirecte link: `uploadCameraPhoto()` schrijft in dezelfde cache (`store.activatePhoto`, `photoCatalog.upsertDownloaded`) die deze klassen later lezen/serveren.

## 3. Advies

De kern van het probleem is dat de huidige flow één ononderbroken automatisch traject is (foto -> direct uploaden -> direct beoordelen), zonder tussentijdse gebruikersbeslissingen en zonder tussentijdse feedback. Voorstel: knip de flow op in vier expliciete stappen met eigen state en duidelijke acties per stap.

1. **Lokale preview vóór upload (nieuw).** Na `takePhoto()` niet direct `showCapturedPhotoAndUpload()` aanroepen, maar een preview-state tonen met alleen de zojuist gemaakte foto en twee knoppen: "Herkansing" (terug naar camera) en "Upload" (start pas dan `uploadImage(...)`). Voorkomt onnodig dataverbruik en sluit aan bij de wens uit de feedback om vóór het versturen al te kunnen afkeuren.
2. **Stapsgewijze voortgang tijdens upload.** Vervang de statische "Foto uploaden..."-tekst door een lijst van stappen met status (bezig/klaar): locatie ophalen, foto uploaden, foto controleren. Dit kan zonder de netwerklaag aan te passen, puur door de bestaande sequentiële aanroepen (`fetchLocation` -> `uploadImage` -> `checkUploadedPhoto`) elk hun eigen statusregel te geven vóór/na aanroep, vergelijkbaar met het bestaande `galleryLog`-patroon in `appendGalleryLog()`. Een echt percentage tijdens de upload zelf vereist wel een aanpassing van `RemoveSkyProvider.uploadFile()` naar een streaming `RequestBody` met progress-callback - dat is een aparte, grotere wijziging (zie 4.3).
3. **Eén foto op het resultaatscherm.** `buildResultCard()` hoeft alleen de thumbnail te tonen; de losse preview-bitmap uit de uploadfase moet verborgen worden zodra het resultaat gerenderd is (dit lost de "dubbele foto" op zonder de databron te wijzigen).
4. **Opslaan/Sluiten toevoegen aan het succespad van een losse camera-upload.** Op dit moment wordt `setLiveWallpaperButton` (dat inhoudelijk al fungeert als een "gebruik deze foto"/opslaan-actie) alleen bij de galerij-flow zichtbaar gemaakt. Voor de camera-uploadflow moet, analoog aan het bestaande succespad in `uploadGalleryPhotos()` (regel 658), na een "Geschikt"-oordeel zowel een "Opslaan"-knop (hergebruik `setLiveWallpaperButton` of nieuwe `saveButton`) als een `closeButton` zichtbaar worden, naast de al bestaande "Herkansing".

Dit advies wijzigt alleen `CameraActivity.kt` en de bijbehorende layouts/strings; het raakt `WallpaperRepository`/`RemoveSkyProvider` alleen in punt 4.3 (optioneel, voor echte upload-progress).

## 4. Implementatieplan

### 4.1 Preview-stap vóór upload

- Nieuwe view-state toevoegen naast `showCameraView()` / `showResultView()`, bijvoorbeeld `showCapturePreview(bitmap: Bitmap)`.
- `takePhoto()` roept na een succesvolle capture `showCapturePreview(bitmap)` aan in plaats van direct `showCapturedPhotoAndUpload()`.
- Preview-layout: bestaande foto-container hergebruiken, met twee knoppen ("Herkansing" -> `showCameraView()`, "Upload" -> start de bestaande `showCapturedPhotoAndUpload()`/`uploadImage()`-keten).
- Galerijflow kan dezelfde preview-stap per foto doorlopen, of bewust overslaan (galerijfoto's zijn al bewust gekozen) - te beslissen bij implementatie, geen blokkerende afhankelijkheid.

### 4.2 Stapsgewijze statusweergave

- `resultTextView` vervangen door of aanvullen met een korte statuslijst (3 regels: locatie/upload/controle), elk met een simpel icoon of tekstprefix voor bezig/klaar/mislukt.
- Statusregels bijwerken op de bestaande overgangen: vóór `fetchLocation{}`, na ontvangst van locatie, vóór/na `uploadImage(...)`, vóór/na `checkUploadedPhoto(...)`.
- Geen wijziging aan `WallpaperRepository`/`RemoveSkyProvider` nodig voor deze stap.

### 4.3 (Optioneel, groter) Echte uploadvoortgang in procenten

- Vereist een streaming `RequestBody` met progress-callback in `RemoveSkyProvider.uploadFile()` (`RemoveSkyProvider.kt:155-209`) in plaats van de huidige synchrone `execute()`-call, plus een callback-parameter die doorloopt tot in `uploadCameraPhoto()` en `CameraActivity`.
- Aparte opdracht/vervolgstap; niet nodig om punt 4.1, 4.2 en 4.4 te kunnen opleveren.

### 4.4 Resultaatscherm opschonen en Opslaan/Sluiten toevoegen

- In `showResultView()` de preview-bitmap-weergave verbergen zodra `buildResultCard()` de definitieve kaart toont, zodat de foto nog maar één keer zichtbaar is.
- In het succespad van de losse camera-upload (analoog aan regel 658 in de galerij-flow) `closeButton` en een opslaan-knop zichtbaar maken zodra het oordeel "Geschikt" is; `closeButton` sluit het scherm zonder verdere actie, opslaan-knop bevestigt (hergebruik van bestaande `setLiveWallpaperButton`-logica of nieuwe, gelijkaardige knop als "opslaan" een andere betekenis moet krijgen dan "als live wallpaper instellen").
- `retakeButton` blijft in alle gevallen zichtbaar, zoals nu.

### 4.5 Niet in scope

- Wijzigingen aan `WallpaperImageStore`, `WallpaperPhotoRefreshWorker`, `MaterialLiveWallpaperService` (achtergrond-cache/-rotatie) - deze flow raakt ze niet.
- Migratie naar een ViewModel/`StateFlow`-architectuur - kan een losse, latere opdracht zijn als de state in `CameraActivity` te complex wordt, maar is niet nodig om bovenstaande punten op te leveren.

## 5. Samenwerkingsregels (herhaling, zie ACT-000)

- Lees eerst `git status` en de actuele diff voordat je begint.
- Wijzig geen bestanden buiten de scope van dit document (camera-flow en directe afhankelijkheden).
- Stage bestanden expliciet, nooit via `git add .`.
- Baseer alle keuzes op de actuele code (bovenstaande regelverwijzingen), niet uitsluitend op dit document, aangezien de code inmiddels kan zijn gewijzigd.
