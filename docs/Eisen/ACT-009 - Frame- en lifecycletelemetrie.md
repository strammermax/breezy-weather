# ACT-009 - Frame- en lifecycletelemetrie

## Status

- Type: implementatieopdracht
- Prioriteit: middelhoog
- Omvang: klein tot middelgroot
- Risico: laag tot middelgroot, omdat dit meetcode aan de renderlus en lifecycle toevoegt
- Prerequisite: ACT-001 - Centrale wallpaper scene state; bij voorkeur ook ACT-007 - Renderer quality profiles
- Doelplatform: Android 13 en hoger heeft prioriteit; Android 6 tot en met 12 behoudt een Canvas fallback

## 1. Opdracht in een zin

Meet gemiddelde en hoge-percentiel-frametijd, dropped frames, het actieve quality profile en de visibility lifecycle in debug, zodat vastlopen en batterijproblemen meetbaar worden in plaats van alleen visueel beoordeeld, en zonder netwerkverkeer vanuit de wallpaper.

## 2. Waarom deze wijziging nodig is

De live wallpaper begrenst FPS en stopt rendering bij onzichtbaarheid, maar er is geen gestructureerde meting van prestaties. Daardoor:

- zijn haperingen en dropped frames moeilijk objectief vast te stellen;
- is onduidelijk hoe vaak automatische degradatie optreedt;
- is niet aantoonbaar dat de renderer echt stopt wanneer de wallpaper onzichtbaar is;
- is batterijgedrag lastig te relateren aan effectbelasting;
- ontbreekt inzicht in de frametijd-distributie per quality profile.

De gewenste situatie is compacte, debug-only telemetrie die deze waarden meet zonder de productieprestaties te schaden.

## 3. Huidige architectuur

### Belangrijkste bestanden

1. `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
   - beheert `WallpaperService.Engine`, de renderthread en de framecadans;
   - meet al frametijd voor adaptive quality;
   - start en stopt rendering op basis van wallpaper visibility.

2. `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperWeatherEffectRenderer.kt`
   - bevat de effect-rendering en, indien aanwezig, het quality profile.

3. De door ACT-001 toegevoegde scene-statebestanden.
   - leveren het actieve quality profile;
   - gebruik de namen en locatie die ACT-001 daadwerkelijk heeft geintroduceerd;
   - maak geen tweede concurrerend scene-state-model.

4. De door ACT-007 toegevoegde quality profile-logica.
   - als ACT-007 is gemerged, moet telemetrie het effectieve profiel en degradatiegebeurtenissen kunnen rapporteren.

### Huidig gedrag

De renderer meet frametijd voor adaptive snow/hail quality, maar er is geen samengevatte telemetrie van frametijd-distributie, dropped frames of lifecycle-gebeurtenissen.

## 4. Afbakening

### Wel uitvoeren

- meten van gemiddelde en hoge-percentiel-frametijd over een venster;
- tellen van dropped frames ten opzichte van de 30 FPS-cadans;
- registreren van het actieve quality profile en degradatiegebeurtenissen;
- registreren van visibility lifecycle (zichtbaar/onzichtbaar, start/stop);
- aantonen dat de renderer stopt wanneer de wallpaper onzichtbaar is;
- debug-only samenvattende logging, niet per frame;
- optioneel een debug-overlay of diagnostische weergave;
- unit tests voor pure aggregatielogica;
- handmatige/emulatortests voor lifecycle en logging.

### Niet uitvoeren

- geen telemetrie naar externe servers of analytics;
- geen logging per frame;
- geen nieuwe effecten;
- geen quality profile-model zelf: dat is ACT-007;
- geen automatische foto-download: dat is ACT-010;
- geen wijziging aan Meteo-, GPS- of RemoveSky-clients;
- geen OpenGL-migratie;
- geen brede UI-herontwerp;
- geen brede refactor van Breezy Weather;
- geen secrets, GPS of fotopaden in logs.

## 5. Architectuurregel

De wallpaper is een read-only consumer van de centrale lokale datalaag.

Tijdens deze opdracht mag de telemetrie:

- de gemeten frametijd en framecadans lezen;
- het actieve quality profile lezen;
- de lifecycle-callbacks observeren;
- in debug naar logcat schrijven.

De telemetrie mag niet:

- data naar externe servers sturen;
- GPS starten;
- een weather provider aanroepen;
- RemoveSky aanroepen;
- HTTP-requests uitvoeren;
- secrets, exacte locaties of fotopaden loggen;
- per frame loggen.

## 6. Prerequisite ACT-001 en ACT-007

ACT-009 gebruikt de centrale scene state uit ACT-001 voor het quality profile. Controleer voor aanvang welke class en velden ACT-001 daadwerkelijk heeft toegevoegd.

Als ACT-007 is gemerged, moet telemetrie het effectieve profiel en degradatie-/herstelgebeurtenissen kunnen rapporteren. Is ACT-007 nog niet aanwezig, dan rapporteert telemetrie het bestaande adaptive quality-niveau.

Als ACT-001 nog niet is gemerged, stop dan en rapporteer deze dependency. Voeg niet stilzwijgend een tweede scene-state-architectuur toe.

## 7. Gewenst telemetriemodel

Introduceer een kleine, herbruikbare aggregator die per venster waarden samenvat en geen allocaties per frame veroorzaakt.

Conceptueel:

```kotlin
class FrameTelemetry(
    val windowSize: Int,
) {
    fun recordFrame(frameTimeMillis: Float)
    fun recordDroppedFrame()
    fun snapshot(): FrameTelemetrySnapshot
    fun reset()
}

data class FrameTelemetrySnapshot(
    val averageFrameTimeMillis: Float,
    val p95FrameTimeMillis: Float,
    val droppedFrames: Int,
    val totalFrames: Int,
    val activeProfile: WallpaperQualityProfile,
)
```

- gebruik een vaste ringbuffer voor de frametijden;
- `windowSize` is minimaal 120 frames (ongeveer 4 seconden bij 30 FPS), zodat p95 stabiel genoeg is om niet op elk los frame te reageren;
- bereken gemiddelde en hoge-percentiel zonder per frame te sorteren waar dat te duur is, of sorteer alleen bij `snapshot()`;
- log een samenvatting periodiek, bijvoorbeeld elke N seconden, niet per frame.

## 8. Wat wordt gemeten

- gemiddelde frametijd over het venster;
- hoge-percentiel-frametijd (bijvoorbeeld p95);
- aantal dropped frames ten opzichte van de doel-cadans;
- totaal aantal frames in het venster;
- actief quality profile;
- aantal degradatie- en herstelgebeurtenissen (indien ACT-007 aanwezig);
- visibility lifecycle-gebeurtenissen en tijdstempels;
- bevestiging dat de renderlus stopt bij onzichtbaarheid.

## 9. Lifecycle-meting

- registreer wanneer `onVisibilityChanged(true/false)` wordt aangeroepen;
- registreer wanneer de renderthread start en stopt;
- meet de tijd tussen onzichtbaar worden en het daadwerkelijk stoppen van frames;
- log een samenvatting bij het onzichtbaar worden;
- toon dat er geen frames meer worden geteld terwijl de wallpaper onzichtbaar is.

## 10. Debug-overlay

**Gekozen scope voor deze opdracht**: geen visuele overlay. De samenvattende debug-logging (sectie 7-9) is voldoende voor deze versie; een overlay kan in een latere, losse opdracht worden toegevoegd als daar behoefte aan blijkt.

Als tijdens implementatie blijkt dat een minimale overlay vrijwel gratis is (bijvoorbeeld 3 regels tekst rechtsboven: FPS, gemiddelde/p95 ms, actief profile), mag die optioneel worden toegevoegd onder deze voorwaarden:

- alleen in debug-builds, uitschakelbaar via een bestaande debug-flag;
- geen secrets of locaties;
- geen permanente verstoring van de scene (rotating testlabel blijft leesbaar).

## 11. Performance-eisen

- maximaal 30 FPS blijft gehandhaafd;
- geen objectallocaties in `recordFrame` en `recordDroppedFrame`;
- gebruik een vaste ringbuffer;
- log niet per frame, alleen samenvattend;
- de meting mag de frametijd zelf niet meetbaar verzwaren;
- rendering en meting stoppen wanneer de wallpaper onzichtbaar is.

Meet minimaal:

- overhead van de telemetrie zelf;
- correcte gemiddelde en p95 bij bekende invoer;
- dropped frame-telling bij een kunstmatig trage frame;
- lifecycle-stop bij onzichtbaar worden.

## 12. Logging en privacy

Alle nieuwe logging is debug-only en niet per frame.

Toegestane informatie:

- gemiddelde en p95 frametijd;
- dropped frames en totaal frames;
- actief quality profile en degradatiegebeurtenissen;
- visibility lifecycle-gebeurtenissen;
- weather family.

Niet loggen:

- API-keys;
- Cloudflare Access credentials;
- volledige GPS-coordinaten;
- RemoveSky-URL met gevoelige queryparameters;
- response bodies;
- lokale fotopaden als die persoonsgegevens kunnen bevatten.

## 13. Voorgestelde implementatiestappen

1. Controleer en documenteer de ACT-001 scene-state API en, indien aanwezig, de ACT-007 quality profile-API.
2. Voeg een pure `FrameTelemetry`-aggregator toe met ringbuffer.
3. Voeg `recordFrame` en `recordDroppedFrame` toe zonder allocaties.
4. Bereken gemiddelde en p95 bij `snapshot()`.
5. Koppel de aggregator aan de bestaande frametijd-meting in de service.
6. Registreer visibility lifecycle-gebeurtenissen.
7. Voeg periodieke samenvattende debug-logging toe.
8. Voeg optioneel een debug-overlay toe.
9. Voeg pure unit tests toe.
10. Build debug en release.
11. Test op emulator en gekoppelde telefoon.
12. Controleer dat release geen verbose telemetrie logt en verifieer dat de telemetrie-logcode in de releasebuild door R8/ProGuard wordt verwijderd of effectief geen-op is (bijvoorbeeld via `BuildConfig.DEBUG`-checks).
13. Controleer `git diff` op scope en secrets.

## 14. Unit tests

Voeg tests toe voor pure logica, zonder echte Surface of WallpaperService waar dat niet nodig is.

Minimale testgevallen:

1. gemiddelde frametijd klopt bij bekende invoer;
2. p95 klopt bij bekende invoer;
3. de ringbuffer overschrijft oude waarden na `windowSize` frames;
4. dropped frames worden correct geteld;
5. `recordFrame` alloceert geen objecten;
6. `reset()` wist de tellers;
7. `snapshot()` bevat het actieve profiel;
8. degradatiegebeurtenissen worden geteld indien aangeleverd;
9. lifecycle-gebeurtenissen worden in volgorde geregistreerd;
10. geen logregel per frame in de hot path.

Gebruik een injecteerbare clock of geef frametijden als parameters door. Tests mogen niet afhankelijk zijn van de echte huidige tijd of een echte Surface.

## 15. Handmatige testmatrix

### Scenario's

| Scenario | Verwachting |
|---|---|
| Rustige scene | lage frametijd, weinig dropped frames |
| Zware thunderstorm | hogere frametijd, telemetrie toont belasting |
| Automatische degradatie | telemetrie toont profielwissel |
| Wallpaper onzichtbaar | meting en rendering stoppen |
| Wallpaper opnieuw zichtbaar | meting hervat |
| Release-build | geen verbose telemetrie-logging |

### Modi

- Auto weather + Auto day/night;
- verschillende quality profiles;
- Rotating;
- animations disabled;
- wallpaper preview en werkelijk ingestelde wallpaper.

### Platforms

- Android 13 of hoger op gekoppelde telefoon;
- actieve emulator;
- waar mogelijk Android 12 of lager voor Canvas fallback.

## 16. Acceptatiecriteria

1. Gemiddelde en hoge-percentiel-frametijd worden gemeten.
2. Dropped frames worden geteld ten opzichte van de doel-cadans.
3. Het actieve quality profile wordt geregistreerd.
4. Visibility lifecycle-gebeurtenissen worden geregistreerd.
5. Het is aantoonbaar dat de renderer stopt wanneer de wallpaper onzichtbaar is.
6. Logging is debug-only en niet per frame.
7. Er zijn geen objectallocaties in de meet-hot-path.
8. De telemetrie verzendt geen data naar externe servers.
9. Er worden geen secrets, exacte locaties of fotopaden gelogd.
10. De maximale FPS blijft 30.
11. De release-build logt geen verbose telemetrie.
12. Bestaande effect- en lifecyclefunctionaliteit blijft werken.

## 17. Definition of done

- implementatie is beperkt tot telemetrie-/meetcode en gerichte tests;
- debugbuild slaagt;
- releasebuild slaagt;
- relevante unit tests slagen;
- app is geinstalleerd en getest op gekoppelde telefoon;
- emulatorcontrole is uitgevoerd;
- lifecycle-stop is geverifieerd;
- `git diff --check` is schoon;
- geen unrelated cleanup of refactor;
- geen bestaande wijzigingen van andere agents overschreven;
- commit bevat uitsluitend ACT-009-bestanden;
- commit en push gebeuren pas nadat de controle is goedgekeurd.

## 18. Samenwerking met andere agents

Er werken meerdere agents tegelijk aan deze repository. Volg daarom deze regels:

- lees eerst `git status` en de actuele diff;
- wijzig geen camera-, RemoveSky-, iconpack- of andere niet- ACT-009-bestanden;
- neem bestaande wijzigingen in gedeelde wallpaperbestanden als uitgangspunt;
- revert nooit wijzigingen die niet door deze opdracht zijn gemaakt;
- voeg de telemetrie bij voorkeur in een klein eigen bestand toe;
- houd wijzigingen in `MaterialLiveWallpaperService.kt` zo lokaal mogelijk;
- stem profiel- en degradatievelden af met ACT-007;
- stage bestanden expliciet, nooit via `git add .`;
- meld conflicten met ACT-001 of ACT-007 in plaats van een tweede architectuur te bouwen;
- baseer alle keuzes op de actuele code, niet uitsluitend op dit document.

## 19. Verwacht eindresultaat

Na ACT-009 zijn frametijd-distributie, dropped frames, quality profile en visibility lifecycle meetbaar in debug. Het is aantoonbaar dat de renderer stopt wanneer de wallpaper onzichtbaar is, de meting voegt geen merkbare overhead toe, en er gaat geen data naar externe servers of in logs met secrets of locaties. De wallpaper blijft begrensd op maximaal 30 FPS en lokaal.