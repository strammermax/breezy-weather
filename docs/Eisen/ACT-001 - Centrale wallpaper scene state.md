# ACT-001 - Centrale wallpaper scene state

## Status

- Type: architectuur en implementatie
- Prioriteit: hoog
- Omvang: middelgroot
- Risico: laag tot middelgroot
- Blokkeert: ACT-002 - Vloeiende state transitions
- Doelplatform: alle ondersteunde Android-versies

## 1. Opdracht

Introduceer één immutable `WallpaperSceneState` als centrale, renderklare snapshot voor de live wallpaper. Deze state wordt uitsluitend opgebouwd uit lokale repositorydata, lokale wallpaperconfiguratie en berekende astronomische waarden.

De state moet verspreide weather-, wind-, dag/nacht- en effectbeslissingen samenbrengen zonder zelf Android-resources, netwerkclients, repositories of mutable rendererobjecten te bevatten.

## 2. Probleem

`MaterialLiveWallpaperService` bewaart momenteel losse velden zoals weather kind, daytime, sunrise, sunset en location data. De effectrenderer ontvangt vervolgens afzonderlijke constructorwaarden. Andere visuele parameters worden opnieuw afgeleid in verschillende functies en shaders.

Dit veroorzaakt:

- meerdere bronnen voor dezelfde visuele beslissing;
- moeilijk testbare mapping van weather family naar effecten;
- kans op afwijkende wind- of dag/nachtlogica tussen lagen;
- onnodige koppeling tussen repositorymodellen en renderer;
- geen eenduidig inputmodel voor ACT-002-transitions.

## 3. Doelarchitectuur

De datastroom wordt:

```text
lokale LocationRepository + lokale WeatherRepository + wallpaperconfig
                              |
                              v
                  WallpaperSceneStateFactory
                              |
                              v
                   immutable scene snapshot
                              |
                              v
        hemel / celestial / clouds / foto / foreground / glass
```

De wallpaper blijft een read-only consumer. De factory haalt zelf niets op en ontvangt alleen reeds beschikbare primitieve waarden.

## 4. Scope

### Wel

- een immutable scene-state datamodel;
- enum voor de twaalf ondersteunde weather families;
- genormaliseerde renderparameters tussen `0f` en `1f` waar toepasselijk;
- een pure factory voor family-, wind- en effectmapping;
- een centrale huidige scene state in `MaterialLiveWallpaperService`;
- gebruik van de state voor effectrendererconstructie;
- unit tests voor mapping, normalisatie en gelijkheid;
- een API waarop ACT-002 direct kan voortbouwen.

### Niet

- geen interpolatie of transition timing;
- geen dubbele outgoing/incoming renderer;
- geen nieuwe weereffecten;
- geen UI-instellingen;
- geen netwerk-, GPS- of RemoveSky-code;
- geen wijzigingen aan repositories of database;
- geen OpenGL-migratie;
- geen brede refactor van bestaande shaders.

## 5. Bestanden

Nieuw:

- `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperSceneState.kt`
- `app/src/test/kotlin/org/breezyweather/wallpaper/WallpaperSceneStateTest.kt`

Gericht aanpassen:

- `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`
- eventueel `TransitionManager.kt` uitsluitend om `Any` te vervangen door `WallpaperSceneState`, wanneer dat parallelle bestand nog aanwezig is.

Niet aanpassen:

- cameraonderdelen;
- appnavigatie;
- RemoveSky-code;
- weather providers;
- databasecode;
- icon packs.

## 6. Datamodel

Gebruik een compact model zonder Android `Context`, `Drawable`, `Paint`, repository of domainmodel.

Minimaal:

```kotlin
enum class WallpaperWeatherFamily {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    RAIN,
    SNOW,
    SLEET,
    HAIL,
    FOG,
    HAZE,
    THUNDER,
    THUNDERSTORM,
    WIND,
}

data class WallpaperSceneState(
    val weatherKind: Int,
    val weatherFamily: WallpaperWeatherFamily,
    val daylight: Float,
    val windSpeedMetersPerSecond: Float,
    val windGustMetersPerSecond: Float,
    val windDirectionDegrees: Float?,
    val windFactor: Float,
    val cloudDensity: Float,
    val cloudDarkness: Float,
    val precipitationIntensity: Float,
    val fogIntensity: Float,
    val hazeIntensity: Float,
    val thunderIntensity: Float,
    val glassRainIntensity: Float,
    val photoNightTint: Float,
    val sunriseMillis: Long?,
    val sunsetMillis: Long?,
    val moonriseMillis: Long?,
    val moonsetMillis: Long?,
)
```

`daylight = 1f` betekent volledig dag en `0f` volledig nacht. `photoNightTint` is het complement of een expliciet afgeleide waarde.

## 7. Factorycontract

De factory is puur en neemt primitieve waarden aan:

```kotlin
WallpaperSceneStateFactory.create(
    weatherKind = weatherKind,
    daylight = if (daytime) 1f else 0f,
    windSpeedMetersPerSecond = speed,
    windGustMetersPerSecond = gust,
    windDirectionDegrees = direction,
    sunriseMillis = sunrise,
    sunsetMillis = sunset,
    moonriseMillis = moonrise,
    moonsetMillis = moonset,
)
```

De factory:

- clamp alle genormaliseerde waarden;
- normaliseert windrichting naar `0..<360`;
- zet onbekende weather kinds veilig om naar Clear;
- gebruikt dezelfde winddrempels als de huidige wallpaper;
- maakt Rain, Snow, Sleet en Hail altijd bewolkt;
- maakt Thunder en Thunderstorm donker en snel;
- activeert glass rain alleen voor Rain, Sleet en Thunderstorm;
- bevat geen actuele tijd en doet geen I/O.

## 8. Basismapping

De precieze waarden mogen beperkt worden afgestemd op de bestaande visuals, maar de onderlinge verhoudingen moeten als volgt zijn:

| Family | Cloud density | Darkness | Precipitation | Fog/Haze | Thunder | Glass rain |
|---|---:|---:|---:|---:|---:|---:|
| Clear | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 |
| Partly cloudy | 0.35 | 0.05 | 0.00 | 0.00 | 0.00 | 0.00 |
| Cloudy | 0.85 | 0.25 | 0.00 | 0.00 | 0.00 | 0.00 |
| Rain | 0.95 | 0.55 | 0.75 | 0.00 | 0.00 | 0.70 |
| Snow | 0.90 | 0.35 | 0.75 | 0.00 | 0.00 | 0.00 |
| Sleet | 0.95 | 0.50 | 0.80 | 0.00 | 0.00 | 0.55 |
| Hail | 1.00 | 0.60 | 0.90 | 0.00 | 0.00 | 0.00 |
| Fog | 0.65 | 0.20 | 0.00 | fog 0.85 | 0.00 | 0.00 |
| Haze | 0.35 | 0.10 | 0.00 | haze 0.65 | 0.00 | 0.00 |
| Thunder | 0.95 | 0.70 | 0.00 | 0.00 | 0.55 | 0.00 |
| Thunderstorm | 1.00 | 0.85 | 1.00 | 0.00 | 1.00 | 0.90 |
| Wind | 0.55 | 0.15 | 0.00 | 0.00 | 0.00 | 0.00 |

Deze waarden zijn renderdefaults, geen meteorologische claims.

## 9. Windfactor

Behoud de huidige drempels:

```text
< 8 m/s      1.0
8-14 m/s     geleidelijk van 1.0 naar 2.8
>= 14 m/s    3.6
Thunder      minimaal 2.8
Thunderstorm minimaal 2.8
Wind         minimaal 4.2
```

Gebruik het maximum van windsnelheid en windstoten. Negatieve, NaN- en oneindige waarden worden veilig behandeld.

## 10. Engine-integratie

Voeg in `WeatherEngine` één veld toe:

```kotlin
private var mSceneState: WallpaperSceneState = WallpaperSceneStateFactory.create(...)
```

Maak één methode die de state opnieuw opbouwt uit de al lokaal geladen waarden:

```kotlin
private fun rebuildSceneState()
```

Roep deze aan wanneer:

- weather kind verandert;
- day/night verandert;
- lokale location/weather snapshot wordt gezet;
- sunrise/sunset/moonrise/moonset wordt bijgewerkt;
- rotating naar een volgende family gaat.

Gebruik daarna minimaal:

- `mSceneState.weatherKind` voor rendererselectie;
- `mSceneState.daylight >= 0.5f` voor bestaande binaire rendererpaden;
- `mSceneState.windFactor` voor effectrendererconstructie;
- `mSceneState` als type in `TransitionManager` indien aanwezig.

Laat bestaande losse velden voorlopig bestaan als adapter naar oudere code. ACT-001 is geen brede omzetting van alle drawfuncties. Documenteer dat ACT-002 de continue daylight- en effectwaarden verder consumeert.

## 11. Compatibiliteit met parallel ACT-002-werk

Er kan al een `TransitionManager.kt` bestaan met tijdelijke `Any`-types. Vervang alleen die types door `WallpaperSceneState` en corrigeer noodzakelijke imports. Implementeer ACT-002 niet binnen ACT-001.

Als `MaterialLiveWallpaperService.kt` al door een andere agent is gewijzigd:

- werk met die wijzigingen;
- revert ze niet;
- beperk de ACT-001-diff tot statevelden, stateopbouw en het vervangen van verspreide inputwaarden;
- herstel geen onaf ACT-002-gedrag tenzij dit nodig is om de build te laten compileren;
- rapporteer resterende ACT-002-testproblemen apart.

## 12. Tests

Minimaal testen:

1. alle twaalf weather kinds mappen naar de juiste family;
2. onbekend kind valt veilig terug op Clear;
3. alle intensiteiten liggen tussen 0 en 1;
4. Rain, Snow, Sleet en Hail hebben cloud density groter dan nul;
5. alleen Rain, Sleet en Thunderstorm hebben glass rain;
6. Thunderstorm heeft thunder- en precipitation-intensity;
7. windsnelheid onder 8 m/s geeft factor 1;
8. factor loopt op tussen 8 en 14 m/s;
9. 14 m/s of hoger geeft basisfactor 3.6;
10. Wind heeft minimaal factor 4.2;
11. Thunderstorm heeft minimaal factor 2.8;
12. gusts kunnen de snelheid verhogen;
13. daylight wordt geclamped;
14. photoNightTint volgt daylight;
15. windrichting wordt genormaliseerd;
16. NaN en infinity veroorzaken geen ongeldige state;
17. astro timestamps worden ongewijzigd bewaard;
18. twee identieke inputs leveren gelijke data-classstates.

## 13. Performance

- state wordt alleen bij inputwijzigingen opnieuw gebouwd, niet noodzakelijk per frame;
- geen I/O in factory of state;
- geen Android bitmap-, shader- of paintobjecten in state;
- geen mutable collections;
- geen logging per frame;
- stateconstructie moet triviaal genoeg zijn voor de renderthread;
- maximaal 30 FPS blijft ongewijzigd.

## 14. Acceptatiecriteria

1. Er bestaat precies één centrale immutable `WallpaperSceneState` voor wallpaperinput.
2. De twaalf bestaande weather families zijn expliciet gemodelleerd.
3. Weather-, wind- en basiseffectmapping is pure, unit-testbare code.
4. De engine bouwt de state uitsluitend uit reeds lokale data.
5. De effectrenderer gebruikt weather kind, daylight en windfactor uit de scene state.
6. Sunrise, sunset, moonrise en moonset zijn onderdeel van de snapshot.
7. Alle genormaliseerde intensiteiten blijven binnen `0f..1f`.
8. Onbekende of ongeldige invoer crasht niet.
9. `TransitionManager` gebruikt geen `Any` meer voor scene states, indien dat bestand aanwezig is.
10. Er is geen netwerk-, GPS- of RemoveSky-call toegevoegd.
11. De huidige laagvolgorde en zichtbare functionaliteit blijven behouden.
12. Android 13+ en Canvas fallback blijven compileren.
13. Unit tests slagen.
14. Debugbuild slaagt.
15. Geen wijzigingen van andere agents zijn teruggedraaid.

## 15. Definition of done

- uitwerking en implementatie volgen dit document;
- `git diff --check` is schoon voor ACT-001-bestanden;
- gerichte unit tests slagen;
- debugbuild is uitgevoerd;
- alleen ACT-001-bestanden worden gestaged;
- bestaande camera- en ACT-002-wijzigingen blijven buiten een eventuele ACT-001-commit;
- resterende problemen uit parallel werk worden expliciet gemeld.

## 16. Overdracht aan ACT-002

ACT-002 mag na afronding vertrouwen op:

- een stabiele immutable state;
- expliciete weather families;
- continue intensiteitsvelden;
- een genormaliseerde daylightfactor;
- centrale windfactor;
- astro timestamps;
- data-classgelijkheid om identieke targets te herkennen.

ACT-002 voegt vervolgens transition timing, interpolatie, outgoing/incoming renderers en continue fototint toe. Die verantwoordelijkheden horen niet in ACT-001.
