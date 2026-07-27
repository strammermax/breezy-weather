Dynamische Sorteerlogica (op basis van Datum, Views & Afstand)
Dit document beschrijft de specifieke sorteerroutine voor itemlijsten met geografische, weergave- en datumdata. De logica geeft prioriteit aan recentheid en lage weergaven boven de fysieke afstand. De ouderdom van een item wordt dynamisch berekend ten opzichte van de huidige datum van vandaag.
1. Datastructuur
De lijst bestaat uit objecten met de volgende eigenschappen:
•	distance (String, bijv. "100m")
•	aantalkeer_gezien (Integer, aantal weergaven)
•	created_date (String of Date, ISO-formaat: "YYYY-MM-DD")
•	imageurl (String, link naar afbeelding)
________________________________________
2. Sorteerregels (Prioriteitsvolgorde)
Het algoritme vergelijkt twee items (Item A en Item B) op basis van de volgende drie opeenvolgende stappen:
Regel 1: Dynamische Recentheid Marge (Prioriteit 1)
Het systeem berekent eerst voor beide items het aantal dagen dat verstreken is sinds de created_date tot NU. Er geldt een drempelwaarde van minimaal 5 dagen.
•	Als Item A minimaal 5 dagen recenter is aangemaakt dan Item B, gaat Item A voor.
•	Als het verschil in ouderdom kleiner is dan 5 dagen, vervalt deze prioriteit en gaan we naar Regel 2.
Regel 2: Populariteit (Prioriteit 2)
Als de aanmaakdatums binnen de marge van 5 dagen liggen, bepaalt de populariteit de volgorde.
•	Het item met het laagste aantal aantalkeer_gezien krijgt voorrang.
•	Hebben beide items exact evenveel weergaven? Dan gaan we naar Regel 3.
Regel 3: Afstand (Fallback / Standaard)
Als zowel de dagenmarge als de weergaven geen doorslag geven, valt het algoritme terug op de basisregel.
•	Het item met de kortste distance (omgezet naar een numerieke waarde in meters) komt bovenaan.
________________________________________
3. Excalidraw / Mermaid Diagram
Kopieer de onderstaande code en importeer deze in Excalidraw via Menu -> Import -> Mermaid om het schema visueel aan te passen:
mermaid
graph TD
    classDef start class: stroke:#333,stroke-width:2px;
    classDef conditie fill:#fff,stroke:#000,stroke-width:2px;
    classDef actie fill:#fff,stroke:#333,stroke-dasharray: 5 5;
    classDef uitkomst fill:#e6f4ea,stroke:#137333,stroke-width:2px;

    Start([Start Vergelijking:<br>Item A vs Item B]) --> CalcDays[Bereken voor beide:<br>Huidige Datum - created_date]:::actie
    CalcDays --> CheckDays{Verschil in dagen<br>minimaal 5 dagen?}
    
    CheckDays -- Ja --> WhichDays{Welk item is<br>minimaal 5 dagen<br>recenter?}
    WhichDays -- Item A --> WinA1[Item A gaat voor Item B]:::uitkomst
    WhichDays -- Item B --> WinB1[Item B gaat voor Item A]:::uitkomst

    CheckDays -- Nee --> CheckViews{Is aantalkeer_gezien<br>verschillend?}
    CheckViews -- Ja --> WhichViews{Welk item heeft<br>de minste views?}
    WhichViews -- Item A --> WinA2[Item A gaat voor Item B]:::uitkomst
    WhichViews -- Item B --> WinB2[Item B gaat voor Item A]:::uitkomst

    CheckViews -- Nee --> CheckDist[Vergelijk basisafstand<br>distance: klein naar groot]:::actie
    CheckDist --> WinDist[Item met kleinste<br>afstand gaat voor]:::uitkomst

    class Start start;
    class CheckDays,WhichDays,CheckViews,WhichViews conditie;
Wees voorzichtig met code.
________________________________________
4. JavaScript Implementatie
javascript
/**
 * Sorteert een lijst op basis van de huidige datum (marge 5 dagen), weergaven en afstand.
 * @param {Array} lijst - De te sorteren lijst met objecten
 * @returns {Array} De gesorteerde lijst
 */
function sorteerItems(lijst) {
  const nu = new Date();
  const msPerDag = 24 * 60 * 60 * 1000;

  return [...lijst].sort((a, b) => {
    // 1. Numerieke waarden van afstand extraheren (bijv. "100m" -> 100)
    const distA = parseInt(a.distance, 10);
    const distB = parseInt(b.distance, 10);

    // 2. Bereken het aantal dagen geleden vanaf NU
    const dagenGeledenA = Math.floor((nu - new Date(a.created_date)) / msPerDag);
    const dagenGeledenB = Math.floor((nu - new Date(b.created_date)) / msPerDag);

    // PRIORITEIT 1: Check het datumverschil (minimaal 5 dagen minder geleden = recenter)
    if (dagenGeledenB <= dagenGeledenA - 5) return 1;  // B is veel recenter
    if (dagenGeledenA <= dagenGeledenB - 5) return -1; // A is veel recenter

    // PRIORITEIT 2: Check aantalkeer_gezien (minder weergaven gaat voor)
    if (a.aantalkeer_gezien !== b.aantalkeer_gezien) {
      return a.aantalkeer_gezien - b.aantalkeer_gezien;
    }

    // PRIORITEIT 3: Standaard sortering op basis van afstand (klein naar groot)
    return distA - distB;
  });
}
Wees voorzichtig met code.
________________________________________
5. Rekenvoorbeeld & Verwacht Resultaat
Stel dat de huidige datum 23 juli 2026 is. Bij invoer van de volgende testset:
json
[
  { "distance": "100m", "aantalkeer_gezien": 50, "created_date": "2026-07-03" }, 
  { "distance": "111m", "aantalkeer_gezien": 40, "created_date": "2026-07-04" }, 
  { "distance": "102m", "aantalkeer_gezien": 4,  "created_date": "2026-07-21" }, 
  { "distance": "200m", "aantalkeer_gezien": 30, "created_date": "2026-07-03" }, 
  { "distance": "200m", "aantalkeer_gezien": 30, "created_date": "2026-07-13" }  
]
Wees voorzichtig met code.
Uiteindelijke volgorde na sortering:
1.	102m (Gezien: 4 | 2 dagen geleden)
Reden: Is met slechts 2 dagen oud significant recenter (minimaal 5 dagen verschil) dan alle andere opties. Wint direct in stap 1.
2.	200m (Gezien: 30 | 10 dagen geleden)
Reden: Is met 10 dagen oud minimaal 5 dagen recenter dan de resterende opties (19 en 20 dagen oud). Wint in stap 1.
3.	200m (Gezien: 30 | 20 dagen geleden)
Reden: Datumverschil met de rest is te klein (< 5 dagen), maar wint op basis van de minste weergaven (30 vs 40/50) in stap 2.
4.	111m (Gezien: 40 | 19 dagen geleden)
Reden: Verliest van de 200m variant op weergaven, maar wint van de 100m variant op weergaven (40 vs 50) in stap 2.
5.	100m (Gezien: 50 | 20 dagen geleden)
Reden: Heeft de meeste weergaven en is het langst geleden aangemaakt. Eindigt onderaan de lijst.

