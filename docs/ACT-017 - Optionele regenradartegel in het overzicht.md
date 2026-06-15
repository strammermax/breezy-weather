# ACT-017 - Optionele regenradartegel in het overzicht

## Status

- **Type:** Implementatieopdracht (UI / radar / instellingen)
- **Prioriteit:** Middelhoog
- **Omvang:** Middelgroot
- **Risico:** Middelgroot, vanwege kaart-lifecycle, netwerkgebruik en recycling in het hoofdscherm
- **Prerequisite:** Het bestaande scherm `Regenradar` en de bestaande RainViewer-integratie moeten functioneel blijven
- **Doelplatform:** Alle ondersteunde Android-versies, met prioriteit voor Android 13 en hoger

## 1. Opdracht in een zin

Voeg de bestaande RainViewer-regenradar toe als optionele, verplaatsbare tegel in het hoofdscherm, waarbij de gebruiker de tegel via **Instellingen -> Tegels** kan toevoegen of verwijderen en een tik op de compacte preview het bestaande volledige regenradarscherm opent.

## 2. Waarom deze wijziging nodig is

De app heeft al een afzonderlijk scherm met een functionele regenradar. Om de radar te bekijken moet de gebruiker dit scherm nu expliciet openen. Voor een snel weeroverzicht is het handiger om direct op het hoofdscherm te zien of neerslag de actieve locatie nadert.

De gewenste situatie:

- de gebruiker kan een compacte regenradarpreview tussen de bestaande weertegels plaatsen;
- de tegel volgt dezelfde volgorde- en zichtbaarheidsinstellingen als de andere tegels;
- de tegel toont voldoende radarinformatie voor een snelle indruk;
- uitgebreide kaartinteractie blijft in het bestaande volledige regenradarscherm.

## 3. Huidige situatie

### Bestaand gedrag

- Er bestaat een afzonderlijk scherm met titel `Regenradar`.
- Dit scherm toont een RainViewer-radarkaart met tijdstip en kaartbediening.
- Het hoofdscherm bestaat uit configureerbare tegels.
- Via **Instellingen -> Tegels** kunnen bestaande tegels worden toegevoegd, verwijderd en verplaatst.
- Er bestaan afzonderlijke instellingen voor **Trends per uur** en **Trends per dag**.

### Relevante onderdelen die eerst onderzocht moeten worden

Controleer voor implementatie de werkelijke bestandsnamen en bestaande patronen voor:

1. het bestaande regenradarscherm en de RainViewer-databron;
2. het centrale tegelregister of de `ViewType`-definities;
3. de adapter/viewholder/composable waarmee hoofdschermtegels worden gerenderd;
4. de opslag van zichtbaarheid en volgorde van tegels;
5. de instellingenpagina **Tegels**;
6. lifecycle- en cachinglogica van de bestaande radarkaart.

Gebruik de bestaande architectuur. Introduceer geen tweede radarclient of parallel tegelinstellingensysteem.

## 4. Afbakening

### Wel uitvoeren

- een nieuw tegeltype `Regenradar` toevoegen aan de beschikbare hoofdschermtegels;
- de tegel via **Instellingen -> Tegels** aan en uit kunnen zetten;
- de tegel via de bestaande drag-and-dropfunctie kunnen verplaatsen;
- de gekozen zichtbaarheid en positie persistent opslaan;
- een compacte RainViewer-preview voor de actieve locatie tonen;
- het tijdstip van het getoonde radarframe tonen;
- bij tikken het bestaande volledige regenradarscherm openen;
- bestaande radardata, kaartconfiguratie en caching hergebruiken;
- correcte lege, offline- en foutstatussen tonen;
- lifecycle en recycling correct afhandelen;
- tests toevoegen voor tegelconfiguratie en navigatie waar dit binnen de bestaande testarchitectuur past.

### Niet uitvoeren

- geen nieuw radar- of detailsscherm bouwen;
- geen tweede RainViewer API-client toevoegen;
- geen regenradar toevoegen aan **Trends per uur**;
- geen regenradar toevoegen aan **Trends per dag**;
- geen brede refactor van het tegel-, kaart- of instellingensysteem;
- geen wijzigingen aan de live wallpaper of RemoveSky-pipeline;
- geen automatische videolaag of continu draaiende radaranimatie buiten de zichtbare tegel;
- geen andere bestaande tegels herontwerpen.

## 5. Gebruikerservaring

### Instellingen

Op **Instellingen -> Tegels** verschijnt een nieuwe beschikbare tegel:

```text
Regenradar
```

De gebruiker kan deze tegel met dezelfde bestaande bediening:

- toevoegen;
- verwijderen;
- omhoog of omlaag slepen;
- na een herstart op dezelfde positie terugvinden.

De tegel is voor bestaande installaties standaard uitgeschakeld. Een migratie mag de huidige tegelvolgorde van gebruikers niet onverwacht wijzigen.

### Hoofdscherm

Wanneer ingeschakeld, verschijnt een glaskaart in dezelfde visuele stijl als de andere overzichtstegels.

De compacte tegel bevat minimaal:

- titel `Regenradar`;
- radarpreview gecentreerd rond de actieve locatie;
- het tijdstip van het radarframe;
- een herkenbare markering van de actieve locatie, indien de bestaande kaartcomponent dit ondersteunt;
- een compacte laad-, offline- of foutstatus wanneer geen kaart getoond kan worden.

### Interactie

- De preview is niet verschuifbaar of zoombaar.
- De tegel bevat geen zwevende `+`/`-`-knoppen.
- Een tik op de tegel opent het bestaande volledige scherm `Regenradar`.
- In het volledige scherm blijven de bestaande zoom-, kaart- en animatiemogelijkheden beschikbaar.

Dit voorkomt conflicten tussen verticaal scrollen door het hoofdscherm en slepen/zoomen op een interactieve kaart.

## 6. Visueel model

Gebruik de bestaande glass-card-stijl van het hoofdscherm:

- dezelfde horizontale marges;
- dezelfde afgeronde hoeken;
- dezelfde rand, transparantie en dag/nacht-kleuren;
- voldoende contrast voor titel, tijdstip en foutstatus;
- een vaste of begrensde hoogte zodat recycling en scrollgedrag voorspelbaar blijven.

Indicatieve opbouw:

```text
+--------------------------------------+
| Regenradar                    13:00   |
|                                      |
|        compacte radarpreview         |
|        rond actieve locatie          |
|                                      |
| Tik voor volledige radar             |
+--------------------------------------+
```

De exacte hoogte moet aansluiten bij de bestaande grotere overzichtstegels en mag het hoofdscherm niet domineren.

## 7. Architectuurregels

1. Hergebruik de bestaande RainViewer repository/service/client.
2. Deel radarframe-metadata en tile-cache tussen de compacte tegel en het volledige scherm waar de bestaande architectuur dit toestaat.
3. Voorkom twee gelijktijdige identieke netwerkrequests voor dezelfde locatie en hetzelfde radarframe.
4. Houd netwerk- en kaartlogica buiten de viewholder/composable zelf.
5. De tegel leest de actieve locatie uit dezelfde centrale app-datalaag als de andere hoofdschermtegels.
6. De tegel mag niet zelfstandig GPS opvragen.
7. Stop of pauzeer animatie en zware rendering wanneer de tegel niet zichtbaar, gerecycled of buiten de actieve lifecycle is.
8. Log geen exacte GPS-coordinaten, tokens, API-headers of andere gevoelige gegevens.
9. Laat het bestaande volledige regenradarscherm functioneel ongewijzigd, behalve kleine aanpassingen die nodig zijn om gedeelde componenten te hergebruiken.

## 8. Voorgestelde technische aanpak

### 8.1 Tegelregistratie

- Voeg een stabiel ID of `ViewType` voor `Regenradar` toe.
- Neem dit type op in de lijst met beschikbare hoofdschermtegels.
- Voeg een Nederlandse titel en eventuele overige vertalingen toe volgens het bestaande stringpatroon.
- Zorg dat onbekende of oudere opgeslagen configuraties veilig blijven laden.

### 8.2 Instellingen en persistentie

- Gebruik dezelfde persistentielaag als de bestaande tegelvolgorde.
- Voeg `Regenradar` standaard niet toe aan bestaande actieve tegelsets.
- Ondersteun toevoegen, verwijderen en verplaatsen zonder speciale losse schakelaar buiten de pagina **Tegels**.
- Verander geen instellingen van **Trends per uur** of **Trends per dag**.

### 8.3 Compacte radarcomponent

- Maak een compacte, read-only radarweergave.
- Gebruik de actieve locatie en een passende vaste zoom voor de lokale omgeving.
- Toon bij voorkeur het meest recente beschikbare radarframe.
- Toon het frame-tijdstip in lokale tijd.
- Hergebruik bestaande kaart- en radartegels waar mogelijk.
- Schakel gestures, zoomknoppen en kaartbewerking uit.

### 8.4 Navigatie

- Koppel de klikactie van de volledige tegel aan de bestaande `RadarActivity` of huidige radar-route.
- Geef de actieve locatie alleen mee als het bestaande scherm dit nodig heeft en nog niet uit de centrale staat haalt.
- Voorkom dat een tik tijdens scrollen onbedoeld het radarscherm opent volgens het bestaande tegelgedrag.

### 8.5 Lifecycle en performance

- Start rendering pas wanneer de tegel zichtbaar en gebonden is.
- Annuleer coroutines, callbacks en kaartresources bij recycling of lifecycle-stop.
- Gebruik geen continue hoge-frequentie animatie in de compacte tegel.
- Een statisch recent frame is voldoende; beperkte animatie mag alleen als dit aantoonbaar soepel blijft.
- Voorkom zware bitmapallocaties tijdens iedere scroll- of bindcyclus.
- Hergebruik cachebestanden en gedeelde frame-metadata.

## 9. Lege en foutstatussen

| Situatie | Gewenst gedrag |
|---|---|
| Geen geldige actieve locatie | Toon `Locatie niet beschikbaar` en laat de app verder normaal werken. |
| Geen netwerk, wel cache | Toon het laatst gecachte radarframe met een subtiele offline-status. |
| Geen netwerk en geen cache | Toon een compacte offline-placeholder. |
| RainViewer tijdelijk niet beschikbaar | Toon een foutstatus met bestaande retry-/refreshlogica; geen crash. |
| Radarframe ontbreekt | Toon `Geen radarbeeld beschikbaar`. |
| Locatie wordt gewijzigd | Laad de preview opnieuw voor de nieuwe actieve locatie. |
| Tegel wordt verwijderd | Stop kaartwerk en verwijder alleen de tegel uit de hoofdschermconfiguratie. |

## 10. Testscenario's

### Functioneel

1. Voeg `Regenradar` toe via **Instellingen -> Tegels**.
2. Controleer dat de tegel op het hoofdscherm verschijnt.
3. Verplaats de tegel en herstart de app.
4. Controleer dat positie en zichtbaarheid behouden blijven.
5. Tik op de tegel en controleer dat het bestaande volledige regenradarscherm opent.
6. Verwijder de tegel en controleer dat deze niet meer op het hoofdscherm staat.
7. Controleer dat de afzonderlijke radarroute vanuit de toolbar nog werkt.

### Data en fouten

1. Test met een geldige actieve locatie.
2. Wissel tussen twee locaties en controleer dat de kaart meebeweegt.
3. Test zonder netwerk met bestaande cache.
4. Test zonder netwerk en zonder cache.
5. Test een mislukte RainViewer-response.
6. Controleer dat geen dubbele identieke radarrequests ontstaan wanneer tegel en volledig scherm kort na elkaar worden geopend.

### UI en lifecycle

1. Scroll de tegel snel in en uit beeld.
2. Open en sluit het volledige radarscherm meerdere keren.
3. Draai het scherm indien landscape wordt ondersteund.
4. Test dag- en nachtstijl.
5. Test op telefoon en tablet-emulator.
6. Controleer op memory leaks, achterblijvende callbacks en crashes tijdens recycling.

## 11. Acceptatiecriteria

1. `Regenradar` staat tussen de beschikbare opties op **Instellingen -> Tegels**.
2. De tegel kan worden toegevoegd, verwijderd en verplaatst.
3. De tegel is standaard uitgeschakeld voor bestaande installaties.
4. De keuze en positie blijven behouden na herstart.
5. De tegel toont een radarpreview voor de actieve locatie en het gebruikte frame-tijdstip.
6. De tegel gebruikt dezelfde RainViewer-datalaag en cache als het bestaande radarscherm.
7. Tikken op de tegel opent het bestaande volledige regenradarscherm.
8. De compacte tegel heeft geen interactieve kaartgestures of zoomknoppen.
9. Offline, ontbrekende locatie en API-fouten veroorzaken geen crash.
10. Er zijn geen dubbele identieke radarrequests.
11. Scrollen en kaartweergave blijven vloeiend.
12. De tegel verschijnt niet in **Trends per uur** of **Trends per dag**.
13. Bestaande tegels en het bestaande regenradarscherm blijven werken.
14. Er worden geen secrets of exacte locatiegegevens gelogd.
15. Debugbuild, relevante tests en lint/compilecontroles slagen.

## 12. Definition of done

- De regenradartegel is functioneel en visueel geïntegreerd in het hoofdscherm.
- De instelling werkt via de bestaande tegelbeheerpagina.
- De tegel gebruikt centrale locatie- en gedeelde radardata.
- De volledige radar opent correct bij een tik.
- Offline- en foutscenario's zijn afgehandeld.
- Getest op een fysieke telefoon en minimaal één emulator.
- Screenshots zijn gemaakt van:
  - de tegel ingeschakeld op het hoofdscherm;
  - de tegeloptie in **Instellingen -> Tegels**;
  - het volledige radarscherm na een tik;
  - de offline- of lege status.
- `git diff` bevat alleen wijzigingen die bij ACT-017 horen.
- De wijziging is na review gecommit, gepusht en voorzien van het afgesproken nieuwe versienummer.

## 13. Verwachte opleverrapportage

De uitvoerende agent rapporteert minimaal:

1. welke bestaande radar- en tegelcomponenten zijn hergebruikt;
2. welke bestanden zijn aangepast;
3. hoe dubbele radarrequests worden voorkomen;
4. hoe lifecycle en recycling zijn afgehandeld;
5. welke tests en apparaten zijn gebruikt;
6. eventuele resterende beperkingen, bijvoorbeeld statisch in plaats van geanimeerd radarbeeld in de compacte tegel.
