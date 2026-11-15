package dk.binumre

import dk.binumre.layout.BibLayout
import dk.binumre.layout.GraphicEdit
import dk.binumre.layout.Handle
import dk.binumre.model.BibBuilder
import dk.binumre.model.BibProject
import dk.binumre.model.BibType
import dk.binumre.model.Frame
import dk.binumre.startlist.StartEntry
import dk.binumre.startlist.StartList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphicEditTest {
    private val a = Frame(10.0, 10.0, 40.0, 20.0)
    private val b = Frame(30.0, 20.0, 40.0, 20.0)

    @Test
    fun rammerØversteGrafikOgDenValgtesHjørner() {
        assertNull(GraphicEdit.hit(listOf(a, b), 100.0, 100.0, null, 2.0))
        assertEquals(0 to Handle.MOVE, GraphicEdit.hit(listOf(a, b), 15.0, 15.0, null, 2.0))
        // I overlappet vinder den øverste (sidste)
        assertEquals(1 to Handle.MOVE, GraphicEdit.hit(listOf(a, b), 35.0, 25.0, null, 2.0))
        // Den valgtes hjørner går forud – også lidt uden for rammen
        assertEquals(0 to Handle.SE, GraphicEdit.hit(listOf(a, b), 51.0, 31.0, 0, 2.0))
        assertEquals(0 to Handle.NW, GraphicEdit.hit(listOf(a, b), 9.0, 9.0, 0, 2.0))
        assertNull(GraphicEdit.hit(listOf(a, b), 9.0, 9.0, null, 2.0))
        // Et lille element oven på et stort gribes først
        val small = Frame(20.0, 15.0, 5.0, 5.0)
        assertEquals(1 to Handle.MOVE, GraphicEdit.hit(listOf(a, small, b), 22.0, 17.0, null, 2.0))
    }

    @Test
    fun drejehåndtagOgVinkel() {
        val f = Frame(50.0, 50.0, 40.0, 20.0)
        val (hx, hy) = GraphicEdit.rotateHandle(f, 2.0)
        assertEquals(70.0 to 45.0, hx to hy)
        assertEquals(0 to Handle.ROTATE, GraphicEdit.hit(listOf(f), 70.5, 45.5, 0, 2.0))
        assertNull(GraphicEdit.hit(listOf(f), 70.5, 45.5, null, 2.0))
        // Vinkler fra midten (70, 60): op = 0, højre = 90, ned = 180, venstre = 270; springer på plads nær kvarte omgange
        assertEquals(0.0, GraphicEdit.angle(f, 70.0, 10.0), 0.0)
        assertEquals(90.0, GraphicEdit.angle(f, 120.0, 63.0), 0.0)
        assertEquals(180.0, GraphicEdit.angle(f, 70.0, 100.0), 0.0)
        assertEquals(270.0, GraphicEdit.angle(f, 20.0, 60.0), 0.0)
        assertEquals(45.0, GraphicEdit.angle(f, 100.0, 30.0), 0.0)
        // Ramme helt oppe ved kanten: håndtaget sidder under den
        val top = Frame(10.0, 1.0, 40.0, 20.0)
        assertEquals(26.0, GraphicEdit.rotateHandle(top, 2.0).second, 0.0)
        assertEquals(0.0, GraphicEdit.angle(top, 30.0, 40.0, handleBelow = true), 0.0)
    }

    @Test
    fun flytningHoldesIndenForStartnummeret() {
        assertEquals(a.copy(x = 15.0, y = 7.5), GraphicEdit.drag(a, Handle.MOVE, 5.0, -2.5))
        assertEquals(a.copy(x = 0.0, y = 0.0), GraphicEdit.drag(a, Handle.MOVE, -500.0, -500.0))
        assertEquals(a.copy(x = BibLayout.WIDTH - 40, y = BibLayout.HEIGHT - 20), GraphicEdit.drag(a, Handle.MOVE, 500.0, 500.0))
        // Afrundes til 0,1 mm
        assertEquals(a.copy(x = 11.2), GraphicEdit.drag(a, Handle.MOVE, 1.2345, 0.0))
    }

    @Test
    fun hjørnerÆndrerStørrelsen() {
        assertEquals(a.copy(width = 50.0, height = 25.0), GraphicEdit.drag(a, Handle.SE, 10.0, 5.0))
        assertEquals(Frame(5.0, 5.0, 45.0, 25.0), GraphicEdit.drag(a, Handle.NW, -5.0, -5.0))
        assertEquals(Frame(10.0, 5.0, 50.0, 25.0), GraphicEdit.drag(a, Handle.NE, 10.0, -5.0))
        assertEquals(Frame(5.0, 10.0, 45.0, 25.0), GraphicEdit.drag(a, Handle.SW, -5.0, 5.0))
        // Mindst 3 mm, og ikke ud over kanten
        assertEquals(a.copy(width = 3.0, height = 3.0), GraphicEdit.drag(a, Handle.SE, -500.0, -500.0))
        assertEquals(a.copy(width = BibLayout.WIDTH - 10, height = BibLayout.HEIGHT - 10), GraphicEdit.drag(a, Handle.SE, 500.0, 500.0))
        assertEquals(Frame(47.0, 27.0, 3.0, 3.0), GraphicEdit.drag(a, Handle.NW, 500.0, 500.0))
        // Talfelter
        assertEquals(Frame(170.0, 0.0, 40.0, 3.0), GraphicEdit.clamp(Frame(500.0, -4.0, 40.0, 1.0)))
    }

    @Test
    fun nytBilledeFårBilledetsForhold() {
        assertEquals(Frame(8.0, 8.0, 40.0, 20.0), GraphicEdit.place(400, 200, 0))
        assertEquals(Frame(20.0, 20.0, 40.0, 60.0), GraphicEdit.place(100, 1000, 2))
        // Vilkårligt mange billeder: de forskydes i ring og holdes på startnummeret
        assertEquals(Frame(8.0, 8.0, 40.0, 20.0), GraphicEdit.place(400, 200, 12))
    }

    // ---------- Fravalgte løbere ----------

    private fun list(vararg e: StartEntry) = StartList("", "test", 0, e.toList())

    @Test
    fun fravalgteLøbereFårIkkeStartnummer() {
        val anna = StartEntry(given = "Anna", family = "A", cls = "D21", bib = "1")
        val bo = StartEntry(given = "Bo", family = "B", cls = "H21", bib = "2")
        val uden = StartEntry(given = "Carl", family = "C", cls = "H21")
        val set = BibBuilder.build(BibProject(), listOf(list(anna, bo.copy(excluded = true), uden)))
        assertEquals(listOf("1"), set.bibs.map { it.number })
        assertEquals(1, set.withoutNumber)
        // En fravalgt løber uden startnummer tæller ikke som manglende
        assertEquals(0, BibBuilder.build(BibProject(), listOf(list(anna, uden.copy(excluded = true)))).withoutNumber)
    }

    @Test
    fun fravalgtPåÉnLinjeGælderHeleLøberen() {
        // Samme løber med to SI-brikker står to gange
        val a1 = StartEntry(given = "Anna", family = "A", cls = "D21", bib = "1", card = 1)
        val set = BibBuilder.build(BibProject(), listOf(list(a1, a1.copy(card = 2, excluded = true))))
        assertEquals(0, set.bibs.size)

        // Flerdagesløb: fravalgt i én etape → intet startnummer
        val bo = StartEntry(given = "Bo", family = "B", cls = "H21", bib = "2", card = 7)
        val multi = BibBuilder.build(
            BibProject(type = BibType.MULTIDAY),
            listOf(list(a1, bo), list(a1, bo.copy(excluded = true))),
        )
        assertEquals(listOf("1"), multi.bibs.map { it.number })

        // Stafet: kun den fravalgte tur udelades
        val t1 = StartEntry(given = "Anna", family = "A", bib = "12", teamName = "Hold 1", leg = "1")
        val t2 = StartEntry(given = "Bo", family = "B", bib = "12", teamName = "Hold 1", leg = "2", excluded = true)
        assertEquals(listOf("1"), BibBuilder.build(BibProject(type = BibType.RELAY), listOf(list(t1, t2))).bibs.map { it.leg })
    }
}
