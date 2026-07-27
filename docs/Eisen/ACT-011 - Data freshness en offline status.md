# ACT-011 - Data freshness en offline status

## Status

- Type: implementatieopdracht
- Prioriteit: middelhoog
- Omvang: klein tot middelgroot
- Risico: laag tot middelgroot, omdat dit de centrale lokale datalaag en de instellingen/debugpreview raakt
- Prerequisite: sluit aan op de centrale lokale datalaag; bij voorkeur na ACT-010 - Centrale automatische foto-refresh
- Doelplatform: alle ondersteunde Android-versies

## 1. Opdracht in een zin

Leg refresh time, photo refresh time en stale status vast in de centrale lokale datalaag en toon data age in instellingen/debugpreview, zodat zonstand, locatie en weer aantoonbaar uit dezelfde actuele snapshot komen en offline de laatst geldige scene beschikbaar blijft.

## 2. Waarom deze wijziging nodig is

De wallpaper leest lokale weer- en fotodata, maar er is geen expliciete, zichtbare registratie van hoe vers die data is. Daardoor:

- is niet aantoonbaar dat zonstand, locatie en weer uit dezelfde snapshot komen;
- is onduidelijk hoe oud de getoonde data is;
- is offline gedrag niet expliciet zichtbaar of diagnostisch;
- ontbreekt een gedocumenteerde fallback bij ontbrekende data;
- is moeilijk te beoordelen of een refresh recent heeft plaatsgevonden.

De gewenste situatie is een duidelijke registratie van data freshness en een zichtbare of diagnostische status.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `WeatherRepository.kt` en `LocationRepository.kt`
   - centrale lokale waarheid voor weer en locatie.

2. `WallpaperImageStore.kt`
   - beheert de lokale fotocache en historie per locatie.

3. `RefreshHelper.kt` en `WeatherUpdateJob.kt`
   - verzorgen de bestaande weerverversing.

4. De door ACT-010 toegevoegde foto-refresh-worker.
   - als ACT-010 is gemerged, levert die een photo refresh time-signaal.

5. `LiveWallpaperConfigActivity.kt`
   - de plek voor instellingen en een debugpreview.

6. De door ACT-001 toegevoegde scene-statebestanden.
   - de scene state kan een data age-veld of timestamp dragen;
   - gebruik de namen en locatie die ACT-001 daadwerkelijk heeft geintroduceerd;
   - maak geen tweede concurrerend scene-state-model.

### Huidig gedrag

De laatst opgeslagen weerdata en gecachete foto blijven offline bruikbaar, maar er is geen expliciete, zichtbare timestamp of stale-status die aantoont uit welke snapshot de scene is opgebouwd.

## 4. Afbakening

### Wel uitvoeren

- vastleggen van weather refresh time in de centrale datalaag;
- vastleggen van photo refresh time per locatie;
- bepalen van een stale status op basis van leeftijd;
- garanderen dat zonstand, locatie en weer uit dezelfde snapshot komen;
- tonen van data age in instellingen of debugpreview;
- een gedocumenteerde fallback bij ontbrekende data;
- behoud van bruikbaar offline gedrag;
- unit tests voor pure freshness- en stale-logica;
- handmatige tests voor offline en data age-weergave.

### Niet uitvoeren

- geen nieuwe weather provider-integratie;
- geen automatische foto-download zelf: dat is ACT-010;
- geen wijziging aan de renderlus of effecten;
- geen quality profile-werk: dat is ACT-007;
- geen brede UI-herontwerp;
- geen brede refactor van Breezy Weather;
- geen HTTP- of GPS-call vanuit de wallpaper;
- geen externe assets uit YoWindow kopieren.

## 5. Architectuurregel

De wallpaper blijft een read-only consumer van de centrale lokale datalaag.

Tijdens deze opdracht mag de implementatie:

- timestamps in de centrale lokale datalaag lezen en schrijven via de juiste laag;
- de stale status afleiden uit lokale timestamps;
- data age tonen in instellingen of debugpreview.

De implementatie mag niet:

- vanuit de wallpaper netwerk of GPS starten;
- een weather provider of RemoveSky rechtstreeks aanroepen vanuit de wallpaper;
- een tweede datacache introduceren;
- secrets of exacte locaties tonen of loggen.

## 6. Prerequisite

ACT-011 sluit aan op de centrale lokale datalaag. Controleer welke timestamps al bestaan in `WeatherRepository`, `LocationRepository` en `WallpaperImageStore`.

Als ACT-010 is gemerged, gebruik het photo refresh time-signaal van de worker. Is ACT-010 nog niet aanwezig, gebruik dan de timestamp van de handmatige refresh.

Als ACT-001 is gemerged, koppel data age aan de scene state. Voeg geen tweede scene-state-architectuur toe.

## 7. Gewenst freshness-model

Leg de freshness expliciet vast.

Conceptueel:

```kotlin
data class DataFreshness(
    val weatherRefreshedAtMillis: Long?,
    val photoRefreshedAtMillis: Long?,
    val isWeatherStale: Boolean,
    val isPhotoStale: Boolean,
)

fun isStale(refreshedAtMillis: Long?, nowMillis: Long, maxAgeMillis: Long): Boolean
```

- definieer een maximale leeftijd voor weer en foto, centraal gedocumenteerd;
- bereken stale status als pure functie van timestamp en nu;
- ontbrekende timestamp betekent onbekend of stale, gedocumenteerd.

Concrete standaardwaarden (mogen worden bijgesteld op basis van bestaande refresh-intervallen, maar moeten expliciet blijven):

- `maxWeatherAgeMillis` = 24 uur;
- `maxPhotoAgeMillis` = 48 uur (foto's wisselen minder vaak dan weer en mogen langer "vers" blijven).

## 8. Snapshot-consistentie

- zonstand, locatie en weer moeten uit dezelfde lokale snapshot komen;
- gebruik dezelfde refresh time voor de afgeleide waarden;
- voorkom dat de wallpaper weer uit snapshot A en zonstand uit snapshot B combineert;
- als ACT-001 aanwezig is, draagt de scene state de snapshot-timestamp.

## 9. Data age-weergave

- toon data age in instellingen of debugpreview;
- toon de leeftijd van zowel weer als foto;
- toon een duidelijke stale-indicatie wanneer data verouderd is;
- houd de weergave compact en zonder persoonlijke gegevens;
- de productie-UI mag een subtiele indicatie tonen; uitgebreide diagnostiek mag debug-only zijn.

Concrete weergave: toon relatieve tijd (bijvoorbeeld "1 uur geleden", "gisteren") zonder exacte timestamps of GPS-coordinaten. Bij stale data: toon een klein waarschuwingsicoon plus het label "offline" of "verouderd" naast de relatieve tijd.

## 10. Offline gedrag

- offline blijft de laatst geldige scene beschikbaar;
- toon dat de data verouderd is in plaats van een lege of foutscene;
- bij volledig ontbrekende data geldt een gedocumenteerde fallback;
- de wallpaper mag offline geen nieuwe data proberen op te halen.

## 11. Fallback bij ontbrekende data

- ontbrekende weather refresh time: behandel als onbekend en gebruik een veilige standaard;
- ontbrekende photo refresh time: toon de laatst beschikbare foto of geen foto;
- ontbrekende sunrise/sunset: gebruik de bestaande astronomische fallback (consistent met ACT-002);
- documenteer elke fallback expliciet.

## 12. Logging en privacy

Alle nieuwe logging is debug-only en compact.

Toegestane informatie:

- weer- en foto-leeftijd in relatieve tijd;
- stale status;
- gekozen fallback bij ontbrekende data.

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- lokale fotopaden als die persoonsgegevens kunnen bevatten.

## 13. Voorgestelde implementatiestappen

1. Inventariseer bestaande timestamps in de centrale datalaag.
2. Voeg of consolideer weather refresh time en photo refresh time.
3. Voeg een pure `isStale`-functie en een `DataFreshness`-model toe.
4. Koppel de freshness aan de scene state indien ACT-001 aanwezig is.
5. Borg snapshot-consistentie voor weer, locatie en zonstand.
6. Toon data age en stale status in instellingen of debugpreview.
7. Documenteer de fallbacks bij ontbrekende data.
8. Voeg pure unit tests toe.
9. Build debug en release.
10. Test offline en data age-weergave.
11. Controleer `git diff` op scope en secrets.

## 14. Unit tests

Voeg tests toe voor pure logica, zonder echte netwerk- of repository-uitvoering waar dat niet nodig is.

Minimale testgevallen:

1. een recente timestamp is niet stale;
2. een oude timestamp boven de maximale leeftijd is stale;
3. een ontbrekende timestamp wordt als onbekend of stale behandeld zoals gedocumenteerd;
4. weer- en foto-staleness worden onafhankelijk berekend;
5. de stale-grens is een pure functie van timestamp, nu en maxAge;
6. snapshot-consistentie levert dezelfde refresh time voor afgeleide waarden;
7. ontbrekende sunrise/sunset gebruikt de astronomische fallback zonder NaN;
8. data age-formattering toont relatieve tijd zonder exacte locatie;
9. offline scenario gebruikt de laatst geldige data;
10. de freshness-berekening is deterministisch bij gelijke input;
11. een scene-snapshot die via ACT-002 een transition tussen twee weather-states rendert, gebruikt voor beide renderers dezelfde `weatherRefreshedAtMillis`/zon-maan-timestamp, ook al verschilt de weather family.

Gebruik een injecteerbare clock of geef tijden als parameters door. Tests mogen niet afhankelijk zijn van de echte huidige tijd.

## 15. Handmatige testmatrix

### Scenario's

| Scenario | Verwachting |
|---|---|
| Recente data | geen stale-indicatie, lage data age |
| Verouderde data | stale-indicatie en hogere data age |
| Offline | laatst geldige scene blijft, met stale-indicatie |
| Geen weer-timestamp | gedocumenteerde fallback |
| Geen foto | laatst beschikbare of geen foto |
| Na ACT-010 verversing | data age daalt na verversing |

### Modi

- Auto weather + Auto day/night;
- debugpreview;
- instellingenpagina;
- offline (vliegtuigmodus);
- wallpaper preview en werkelijk ingestelde wallpaper.

### Platforms

- recente Android-versie op gekoppelde telefoon;
- actieve emulator;
- waar mogelijk een oudere Android-versie.

## 16. Acceptatiecriteria

1. Weather refresh time wordt vastgelegd in de centrale lokale datalaag.
2. Photo refresh time wordt per locatie vastgelegd.
3. Een stale status wordt afgeleid uit de leeftijd.
4. Zonstand, locatie en weer komen aantoonbaar uit dezelfde snapshot.
5. Data age wordt getoond in instellingen of debugpreview.
6. Offline blijft de laatst geldige scene beschikbaar.
7. Ontbrekende data heeft een gedocumenteerde fallback.
8. De wallpaper doet zelf geen HTTP- of GPS-call.
9. Er wordt geen tweede datacache geintroduceerd.
10. Er worden geen secrets of exacte locaties getoond of gelogd.
11. Bestaande weer-, foto- en cachefunctionaliteit blijft werken.

## 17. Definition of done

- implementatie is beperkt tot de datalaag, freshness-logica en instellingen/debugpreview;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- offline gedrag en data age-weergave zijn geverifieerd;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-011-bestanden;
- commit en push gebeuren pas nadat de controle is goedgekeurd.

## 18. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, iconpack- of niet- ACT-011-bestanden buiten freshness en weergave;
- raak de renderlus en effecten niet aan;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg freshness-logica bij voorkeur in een klein eigen bestand toe;
- stem het photo refresh time-signaal af met ACT-010;
- stem de scene state-koppeling af met ACT-001 en ACT-002;
- stage bestanden expliciet, nooit via `git add .`;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 19. Verwacht eindresultaat

Na ACT-011 is duidelijk hoe vers de getoonde weer- en fotodata is. Zonstand, locatie en weer komen aantoonbaar uit dezelfde snapshot, data age is zichtbaar in instellingen of debugpreview, en offline blijft de laatst geldige scene beschikbaar met een duidelijke stale-indicatie. De wallpaper blijft read-only en doet zelf geen HTTP- of GPS-call.