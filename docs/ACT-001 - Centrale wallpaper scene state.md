# ACT-001 - Centrale wallpaper scene state

## Status

- Type: architectuur en implementatie
- Prioriteit: hoog
- Omvang: middelgroot
- Blokkeert: ACT-002 - Vloeiende state transitions
- Doelplatform: alle ondersteunde Android-versies

## 1. Opdracht

Introduceer één immutable `WallpaperSceneState` als centrale, renderklare snapshot voor de live wallpaper. De state wordt uitsluitend opgebouwd uit reeds lokaal beschikbare weer-, locatie-, configuratie- en astronomische waarden.

De state bevat geen Android `Context`, repositories, netwerkclients, `Drawable`, `Paint`, bitmap of mutable rendererobjecten.

## 2. Waarom

De wallpaper bewaart weather kind, daytime, wind en astrodata nu als losse waarden. Renderparameters worden op meerdere plaatsen opnieuw afgeleid. Dit maakt de code moeilijker testbaar en geeft ACT-002 geen eenduidig transitiontarget.

ACT-001 brengt deze input samen zonder de renderer breed te refactoren.

## 3. Datastroom

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

De factory doet geen I/O en krijgt alleen primitieve waarden.

## 4. Scope

### Wel

- `WallpaperWeatherFamily` met de twaalf bestaande families;
- immutable `WallpaperSceneState`;
- pure `WallpaperSceneStateFactory`;
- genormaliseerde intensiteiten;
- centrale windfactor;
- astro timestamps in de snapshot;
- gerichte integratie in `MaterialLiveWallpaperService`;
- unit tests.

### Niet

- geen transition timing of crossfade;
- geen nieuwe weereffecten;
- geen UI-instellingen;
- geen repository- of databasewijzigingen;
- geen GPS-, HTTP-, Meteo- of RemoveSky-call;
- geen OpenGL-migratie;
- geen camera- of iconpackwijzigingen.

## 5. Bestanden

Nieuw:

- `app/src/main/kotlin/org/breezyweather/wallpaper/WallpaperSceneState.kt`
- `app/src/test/kotlin/org/breezyweather/wallpaper/WallpaperSceneStateTest.kt`

Gericht aangepast:

- `app/src/main/kotlin/org/breezyweather/wallpaper/MaterialLiveWallpaperService.kt`

## 6. Statecontract

De state bevat minimaal:

- oorspronkelijk/genormaliseerd weather kind;
- expliciete weather family;
- daylightfactor `0f..1f`;
- wind speed, gusts, direction en afgeleide windfactor;
- cloud density en darkness;
- precipitation-, fog-, haze-, thunder- en glass-rain-intensity;
- photo night tint;
- sunrise, sunset, moonrise en moonset.

`daytime` is een afgeleide compatibiliteitsproperty op basis van `daylight >= 0.5f`.

## 7. Weather families

De bestaande families blijven ongewijzigd:

1. Clear
2. Partly cloudy
3. Cloudy / Overcast
4. Rain
5. Snow
6. Sleet
7. Hail
8. Fog
9. Haze
10. Thunder
11. Thunderstorm
12. Wind

Een onbekend kind valt veilig terug op Clear.

## 8. Basismapping

| Family | Clouds | Darkness | Precipitation | Fog/Haze | Thunder | Glass rain |
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

Dit zijn visuele defaults, geen meteorologische claims.

## 9. Windfactor

Gebruik het maximum van speed en gusts:

```text
< 8 m/s      1.0
8-14 m/s     geleidelijk van 1.0 naar 2.8
>= 14 m/s    3.6
Thunder      minimaal 2.8
Thunderstorm minimaal 2.8
Wind         minimaal 4.2
```

Negatieve, NaN- en oneindige waarden worden veilig afgehandeld. Variabele windrichting `-1` wordt `null`; andere hoeken worden naar `0..<360` genormaliseerd.

## 10. Engine-integratie

`WeatherEngine` bewaart één `mSceneState`. Een kleine `rebuildSceneState()` bouwt deze opnieuw wanneer weather kind, day/night, lokale weather snapshot of astrodata verandert.

De bestaande renderer gebruikt daarna minimaal:

- `sceneState.weatherKind`;
- `sceneState.daytime`;
- `sceneState.windFactor`.

Bestaande losse velden mogen als compatibiliteitsadapter blijven bestaan. ACT-001 zet niet alle drawfuncties om; ACT-002 gebruikt later de continue velden.

## 11. Architectuurregels

De wallpaper blijft een read-only consumer van lokale data. ACT-001 voegt geen refreshmechanisme toe en doet geen netwerkwerk.

State en factory:

- zijn immutable/pure;
- alloceren niet per frame;
- bevatten geen secrets;
- loggen niets;
- zijn onafhankelijk van Canvas en AGSL;
- zijn bruikbaar in plain JVM-tests.

## 12. Tests

Test minimaal:

1. mapping van alle twaalf weather kinds;
2. veilige fallback voor onbekend kind;
3. intensiteiten blijven in `0f..1f`;
4. neerslagfamilies hebben bewolking;
5. glass rain alleen voor Rain, Sleet en Thunderstorm;
6. Thunderstorm combineert wolken, neerslag en thunder;
7. winddrempels en gusts;
8. storm- en windminimum;
9. daylight/photo tint;
10. ongeldige windwaarden en hoeknormalisatie;
11. astro timestamps en data-classgelijkheid.

## 13. Acceptatiecriteria

1. Er is precies één centrale immutable `WallpaperSceneState`.
2. De twaalf families zijn expliciet gemodelleerd.
3. Mapping en normalisatie zijn pure code.
4. De engine bouwt de state uitsluitend uit lokaal beschikbare data.
5. De renderer gebruikt weather kind, daytime en windfactor uit de state.
6. Astro timestamps zijn onderdeel van de snapshot.
7. Alle genormaliseerde intensiteiten liggen in `0f..1f`.
8. Ongeldige input crasht niet.
9. Geen netwerk-, GPS- of RemoveSky-call is toegevoegd.
10. Bestaande laagvolgorde en functionaliteit blijven behouden.
11. Unit tests en debugbuild slagen.
12. Wijzigingen van andere agents worden niet geraakt.

## 14. Overdracht aan ACT-002

ACT-002 mag vertrouwen op:

- immutable data-classstates;
- expliciete weather families;
- continue intensiteitsvelden;
- daylight en photo night tint;
- centrale windfactor;
- astro timestamps;
- equality voor identieke targets.

ACT-002 blijft eigenaar van transition timing, interpolation, outgoing/incoming renderers en continue fototint.
