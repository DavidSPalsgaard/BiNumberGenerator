package dk.binumre.desktop

import dk.binumre.desktop.ui.AppState
import dk.binumre.desktop.ui.EntryFields
import dk.binumre.desktop.ui.FieldException
import dk.binumre.desktop.ui.PrintScope
import dk.binumre.desktop.ui.Screen
import dk.binumre.desktop.ui.Selection
import dk.binumre.desktop.ui.Strings
import dk.binumre.desktop.ui.TableLogic
import dk.binumre.layout.BibFont
import dk.binumre.layout.BibLayout
import dk.binumre.layout.BibTexts
import dk.binumre.model.Bib
import dk.binumre.model.BibProject
import dk.binumre.model.BibStage
import dk.binumre.model.BibType
import dk.binumre.model.Graphic
import dk.binumre.model.Stage
import dk.binumre.startlist.StartEntry
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class GuiLogicTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    /** Eksempelfilerne deles med core (testen kører i desktop/). */
    private fun resource(name: String) = File("../core/src/test/resources/$name").also { check(it.isFile) { it.absolutePath } }
    private fun tempDir() = Files.createTempDirectory("binumre").toFile().also { it.deleteOnExit() }

    // ---------- Projektfil ----------

    @Test
    fun grafikstierErRelativeIProjektfilen() {
        val dir = tempDir()
        val project = BibProject(
            eventName = "Testløbet",
            graphics = listOf(
                Graphic(File(dir, "logo.png").path, 1.0, 2.0, 30.0, 10.0),
                Graphic(File(dir, "grafik/sponsor.jpg").path, 5.0, 6.0, 30.0, 10.0),
            ),
        )
        val file = File(dir, "loeb.json")
        ProjectFiles.save(project, file)
        val stored = BibProject.fromJson(file.readText())
        assertEquals(listOf("logo.png", "grafik/sponsor.jpg"), stored.graphics.map { it.file })
        assertEquals(project, ProjectFiles.load(file))

        // Mappen kan flyttes: stierne følger projektfilen
        val moved = File(tempDir(), "loeb.json").also { file.copyTo(it) }
        assertEquals(File(moved.parentFile, "logo.png").path, ProjectFiles.load(moved).graphics[0].file)
    }

    // ---------- Tabel ----------

    private val entries = listOf(
        StartEntry(given = "Anna", family = "Eksempel", club = "OK Testklub", cls = "D21", bib = "101", card = 8000001),
        StartEntry(given = "Bo", family = "Prøve", cls = "D21"),
        StartEntry(name = "Carl Demo", club = "Test OK", cls = "H21", bib = "301"),
    )

    @Test
    fun søgningOgFiltre() {
        assertEquals(listOf(0, 1, 2), TableLogic.filter(entries, "", null, false))
        assertEquals(listOf(0, 1), TableLogic.filter(entries, "", "D21", false))
        assertEquals(listOf(1), TableLogic.filter(entries, "", null, true))
        assertEquals(listOf(0), TableLogic.filter(entries, "ANNA testklub", null, false))
        assertEquals(listOf(0), TableLogic.filter(entries, "8000001", null, false))
        assertEquals(listOf(2), TableLogic.filter(entries, "demo", null, false))
        assertEquals(emptyList<Int>(), TableLogic.filter(entries, "demo", "D21", false))
    }

    @Test
    fun rettelseAfLøber() {
        val date = LocalDate.of(2026, 5, 2)
        val f = TableLogic.fields(entries[1], zone)
        assertEquals("", f.start)
        val e = TableLogic.apply(entries[1], f.copy(bib = " 102 ", card = "2000002", start = "9.43", club = "OK Ny"), zone, date)
        assertEquals("102", e.bib)
        assertEquals(2000002L, e.card)
        assertEquals("OK Ny", e.club)
        assertEquals("09:43:00", TableLogic.time(e.start, zone))
        assertEquals(date, java.time.Instant.ofEpochMilli(e.start!!).atZone(zone).toLocalDate())

        // Ændret klokkeslæt bliver på løberens dag; uændret tekst rører ikke tiden
        val later = TableLogic.apply(e, TableLogic.fields(e, zone).copy(start = "10:15:30"), zone, LocalDate.of(2030, 1, 1))
        assertEquals("10:15:30", TableLogic.time(later.start, zone))
        assertEquals(date, java.time.Instant.ofEpochMilli(later.start!!).atZone(zone).toLocalDate())
        assertEquals(e, TableLogic.apply(e, TableLogic.fields(e, zone), zone, date))

        // Tom starttid = fri start; tomt SI-nummer = intet
        val free = TableLogic.apply(e, TableLogic.fields(e, zone).copy(start = "", card = ""), zone, date)
        assertNull(free.start)
        assertNull(free.card)

        assertEquals("card", assertThrows(FieldException::class.java) { TableLogic.apply(e, EntryFields(card = "abc"), zone, date) }.field)
        assertEquals("start", assertThrows(FieldException::class.java) { TableLogic.apply(e, EntryFields(start = "kl. 10"), zone, date) }.field)
        assertEquals("start", assertThrows(FieldException::class.java) { TableLogic.apply(e, EntryFields(start = "25:00"), zone, date) }.field)
    }

    @Test
    fun etaperOgAfstande() {
        var p = TableLogic.withStage(BibProject(), 1) { it.copy(label = "Søndag") }
        assertEquals(listOf(Stage(), Stage("Søndag")), p.stages)
        p = TableLogic.withStage(p, 0) { TableLogic.withDistance(it, "S1", "400 m") }
        p = TableLogic.withStage(p, 0) { TableLogic.withDistance(it, "", "1 km") }
        assertEquals("400 m", p.stage(0).distance("S1"))
        assertEquals("1 km", p.stage(0).distance("S2"))
        p = TableLogic.withStage(p, 0) { TableLogic.withDistance(it, "S1", "") }
        assertEquals(mapOf("" to "1 km"), p.stage(0).distances)
    }

    // ---------- Programtilstand ----------

    @Test
    fun indlæsRetOgSkrivPdf() {
        val state = AppState()
        assertEquals(Screen.START, state.screen)
        state.start(BibType.SINGLE)
        assertEquals(Screen.MAIN, state.screen)
        state.loadFiles(listOf(resource("startliste_eksempel.xml")))
        assertNull(state.error)
        assertEquals(BibType.SINGLE, state.project.type)
        assertEquals("Testløbet", state.project.eventName)
        assertEquals(2, state.bibSet.bibs.size)
        assertEquals(1, state.bibSet.withoutNumber)
        assertEquals(listOf("S1"), TableLogic.startNames(state.lists[0]!!))

        // Giv løberen uden nummer et startnummer, og fravælg en anden
        state.updateEntries(0) { _, e -> if (e.bib.isEmpty()) e.copy(bib = "102") else e }
        assertEquals(listOf("101", "102", "301"), state.bibSet.bibs.map { it.number })
        assertEquals(0, state.bibSet.withoutNumber)
        state.updateEntries(0) { _, e -> if (e.bib == "301") e.copy(excluded = true) else e }
        assertEquals(listOf("101", "102"), state.bibSet.bibs.map { it.number })

        // En ny fil erstatter listen ved enkeltløb; flere filer → kun den første bruges
        state.loadFiles(listOf(resource("stafet_eksempel.xml"), resource("etape2_eksempel.xml")))
        assertEquals(1, state.lists.size)
        assertEquals(BibType.SINGLE, state.project.type)
        state.setType(BibType.RELAY)
        assertEquals(listOf("12", "12", "12"), state.bibSet.bibs.map { it.number })

        val dir = tempDir()
        val pdf = File(dir, "ud.pdf")
        state.writePdf(pdf)
        assertNull(state.error)
        assertTrue(pdf.length() > 1000)
        assertEquals(pdf, state.lastPdf)
    }

    @Test
    fun flerdagesløbMedÉnListePrEtapeEllerFælles() {
        val state = AppState()
        state.start(BibType.MULTIDAY, 3)
        assertEquals(3, state.project.stageCount)
        assertEquals(3, state.slotCount)
        assertEquals(3, state.previewBib.stages.size)

        // To filer valgt fra etape 1 fylder etape 1 og 2; etape 3 mangler endnu
        state.loadFiles(listOf(resource("startliste_eksempel.xml"), resource("etape2_eksempel.xml")))
        assertNull(state.error)
        assertEquals(listOf("startliste_eksempel.xml", "etape2_eksempel.xml", null), state.usedLists.map { it?.source })
        val anna = state.bibSet.bibs.first { it.number == "101" }
        // Anna har intet nummer i etape 2's fil, men får sit nummer fra etape 1 (matchet på SI-brik)
        val annaDay2 = state.lists[1]!!.entries.first { it.given == "Anna Marie" }
        assertEquals("", annaDay2.bib)
        assertEquals("101", TableLogic.resolvedNumber(annaDay2, state.bibSet.bibs))
        assertEquals(3, anna.stages.size)
        assertEquals("S2", anna.stages[1]!!.startName)
        assertNull(anna.stages[2])

        // Fil til etape 3 alene; flere filer end etaper udvider antallet
        state.loadFiles(listOf(resource("etape2_eksempel.xml")), slot = 2)
        assertEquals("etape2_eksempel.xml", state.usedLists[2]?.source)
        state.loadFiles(listOf(resource("etape2_eksempel.xml"), resource("etape2_eksempel.xml")), slot = 2)
        assertEquals(4, state.project.stageCount)

        state.project = TableLogic.withStage(state.project, 1) { it.copy(label = "Søndag") }
        state.setStageCount(2)
        assertEquals(listOf(Stage(), Stage("Søndag")), state.project.stages)
        assertEquals(2, state.lists.size)
        state.removeList(0)
        assertNull(state.usedLists[0])

        // Fælles liste: én plads, alle etaper fra samme fil
        state.setShared(true)
        assertEquals(1, state.slotCount)
        state.loadFiles(listOf(resource("startliste_eksempel.xml")))
        assertEquals(2, state.bibSet.bibs.first().stages.count { it != null })

        state.loadFiles(listOf(File("findes-ikke.xml")))
        assertTrue(state.error != null)
    }

    @Test
    fun udskriftAfAlleNyeOgÉn() {
        val state = AppState()
        state.start(BibType.SINGLE)
        state.loadFiles(listOf(resource("startliste_eksempel.xml")))
        assertEquals(listOf("101", "301"), state.bibsFor(PrintScope.All).map { it.number })
        assertEquals(listOf("301"), state.bibsFor(PrintScope.One("301")).map { it.number })

        state.markPrinted(state.bibsFor(PrintScope.All))
        assertEquals(emptyList<String>(), state.notPrinted.map { it.number })
        // En nytilkommen får nummer → kun den er "ikke udskrevet"
        state.updateEntries(0) { _, e -> if (e.bib.isEmpty()) e.copy(bib = "102") else e }
        assertEquals(listOf("102"), state.bibsFor(PrintScope.NotPrinted).map { it.number })
        assertEquals("102", TableLogic.entryKey(state.lists[0]!!.entries.first { it.bib == "102" }, BibType.SINGLE))
        // Huskes i projektfilen
        assertEquals(setOf("101", "301"), BibProject.fromJson(state.project.toJson()).printed)
        state.forgetPrinted()
        assertEquals(3, state.notPrinted.size)

        // Stafet: nøglen er nummer/tur
        state.loadFiles(listOf(resource("stafet_eksempel.xml")))
        state.setType(BibType.RELAY)
        val keys = state.bibSet.bibs.map { it.key }
        assertEquals(listOf("12/1", "12/2", "12/3"), keys)
        assertEquals(keys.toSet(), state.lists[0]!!.entries.map { TableLogic.entryKey(it, BibType.RELAY) }.toSet())
    }

    /** OFeed-forbindelsen huskes; "Opdater" henter kun ændringer og bevarer fravalg. Mod en lokal testserver. */
    @Test
    fun ofeedOpdateresUdenNytLink() {
        fun competitor(id: Int, name: String, bib: Int?) =
            """{"id":$id,"lastname":"$name","firstname":"Test","organisation":"OK","bibNumber":${bib ?: "null"},"card":${1000 + id},"startTime":null,"status":"Inactive"}"""
        fun body(vararg c: String) = """{"results":{"data":{"name":"OFeed-løbet","classes":[{"name":"H21","startName":"S1","competitors":[${c.joinToString(",")}]}]}},"meta":{"timestamp":"2026-11-15T10:00:00.000Z"}}"""
        val requests = mutableListOf<String>()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/events/42/competitors") { ex ->
            val query = ex.requestURI.rawQuery.orEmpty()
            requests += query
            // Første hentning: alle; derefter kun ændringer (en ny løber og et nyt nummer)
            val json = if (query.isEmpty()) body(competitor(1, "Alfa", 1), competitor(2, "Beta", 2))
            else body(competitor(2, "Beta", 22), competitor(3, "Gamma", 3))
            val bytes = json.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val link = "https://stigning.se/ofeed?url=" + java.net.URLEncoder.encode("http://127.0.0.1:${server.address.port}/events", "UTF-8") + "&id=42&pwd=x"
            val state = AppState()
            state.start(BibType.SINGLE)
            val source = dk.binumre.model.OFeedSource(link)
            state.addOFeed(state.fetchOFeed(source), 0, source)
            assertEquals(listOf("1", "2"), state.bibSet.bibs.map { it.number })
            assertEquals(source, state.project.ofeed[0])
            state.updateEntries(0) { _, e -> if (e.bib == "1") e.copy(excluded = true) else e }

            // Projektet gemmes og åbnes igen: forbindelsen er der stadig
            val file = File(tempDir(), "ofeed.json")
            state.saveProject(file)
            assertEquals(source, BibProject.fromJson(file.readText()).ofeed[0])

            state.setList(0, state.refreshOFeed(0))
            assertEquals(2, state.lastChanges)
            assertTrue(requests.last().startsWith("lastUpdate="))
            // Alfa er stadig fravalgt, Beta har fået nyt nummer, Gamma er kommet til
            assertEquals(listOf("3", "22"), state.bibSet.bibs.map { it.number })

            // En fil på samme plads afløser forbindelsen
            state.loadFiles(listOf(resource("startliste_eksempel.xml")))
            assertTrue(state.project.ofeed.isEmpty())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun direkteUdskriftSenderSiderneTilPrinteren() {
        val state = AppState()
        state.start(BibType.SINGLE)
        state.loadFiles(listOf(resource("startliste_eksempel.xml")))
        val out = java.io.ByteArrayOutputStream()
        val factory = java.awt.print.PrinterJob.lookupStreamPrintServices("application/postscript").firstOrNull()
            ?: return // Ingen PostScript-udskrift i denne Java – springes over
        val job = Printer.prepare(state.bibSet.bibs, state.project, "Test", factory.getPrintService(out))!!
        assertEquals(1, job.pages)
        job.print()
        val ps = out.toString(Charsets.ISO_8859_1)
        assertTrue(ps.startsWith("%!PS"))
        assertTrue(ps.length > 10_000)
    }

    @Test
    fun elementerFlyttesOgLayoutNulstilles() {
        val state = AppState()
        state.start(BibType.RELAY)
        assertFalse(state.project.customLayout)
        // Foldehjørnerne er ét kvadrat i hjørnet
        val f = state.elements.indexOfFirst { it.kind == dk.binumre.model.ElementKind.FOLD }
        state.updateElement(f, state.elements[f].copy(x = 50.0, y = 9.0, width = 20.0, height = 12.0))
        assertEquals(dk.binumre.model.Frame(0.0, 0.0, 20.0, 20.0), state.elements[f].frame)
        val i = state.elements.indexOfFirst { it.kind == dk.binumre.model.ElementKind.NUMBER }
        state.updateElement(i, state.elements[i].copy(x = 20.0, background = 0x5BA4CF, color = 0xFFFFFF))
        assertTrue(state.project.customLayout)
        assertEquals(20.0, state.elements[i].x, 0.0)
        assertEquals(BibProject.fromJson(state.project.toJson()).elements, state.project.elements)
        state.resetLayout()
        assertFalse(state.project.customLayout)
    }

    @Test
    fun projektGemmesOgÅbnesMedGrafik() {
        val dir = tempDir()
        val logo = File(dir, "logo.png")
        ImageIO.write(BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB), "png", logo)
        val state = AppState()
        assertFalse(state.dirty)
        state.addGraphic(logo)
        assertEquals(Graphic(logo.absolutePath, 8.0, 8.0, 40.0, 20.0), state.project.graphics.single())
        assertEquals(Selection(true, 0), state.selection)
        // Vilkårligt mange billeder
        repeat(14) { state.addGraphic(logo) }
        assertEquals(15, state.project.graphics.size)
        repeat(14) { state.removeGraphic(1) }
        assertTrue(state.dirty)

        state.addGraphic(File(dir, "mangler.png"))
        assertTrue(state.error != null)
        assertEquals(1, state.project.graphics.size)

        val file = File(dir, "loeb.json")
        state.saveProject(file)
        assertFalse(state.dirty)
        assertTrue("\"logo.png\"" in file.readText())

        val other = AppState()
        other.openProject(file)
        assertEquals(state.project, other.project)
        other.removeGraphic(0)
        assertTrue(other.dirty)

        // Eksempel-startnummeret bruges, når der ingen startliste er
        assertEquals("123", other.previewBib.number)
    }

    // ---------- Testpakken ----------

    /** Det TESTVEJLEDNING.txt lover om testfilerne. */
    @Test
    fun testpakkensFilerSvarerTilVejledningen() {
        val dir = File("../testpakke")
        val csv = AppState().apply { start(BibType.SINGLE); loadFiles(listOf(File(dir, "startliste_eksempel.csv"))) }
        assertNull(csv.error)
        val entries = csv.lists.single()!!.entries
        assertEquals(5, entries.size)
        assertEquals(4, csv.bibSet.bibs.size)
        assertEquals(1, csv.bibSet.withoutNumber)
        assertEquals("Carl Larsen", entries.single { it.bib.isEmpty() }.displayName(dk.binumre.startlist.NameFormat.GIVEN_FAMILY))
        assertNull(entries.single { it.given == "Ditte" }.start)
        assertEquals("Æse Ørsted", entries.single { it.bib == "14" }.displayName(dk.binumre.startlist.NameFormat.GIVEN_FAMILY))

        val xlsx = AppState().apply { start(BibType.SINGLE); loadFiles(listOf(File(dir, "startliste_eksempel.xlsx"))) }
        assertNull(xlsx.error)
        assertEquals(4, xlsx.lists.single()!!.entries.size)
        assertEquals("Åberg", xlsx.lists.single()!!.entries.single { it.bib.isEmpty() }.family)

        // Tre etaper i én fil: én startliste til alle etaper, hver etape med sin egen starttid
        val multi = AppState().apply { start(BibType.MULTIDAY, 3); setShared(true); loadFiles(listOf(File(dir, "flerdages_alle_etaper.xml"))) }
        assertNull(multi.error)
        assertEquals(listOf("12", "13", "14", "15"), multi.bibSet.bibs.map { it.number })
        val first = multi.bibSet.bibs.first()
        assertEquals(listOf("10:00:00", "11:00:00", "12:00:00"), first.stages.map { TableLogic.time(it!!.start, java.time.ZoneId.of("Europe/Copenhagen")) })

        val relay = AppState()
        relay.openProject(File(dir, "stafet_projekt.json"))
        assertEquals(Screen.MAIN, relay.screen)
        assertNull(relay.error)
        assertEquals(BibType.RELAY, relay.project.type)
        assertTrue(relay.images.get(relay.project.graphics.single().file) != null)
        relay.loadFiles(listOf(resource("stafet_eksempel.xml")))
        assertEquals(listOf("12", "12", "12"), relay.bibSet.bibs.map { it.number })
        assertEquals("Teststafetten 2026", relay.project.eventName)
    }

    // ---------- Sprog ----------

    @Test
    fun danskOgEngelskHarDeSammeTekster() {
        val da = Strings.read("da")
        val en = Strings.read("en")
        assertTrue(en.size > 80)
        assertEquals(en.keys.sorted(), da.keys.sorted())
        assertTrue(Strings.available.containsAll(listOf("da", "en")))
        assertTrue(BibTexts.languages.containsAll(Strings.available))
        // Samme pladsholdere i begge sprog
        val placeholder = Regex("\\{\\d}")
        for (k in en.keys) assertEquals(k, placeholder.findAll(en.getValue(k)).map { it.value }.toSet(), placeholder.findAll(da.getValue(k)).map { it.value }.toSet())
    }

    /** Alle faste nøgler i koden (s["…"] og s.format("…")) findes i en.properties. */
    @Test
    fun alleNøglerIKodenFindes() {
        val en = Strings.read("en")
        val key = Regex("""\b(?:s|strings|state\.strings)(?:\.format\(|\[)"([a-zA-Z.]+)"""")
        val used = File("src/main/kotlin").walk().filter { it.extension == "kt" }
            .flatMap { f -> key.findAll(f.readText()).map { it.groupValues[1] } }.toSet()
        assertTrue(used.size > 100)
        assertEquals(emptySet<String>(), used - en.keys)
        // Nøgler der sættes sammen af enum-navne
        val composed = dk.binumre.model.ElementKind.entries.map { "kind.${it.name}" } + dk.binumre.model.InfoField.entries.map { "info.${it.name}" } +
            dk.binumre.layout.Align.entries.map { "align.${it.name}" } + BibType.entries.map { "type.${it.name}" } +
            dk.binumre.model.Sheet.entries.map { "sheet.${it.name}" } + dk.binumre.model.SortBy.entries.map { "sort.${it.name}" }
        assertEquals(emptySet<String>(), composed.toSet() - en.keys)
    }

    @Test
    fun teksterMedPladsholdereOgReserve() {
        val da = Strings.load("da")
        assertEquals("3 startnumre", da.format("status.bibs", 3))
        assertEquals("Startliste", da["nav.list"])
        assertEquals("Start list", Strings.load("en")["nav.list"])
        // Ukendt sprog giver engelsk; ukendt nøgle giver nøglen
        assertEquals("en", Strings.load("xx").language)
        assertEquals("findes.ikke", da["findes.ikke"])
        val partial = Strings("sv", mapOf("ok" to "Okej"), Strings.read("en"))
        assertEquals("Okej", partial["ok"])
        assertEquals("Cancel", partial["cancel"])
    }

    // ---------- Forhåndsvisning ----------

    @Test
    fun skærmOgPdfMålerTekstEns() {
        val canvas = SkiaBibCanvas(Surface.makeRasterN32Premul(10, 10).canvas, 4f) { null }
        PDDocument().use { doc ->
            for ((font, file) in listOf(BibFont.REGULAR to "DejaVuSans.ttf", BibFont.BOLD to "DejaVuSans-Bold.ttf")) {
                val pdf = BibLayout::class.java.getResourceAsStream("/fonts/$file").use { PDType0Font.load(doc, it!!, true) }
                for (text in listOf("101", "Anna Marie Eksempel", "Åse Østergård-Ærø", "Afstand til start")) {
                    val expected = pdf.getStringWidth(text) / 1000.0 * 11.0
                    assertEquals("$text ($font)", expected, canvas.textWidth(text, font, 11.0), expected * 0.002)
                }
            }
        }
    }

    @Test
    fun startnummerTegnesSomBillede() {
        val bib = Bib(
            number = "101", given = "Anna", family = "Eksempel", name = "", club = "OK Testklub", card = 8000001L,
            cls = "D21", stages = listOf(BibStage(null, "D21", "3", 6100, "S1")),
        )
        val png = BibImage.png(bib, BibProject(eventName = "Testløbet"), pixelsPerMm = 4f)
        val image = Image.makeFromEncoded(png)
        assertEquals(840, image.width)
        assertEquals(594, image.height)
        // Der er tegnet noget sort (nummeret) på den hvide baggrund
        val pixels = ImageIO.read(png.inputStream())
        val dark = (0 until pixels.width step 4).sumOf { x -> (0 until pixels.height step 4).count { y -> pixels.getRGB(x, y) and 0xFFFFFF == 0 } }
        assertTrue(dark > 1000)
        assertEquals(0xFFFFFF, pixels.getRGB(20, 300) and 0xFFFFFF)
        // Foldehjørnet er gråt
        assertEquals(0xE6E6E6, pixels.getRGB(3, 3) and 0xFFFFFF)
    }
}
