# RemoveSky: GPU-fix en Kaart-tab (Beheren-stijl bewerken)

**Datum:** 2026-07-24 (werkzaamheden), samenvatting geschreven 2026-07-28
**Onderwerp:** (1) gebruikers konden geen foto's meer uploaden ("Checking photo..." liep vast), root cause en fix; (2) Kaart-tab in removesky-service krijgt in-/uitklapbare kaart, kolommen-dropdown en bewerkbare tabelcellen zoals bij Beheren.

---

## 1. Kaart-tab UI-uitbreiding (`removesky-service/static/index.html`)

Verzoek van de gebruiker: op de "Kaart"-pagina moet je (a) de kaart kunnen in-/uitklappen, (b) dezelfde soort kolommen-dropdown hebben als op andere tabs, en (c) tabelcellen inline bewerkbaar maken zoals op de Beheren-tab.

**Uitgevoerd:**
- Kolommen-dropdown ("Kolommen ▾") bleek al aanwezig en functioneel identiek aan Beheren — geen wijziging nodig.
- Nieuwe knop "Kaart tonen/verbergen" toegevoegd die de Leaflet-kaart-div toont/verbergt, met onthouden voorkeur via `localStorage` en een `invalidateSize()`-call bij het weer tonen.
- Tabelcellen (`status`, `season`, `day_period`, `scene_type`, `weather`, `is_city`, `is_outdoor`, `person`) omgezet naar hetzelfde badge+dropdown-bewerkpatroon als Beheren: klik op badge → dropdown verschijnt → wijziging → PATCH-call naar de backend.

**Bugs die zijn opgetreden en gefixt tijdens het bouwen:**

1. **Verkeerde waarde-normalisatie** — `is_city`/`is_outdoor`/`person` gebruikten de ruwe rijwaarden in plaats van genormaliseerde `'1'/'0'/''`-strings, en de dropdown-opties misten een `selected`-markering. Gefixt door normalisatie toe te voegen (`cityValMap`, `checksIsOutdoor(row)`, `checksPerson(row)`) en `selected`-attributen aan elke optie toe te voegen.
2. **TDZ ReferenceError** (`can't access lexical declaration 'leafletMap' before initialization`) — de nieuwe toggle-knop riep bij het laden `applyMapVisibility()` aan, wat gebeurde vóórdat de `let leafletMap = null;`-declaratie verderop in hetzelfde script werd uitgevoerd. Hierdoor bleef de hele Kaart-tab (kaart én tabel) op "Laden..." hangen. Fix: de declaratie naar boven verplaatst, vóór het eerste gebruik.
3. **`checksIsOutdoor`/`checksPerson`/`triStateBadge` niet gedefinieerd** — deze functies (en `EDITABLE_FIELD_CONFIG`/`updateEditableField`) bleken lokaal genest te zitten in de Beheren-tab's eigen IIFE (een aparte `<script>`-blok/closure), niet globaal beschikbaar. De Kaart-tab draait in zijn eigen IIFE en kon ze dus niet bereiken → opnieuw "Laden..." bleef hangen, nu met `ReferenceError: checksIsOutdoor is not defined`. Fix: lokale duplicaten van deze helpers toegevoegd binnen de Kaart-tab's eigen IIFE.

**Commits (removesky-service, `main`):**
- `06eeef0` — Add map show/hide toggle and inline-editable table cells to Kaart tab
- `b5f846d` — Fix TDZ ReferenceError breaking the Kaart tab (map + table stuck loading)
- `2afb802` — Fix ReferenceError: checksIsOutdoor/checksPerson/triStateBadge not defined

Na elke fix is een echte deploy op productie (`lxc-154`) geverifieerd via `/health` en de browserconsole-foutmelding die de gebruiker terugkoppelde.

---

## 2. GPU-crash-loop: foto-upload liep vast op "Checking photo..."

**Melding gebruiker:** "gebruikers kunnen nu geen fotos uploaden" — de app hing op de "Checking photo..."-stap (screenshot: bergfoto met GPS-locatie, uploadflow gestopt na "✓ Photo uploaded").

**Root cause (initiële analyse, deels achterhaald):**
`removesky.service` had `Environment=REMOVESKY_FORCE_CPU=1` hardcoded, ingesteld tijdens een eerdere sessie toen de GPU-passthrough op `lxc-154` (draait op Proxmox-host `pve-amd`) kapot was. Met CPU-only inferentie duurt de `/check`-stap (sky/CLIP-beeldherkenning) tot ~90 seconden bij een full-res foto — de app geeft het na 90s stilzwijgend op zonder foutmelding, wat aanvoelt als vastlopen.

**Verificatie dat de GPU écht weer werkt:** via tijdelijke SSH-toegang (zie sectie 3) live getest op `lxc-154`:
- `torch.cuda.is_available()` → `True`
- Een echte SegFormer-inferentie (het model dat removesky gebruikt) op de GPU: **0,7 seconden**, tegenover ~90s op CPU.
- GPU: AMD Radeon 890M (iGPU, gfx1103/RDNA3 Phoenix/Hawk Point), gespoofed als ondersteund gfx1102-target via `HSA_OVERRIDE_GFX_VERSION=11.0.2` (torch's ROCm 6.3-wheel ondersteunt gfx1100/1101/1102 maar niet native gfx1103).

**Fix:** `REMOVESKY_FORCE_CPU=1` verwijderd uit `removesky.service` (commit `bb8da79`), zodat GPU-auto-detectie weer normaal werkt via `removesky.env`.

**Complicatie — reboot brak de fix opnieuw:**
Na een `reboot`-commando (uitgevoerd *binnen* de container `remvebg`/lxc-154, niet op de fysieke host) crashte de service opnieuw met `RuntimeError: No HIP GPUs are available`. Oorzaak: de Proxmox-devicepassthrough voor `/dev/kfd` (`dev6` in `pct config 154`) had **geen expliciete `gid=`**, in tegenstelling tot `dev5` (`renderD128,gid=104,uid=0`). Zonder gepinde gid krijgt de container bij elke (her)start een andere/onvoorspelbare group-eigenaar voor dat device — de eerdere "fix" was slechts een handmatige `chmod`, geen permanente instelling, en verdween dus bij de eerstvolgende restart.

**Permanente fix:** `pct set 154 -dev6 /dev/kfd,gid=104,uid=0` (matcht het patroon van `dev5`; groep 104 = `postdrop`, waar de `removesky`-gebruiker al lid van is). Geverifieerd stabiel over meerdere herstarts.

---

## 3. Tijdelijke SSH-toegang gebruikt voor diagnose

Voor dit werk is gebruikgemaakt van een tijdelijk PVE-host-account (`tmpuser-20260724-1`, key-based, sudo NOPASSWD, verloopt 2026-07-26) dat de gebruiker eerder had aangemaakt via de nieuwe `pve-host-user-management.sh`-wizard in `myscripts-proxmox`. Hiermee is rechtstreeks op `pve-amd` (192.168.1.98) en via `pct exec 154` in de container zelf gediagnosticeerd en gefixt, zonder verdere tussenkomst van de gebruiker nodig te hebben tijdens het debuggen.

---

## 4. Bijkomende robuustheidsfix: DNS-race bij `check_update.sh`

Bij het herstarten van alleen de container (niet de hele host) faalde `check_update.sh`'s `git fetch` soms kort met `Could not resolve host: github.com`. Verklaring: de veth/netwerkbrug van de zojuist herstarte container rapporteert `network-online.target` als bereikt vóórdat pakketten daadwerkelijk via de host-bridge (`vmbr0`) kunnen worden doorgestuurd — een bekende korte race bij LXC-containers. Dit was al onschuldig (het script viel netjes terug op de huidige versie), maar op verzoek van de gebruiker is er een retry met 2 seconden sleep toegevoegd zodat een update-check niet onnodig wordt overgeslagen.

**Commit:** `38fa706` — Retry check_update.sh's git fetch once after a 2s sleep.

---

## Samenvatting van alle commits deze sessie (removesky-service, `main`)

| Commit | Omschrijving |
|---|---|
| `06eeef0` | Kaart-tab: kaart in-/uitklappen + bewerkbare tabelcellen (Beheren-stijl) |
| `bb8da79` | GPU-auto-detectie weer aangezet (FORCE_CPU=1 verwijderd) |
| `38fa706` | `check_update.sh`: retry git fetch na 2s bij DNS-falen |
| `b5f846d` | Fix TDZ-bug die Kaart-tab liet vastlopen op "Laden..." |
| `2afb802` | Fix ontbrekende `checksIsOutdoor`/`checksPerson`/`triStateBadge` in Kaart-tab-scope |

Losstaande, permanente Proxmox-configwijziging (niet in git, want infrastructuur): `pct set 154 -dev6 /dev/kfd,gid=104,uid=0` op host `pve-amd`.

## Openstaand (niet in deze sessie opgelost)

- Geen foutmelding in de app zelf wanneer de `/check`-call mislukt of timeout (`CameraActivity.kt:1003`) — gebruiker ziet een lege/stille kaart in plaats van een duidelijke fout met "opnieuw proberen"-optie.

## Afbeeldingen

De gebruiker deelde tijdens dit gesprek twee schermafbeeldingen (een vastgelopen upload-flow op een bergfoto, en een leeg/"Laden..."-tabelscherm van de Kaart-tab). Deze zijn inline in de chatgeschiedenis gedeeld en niet als los bestand beschikbaar, dus niet opgenomen als los beeldbestand in deze map.
