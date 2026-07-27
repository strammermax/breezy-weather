# ACT-015 - Backend Endpoint voor App-updates (updateapp)

## Status

- **Type:** Implementatieopdracht (Backend / API / File Streaming)
- **Prioriteit:** Hoog
- **Omvang:** Klein tot middelgroot
- **Risico:** Laag (interne netwerkomgeving)
- **Prerequisite:** Geen, maar werkt nauw samen met client-opdracht ACT-014.
- **Doelplatform:** Python / FastAPI (draaiend op poort 12345)

## 1. Opdracht in een zin

Bouw een API-endpoint `/updateapp` die een POST-verzoek van de Android-app met de huidige `versionCode` ontvangt, automatisch de opslagmap scant op het hoogste beschikbare versienummer, en die specifieke APK streamt als de client een oudere versie gebruikt.

## 2. Waarom deze wijziging nodig is

Om het releaseproces voor interne bedrijfs-apps te minimaliseren, moet de server zelfstandig ontdekken wanneer er een nieuwe app-versie is geüpload. Door de map dynamisch te scannen, hoeft de ontwikkelaar bij een nieuwe app-release alleen de nieuwe APK in de map te plaatsen. De server past de update-logica direct en zonder herstart aan.

## 3. Huidige architectuur

### Belangrijkste bestanden
1. `main.py` (of de centrale router van de FastAPI-applicatie).
2. Een lokale opslagmap (bijv. `./updates/`) waar de productie-APK's worden neergezet.

### Huidg gedrag
De server heeft nog geen logica of endpoint ingericht om app-versies te controleren of `.apk`-bestanden veilig en dynamisch te serveren.

## 4. Afbakening

### Wel uitvoeren
- Het aanmaken van een POST-endpoint op het pad `/updateapp`;
- Het valideren van de binnenkomende JSON-body (`{"versionCode": int}`);
- Het scannen van de map `./updates/` met een Regular Expression (`regex`) om automatisch de hoogste `versionCode` uit de bestandsnamen te filteren;
- Het retourneren van een `HTTP 204 No Content` status als de app al up-to-date is (of als er geen APK op de server staat);
- Het streamen van de specifieke, nieuwste APK met de juiste MIME-type (`application/vnd.android.package-archive`) en een `HTTP 200 OK` status als er een update beschikbaar is.

### Niet uitvoeren
- Geen database-integratie vereist (bestandsnamen dienen als de 'single source of truth');
- Geen dashboards of web-interfaces om APK's te uploaden (bestanden worden handmatig of via CI/CD in de map geplaatst).

## 5. Architectuurregel

Dit is een pure API- en bestandsservice-uitbreiding.

Tijdens deze opdracht mag de implementatie:
- Gebruikmaken van standaard FastAPI componenten zoals `FileResponse`;
- Gebruikmaken van ingebouwde Python bibliotheken (`os`, `re`) voor map- en patroonanalyses.

De implementatie mag niet:
- Het volledige APK-bestand in één keer in het RAM-geheugen laden alvorens het te versturen (gebruik streaming via `FileResponse`);
- Crashen als er ongerelateerde bestanden (zoals `.txt` of `.DS_Store`) in de updates-map staan.

## 6. Prerequisite

De APK-bestanden in de servermap moeten een vaste naamgeving krijgen waarin de `versionCode` (het hele getal) duidelijk herkenbaar is. 
*Toegestane formats:* `app-v2.apk`, `release-v24.apk`, `v105.apk`.

## 7. Gewenst Technisch Model

### API Specificatie

#### Request
- **Method:** `POST`
- **Path:** `/updateapp`
- **Headers:** `Content-Type: application/json`
- **Body:**
```json
{
  "versionCode": 1
}
```

#### Response (Indien update beschikbaar)
- **Status:** `200 OK`
- **Headers:** `Content-Type: application/vnd.android.package-archive`
- **Body:** Binaire datastream van de nieuwste APK.

#### Response (Indien app up-to-date of geen bestanden)
- **Status:** `204 No Content`
- **Body:** Leeg.

---

### Referentie-implementatie (Python / FastAPI met Auto-Detect)

Hieronder staat de code die de map automatisch scant op basis van de bestandsnaam:

```python
import os
import re
from fastapi import FastAPI, status
from fastapi.responses import FileResponse
from pydantic import BaseModel

app = FastAPI()

UPDATE_DIR = "./updates"

class UpdateRequest(BaseModel):
    versionCode: int

def get_latest_apk_info(directory: str):
    """
    Scant de map en zoekt naar het bestand met de hoogste 'versionCode' in de naam.
    Voorbeeld match: 'app-v12.apk' -> versionCode = 12
    """
    if not os.path.exists(directory):
        os.makedirs(directory)
        return None, 0

    latest_version = 0
    latest_file = None

    # Regex zoekt naar een 'v' of 'v-' gevolgd door cijfers vlak voor .apk
    # Werkt voor: app-v2.apk, v14.apk, release-v102.apk
    pattern = re.compile(r'[vV](?:-)?(\d+)\.apk\$')

    for filename in os.listdir(directory):
        match = pattern.search(filename)
        if match:
            version_code = int(match.group(1))
            if version_code > latest_version:
                latest_version = version_code
                latest_file = filename

    return latest_file, latest_version

@app.post("/updateapp")
async def update_app(request: UpdateRequest):
    # 1. Scan automatisch de map voor de nieuwste versie
    latest_file, latest_version = get_latest_apk_info(UPDATE_DIR)

    # 2. Als er geen geldige APK is gevonden, of de app is al up-to-date
    if not latest_file or request.versionCode >= latest_version:
        return status.HTTP_204_NO_CONTENT

    # 3. Bouw het volledige pad naar het bestand
    apk_path = os.path.join(UPDATE_DIR, latest_file)

    # 4. Stream de specifieke nieuwste APK terug
    return FileResponse(
        path=apk_path,
        media_type="application/vnd.android.package-archive",
        filename=latest_file
    )
```

## 8. Performance-eisen

- De map-scan (`os.listdir`) is extreem snel bij mappen met weinig bestanden (<100). Om de server schoon te houden is het advies om oude APK's periodiek te archiveren of te verwijderen, alhoewel de code altijd de hoogste blijft pakken;
- Grote APK-bestanden worden in efficiënte chunks gestreamd naar de Android-client dankzij FastAPI's `FileResponse`.

## 9. Handmatige testmatrix

| Scenario | Map-inhoud op server | Request body | Verwachting |
|---|---|---|---|
| Geen bestanden | *Leeg* | `{"versionCode": 1}` | `HTTP 204 No Content` |
| App up-to-date | `app-v2.apk` | `{"versionCode": 2}` | `HTTP 204 No Content` |
| Update beschikbaar | `app-v1.apk`, `app-v2.apk` | `{"versionCode": 1}` | `HTTP 200 OK`, `app-v2.apk` wordt gedownload |
| Ruis in de map | `app-v3.apk`, `test.txt`, `readme.md` | `{"versionCode": 1}` | `HTTP 200 OK`, `app-v3.apk` wordt netjes gedownload |

## 10. Acceptatiecriteria

1. Het endpoint `/updateapp` detecteert direct een nieuwe APK zodra deze in de map `./updates/` wordt geplaatst, zonder de server te herstarten.
2. Bestanden zonder geldig versienummer (zoals `app-beta.apk` of tekstbestanden) worden genegeerd en veroorzaken geen crashes.
3. Bij een succesvolle match krijgt de Android-client exact de binaire stream van de hoogste versie terug.

## 11. Definition of done

- De server-code is succesvol geïntegreerd in de FastAPI backend;
- Het endpoint is succesvol getest door handmatig verschillende versies van APK-bestanden in de map te schuiven en de response te controleren;
- `git diff` bevat uitsluitend de backendwijzigingen die nodig zijn voor dit endpoint.
