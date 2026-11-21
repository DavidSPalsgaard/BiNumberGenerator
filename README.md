# BiNumreGenerator – startnumre til orienteringsløb

Laver startnumre (A5) som PDF ud fra en startliste: stort startnummer, navn, klub, SI-nummer og
stregkode (Code 128 med startnummeret) – og i bunden, vendt på hovedet så løberen selv kan læse
det, starttid, klasse, bane, start og afstand til start.

**Status:** PC-programmet virker – både med grafisk brugerflade (tabel til at rette/vælge løbere,
opsætning af løbet og editor til grafik med forhåndsvisning) og som kommandolinje. Direkte
forbindelse til MeOS, installationspakker og Android-appen mangler – se "Plan".

## Skabeloner
- **Enkeltløb** (`single`): standardoplysningerne.
- **Stafet** (`relay`): holdets ture har samme startnummer; turen står i et farvet bånd øverst og
  som farvet kant i bunden. Farve pr. tur vælges i projektfilen (`legColors`).
- **Flerdagesløb** (`multiday`): én startliste pr. etape; bunden får én linje pr. etape. Samme
  løber findes på SI-brik, ellers på navn + klub.

## Startlister
IOF XML 3.0 / 2.0.3 (MeOS, OE, Eventor), Excel (.xlsx), CSV og OFeed (QR-linket fra
løbet → Integrationer → O Checklist). Koden i `startlist/` og `ofeed/` er kopieret fra SI Startinfo
og udvidet med tur, holdnavn og banelængde. Løbere uden startnummer springes over.

## Hent og kør (Windows)
Under **Releases → Seneste udgave** på GitHub ligger `BiNumreGenerator-testpakke.zip`, som bygges
automatisk ved hver ændring på `main` (`.github/workflows/build.yml`). Pak den ud; den indeholder:
- `Program\BiNumreGenerator.exe` (eller `Start BiNumreGenerator.bat`) – intet skal installeres;
  Java-runtime følger med i mappen.
- `Installation\BiNumreGenerator-1.0.0.msi` – installerer i brugerens egen mappe med startmenu og
  genvej (kræver ikke administrator).
- `Testfiler\` og `TESTVEJLEDNING.txt` – startlister (XML, CSV, Excel), logo og projektfil med en
  trin-for-trin afprøvning. Filerne ligger i `testpakke/` (XML-filerne i `core/src/test/resources/`),
  og en test sikrer, at de passer til vejledningen.

**Udgivne versioner** (fx `v1.0.0`) ligger også under Releases med `BiNumreGenerator-<version>.msi` og
`BiNumreGenerator-<version>-windows.zip`. En ny version udgives ved at sætte `appVersion` i
`desktop/build.gradle.kts` og skubbe et mærke med samme nummer: `git tag v1.0.0 && git push origin v1.0.0`.

Windows viser måske "Windows beskyttede din pc", fordi programmet ikke er kodesigneret endnu:
klik "Flere oplysninger" → "Kør alligevel".

## Byg selv
Kræver JDK 17 eller nyere (kun for at bygge – ikke for at køre den færdige pakke).
```
gradlew :desktop:run                    # starter programmets vindue
gradlew :desktop:createDistributable    # -> desktop/build/compose/binaries/main/app/BiNumreGenerator/
gradlew :desktop:packageMsi             # -> .msi (kun på Windows); packageDeb/packageDmg på Linux/Mac
```
Pakken bygges til det styresystem, den bygges på. Uden argumenter åbner programmet vinduet; med
argumenter er det kommandolinjen (se nedenfor). `BiNumreGenerator --gui startliste.xml [--project
loeb.json]` åbner vinduet med startlisten og projektet indlæst.

### Programmets vindue
- **Startskærm:** vælg løbstype – enkeltløb, stafet eller flerdagesløb (med antal etaper) – eller
  åbn et gemt projekt.
- **Startliste:** indlæs IOF XML, Excel eller CSV, eller hent fra OFeed. Flerdagesløb: én liste pr.
  etape, eller "Én startliste til alle etaper" (en IOF XML-fil med `raceNumber` pr. start giver hver
  etape sin starttid). Løberne matches på tværs af etaper på startnummer, derefter SI-brik, derefter
  navn og klub. Hentes listen fra OFeed, huskes forbindelsen (også i projektfilen), og "Opdater"
  henter ændringer. Tabellen kan søges i og filtreres; klik på en løber for at rette. Fluebenet afgør, om
  løberen kommer med; printerikonet udskriver kun den løber; "Ny" markerer dem der ikke er udskrevet.
- **Opsætning:** løbstype, navn, antal etaper, sprog på startnummeret, navneformat, tidszone,
  afstand til start pr. startsted, etapernes overskrifter, farve pr. tur, arkformat og sortering.
- **Design:** forhåndsvisning tegnet med samme kode og skrifter som udskriften. Alle elementer –
  nummer, navn, klub, hold, tur, klasse, starttid, bane, SI-nummer, stregkode, løbets navn,
  løberens felt (vendt; ved flerdagesløb én kasse pr. etape) og foldehjørner med sikkerhedsnål (ét
  element for alle fire hjørner) – kan slås til/fra, flyttes og ændres i størrelse med musen, med
  tekst- og baggrundsfarve pr. element; ved stafet også turens farve. Elementer og billeder kan
  drejes (håndtag, ±90° eller vinkel i grader); ved 90°/270° følger indholdet feltets lange side. Kun 1. tur har starttid.
  Vilkårligt mange billeder.
- **Udskriv** sender direkte til en printer (styresystemets printerdialog); vælg alle, kun dem der
  ikke er udskrevet før (nytilkomne), eller én løber. **Eksportér PDF** gemmer det samme som PDF.
- **Gem projekt** gemmer alt i én JSON-fil, der kan gives videre: opsætning, layout, startlisterne
  med rettelser, hvad der er udskrevet, OFeed-forbindelsen (med adgangskode) og billederne
  (indlejret). Ved åbning pakkes billederne ud i `%LOCALAPPDATA%\BiNumreGenerator\grafik`
  (Linux/Mac: `~/.cache/BiNumreGenerator/grafik`).
- **Logoer og sponsorer:** knapper til løbets logo, arrangørens logo og sponsorer (under Opsætning
  og i Design), hver med sin standardplads.
- **Sprog:** programmets sprog følger computerens. Teksterne ligger i
  `desktop/src/main/resources/i18n/<sprogkode>.properties` (UTF-8). Dansk og engelsk er oversat;
  et nyt sprog tilføjes ved at oversætte `en.properties` til en fil med en af sprogkoderne fra
  `BibTexts.languages`. Manglende tekster vises på engelsk. Startnummerets sprog vælges for sig.
- **Hjælp og Om:** hjælp og nye versioner på https://orientering.palsgaard.net; version, ophavsret
  og licenser for de komponenter programmet bruger.

### Kommandolinje
Kommandolinjen er den samme programfil med argumenter (`bin/BiNumreGenerator` på Linux og Mac;
på Windows er `.exe`-filen bygget uden konsolvindue, så brug `gradlew :desktop:run --args="..."`
for at se udskrifterne).
```
BiNumreGenerator startliste.xml -o numre.pdf --distance S1=400m --distance S2=1,2km
BiNumreGenerator stafet.xml --type relay --lang sv
BiNumreGenerator etape1.xml etape2.xml --stage Lørdag --stage Søndag --distance 1:400m --distance 2:S1=600m
BiNumreGenerator --ofeed "<QR-link>" --project loeb.json
BiNumreGenerator --help
```
Udskrift: to A5 pr. A4-ark med skærelinje (`--sheet a4x2`, standard) eller ét A5 pr. side (`--sheet a5`).

### Projektfil (JSON)
`--save-project loeb.json` gemmer opsætningen; `--project loeb.json` bruger den igen.
```json
{
  "type": "RELAY",
  "eventName": "Teststafetten",
  "language": "da",
  "zone": "Europe/Copenhagen",
  "graphics": [ { "file": "logo.png", "x": 8, "y": 20, "width": 32, "height": 14 } ],
  "legColors": ["#FFD400", "#3B82F6", "#22C55E"],
  "stages": [ { "label": "", "distances": { "S1": "400 m", "": "350 m" } } ]
}
```
- `graphics`: billeder (PNG/JPG), mål i mm fra startnummerets øverste venstre hjørne
  (210 × 148,5 mm). Stien er relativ til projektfilen. Billedet skaleres ind i rammen og ligger
  under teksten (men over stafettens farvede bånd).
- `elements`: startnummerets elementer (`kind`, `x`, `y`, `width`, `height`, `visible`, `rotation`
  i grader med uret, `align`, evt. `color`/`background` som `#RRGGBB` eller `leg` for turens farve). Udeladt = standardlayoutet for løbstypen.
- `infoFields`, `sharedStartList`, `printed`, `ofeed`: felter i løberens felt, én liste til alle
  etaper, startnumre der er udskrevet, og OFeed-forbindelse pr. startliste (link og adgangskode –
  gem ikke projektfilen offentligt).
- Hjælpesiden til hjemmesiden: `docs/hjaelp.md`. Projektets samlede viden (krav, beslutninger, læring): `docs/PROJEKT.md`.
- Programmets ikon tegnes af `desktop/icons/lav_ikon.py` (Pillow).
- `distances`: afstand til start pr. startsted; nøglen `""` gælder alle andre startsteder.
- `language`: sprog på startnummeret – engelsk, dansk og 22 andre europæiske sprog
  (`layout/BibTexts.kt`). "Afstand til start", "Tur" og "Etape" er oversat uden gennemsyn af
  modersmålstalende.

## Ophavsret og licenser
Copyright © 2026 David S. Palsgaard. Alle rettigheder forbeholdes – se `LICENSE.txt`.
Programmet bruger komponenter under frie licenser (Apache 2.0, Public Domain, DejaVu-licensen og
OpenJDK under GPLv2 med Classpath Exception); oversigten og licensteksterne ligger i
`desktop/src/main/resources/licenses/` og vises under "Om" i programmet. Versionen sættes i
`desktop/build.gradle.kts` (`appVersion`).

## Struktur
- `core/` – uden platformsafhængigheder (hverken Compose eller PDFBox): `startlist/`, `ofeed/`,
  `model/` (projekt, startnumre, fletning af etaper, fravalgte løbere), `layout/` (layout i mm
  mod `BibCanvas`, flytning/skalering af grafik i `GraphicEdit`, Code 128, tekster).
- `desktop/` – PC-program: `PdfWriter` (PDFBox, indlejret DejaVu Sans), `SkiaBibCanvas`
  (`BibCanvas` til skærmen, samme skrifter), `ProjectFiles`, kommandolinjen (`Main.kt`) og
  brugerfladen i `ui/` (Compose Multiplatform): `AppState` (tilstand), `TableLogic` (søgning og
  rettelser), `Strings` (sprogfiler) og ét skærmbillede pr. fane.

Compose hentes fra Maven Central og Googles Maven-arkiv (`google()` i `settings.gradle.kts`).

## Test
```
gradlew :core:test :desktop:test    # opdigtede testdata i core/src/test/resources
```
Brugerfladens logik (tabel, projektfil, sprogfiler, grafik-editorens geometri, og at skærm og PDF
måler teksten ens) er dækket af tests; selve vinduet er afprøvet i hånden med eksempelfilerne.

## Plan
1. ~~Import, datamodel, layout og PDF for alle tre skabeloner (kommandolinje)~~
2. ~~Grafisk brugerflade (Compose Multiplatform) med editor til grafik og tabel til at rette/vælge løbere~~
3. Direkte forbindelse til MeOS
4. ~~Installationspakke til Windows (zip uden installation og .msi, bygget på GitHub)~~ –
   kodesignering samt pakker til Linux og Mac mangler
5. Android-app (genbruger `core/` med sin egen `BibCanvas`)
