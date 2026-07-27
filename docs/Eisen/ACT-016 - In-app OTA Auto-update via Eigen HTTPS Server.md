# ACT-016 - In-app OTA Auto-update via Eigen HTTPS Server

## Status

- **Type:** Implementatieopdracht (Core / Netwerk / Systeemintegratie)
- **Prioriteit:** Hoog
- **Omvang:** Middelgroot
- **Risico:** Middelgroot (vanwege Android-systeemrechten en bestandsrechten)
- **Prerequisite:** Een functionele backend endpoint op `removesky.vanburik.info` die een POST-verzoek accepteert en de APK streamt, zie ACT-015.
- **Doelplatform:** Alle ondersteunde Android-versies; specifieke permissie-afhandeling voor Android 8.0+ (`Oreo`) en veilige bestandsuitwisseling via `FileProvider` voor Android 7.0+.

## 1. Opdracht in een zin

Bouw een in-app over-the-air (OTA) update-mechanisme dat bij het opstarten van de app de huidige versie POST naar de eigen lokale HTTPS-server, en indien beschikbaar, de nieuwe APK downloadt naar de cache en de installatie triggert met een voorafgaande vriendelijke waarschuwing voor de benodigde Android-permissies.

## 2. Waarom deze wijziging nodig is

De app wordt als interne bedrijfs-app gedistribueerd via sideloading (handmatige APK-installatie). Omdat er geen gebruik wordt gemaakt van de Google Play Store, ontvangen gebruikers momenteel geen automatische updates. Dit zorgt voor:
- Gebruikers die op verouderde app-versies blijven werken;
- Handmatige handelingen bij elke nieuwe release (APK handmatig rondsturen);
- Moeizame foutopsporing omdat versies tussen apparaten gaan afwijken.

De gewenste situatie: de basis-app wordt eenmalig handmatig geïnstalleerd, waarna de app zichzelf geruisloos up-to-date houdt via het lokale bedrijfsnetwerk zodra er een nieuwe release op de server staat.

## 3. Huidige architectuur

### Belangrijkste bestanden
1. `MainActivity.kt` (of de hoofd-entrypoint van de applicatie).
2. `AndroidManifest.xml` (beheert applicatierechten en componenten).
3. `build.gradle` (bevat de netwerkafhankelijkheden en definieert het huidige `VERSION_CODE`).

### Huidig gedrag
De app start op zonder netwerkcontroles uit te voeren met betrekking tot zijn eigen versie. Er is geen logica aanwezig om APK-bestanden te downloaden, op te slaan of aan te bieden aan het Android-besturingssysteem ter installatie.

## 4. Afbakening

### Wel uitvoeren
- Implementatie van de `REQUEST_INSTALL_PACKAGES` en `INTERNET` permissies;
- Configuratiescherm (`network_security_config.xml`) controleren/inrichten om (indien nodig bij self-signed certificaten) veilige communicatie met de lokale HTTPS-server te garanderen;
- Het ontwerpen en tonen van een vriendelijk dialoogvenster (waarschuwing) die uitlegt waarom de app om installatierechten vraagt (Android 8.0+);
- Het bouwen van de API-aanroep (POST) met de actuele `versionCode` in de JSON-body naar `https://192.168.1`;
- Het downloaden van het APK-bestand naar de afgeschermde `context.cacheDir` van de app;
- Het configureren van een `FileProvider` om de gedownloade APK veilig over te dragen aan de Android `PackageInstaller`.

### Niet uitvoeren
- Geen wijziging aan de functionele UI-schermen (weergegevens, kaarten, grafieken, theming) buiten het updatemedium zelf;
- Geen automatische achtergrond-updates via een `WorkManager` die draait als de app gesloten is (de check gebeurt uitsluitend bij het opstarten van de app);
- Geen ik-vorm of externe merkintegratie van een publieke app-store of integratie met Google Play Enterprise;
- Geen wijzigingen aan de server-side code (er wordt van uitgegaan dat de server reeds functioneel is).

## 5. Architectuurregel

Dit is een infrastructurele wijziging in de core/netwerklaag van de app. 

Tijdens deze opdracht mag de implementatie:
- Bestaande netwerkbibliotheken (zoals `OkHttp`) hergebruiken of toevoegen;
- De `MainActivity` uitbreiden om de update-check te initialiseren bij de appstart;
- Systeem-intents aanroepen om de gebruiker naar de Android-instellingen te sturen.

De implementatie mag niet:
- Onbeveiligd globaal HTTP-verkeer toestaan (beperk uitzonderingen strikt tot het specifieke server-IP indien een lokaal self-signed certificaat wordt gebruikt);
- Gevoelige data logs wegschrijven (zoals server-tokens of netwerk-dumps in productie-builds);
- De installatie forceren zonder de gebruiker de kans te geven dit te annuleren via de UI-dialoog.

## 6. Prerequisite

- De app moet succesvol kunnen compileren met een unieke `applicationId` (nodig voor de `FileProvider`-koppeling).
- Het release-ondertekeningscertificaat (Keystore) moet consistent worden gebruikt; toekomstige server-updates slagen alleen als de APK's met exact dezelfde sleutel zijn ondertekend.

## 7. Gewenst Visueel & Technisch Model

### Gebruikersinteractie (Vriendelijke Waarschuwing)
Wanneer er een update is en de app heeft nog geen rechten om apps te installeren, wordt er een `AlertDialog` getoond:

Wees voorzichtig met code.+-------------------------------------------------------+| Update Toestaan                                       ||                                                       || Om deze bedrijfs-app te kunnen updaten, moet je de    || app eenmalig toestemming geven om updates te          || installeren. Je wordt nu doorgestuurd naar de         || instellingen. Zet daar de schakelaar aan.             ||                                                       ||                     (ANNULEREN)   (NAAR INSTELLINGEN) |+-------------------------------------------------------+
### Technische Componenten

#### 1. FileProvider Pad definitie (`res/xml/file_paths.xml`)
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared_apk" path="." />
</paths>
```

#### 2. API Contract (POST naar `/updateapp`)
- **Request Body:** `{"versionCode": Int}`
- **Response HTTP 200:** Stream van de nieuwe `.apk` binary (wordt weggeschreven naar `context.cacheDir/update.apk`).
- **Response HTTP 204:** No Content (app is up-to-date, start normaal op).

#### 3. SSL/TLS Afhandeling (Lokale HTTPS Server)
Indien de server gebruikmaakt van een *self-signed* certificaat op het IP `192.168.1.154`, dient de `OkHttpClient` geconfigureerd te worden met een aangepaste `X509TrustManager` en `HostnameVerifier` die dit specifieke IP accepteert, om `SSLHandshakeException` te voorkomen.

## 8. Toepassing per component

| Component | Huidige status | Nieuwe status |
|---|---|---|
| **AndroidManifest.xml** | Geen installatierechten, geen file providers. | Bevat `INTERNET`, `REQUEST_INSTALL_PACKAGES` en de `<provider>` tag voor `androidx.core.content.FileProvider`. |
| **MainActivity.kt** | Start direct de hoofd-UI op. | Controleert in `onCreate` asynchroon via een achtergrond-thread op updates alvorens door te gaan. |
| **Bestandsopslag** | Schrijft geen tijdelijke bestanden. | Downloadt de APK veilig naar `context.cacheDir` en overschrijft eventuele oude update-restanten om opslag te besparen. |

## 9. Performance-eisen

- Het downloaden van de APK mag de hoofdthread (**UI thread**) nooit blokkeren. Gebruik de asynchrone `enqueue` methode van OkHttp of Kotlin Coroutines (`Dispatchers.IO`);
- De gedownloade APK moet in de **cache-map** (`cacheDir`) worden opgeslagen, zodat het Android-besturingssysteem de ruimte automatisch kan vrijmaken bij extreem opslagtekort;
- Er mag maximaal één update-bestand (`update.apk`) tegelijk in de cache bestaan om onnodig geheugengebruik te voorkomen.

## 10. Logging en privacy

- Er mag logruimte zijn voor statuscodes (bijv. `HTTP 200` of `HTTP 204`);
- Er mogen **geen** persoonsgegevens, apparaat-ID's of unieke tokens naar de server worden gestuurd, tenzij dit expliciet voor authenticatie vereist is;
- Netwerkfouten (zoals een time-out als de gebruiker niet op het bedrijfsnetwerk zit) moeten 'silent' falen in productie, zodat de app bruikbaar blijft zonder crash.

## 11. Voorgestelde implementatiestappen

1. Voeg `uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"` toe aan het manifest.
2. Maak de `file_paths.xml` aan en declareer de `FileProvider` in het manifest.
3. Bouw de SSL-bypass/acceptatie-logica in de `OkHttpClient` mocht de HTTPS-server gebruikmaken van een lokaal/self-signed certificaat.
4. Schrijf de asynchrone `checkForUpdateAndDownload` netwerkfunctie.
5. Implementeer de `checkInstallationPermission` dialoog-logica die controleert om `canRequestPackageInstalls()`.
6. Schrijf de `installApk` functie die via de `FileProvider` de installatie-intent afvuurt.
7. Koppel het geheel in de `onCreate` van de start-activity.
8. Test de flow door lokaal een APK met een hoger versienummer op de server te plaatsen.

## 12. Handmatige testmatrix

| Scenario | Verwachting |
|---|---|
| App start, server heeft *geen* hogere versie (`HTTP 204`) | App start direct en geruisloos door naar het hoofdscherm. |
| App start, server *heeft* hogere versie, permissie ontbreekt | Dialoogvenster verschijnt. Klikken op 'Naar Instellingen' opent de juiste Android-systeempagina voor deze app. |
| Permissie is zojuist aangezet, update start opnieuw | De APK downloadt op de achtergrond en opent direct het Android-installatiescherm ("Wilt u een update voor deze app installeren?"). |
| Telefoon is niet verbonden met het bedrijfsnetwerk (server offline) | De app start zonder haperingen of crashes door naar het hoofdscherm (fout wordt stil opgevangen). |
| Server gebruikt HTTPS met self-signed certificaat | De app accepteert de handshaking en downloadt de APK succesvol zonder `SSLHandshakeException`. |

## 13. Acceptatiecriteria

1. De applicatie voert bij elke koude start een versiecontrole uit tegen `https://192.168.1`.
2. De gebruiker krijgt een duidelijke, vriendelijke melding te zien voordat hij naar het Android-instellingenscherm wordt gestuurd om onbekende apps toe te staan.
3. Het downloaden gebeurt volledig op de achtergrond zonder haperingen in de UI.
4. Het installatiescherm van Android wordt succesvol geopend zodra de download is voltooid.
5. Oude APK-bestanden in de cache worden correct opgeruimd of overschreven.
6. Er vinden geen crashes plaats wanneer de lokale server onbereikbaar is.

## 14. Definition of done

- Het update-mechanisme is volledig operationeel in Kotlin/Java;
- Debug- en releasebuilds compileren zonder fouten;
- De app is succesvol getest op een fysiek testtoestel binnen het lokale netwerk;
- `git diff` bevat uitsluitend de gewijzigde core- en manifestbestanden behorend bij deze opdracht;
- Er zijn geen onbeveiligde HTTP (cleartext) uitzonderingen gemaakt voor het wijde 