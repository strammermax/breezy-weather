# ACT-019 - Testpagina sorteervolgorde (admin)

## Status

- **Type:** Implementatieopdracht (Backend / API / Admin-UI)
- **Prioriteit:** Middel
- **Omvang:** Middelgroot
- **Risico:** Laag (interne netwerkomgeving, admin-only)
- **Prerequisite:** Geen. Draait op dezelfde FastAPI-backend als ACT-015 (poort 12345).
- **Doelplatform:** Python / FastAPI (server) + eenvoudige server-rendered admin-pagina (Jinja2 of statische HTML + fetch)

## 1. Opdracht in een zin

Bouw een admin-testpagina op `/test/sortorder` waarmee een beheerder kolommen (GPS, Createdate, Status, Dag/nacht, Weather, …) kan aan-/uitzetten, prioriteren (drag up/down), per kolom punten kan toekennen, kolommen kan dupliceren, en voor de GPS-kolom een bereik (1/2/5/10 km) kan instellen — om vervolgens met opgegeven testcoördinaten + testdatumtijd te simuleren welke sorteervolgorde daaruit rolt.

## 2. Waarom deze wijziging nodig is

Sortering van items (bijv. locaties/kaarten in de app) hangt af van meerdere, onderling wisselende factoren. In plaats van sorteerlogica blind te tunen in productiecode, moet een beheerder scenario's kunnen doorrekenen: "als GPS-nabijheid (binnen 1 km) 10 punten waard is en Createdate ook 10, wat wint er dan bij deze coördinaten en dit tijdstip?" Dit voorkomt dat sorteerregels alleen impliciet in code bestaan.

## 3. Huidige architectuur

### Belangrijkste bestanden (te introduceren)
1. `main.py` (of de centrale router) — nieuwe routes onder `/test/sortorder`.
2. `sortorder/models.py` — Pydantic-modellen voor kolomconfiguratie en testinvoer.
3. `sortorder/engine.py` — pure scoring-/sorteerfunctie, los van FastAPI (testbaar zonder HTTP).
4. `sortorder/storage.py` — configuratie-opslag (zie sectie 6).
5. `templates/sortorder.html` (of static SPA-achtige pagina) — drag/drop admin-UI.

### Huidig gedrag
Nog niet aanwezig. Dit is een nieuwe testfaciliteit naast het bestaande `/updateapp`-endpoint (ACT-015).

## 4. Afbakening

### Wel uitvoeren
- Een GET-pagina `/test/sortorder` die de huidige kolomconfiguratie toont en een admin-UI biedt om:
  - kolommen aan/uit te zetten;
  - kolomvolgorde (prioriteit) te wijzigen via drag-up/drag-down;
  - per kolom een puntenwaarde (score-gewicht) in te vullen;
  - een kolom te dupliceren (bijv. twee keer GPS met verschillend bereik/gewicht, zoals in het voorbeeld);
  - voor GPS-kolommen een bereik te kiezen uit `{1, 2, 5, 10} km`;
  - testinvoer op te geven: GPS-coördinaten (`lat`, `lon`) en een test-datumtijd (`ISO 8601`);
- Een POST/PUT-endpoint om de kolomconfiguratie op te slaan;
- Een POST-endpoint dat, gegeven de opgeslagen configuratie + testinvoer, de resulterende sortering + score-breakdown per item teruggeeft (JSON), zodat de UI dit kan tonen zonder de pagina te herladen;
- Validatie: minstens één actieve kolom, unieke prioriteitsvolgorde na een drag-actie, geldig GPS-bereik voor GPS-kolommen.

### Niet uitvoeren
- Geen wijziging van de daadwerkelijke productie-sorteerlogica van de Android-app in deze opdracht — dit is een losstaande testomgeving op de backend;
- Geen authenticatie-/rollensysteem bouwen (aanname: `/test/*` staat alleen open op het interne netwerk, zoals `/updateapp`);
- Geen database-migraties; configuratie mag in een simpel JSON-bestand op de server staan (single source of truth, vergelijkbaar met de bestandsnaam-aanpak van ACT-015).

## 5. Datamodel (voorstel)

```python
from pydantic import BaseModel
from typing import Literal, Optional

class ColumnConfig(BaseModel):
    id: str                      # uniek, ook na duplicatie (bijv. "gps-1", "gps-2")
    type: Literal["gps", "createdate", "status", "daynight", "weather"]
    label: str                   # weergavenaam, bijv. "GPS (1km)"
    enabled: bool = True
    priority: int                # 1 = hoogste prioriteit; bepaalt sorteervolgorde van criteria
    points: int                  # score-gewicht als dit criterium "matcht"
    # alleen relevant voor type == "gps"
    radius_km: Optional[Literal[1, 2, 5, 10]] = None

class SortOrderConfig(BaseModel):
    columns: list[ColumnConfig]

class TestInput(BaseModel):
    lat: float
    lon: float
    test_datetime: str           # ISO 8601, bijv. "2026-07-12T14:30:00"

class ScoreBreakdown(BaseModel):
    item_id: str
    total_score: int
    per_column: dict[str, int]   # column.id -> toegekende punten

class SortResult(BaseModel):
    ordered_items: list[ScoreBreakdown]
```

Dit correspondeert direct met het voorbeeld uit de opdracht:

| prio | kolom | punten | extra |
|---|---|---|---|
| 1 | GPS | 10 | 1km |
| 2 | Createdate | 10 | |
| 3 | Status | 10 | |
| 4 | Dag/nacht | 10 | |
| 5 | Weather | 10 | |
| 6 | GPS | 5 | 1km |
| 7 | Createdate | 10 | |
| 8 | Status | 10 | |
| 9 | Dag/nacht | 10 | |
| 10 | Weather | 9 | |

## 6. Opslag

Configuratie wordt weggeschreven naar `./config/sortorder.json` (leidend bestand, geen database — zelfde patroon als de APK-bestandsnaam-aanpak in ACT-015). Bij elke save herschrijft de server dit bestand volledig (geen partial updates), zodat de bestandsinhoud altijd de actuele staat weerspiegelt.

## 7. Sorteerlogica (`sortorder/engine.py`)

Kernidee: elke actieve kolom levert per item 0 of `points` punten op, afhankelijk van of het item aan het criterium "matcht". De uiteindelijke sortering is:

1. bereken per item de som van alle kolomscores (`total_score`);
2. sorteer items aflopend op `total_score`;
3. bij gelijke score: gebruik kolomvolgorde (`priority`, oplopend) als tiebreaker — het item dat op de hoogst-geprioriteerde kolom een match heeft wint.

Matchregels per kolomtype (eerste opzet, later verfijnbaar):

- **GPS**: match als de afstand (haversine) tussen item-locatie en testcoördinaten `<= radius_km`;
- **Createdate**: match/score naar recentheid t.o.v. `test_datetime` (bijv. lineair aflopend binnen een venster, of simpele "binnen 24u = volledige punten");
- **Status**: match als item-status in een vooraf ingestelde "gewenste status"-set zit;
- **Dag/nacht**: match als het item se dag/nacht-tag overeenkomt met de dag/nacht-periode op `test_datetime` (zon op/onder, evt. via bestaande zoncalculatie);
- **Weather**: match als weertype van het item overeenkomt met het actuele/testweertype.

Deze functie is puur (geen FastAPI-afhankelijkheden) en apart unit-testbaar, in lijn met de `ACT-000` conventie voor testbare, framework-onafhankelijke logica.

```python
def compute_sort_order(
    config: SortOrderConfig,
    items: list[dict],
    test_input: TestInput,
) -> SortResult:
    ...
```

## 8. Admin-UI (`/test/sortorder`)

- Lijst van kolommen, elk met: aan/uit-toggle, sleep-handvat (drag up/down om `priority` te wijzigen), punten-invoerveld, "Dupliceer"-knop, en (alleen voor GPS) een dropdown voor bereik;
- "Nieuwe kolom toevoegen"-knop (kiest kolomtype uit de vaste lijst);
- Testpaneel: invoervelden voor `lat`, `lon`, `test_datetime` + "Bereken sortering"-knop;
- Resultaattabel: toont de gesorteerde items met per kolom hoeveel punten zijn bijgedragen (transparantie in de scoreopbouw), zodat direct zichtbaar is waarom item X boven item Y staat;
- "Opslaan"-knop persisteert de kolomconfiguratie naar `sortorder.json`.

Drag-and-drop mag met vanilla JS (bijv. HTML5 drag events of SortableJS via CDN) — geen zwaar frontend-framework nodig voor een interne testpagina.

## 9. API-specificatie

| Method | Path | Doel |
|---|---|---|
| `GET` | `/test/sortorder` | Rendert de admin-HTML-pagina |
| `GET` | `/test/sortorder/config` | Retourneert huidige `SortOrderConfig` als JSON |
| `PUT` | `/test/sortorder/config` | Slaat gewijzigde `SortOrderConfig` op |
| `POST` | `/test/sortorder/simulate` | Body: `TestInput` (+ evt. testitems); retourneert `SortResult` |

## 10. Performance-eisen

- Configuratie is klein (tientallen kolommen max) — geen paginering nodig;
- `simulate` moet synchroon en direct antwoorden (geen achtergrondtaak) zodat de UI live kan herberekenen.

## 11. Handmatige testmatrix

| Scenario | Configuratie | Testinvoer | Verwachting |
|---|---|---|---|
| Enkele GPS-kolom | 1 kolom: GPS, 10 pt, 1km | Coördinaten binnen 1km van item A | Item A bovenaan met 10 punten |
| Duplicatie | GPS 1km (10pt, prio 1) + GPS 1km (5pt, prio 6), zoals voorbeeld | Item matcht op beide GPS-kolommen | Score = 15, beide bijdrages zichtbaar in breakdown |
| Kolom uitgezet | Status-kolom `enabled=false` | Item met afwijkende status | Status-kolom draagt 0 punten bij, ongeacht match |
| Gelijke score | Twee items met identieke totaalscore | — | Tiebreak via hoogste-prioriteit-kolom bepaalt volgorde |
| Ongeldig bereik | GPS-kolom met `radius_km=3` | — | Validatiefout bij opslaan (alleen 1/2/5/10 toegestaan) |

## 12. Acceptatiecriteria

1. Een beheerder kan op `/test/sortorder` kolommen aan/uit zetten, herordenen via drag-up/down, punten toekennen en GPS-kolommen dupliceren met een eigen bereik, zonder de server te herstarten;
2. Met opgegeven testcoördinaten en testdatumtijd toont de pagina de resulterende sortering inclusief scoreopbouw per kolom;
3. De configuratie blijft bewaard in `./config/sortorder.json` en overleeft een server-restart;
4. `compute_sort_order` is los van FastAPI unit-testbaar met een vaste seed/config/testinvoer (deterministisch resultaat).

## 13. Definition of done

- De server-code (routes, engine, opslag, template) is geïntegreerd in de bestaande FastAPI-backend naast `/updateapp`;
- Het scenario uit sectie 5 (het 10-koloms voorbeeld) is handmatig getest en levert een voorspelbare, uitlegbare sortering op;
- `git diff` bevat uitsluitend de wijzigingen die nodig zijn voor deze testpagina.
