package dk.binumre

import dk.binumre.layout.Align
import dk.binumre.layout.BibCanvas
import dk.binumre.layout.BibFont
import dk.binumre.layout.BibLayout
import dk.binumre.layout.BibTexts
import dk.binumre.layout.Code128
import dk.binumre.model.BibBuilder
import dk.binumre.model.BibProject
import dk.binumre.model.BibType
import dk.binumre.model.Graphic
import dk.binumre.model.ElementKind
import dk.binumre.model.InfoField
import dk.binumre.model.Stage
import dk.binumre.startlist.StartEntry
import dk.binumre.startlist.StartList
import dk.binumre.startlist.StartListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BibTest {
    private val zone = ZoneId.of("Europe/Copenhagen")

    private fun load(name: String): StartList {
        val p = StartListParser.parse(javaClass.getResourceAsStream("/$name")!!.readBytes(), zone)
        return StartList(p.event, name, 0, p.entries, date = p.date)
    }

    /** Samler al tekst der tegnes, så layoutet kan afprøves uden PDF. */
    private class Recorder : BibCanvas {
        val texts = mutableListOf<String>()
        val rects = mutableListOf<Int>()
        var rotated = mutableListOf<String>()
        private var inRotation = false
        override fun textWidth(text: String, font: BibFont, size: Double) = text.length * size * 0.6
        override fun text(text: String, x: Double, baseline: Double, font: BibFont, size: Double, align: Align, color: Int) {
            texts += text
            if (inRotation) rotated += text
        }
        override fun rect(x: Double, y: Double, width: Double, height: Double, color: Int) { rects += color }
        override fun line(x1: Double, y1: Double, x2: Double, y2: Double, width: Double, color: Int) {}
        override fun image(file: String, x: Double, y: Double, width: Double, height: Double) {}
        val polygons = mutableListOf<Pair<Double, Double>>()
        override fun polygon(xs: DoubleArray, ys: DoubleArray, color: Int) { polygons += xs[0] to ys[0] }
        override fun circle(cx: Double, cy: Double, r: Double, width: Double, color: Int, fill: Boolean) {}
        override fun rotated180(cx: Double, cy: Double, block: () -> Unit) { inRotation = true; block(); inRotation = false }
    }

    @Test
    fun code128KendteVaerdier() {
        // Sæt B: start 104, "1"=17, "0"=16, "1"=17, kontrol (104+17+32+51)%103=101, stop 106
        assertEquals(listOf(104, 17, 16, 17, 101, 106), Code128.symbols("101"))
        // Sæt C for et lige antal cifre: start 105, 12, 34, kontrol (105+12+68)%103=82
        assertEquals(listOf(105, 12, 34, 82, 106), Code128.symbols("1234"))
        // 11 moduler pr. symbol, stop har 13
        assertEquals(5 * 11 + 13, Code128.modules("101").size)
        assertTrue(Code128.modules("101").first())
    }

    @Test
    fun enkeltloebSpringerLoebereUdenNummerOver() {
        val set = BibBuilder.build(BibProject(), listOf(load("startliste_eksempel.xml")))
        assertEquals(listOf("101", "301"), set.bibs.map { it.number })
        assertEquals(1, set.withoutNumber)
        val anna = set.bibs[0]
        assertEquals(8000001L, anna.card)
        assertEquals(6050, anna.stages[0]!!.courseLength)
        assertEquals("S1", anna.stages[0]!!.startName)
    }

    @Test
    fun enkeltloebTegnerBundenPaaHovedet() {
        val project = BibProject(eventName = "Testløbet", stages = listOf(Stage(distances = mapOf("S1" to "400 m"))))
        val bib = BibBuilder.build(project, listOf(load("startliste_eksempel.xml"))).bibs[0]
        val r = Recorder()
        BibLayout.draw(r, bib, project, zone)
        assertTrue(r.texts.containsAll(listOf("101", "Anna Marie Eksempel", "OK Testklub", "SI 8000001", "Testløbet")))
        assertEquals(listOf("Starttid", "11:41", "Klasse", "D21", "Bane", "3 · 6,1 km", "Start", "S1", "Afstand til start", "400 m"), r.rotated)
    }

    @Test
    fun stafetHarSammeNummerOgFarvePrTur() {
        val project = BibProject(type = BibType.RELAY, legColors = listOf("#FF0000", "#00FF00"))
        val bibs = BibBuilder.build(project, listOf(load("stafet_eksempel.xml"))).bibs
        assertEquals(listOf("12", "12", "12"), bibs.map { it.number })
        assertEquals(listOf("1", "2", "3"), bibs.map { it.leg })
        assertEquals("OK Testklub 1", bibs[0].teamName)
        // Farverne gentages når der er flere ture end farver
        assertEquals(listOf(0xFF0000, 0x00FF00, 0xFF0000), bibs.map { project.legColor(it.leg) })
        val r = Recorder()
        BibLayout.draw(r, bibs[1], project, zone)
        assertTrue("Tur 2" in r.texts)
        assertTrue(0x00FF00 in r.rects)
    }

    @Test
    fun holdnummerUdenTur() {
        assertEquals("12", BibBuilder.teamNumber("12.1", "1"))
        assertEquals("12", BibBuilder.teamNumber("12-2", "2"))
        assertEquals("12-2", BibBuilder.teamNumber("12-2", "1"))
        assertEquals("12", BibBuilder.teamNumber("12", "1"))
    }

    @Test
    fun flerdagesloebFletterEtaper() {
        val project = BibProject(type = BibType.MULTIDAY, stages = listOf(Stage("Lørdag"), Stage(distances = mapOf("" to "1,2 km"))))
        val set = BibBuilder.build(project, listOf(load("startliste_eksempel.xml"), load("etape2_eksempel.xml")))
        // Anna findes på SI-brik, Carl på navn (anden brik på etape 2), Bo har intet nummer
        assertEquals(listOf("101", "301"), set.bibs.map { it.number })
        val (anna, carl) = set.bibs
        assertEquals(listOf("S1", "S2"), anna.stages.map { it!!.startName })
        assertEquals(2, carl.stages.size)
        assertNull(carl.stages[0]!!.start)

        val r = Recorder()
        BibLayout.draw(r, carl, project, zone)
        assertTrue(r.rotated.containsAll(listOf("Lørdag", "Fri start", "Etape 2", "09:30:30", "1,2 km")))
        assertTrue(BibLayout.infoHeight(carl.stages.size) >= 24.0)
    }

    @Test
    fun projektGemmesOgLaeses() {
        val p = BibProject(
            type = BibType.RELAY, eventName = "Løb", language = "sv", zone = "Europe/Copenhagen",
            graphics = listOf(Graphic("logo.png", 8.0, 4.0, 30.0, 15.0)),
            legColors = listOf("#112233"), stages = listOf(Stage("Dag 1", mapOf("S1" to "400 m"))),
        )
        assertEquals(p, BibProject.fromJson(p.toJson()))
    }

    @Test
    fun elementerKanSlåsFraOgFlyttes() {
        val base = BibProject(eventName = "Testløbet")
        val bib = BibBuilder.build(base, listOf(load("startliste_eksempel.xml"))).bibs[0]
        // Standard: fire foldehjørner med sikkerhedsnål (trekant + nålens hætte = 2 mangekanter pr. hjørne)
        val r = Recorder().also { BibLayout.draw(it, bib, base, zone) }
        assertEquals(8, r.polygons.size)
        assertEquals(setOf(0.0 to 0.0, 210.0 to 0.0, 0.0 to 148.5, 210.0 to 148.5), r.polygons.toSet().filter { (x, y) -> (x == 0.0 || x == 210.0) && (y == 0.0 || y == 148.5) }.toSet())
        assertTrue("Testløbet" in r.texts)

        // Klub og løbets navn fra, klasse til; kun starttid og klasse i løberens felt
        val layout = base.layout().map {
            when (it.kind) {
                ElementKind.CLUB, ElementKind.EVENT_NAME, ElementKind.FOLD -> it.copy(visible = false)
                ElementKind.CLASS -> it.copy(visible = true)
                else -> it
            }
        }
        val custom = base.copy(elements = layout, infoFields = setOf(InfoField.START_TIME, InfoField.CLASS))
        val r2 = Recorder().also { BibLayout.draw(it, bib, custom, zone) }
        assertTrue("OK Testklub" !in r2.texts)
        assertTrue("Testløbet" !in r2.texts)
        assertEquals(0, r2.polygons.size)
        assertEquals(listOf("D21", "Starttid", "11:41", "Klasse", "D21"), r2.texts.filter { it == "D21" || it in r2.rotated })
        assertEquals(listOf("Starttid", "11:41", "Klasse", "D21"), r2.rotated)

        // Gemmes og læses
        assertEquals(custom, BibProject.fromJson(custom.toJson()))
        assertTrue(custom.customLayout)
        assertTrue(!base.customLayout)
    }

    @Test
    fun standardlayoutFølgerSkabelonen() {
        val single = BibLayout.defaultElements(BibType.SINGLE, 1).associateBy { it.kind }
        val relay = BibLayout.defaultElements(BibType.RELAY, 1)
        assertTrue(ElementKind.LEG_BAND !in single)
        assertTrue(relay.any { it.kind == ElementKind.LEG_BAND })
        assertTrue(relay.single { it.kind == ElementKind.TEAM }.visible)
        assertTrue(!relay.single { it.kind == ElementKind.CLUB }.visible)
        // Løberens felt vokser med antal etaper; alt ligger inden for startnummeret
        val multi = BibLayout.defaultElements(BibType.MULTIDAY, 5)
        assertTrue(multi.single { it.kind == ElementKind.INFO }.height > single.getValue(ElementKind.INFO).height)
        for (e in single.values + relay + multi) {
            assertTrue("$e", e.x >= 0 && e.y >= 0 && e.x + e.width <= BibLayout.WIDTH + 1e-9 && e.y + e.height <= BibLayout.HEIGHT + 1e-9)
        }
    }

    @Test
    fun flerdagesløbMatcherPåStartnummer() {
        fun list(vararg e: StartEntry) = StartList("", "", 0, e.toList())
        // Samme nummer → samme løber, selv om brik og navnets stavning er ændret
        val d1 = list(StartEntry(given = "Anna", family = "Hansen", club = "OK Ø", bib = "12", card = 1, cls = "D21"))
        val d2 = list(StartEntry(given = "Anne", family = "Hansen", club = "OK Øst", bib = "12", card = 2, cls = "D21"))
        val set = BibBuilder.build(BibProject(type = BibType.MULTIDAY, stages = listOf(Stage(), Stage())), listOf(d1, d2))
        assertEquals(1, set.bibs.size)
        assertEquals(2, set.bibs[0].stages.count { it != null })

        // Etape 2 mangler endnu: tre etaper, kun den første indlæst
        val three = BibProject(type = BibType.MULTIDAY, stages = listOf(Stage(), Stage(), Stage()))
        val partial = BibBuilder.build(three, listOf(d1, null, null)).bibs.single()
        assertEquals(3, partial.stages.size)
        assertNull(partial.stages[1])
    }

    @Test
    fun énStartlisteTilAlleEtaper() {
        val xml = """
            <StartList xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0">
            <Event><Name>Tredages</Name></Event>
            <ClassStart><Class><Name>H21</Name></Class>
            <PersonStart><Person><Name><Family>Demo</Family><Given>Carl</Given></Name></Person>
            <Start raceNumber="1"><BibNumber>7</BibNumber><StartTime>2026-07-01T10:00:00+02:00</StartTime></Start>
            <Start raceNumber="2"><BibNumber>7</BibNumber><StartTime>2026-07-02T11:00:00+02:00</StartTime></Start>
            </PersonStart></ClassStart></StartList>
        """.trimIndent()
        val p = StartListParser.parse(xml.toByteArray(), zone)
        assertEquals(listOf(1, 2), p.entries.map { it.race })
        val list = StartList(p.event, "x", 0, p.entries)

        // Delt liste med raceNumber: hver etape får sin egen starttid
        val project = BibProject(type = BibType.MULTIDAY, sharedStartList = true, stages = listOf(Stage(), Stage(), Stage()))
        val bib = BibBuilder.build(project, listOf(list)).bibs.single()
        assertEquals("7", bib.number)
        assertEquals(3, bib.stages.size)
        assertEquals(listOf("10:00", "11:00"), bib.stages.take(2).map { BibLayout.time(it!!.start, zone, BibTexts.of("da")) })
        assertNull(bib.stages[2])

        // Enkeltløb fra samme fil bruger første etape
        assertEquals(1, BibBuilder.build(BibProject(), listOf(list)).bibs.size)

        // Delt liste uden raceNumber: samme linje på alle etaper
        val plain = load("startliste_eksempel.xml")
        val all = BibBuilder.build(project.copy(stages = listOf(Stage(), Stage())), listOf(plain)).bibs.first()
        assertEquals(all.stages[0], all.stages[1])
    }

    @Test
    fun ukendtSprogGiverEngelsk() {
        assertEquals("Start time", BibTexts.of("xx").startTime)
        assertEquals("Afstand til start", BibTexts.of("da").distance)
        assertEquals(24, BibTexts.languages.size)
    }
}
