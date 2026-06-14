# ACT-010 - Centrale automatische foto-refresh

## Status

- Type: implementatieopdracht
- Prioriteit: middelhoog
- Omvang: middelgroot
- Risico: middelgroot, omdat dit een achtergrondworker, netwerk en lokale cache raakt
- Prerequisite: geen wallpaper-renderafhankelijkheid; sluit aan op bestaande RemoveSky-, WallpaperRepository- en WallpaperImageStore-componenten
- Doelplatform: alle ondersteunde Android-versies; werk via WorkManager met netwerk- en batterijconstraints

## 1. Opdracht in een zin

Laat een app/background-worker periodiek een nieuwe RemoveSky-foto per locatie ophalen en lokaal opslaan, zodat foto's automatisch kunnen wisselen terwijl de wallpaper lokaal en read-only blijft en zelf geen HTTP- of GPS-call doet.

## 2. Waarom deze wijziging nodig is

De weerdata wordt al centraal automatisch bijgewerkt, maar de fotoverversing is nog vooral gekoppeld aan de handmatige refresh in de instellingen. Daardoor:

- wisselen foto's niet automatisch mee met de tijd of nieuwe beschikbaarheid;
- zit fotologica deels in de instellingen in plaats van in een achtergrondproces;
- bestaat het risico dat fotoverversing in de `WallpaperService.Engine` terechtkomt, wat ongewenst is;
- is er geen centrale plek die cachelimiet, maximum per locatie en historie respecteert bij automatische verversing.

De gewenste situatie is een centrale background-worker die foto's ophaalt en opslaat, terwijl de wallpaper alleen lokaal gecachete foto's leest.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `WallpaperRepository.kt`
   - haalt foto's op (RemoveSky) en beheert de relatie met de lokale store.

2. `WallpaperImageStore.kt`
   - beheert de lokale WebP-cache, historie per locatie en cachelimiet.

3. `RefreshHelper.kt`, `SourceManager.kt` en locatiebronnen.
   - bestaande externe data-ophaal-infrastructuur.

4. `WeatherUpdateJob.kt`
   - bestaande periodieke achtergrondverversing via WorkManager met netwerk- en batterijconstraints; dient als referentiepatroon.

5. `LocationRepository.kt` en `WeatherRepository.kt`
   - centrale lokale waarheid voor locaties en weerdata.

6. `LiveWallpaperConfigActivity.kt` en `LiveWallpaperConfigManager.kt`
   - bevatten de handmatige refresh en wallpaperinstellingen, waaronder cachelimiet en maximum aantal foto's per locatie.

7. `MaterialLiveWallpaperService.kt`
   - leest uitsluitend de lokaal gecachete foto; mag niet wijzigen om zelf te downloaden.

### Huidig gedrag

Fotoverversing gebeurt vooral via een handmatige actie in de instellingen. Er is geen geplande achtergrondworker die per locatie automatisch een nieuwe foto ophaalt en de cache bijwerkt.

## 4. Afbakening

### Wel uitvoeren

- een centrale WorkManager-worker voor geplande fotoverversing per locatie;
- respecteren van netwerk- en batterijconstraints;
- respecteren van cachelimiet, maximum per locatie en recente URL-historie;
- opslaan van nieuwe foto's in de bestaande `WallpaperImageStore`;
- alleen signaleren dat nieuwe lokale data beschikbaar is, zodat de wallpaper kan invalideren;
- een gedocumenteerd interval en triggers;
- foutafhandeling en retries binnen WorkManager;
- unit tests voor pure planning- en selectielogica;
- handmatige tests voor de worker en cache.

### Niet uitvoeren

- geen HTTP- of GPS-call vanuit de wallpaper;
- geen wijziging aan de renderlus of effecten;
- geen nieuwe weather provider-integratie;
- geen wijziging aan de scene state-architectuur (ACT-001);
- geen quality profile-werk: dat is ACT-007;
- geen data freshness-UI: dat hoort bij ACT-011;
- geen brede UI-herontwerp;
- geen brede refactor van Breezy Weather;
- geen externe assets uit YoWindow kopieren.

## 5. Architectuurregel

De wallpaper blijft een read-only consumer van de centrale lokale datalaag. Alle netwerk- en GPS-acties horen bij de app/background-laag.

Tijdens deze opdracht mag de worker:

- de RemoveSky-client via de bestaande infrastructuur aanroepen;
- de lokale locatie-, weer- en fotostore lezen en bijwerken;
- WorkManager gebruiken voor planning en constraints;
- de wallpaper signaleren dat nieuwe lokale data beschikbaar is.

De worker mag niet:

- in de `WallpaperService.Engine` draaien;
- de renderlus blokkeren;
- secrets of gevoelige URL's loggen;
- een tweede weather/foto-cache naast de bestaande introduceren.

## 6. Gewenst workermodel

Volg het patroon van `WeatherUpdateJob.kt`.

Conceptueel:

```kotlin
class WallpaperPhotoRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // 1. lees locaties uit LocationRepository
        // 2. bepaal per locatie of een nieuwe foto nodig is
        // 3. haal via WallpaperRepository een nieuwe RemoveSky-foto op
        // 4. sla op via WallpaperImageStore met respect voor limieten
        // 5. signaleer de wallpaper dat nieuwe lokale data beschikbaar is
        return Result.success()
    }
}
```

Planning:

- gebruik een `PeriodicWorkRequest` met netwerk- en batterijconstraints;
- standaardinterval: 24 uur (volgt het patroon van `WeatherUpdateJob.kt`); overslaan als er geen netwerk of een lage batterijstand is;
- ondersteun een eenmalige trigger bij relevante gebeurtenissen.

## 7. Selectielogica per locatie

- bepaal per locatie of een nieuwe foto nodig is op basis van leeftijd, beschikbaarheid en historie;
- respecteer maximum aantal foto's per locatie;
- respecteer de cachelimiet en evict oudste of minst relevante foto's;
- vermijd het opnieuw ophalen van een recente, identieke URL;
- behandel locaties zonder geldige data met een gedocumenteerde fallback.

## 8. Constraints en retries

- vereis netwerk volgens WorkManager-constraints (bijvoorbeeld unmetered indien gewenst);
- respecteer batterij- en idle-constraints;
- gebruik exponentiele backoff bij fouten;
- vermijd onnodige downloads bij ongewijzigde data;
- maximaliseer hergebruik van de bestaande RemoveSky-infrastructuur.

## 9. Signaleren naar de wallpaper

- de worker mag de wallpaper alleen laten weten dat nieuwe lokale data beschikbaar is;
- gebruik een bestaand invalidatiemechanisme of voeg een kleine lokale invalidatie-ingang toe;
- werk hierbij een freshness-timestamp per locatie bij (bijvoorbeeld `WallpaperImageStore.setPhotoRefreshedAt(locationId, nowMillis)` of een vergelijkbaar bestaand veld), zodat ACT-011 die timestamp kan lezen voor de "laatst bijgewerkt"-weergave;
- de wallpaper haalt vervolgens zelf de nieuwe lokale foto en timestamp uit de cache;
- de worker mag de wallpaper niet rechtstreeks aansturen om te tekenen.

## 10. Cache-integratie

- gebruik uitsluitend de bestaande `WallpaperImageStore`;
- respecteer de bestaande WebP-cache, historie per locatie en cachelimiet;
- introduceer geen tweede fotocache;
- werk de cache atomisch bij zodat de wallpaper geen half geschreven bestand leest.

## 11. Lifecycle en planning

- plan de worker bij app-start of bij een relevante configwijziging;
- annuleer en herplan netjes bij wijziging van het interval;
- voorkom dubbele gelijktijdige workers via een unieke worknaam;
- de worker moet ook werken nadat de app is afgesloten, binnen WorkManager-grenzen.

## 12. Performance- en batterij-eisen

- de worker draait niet vaker dan nodig;
- downloads gebeuren onder geschikte netwerkconstraints;
- vermijd onnodige decodes en herhaalde downloads;
- de worker mag de UI of renderlus niet blokkeren;
- log niet bovenmatig.

## 13. Logging en privacy

Alle nieuwe logging is debug-only en compact.

Toegestane informatie:

- aantal verwerkte locaties;
- of een foto is vernieuwd of overgeslagen;
- cache-evictiegebeurtenissen;
- worker-resultaat (success/retry/failure).

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- lokale fotopaden als die persoonsgegevens kunnen bevatten.

## 14. Voorgestelde implementatiestappen

1. Bestudeer `WeatherUpdateJob.kt` als referentiepatroon.
2. Bestudeer `WallpaperRepository.kt` en `WallpaperImageStore.kt`.
3. Voeg een `WallpaperPhotoRefreshWorker` toe als `CoroutineWorker`.
4. Voeg pure selectielogica toe voor "moet deze locatie verversen".
5. Plan een `PeriodicWorkRequest` met constraints en een unieke worknaam.
6. Integreer ophalen via de bestaande RemoveSky-infrastructuur.
7. Sla op via `WallpaperImageStore` met respect voor limieten en historie.
8. Voeg het signaleren van nieuwe lokale data toe.
9. Voeg backoff en foutafhandeling toe.
10. Voeg pure unit tests toe.
11. Build debug en release.
12. Test de worker handmatig en controleer de cache.
13. Controleer dat de wallpaper zelf geen HTTP/GPS doet.
14. Controleer `git diff` op scope en secrets.

## 15. Unit tests

Voeg tests toe voor pure logica, zonder echte netwerk- of WorkManager-uitvoering waar dat niet nodig is.

Minimale testgevallen:

1. een locatie met een verouderde foto wordt geselecteerd voor verversing;
2. een locatie met een recente foto wordt overgeslagen;
3. het maximum aantal foto's per locatie wordt gerespecteerd;
4. de cachelimiet leidt tot eviction van de oudste of minst relevante foto;
5. een recente, identieke URL wordt niet opnieuw opgehaald;
6. een locatie zonder geldige data gebruikt de gedocumenteerde fallback;
7. de planning gebruikt een unieke worknaam en voorkomt duplicaten;
8. backoff wordt toegepast bij een gesimuleerde fout;
9. het signaal naar de wallpaper wordt alleen gegeven bij daadwerkelijke wijziging;
10. de pure selectielogica is deterministisch bij gelijke input.

Gebruik test-doubles voor repositories en netwerk. Tests mogen geen echte HTTP- of GPS-call doen.

## 16. Handmatige testmatrix

### Scenario's

| Scenario | Verwachting |
|---|---|
| Eerste run met lege cache | foto wordt opgehaald en opgeslagen |
| Recente foto aanwezig | verversing wordt overgeslagen |
| Meerdere locaties | per locatie correct verwerkt |
| Cachelimiet bereikt | oudste foto wordt geevict |
| Geen netwerk | worker wacht op constraint, geen crash |
| Netwerkfout | retry met backoff |
| Na verversing | wallpaper toont nieuwe lokale foto na invalidatie |
| Wallpaper actief | wallpaper doet zelf geen HTTP/GPS |
| Worker draait twee keer achter elkaar | tweede run downloadt niet opnieuw dezelfde foto en evict niet onnodig |

### Modi

- standaardinterval;
- eenmalige trigger;
- app afgesloten (binnen WorkManager-grenzen);
- verschillende netwerkconstraints.

### Platforms

- recente Android-versie op gekoppelde telefoon;
- actieve emulator;
- waar mogelijk een oudere Android-versie.

## 17. Acceptatiecriteria

1. Een background-worker haalt periodiek per locatie een nieuwe RemoveSky-foto op.
2. De worker respecteert netwerk- en batterijconstraints.
3. De worker respecteert cachelimiet, maximum per locatie en recente historie.
4. Nieuwe foto's worden via de bestaande `WallpaperImageStore` opgeslagen.
5. De worker signaleert de wallpaper alleen dat nieuwe lokale data beschikbaar is.
6. De wallpaper doet zelf geen HTTP- of GPS-call.
7. Er wordt geen tweede fotocache geintroduceerd.
8. Fouten leiden tot retries met backoff, niet tot crashes.
9. Er worden geen secrets of gevoelige URL's gelogd.
10. Bestaande handmatige refresh- en cachefunctionaliteit blijft werken.
11. De renderlus en effecten zijn niet gewijzigd.

## 18. Definition of done

- implementatie is beperkt tot worker-, repository-koppeling en gerichte tests;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- de worker is handmatig geverifieerd en de cache gecontroleerd;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-010-bestanden;
- commit en push gebeuren pas nadat de controle is goedgekeurd.

## 19. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, iconpack- of niet- ACT-010-bestanden buiten de fotoverversing;
- raak de renderlus en effecten niet aan;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg de worker bij voorkeur in een eigen bestand toe naast bestaande jobs;
- stem het invalidatiesignaal af met ACT-002 en ACT-011;
- stage bestanden expliciet, nooit via `git add .`;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 20. Verwacht eindresultaat

Na ACT-010 wisselen locatiefoto's automatisch dankzij een centrale background-worker die netwerk- en batterijconstraints, cachelimiet, maximum per locatie en historie respecteert. De worker slaat nieuwe foto's lokaal op en signaleert de wallpaper alleen dat nieuwe data beschikbaar is. De wallpaper blijft volledig read-only en doet zelf geen HTTP- of GPS-call.