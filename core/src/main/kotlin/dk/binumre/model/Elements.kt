package dk.binumre.model

import dk.binumre.layout.Align

/** De faste elementer på startnummeret. Hvert element har sin egen ramme og kan flyttes, skaleres og slås fra. */
enum class ElementKind {
    /** Stafet: farvet bånd med "Tur n" og en tynd stribe i samme farve. */
    LEG_BAND, LEG_STRIPE,
    EVENT_NAME, NUMBER, NAME, CLUB, TEAM, CARD, BARCODE, CLASS, START_TIME, COURSE,
    /** Stafet: turens nummer alene (stort, fx øverst i hjørnet). */
    LEG,
    /** Feltet løberen selv læser (som standard vendt på hovedet): starttid, klasse, bane, start, afstand. */
    INFO,
    /** Hjørnerne der skal foldes, før sikkerhedsnålen sættes i – ét element for alle fire hjørner (rammens størrelse). */
    FOLD,
}

/** Felterne i [ElementKind.INFO]. */
enum class InfoField { START_TIME, CLASS, COURSE, START_NAME, DISTANCE }

/** En ramme i mm fra startnummerets øverste venstre hjørne. */
data class Frame(val x: Double, val y: Double, val width: Double, val height: Double) {
    val right get() = x + width
    val bottom get() = y + height
    fun contains(px: Double, py: Double) = px >= x && px <= right && py >= y && py <= bottom
}

data class BibElement(
    val kind: ElementKind,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val visible: Boolean = true,
    /** Tegnes vendt på hovedet (så løberen selv kan læse det). */
    val rotated: Boolean = false,
    val align: Align = Align.CENTER,
    /** Tekstens farve (0xRRGGBB); null = elementets standard. */
    val color: Int? = null,
    /** Udfyldt baggrund bag elementet (fx farvet felt bag nummeret); null = ingen. */
    val background: Int? = null,
    /** Stafet: teksten får turens farve (i stedet for [color]). */
    val colorFromLeg: Boolean = false,
    /** Stafet: baggrunden får turens farve (i stedet for [background]); teksten bliver sort eller hvid. */
    val backgroundFromLeg: Boolean = false,
) {
    val frame get() = Frame(x, y, width, height)
    fun moved(f: Frame) = copy(x = f.x, y = f.y, width = f.width, height = f.height)
}
