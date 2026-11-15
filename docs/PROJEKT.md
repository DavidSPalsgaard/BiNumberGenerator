# BiNumreGenerator – projektets samlede viden

Dette dokument samler det vi har lært undervejs i projektet: krav og ønsker fra brugeren, de
beslutninger der er truffet og hvorfor, arkitekturen, byggeopsætningen, faldgruber og hvad der
mangler. Det er skrevet så det kan genbruges som udgangspunkt for et andet projekt (fx en
Android-app eller et nyt orienteringsværktøj).

Brugerhjælpen til hjemmesiden ligger i `docs/hjaelp.md`; teknisk overblik i `README.md`.

---

## 1. Formål og målgruppe

- **Hvad:** Program der laver startnumre (A5) til orienteringsløb ud fra en startliste – enkeltløb,
  stafet og flerdagesløb – og udskriver dem direkte eller som PDF.
- **Hvem:** Arrangører i orienteringsklubber (frivillige), typisk på en Windows-bærbar på
  løbspladsen eller hjemme før løbet. Mange er ikke teknikere.
- **Ejer:** David S. Palsgaard. Hjemmeside til hjælp og nye versioner: https://orientering.palsgaard.net.
- **Sprog i samarbejdet:** dansk. Programmet skal kunne oversættes til de 24 europæiske sprog.
- **Søsterprojekter:** SI Startinfo og SiCardEditor (startlisteindlæsning og OFeed er oprindeligt
  kopieret fra SI Startinfo og udvidet).

---

## 2. Krav og ønsker – samlet fra brugerens tilbagemeldinger

Kravene kom i runder efter afprøvning af testpakker. De står her grupperet, med den runde de kom i.

### Platform og distribution
- **Windows er den primære maskine.** Pakken skal kunne køre **uden at installere ekstra
  komponenter** (ingen separat Java). → Selvstændig pakke med indbygget runtime (runde 1–2).
- Én samlet **testpakke** (zip) med program, installationsfil, testfiler og testvejledning.
- Kodet i eget GitHub-repository i roden: `DavidSPalsgaard/BiNumberGenerator`.

### Brugerflade
- Programmet skal **starte med valg af løbstype** (enkeltløb/stafet/flerdagesløb) og ved
  flerdagesløb **antal etaper**.
- **Moderne design** efter nyeste standarder med **orienteringslook** (postflagets orange/hvid,
  højdekurver).
- **Programmets sprog følger PC'ens sprog** – brugeren skal ikke vælge det. Startnummerets sprog
  vælges derimod for sig (fx svensk nummer på dansk PC).
- **Bedre programikon.**
- **Hjælp** (link til hjemmesiden, også til nye versioner) og **Om** med ophavsret og
  licensforhold.
- I elementlisten: **fluebenet må ikke vælge elementet** (listen hoppede); indstillinger først når
  man klikker på selve elementet.

### Startnummeret
- **Alle standardelementer skal kunne flyttes, ændres i størrelse og drejes**, og **slås til/fra**
  (vælg hvilke oplysninger fra startlisten der kommer på nummeret).
- **Foldehjørne med lille sikkerhedsnål** som standard: hjørnet foldes før nålen sættes i.
  Senere: **alle fire hjørner er ét element**, **14 × 14 mm**.
- **Vilkårligt antal grafik** – løbets logo, arrangørklubbens logo, sponsorer.
- **Teksten på startnummeret var for stor – også selve nummeret** (vigtig misforståelse: det var
  *ikke* programmets brugerflade der var for stor).
- **Stafet:** selve nummeret skal kunne **skifte farve efter turen**; **kun 1. tur har starttid**
  (de næste starter når holdkammeraten kommer i mål).
- **Flerdagesløb:** løberens felt skal være **én kasse pr. etape side om side** (som 5 Days), ikke
  en tabel.

### Data
- **Flerdagesløb:** én startliste pr. etape **eller** én startliste til alle etaper; løberne skal
  matches med **samme startnummer og person**.
- **OFeed skal kunne opdateres uden at indsætte forbindelsen igen.**
- **Al information gemmes i samme fil**, og filen skal kunne **gives videre** til andre.

### Udskrift
- **Udskriv direkte som standard** (ikke via PDF); PDF-eksport skal stadig findes.
- **Udskriv kun én eller et udvalg** – særligt nytilkomne på listen.

### Juridisk
- Ophavsret til David S. Palsgaard; licensforhold for tredjepartskomponenter skal overholdes og
  vises. Programmet skal overholde sikkerhed og rettigheder.

### Eksempler brugeren viste (reference for layout)
- **10MILA 2024 (Nynäshamn):** logo øverst til venstre, sponsorbånd, stort turnummer øverst til
  højre, kæmpe nummer på farvet (blåt) felt, holdnavn stort under, stregkode nederst til venstre med
  teksten `*568-4-1*` under.
- **10MILA 2026 (Tranås):** lodret grønt bånd med "10MILA 2026 TRANÅS" langs kanten, stort nummer,
  turnummer, sponsorlogo, stregkode med `257-5-1`.
- **5 Days (Dolomiti 2024):** farvet baggrund, hvidt nummer, navn øverst til højre, landeflag, én
  afrundet kasse pr. etape i bunden (vendt) med etape, starttid og bane, sponsorrække nederst.

---

## 3. Beslutninger og begrundelser

| Beslutning | Begrundelse / alternativer |
|---|---|
| **Kotlin + Compose Multiplatform (desktop)** | Kernen kan genbruges direkte i en Android-app. Alternativerne C#/.NET (selvstændig exe, kun Windows) og Rust/Tauri (lille pakke) ville kræve fuld omskrivning og miste Android-genbrug. |
| **Indbygget Java-runtime** (Compose `createDistributable`/`packageMsi`, jlink) | Brugeren skal ikke installere noget. Pris: pakken fylder ca. 150 MB udpakket. |
| **Byg Windows-pakken på GitHub Actions** (`windows-latest`) | jpackage kan ikke krydsbygge; udviklingsmiljøet er Linux. Release "seneste" opdateres ved hver push til `main`. |
| **`core/` uden Compose og PDFBox** | Android kan genbruge kernen med sin egen `BibCanvas`. |
| **Layout i mm mod et `BibCanvas`-interface** | Samme tegnekode til PDF (PDFBox), skærm (Skia) og senere Android – skærm og udskrift er ens. |
| **DejaVu Sans indlejret** | Fri licens, dækker alle europæiske sprog inkl. græsk/kyrillisk; samme skrift på skærm og i PDF så tekstbredder måles ens (testet). |
| **Elementmodel** (`BibElement` med ramme, synlighed, drejning, justering, farver) | Alle dele af nummeret kan flyttes/skaleres/drejes/slås fra. Tom liste = standardlayout for løbstypen, så layoutet følger typen indtil brugeren ændrer noget. |
| **Tekst skaleres til rammen** (rammens højde bestemmer skriftstørrelsen; formindskes hvis for bred) | Brugeren styrer størrelsen ved at trække i rammen – ingen separat skriftstørrelse. Cifre fylder 0,76 af skriftstørrelsen, almindelig tekst 1,17 (DejaVu). |
| **Ved 90°/270° bytter rammens bredde og højde** | Lodret tekst i et smalt, højt felt (som 10MILA 2026's bånd) uden at rammen skal være drejet. |
| **Direkte udskrift via PDFBox `PDFPageable` + `PrinterJob`** | Samme sider som PDF'en; Windows' egen printerdialog; ingen fil. |
| **Udskrevne gemmes som nøgler** (`nummer` eller `nummer/tur`) i projektet | "Kun ikke udskrevne" og mærket "Ny" virker også efter genstart og i en kopi af projektfilen. |
| **Flerdagesmatchning: startnummer → SI-brik → navn+klub** | Brugerens krav (samme nummer = samme løber); SI-brik og navn som reserve når nummeret mangler på en etape. |
| **Projektfilen er JSON med indlejrede billeder (base64) og startlister** | Én fil kan gives videre. Billeder pakkes ud i `%LOCALAPPDATA%\BiNumreGenerator\grafik` under et indholdshash. Ældre filer med relative stier virker stadig. |
| **OFeed-forbindelse gemmes i projektfilen (inkl. adgangskode)** | "Opdater" uden nyt link. Afvejning: filen indeholder adgangskoden – advarsel i dialogen og hjælpen. |
| **Programmets sprog fra `Locale.getDefault()`**; fallback engelsk | Brugerens krav. Tekster i `i18n/<kode>.properties`; manglende nøgler fra engelsk; test tjekker at alle nøgler i koden findes. |
| **Stregkode Code 128**, stafet `nummer-tur` | Som 10MILA. Læsbar tekst under når rammen er ≥ 9 mm høj; højst 0,5 mm pr. modul. |
| **Licens: "Alle rettigheder forbeholdes"** (foreløbig) | Ingen licens valgt endnu – åbent spørgsmål til ejeren. |

---

## 4. Arkitektur

```
BiNumberGenerator/
├─ core/                      Kotlin/JVM, ingen UI- eller PDF-afhængigheder (genbruges af Android)
│  ├─ startlist/              IOF XML 3.0/2.0.3, Excel (.xlsx), CSV, StartList ⇄ JSON
│  ├─ ofeed/                  OFeed-link (QR), hentning med lastUpdate, sammenfletning af ændringer
│  ├─ model/                  BibProject (projekt + JSON), BibElement/Frame/ElementKind/InfoField,
│  │                          Graphic/GraphicRole, OFeedSource, Bib + BibBuilder (single/relay/multiday)
│  └─ layout/                 BibCanvas (interface), BibLayout (tegning + standardlayout),
│                             GraphicEdit (hit-test, flyt/skalér/drej, standardplaceringer),
│                             Code128, BibTexts (24 sprog)
├─ desktop/                   Compose Multiplatform + PDFBox
│  ├─ PdfWriter / Printer     PDF-dokument, fil og direkte udskrift
│  ├─ SkiaBibCanvas           BibCanvas på skærm (samme skrifter) + PNG-eksport
│  ├─ ProjectFiles            Projektfil med indlejrede billeder og startlister
│  ├─ AppInfo                 Navn, version (fra build), hjemmeside, copyright, licenstekster
│  ├─ Main.kt                 Kommandolinje; uden argumenter → brugerflade
│  └─ ui/                     AppState, StartScreen, App (navigation, top, status), StartListTab,
│                             SetupTab, EditorTab, PrintDialog, AboutDialog, Theme, Strings, Widgets,
│                             TableLogic, FileDialogs
├─ desktop/icons/             icon.ico/.icns/.png + lav_ikon.py (Pillow)
├─ desktop/src/main/resources/i18n/      da.properties, en.properties
├─ desktop/src/main/resources/licenses/  THIRD-PARTY-NOTICES, Apache-2.0, NOTICE, PDFBox, DejaVu
├─ testpakke/                 Testfiler (CSV, XLSX, flerdages XML, logo, stafetprojekt), TESTVEJLEDNING
├─ docs/                      hjaelp.md (brugerhjælp), PROJEKT.md (dette dokument)
└─ .github/workflows/build.yml  Test + Windows-pakke + release "seneste"
```

### Nøglebegreber
- **Startnummer:** A5 liggende, 210 × 148,5 mm. Mål i mm fra øverste venstre hjørne. Vinkler i
  grader med uret om rammens midte.
- **Tegnerækkefølge:** turfarver → elementbaggrunde → grafik → foldehjørner → tekst/stregkode.
  Grafik ligger dermed under teksten men over farvede felter.
- **Standardlayout** beregnes ud fra løbstype og antal etaper (`BibLayout.defaultElements`):
  nummer ca. 46 mm højt felt centreret; navn 10 mm; klub 7 mm; løbets navn 6,2 mm; SI 6,6 mm;
  stregkode 80 × 11 mm; løberens felt 26 mm (30 mm ved flerdagesløb) vendt 180°; foldehjørner 14 mm.
- **Løberens felt (INFO):** enkeltløb = kolonner (starttid, klasse, bane, start, afstand);
  flerdagesløb = én kasse pr. etape. Felterne kan vælges (`infoFields`). Stafet: starttid kun tur 1.
- **Stafetnumre:** `12.1`, `12-1`, `12/1` → `12` når tallet efter tegnet er turen.
- **Startlister pr. plads:** plads 0 ved enkeltløb/stafet/fælles liste; ellers én pr. etape
  (`null` = mangler endnu). IOF `raceNumber` → `StartEntry.race`; en etape bruger linjer med
  `race == null || race == etape`.

### Projektfilens felter
`type, eventName, language, nameFormat, sheet, sortBy, zone, legColors, stages[label, distances],
elements[kind, x, y, width, height, visible, rotation, align, color|background (#RRGGBB eller "leg")],
graphics[file, x, y, width, height, role, rotation, data(base64)], infoFields, sharedStartList,
printed, ofeed{slot: {link, password}}, startLists[slot, event, source, loadedAt, cursor, date, entries]`.
Bagudkompatibilitet: `rotated: true` → 180°; fire separate FOLD-elementer → ét.

---

## 5. Byggeri, miljø og CI

- **Gradle 8.11.1**, Kotlin 2.1.0, Compose Multiplatform 1.7.3 (`org.jetbrains.compose` +
  `org.jetbrains.kotlin.plugin.compose`), JVM-target 17. Byg med JDK 17+ (CI bruger Temurin 21).
- Compose kræver **Googles Maven-arkiv** (`google()` i `settings.gradle.kts`). I sky-miljøet var
  `dl.google.com` spærret – løst ved at oprette et miljø der tillader værten.
- **`application`-pluginet kan ikke bruges sammen med Compose** (begge laver opgaven `run`). Brug
  Compose' `run`, `createDistributable`, `packageMsi`.
- Native pakker bygges kun til det styresystem man bygger på → `compose.desktop.currentOs`.
- **Runtime-moduler** (`nativeDistributions.modules`): `java.desktop, java.prefs, java.instrument,
  jdk.unsupported, java.xml, java.naming, java.net.http, jdk.crypto.ec, jdk.charsets,
  jdk.localedata` (fundet med `:desktop:suggestRuntimeModules` + HTTPS/tegnsæt/lokaliteter).
- **MSI:** `perUserInstall = true` (ingen administrator), `upgradeUuid` fast så nye versioner
  erstatter gamle, `packageVersion` skal være `MAJOR.MINOR.BUILD` med MAJOR > 0.
- **Version** sættes ét sted: `appVersion` i `desktop/build.gradle.kts`; skrives ind i
  `app.properties` via `processResources { expand(...) }`.
- **Ikon:** `windows.iconFile` (.ico), `macOS.iconFile` (.icns), `linux.iconFile` (.png) + vinduets
  ikon fra ressourcen `icon.png`.
- **GitHub Actions:** `windows-latest`, `actions/setup-java` (Temurin 21), `gradle/actions/setup-gradle`,
  tests → `createDistributable` + `packageMsi` → saml testpakke (PowerShell `Compress-Archive`) →
  artefakt + `gh release delete/create seneste` med `GITHUB_TOKEN` (`permissions: contents: write`).
- **Afhængigheder:** PDFBox 3.0.8, org.json 20260814 (opdateret til nyeste ved sikkerhedsgennemgangen).

### Afprøvning af brugerfladen uden skærm (Linux)
- `Xvfb :99` + `SKIKO_RENDER_API=SOFTWARE` + `xdotool` (klik, træk, tast) + `import` (skærmbillede).
- Fildialoger på Linux er Javas `JFileChooser` – skriv fuld sti i filnavnfeltet og tryk Enter.
- Start den byggede app (`desktop/build/compose/binaries/main/app/BiNumreGenerator/bin/BiNumreGenerator`)
  med `env -i` for at sikre at den indbyggede runtime bruges (ingen system-Java).
- PDF tjekkes med `pdftoppm` (billede) og `pdftotext`.
- **Faldgrube:** `pkill -f '<sti>'` dræber også den shell, hvis kommandolinje indeholder samme
  tekst (exit 144). Kør start/stop i et separat script.

---

## 6. Test

- `gradlew :core:test :desktop:test` – 56 tests.
- Kernen testes med en optagende `BibCanvas` (`Recorder`) – tekster, rektangler, mangekanter,
  drejninger – uden PDF.
- Desktop-tests dækker: tabel-logik (søg, filter, rettelser), projektfil (indlejrede billeder og
  startlister, flytning af filen, ældre format), programtilstand (løbstyper, etaper, fælles liste,
  udskrivningsomfang, elementer), **OFeed-opdatering mod en lokal `HttpServer`**, **direkte
  udskrift mod en PostScript-`StreamPrintService`**, at skærm og PDF måler tekst ens, at alle
  tekstnøgler i koden findes i `en.properties`, og at testpakkens filer svarer til testvejledningen.
- Eksempelfiler: `core/src/test/resources/*_eksempel.xml` (opdigtede data), `testpakke/`.

---

## 7. Faldgruber og læring

- **Misforståelser om "for stor tekst":** spørg hvad der menes – brugerfladen eller startnummeret.
  Vi gjorde først brugerfladen mindre (forkert), siden startnummerets tekst og nummeret.
- **Afbrudte beskeder** ("der er ikke rigtig …", "på stafet kan selve start nr …") – spørg ind i
  stedet for at gætte; den næste besked afklarede det.
- **Talfelter i Compose:** formatér ikke værdien tilbage mens feltet har fokus – ellers bliver
  "1" til minimumsværdien før brugeren når at skrive "100". Opdatér først ved fokus-tab.
- **Lister der hopper:** vis indstillinger inline under det valgte element og rul det frem
  (`BringIntoViewRequester`), og lad afkrydsning være adskilt fra valg.
- **Flerdagesløb:** et nummer der kun står på én etape skal vises (gråt) på de andre etaper –
  ellers ligner løberen en der mangler nummer.
- **Cifre i DejaVu Bold er lidt højere end versalhøjden** (0,76 mod 0,729) – ellers stikker de ud
  over et farvet felt.
- **Foldehjørner og etapekasser** kolliderer i bunden – kasserne har ekstra sidemargen.
- **PDF-koordinater** har y-aksen opad: drejning med uret på siden er en negativ vinkel.
- **Java-printerdialog uden printer** viser en engelsk standardbesked – tjek
  `PrintServiceLookup` først og vis egen dansk besked.
- **Projektfil fra andre er ikke betroet:** begræns billedstørrelse, tillad kun kendte
  filendelser, skriv kun i programmets egen mappe; XML uden DTD/eksterne entiteter; Excel med
  størrelsesgrænse pr. zip-post.
- **Licenser:** Apache 2.0 kræver licenstekst + NOTICE-filer med i distributionen; OpenJDK-runtimens
  licenser ligger i `lib/runtime/legal` (jlink tager dem med).
- **Windows SmartScreen** advarer om ukodesignerede programmer – forklar "Kør alligevel" i
  vejledningen indtil kodesignering er på plads.

---

## 8. Arbejdsgang med brugeren

- Arbejd i **runder**: byg → testpakke (GitHub Release "seneste") → brugeren tester på Windows →
  bemærkninger → næste runde. Hver runde får en opdateret `TESTVEJLEDNING.txt` med "Nyt i runde N".
- Brugeren tester på Windows; udviklingen sker på Linux – Windows-specifikke ting (exe, printer,
  fildialoger) kan kun afprøves af brugeren.
- Svar og tekster på dansk.

---

## 9. Åbne spørgsmål

- **Licens for selve programmet:** p.t. "Alle rettigheder forbeholdes". Gratis brug for klubber?
  Open source?
- **Navnet i copyright-linjen:** "David S. Palsgaard" – bekræftes.
- **Automatisk versionstjek:** kræver en lille versionsfil på hjemmesiden (fx `version.txt`); i dag
  åbner "Søg efter ny version" blot hjemmesiden.
- **Drejet markeringsramme:** ved skæve vinkler viser editoren den lige ramme, ikke den drejede figur.

---

## 10. Plan og idéer

1. ~~Import, datamodel, layout, PDF (kommandolinje)~~
2. ~~Grafisk brugerflade, editor, tabel~~
3. ~~Selvstændig Windows-pakke (zip + msi) bygget på GitHub~~
4. ~~Flytbare/drejelige elementer, foldehjørner, logoer, direkte udskrift, alt i én projektfil~~
5. Direkte forbindelse til MeOS (MeOS' informationsserver/REST) i stedet for eksport/import
6. Kodesignering af Windows-pakken
7. Pakker til Linux og Mac (samme Compose-opsætning; byg på deres egne runners)
8. Android-app – genbruger `core/` med en Android-`BibCanvas` (Canvas/PdfDocument) og
   Android-udskrift
9. Etapekasser som afrundede bokse (5 Days-stil), landeflag, fritekst-elementer
10. Automatisk versionstjek mod hjemmesiden

---

## 11. Genbrug i et andet projekt

Det der umiddelbart kan tages med:

- **Startlisteindlæsning** (`core/startlist/`): IOF XML 3.0/2.0.3 inkl. stafet og `raceNumber`,
  Excel uden eksterne biblioteker, CSV med automatisk skilletegn og flersprogede overskrifter,
  robust tidsfortolkning (klokkeslæt, Excel-tal, ISO).
- **OFeed-klient** (`core/ofeed/`): QR-link-fortolkning, Basic-auth kun over HTTPS til samme vært,
  `lastUpdate`-cursor med overlap og sammenfletning på deltager-id.
- **Tegning i mm mod et canvas-interface** med PDF- og Skia-implementering – samme layout på skærm,
  PDF og printer.
- **Editor-geometri** (`GraphicEdit`): hit-test (mindste ramme vinder), flyt/skalér med grænser,
  drejehåndtag med snap til kvarte omgange.
- **Compose desktop-skabelon:** tema med orienteringsfarver, navigation rail, startskærm med
  højdekurver, sprogfiler fra PC-sprog, Om-dialog med licenser, selvstændig Windows-pakke via
  GitHub Actions, testpakke som release.
- **Testteknikker:** optagende canvas, lokal HTTP-server, PostScript-printer, nøgletjek af
  sprogfiler, UI-afprøvning med Xvfb/xdotool.
