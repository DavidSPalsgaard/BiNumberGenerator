package dk.binumre.layout

import dk.binumre.model.Bib
import dk.binumre.model.BibElement
import dk.binumre.model.BibProject
import dk.binumre.model.BibStage
import dk.binumre.model.BibType
import dk.binumre.model.ElementKind
import dk.binumre.model.Frame
import dk.binumre.model.InfoField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Tegner ét startnummer i A5 liggende (210 × 148,5 mm) ud fra projektets elementer
 * ([BibProject.layout]): hvert element tegnes i sin ramme, og teksten skaleres til rammen.
 * Standardlayoutet ([defaultElements]): øverst nummer, navn, klub, SI-nummer og stregkode – nederst,
 * vendt på hovedet så løberen selv kan læse det, starttid, klasse, bane, start og afstand til start.
 */
object BibLayout {
    const val WIDTH = 210.0
    const val HEIGHT = 148.5

    private const val MARGIN = 8.0
    private const val GREY = 0x555555
    /** DejaVu Sans: store bogstavers højde, over- og underlængde i forhold til skriftstørrelsen. */
    private const val CAP = 0.729
    /** Cifrenes højde inkl. runde overhæng (fede cifre er lidt højere end versaler). */
    private const val DIGITS = 0.76
    private const val ASCENT = 0.93
    private const val EM = 1.17
    private const val LEG_BAND = 16.0
    private const val ROW = 7.5
    const val FOLD_SIZE = 14.0
    /** Flerdagesløb: højden af løberens felt med én kasse pr. etape. */
    private const val MULTI_INFO = 30.0

    /** Højden af løberens felt: fast for ét løb, vokser med antal etaper. */
    fun infoHeight(stages: Int): Double = if (stages > 1) MULTI_INFO else 26.0

    /** Standardplaceringen af elementerne for en skabelon. */
    fun defaultElements(type: BibType, stages: Int): List<BibElement> {
        val infoH = infoHeight(stages)
        val split = HEIGHT - infoH
        val relay = type == BibType.RELAY
        val top0 = if (relay) LEG_BAND else 0.0
        val top = top0 + 11.0
        val barcodeTop = split - 15.0
        val clubBase = barcodeTop - 4.0
        val nameBase = clubBase - 10.0
        val numberBottom = nameBase - 11.0
        fun e(kind: ElementKind, x: Double, y: Double, w: Double, h: Double, visible: Boolean = true, rotated: Boolean = false, align: Align = Align.CENTER) =
            BibElement(kind, x, y, w, h, visible, rotated, align)
        val inner = WIDTH - 2 * MARGIN
        return listOfNotNull(
            if (relay) e(ElementKind.LEG_BAND, 0.0, 0.0, WIDTH, LEG_BAND) else null,
            if (relay) e(ElementKind.LEG_STRIPE, 0.0, HEIGHT - 3.0, WIDTH, 3.0) else null,
            e(ElementKind.EVENT_NAME, 30.0, top0 + 2.5, WIDTH - 60.0, 7.6),
            e(ElementKind.CLASS, MARGIN + 6.0, top0 + 2.5, 30.0, 7.6, visible = false, align = Align.LEFT),
            e(ElementKind.START_TIME, WIDTH - MARGIN - 36.0, top0 + 2.5, 30.0, 7.6, visible = false, align = Align.RIGHT),
            e(ElementKind.NUMBER, MARGIN, top + 1.0, inner, numberBottom - top - 2.0),
            e(ElementKind.NAME, MARGIN, nameBase - 10.2, inner, 12.9),
            e(ElementKind.CLUB, MARGIN, clubBase - 7.0, inner, 8.8, visible = !relay),
            e(ElementKind.TEAM, MARGIN, clubBase - 7.0, inner, 8.8, visible = relay),
            e(ElementKind.CARD, MARGIN, split - 12.0, 60.0, 8.2, align = Align.LEFT),
            e(ElementKind.COURSE, 72.0, split - 12.0, 46.0, 8.2, visible = false, align = Align.LEFT),
            e(ElementKind.LEG, WIDTH - MARGIN - 24.0, top0 + 1.0, 24.0, 22.0, visible = false, align = Align.RIGHT),
            e(ElementKind.BARCODE, WIDTH - MARGIN - 80.0, barcodeTop, 80.0, 12.0),
            e(ElementKind.INFO, 0.0, split, WIDTH, infoH, rotated = true),
            e(ElementKind.FOLD, 0.0, 0.0, FOLD_SIZE, FOLD_SIZE),
        )
    }

    fun draw(c: BibCanvas, bib: Bib, project: BibProject, zone: ZoneId = zoneOf(project)) {
        val texts = BibTexts.of(project.language)
        val elements = project.layout().filter { it.visible }
        val relay = project.type == BibType.RELAY && bib.leg.isNotEmpty()
        val legColor = if (relay) project.legColor(bib.leg) else 0
        // Stafet: kun 1. tur har en starttid – de næste starter, når holdkammeraten kommer i mål
        val showStart = !relay || firstLeg(bib.leg)

        // Rækkefølge: farvede flader, grafik, foldehjørner og til sidst tekst – grafik må ligge i turens bånd
        for (e in elements) {
            if (relay && (e.kind == ElementKind.LEG_BAND || e.kind == ElementKind.LEG_STRIPE)) c.rect(e.x, e.y, e.width, e.height, legColor)
        }
        fun background(e: BibElement) = if (e.backgroundFromLeg && relay) legColor else e.background
        for (e in elements) background(e)?.let { c.rect(e.x, e.y, e.width, e.height, it) }
        for (g in project.graphics) c.image(g.file, g.x, g.y, g.width, g.height)
        for (e in elements) if (e.kind == ElementKind.FOLD) {
            val s = min(e.width, e.height)
            for ((x, y) in listOf(0.0 to 0.0, WIDTH - s to 0.0, 0.0 to HEIGHT - s, WIDTH - s to HEIGHT - s)) fold(c, Frame(x, y, s, s))
        }

        for (e in elements) {
            // Med farvet baggrund får teksten lidt luft til kanten
            val bg = background(e)
            val f = if (bg != null && e.kind != ElementKind.INFO) e.frame.inset(e.width * 0.03, e.height * 0.1) else e.frame
            if (e.kind == ElementKind.INFO) {
                c.line(f.x, f.y, f.right, f.y, 0.3)
            }
            val ink = when {
                e.colorFromLeg && relay -> legColor
                e.color != null -> e.color
                bg != null -> contrast(bg)
                else -> 0x000000
            }
            val block: () -> Unit = {
                when (e.kind) {
                    ElementKind.LEG_BAND -> if (!relay) Unit else textIn(c, "${texts.leg} ${bib.leg}", f.inset(0.0, f.height * 0.18), BibFont.BOLD, e.align, e.color ?: contrast(legColor), caps = true)
                    ElementKind.LEG_STRIPE, ElementKind.FOLD -> Unit
                    ElementKind.EVENT_NAME -> textIn(c, project.eventName, f, BibFont.REGULAR, e.align, if (ink == 0x000000 && e.color == null) GREY else ink)
                    ElementKind.NUMBER -> textIn(c, bib.number, f, BibFont.BOLD, e.align, ink, caps = true)
                    ElementKind.NAME -> textIn(c, bib.displayName(project.nameFormat), f, BibFont.BOLD, e.align, ink)
                    ElementKind.CLUB -> textIn(c, bib.club, f, BibFont.REGULAR, e.align, ink)
                    ElementKind.TEAM -> textIn(c, bib.teamName, f, BibFont.REGULAR, e.align, ink)
                    ElementKind.LEG -> textIn(c, bib.leg, f, BibFont.BOLD, e.align, ink, caps = true)
                    ElementKind.CARD -> bib.card?.let { textIn(c, "SI $it", f, BibFont.REGULAR, e.align, ink) }
                    ElementKind.CLASS -> textIn(c, bib.cls, f, BibFont.BOLD, e.align, ink)
                    ElementKind.START_TIME -> bib.stages.firstOrNull { it != null }?.takeIf { showStart }?.let { textIn(c, time(it.start, zone, texts), f, BibFont.BOLD, e.align, ink) }
                    ElementKind.COURSE -> bib.stages.firstOrNull { it != null }?.let { textIn(c, course(it, project.language), f, BibFont.REGULAR, e.align, ink) }
                    ElementKind.BARCODE -> barcode(c, barcodeText(bib), f, e.align, ink)
                    ElementKind.INFO -> info(c, bib, project, texts, zone, f, showStart)
                }
                Unit
            }
            if (e.rotated) c.rotated180(f.x + f.width / 2, f.y + f.height / 2, block) else block()
        }
    }

    private fun Frame.inset(dx: Double, dy: Double) = Frame(x + dx, y + dy, max(0.1, width - 2 * dx), max(0.1, height - 2 * dy))

    /**
     * Teksten så stor som rammen tillader. [caps]: kun store bogstaver/cifre (startnummeret) – rammen fyldes
     * af versalhøjden; ellers er der plads til over- og underlængder.
     */
    private fun textIn(c: BibCanvas, text: String, f: Frame, font: BibFont, align: Align, color: Int = 0x000000, caps: Boolean = false) {
        if (text.isEmpty() || f.width <= 0 || f.height <= 0) return
        val size = fitSize(c, text, font, if (caps) f.height / DIGITS else f.height / EM, f.width)
        val baseline = if (caps) f.y + (f.height + size * CAP) / 2 else f.y + (f.height - size * EM) / 2 + size * ASCENT
        val x = when (align) {
            Align.LEFT -> f.x
            Align.CENTER -> f.x + f.width / 2
            Align.RIGHT -> f.right
        }
        c.text(text, x, baseline, font, size, align, color)
    }

    private fun course(s: BibStage, language: String): String = listOfNotNull(
        s.course.ifEmpty { null },
        s.courseLength?.let { String.format(Locale.forLanguageTag(language), "%.1f km", it / 1000.0) },
    ).joinToString(" · ")

    /** Løberens felt – tegnes retvendt her; [BibElement.rotated] vender det. Mål skaleres med rammen. */
    private fun info(c: BibCanvas, bib: Bib, project: BibProject, t: BibTexts, zone: ZoneId, f: Frame, showStart: Boolean = true) {
        val fields = InfoField.entries.filter { it in project.infoFields && (showStart || it != InfoField.START_TIME) }
        val sx = f.width / WIDTH
        val k = { sy: Double -> min(sx, sy) }
        val left = f.x + MARGIN * sx
        val inner = f.width - 2 * MARGIN * sx

        if (bib.stages.size <= 1) {
            val s = bib.stages.firstOrNull() ?: return
            val stage = project.stage(0)
            val sy = f.height / infoHeight(1)
            val base = mapOf(
                InfoField.START_TIME to Triple(t.startTime, time(s.start, zone, t), 46.0),
                InfoField.CLASS to Triple(t.cls, s.cls, 36.0),
                InfoField.COURSE to Triple(t.course, course(s, project.language), 44.0),
                InfoField.START_NAME to Triple(t.start, s.startName, 30.0),
                InfoField.DISTANCE to Triple(t.distance, stage.distance(s.startName), 38.0),
            )
            val cols = fields.map { base.getValue(it) }
            val total = cols.sumOf { it.third }
            var x = left
            for ((field, col) in fields.zip(cols)) {
                val (label, value, w0) = col
                val w = inner * w0 / total
                if (value.isNotEmpty()) fit(c, label, x, f.y + 8.0 * sy, BibFont.REGULAR, 3.8 * k(sy), w - 3.0 * sx, Align.LEFT, GREY)
                fit(c, value, x, f.y + 20.0 * sy, BibFont.BOLD, (if (field == InfoField.START_TIME) 12.0 else 9.0) * k(sy), w - 3.0 * sx, Align.LEFT)
                x += w
            }
            return
        }

        // Flerdagesløb: én kasse pr. etape side om side (som på 5 Days) – etape, starttid, klasse/bane, start/afstand
        val n = bib.stages.size
        val sy = f.height / MULTI_INFO
        val gap = 2.5 * sx
        // Lidt længere fra kanten end resten, så foldehjørnerne ikke rammer kasserne
        val side = 13.0 * sx
        val boxW = (f.width - 2 * side - gap * (n - 1)) / n
        val top = f.y + 2.5 * sy
        val boxH = f.height - 5.0 * sy
        val pad = 1.8 * k(sy)
        for ((i, s) in bib.stages.withIndex()) {
            val x = f.x + side + i * (boxW + gap)
            // Tynd ramme om kassen
            c.line(x, top, x + boxW, top, 0.2, GREY)
            c.line(x, top + boxH, x + boxW, top + boxH, 0.2, GREY)
            c.line(x, top, x, top + boxH, 0.2, GREY)
            c.line(x + boxW, top, x + boxW, top + boxH, 0.2, GREY)
            val stage = project.stage(i)
            val w = boxW - 2 * pad
            val tx = x + pad
            fun line(text: String, baseline: Double, font: BibFont, size: Double, color: Int = 0x000000) =
                fit(c, text, tx, top + baseline * sy, font, size * k(sy), w, Align.LEFT, color)
            line(stage.label.ifEmpty { "${t.stage} ${i + 1}" }, 5.0, BibFont.BOLD, 3.6, GREY)
            if (s == null) {
                line("–", 13.0, BibFont.BOLD, 7.0)
                continue
            }
            if (InfoField.START_TIME in fields) line(time(s.start, zone, t), 12.5, BibFont.BOLD, 7.0)
            val second = listOfNotNull(
                s.cls.takeIf { InfoField.CLASS in fields && it.isNotEmpty() },
                course(s, project.language).takeIf { InfoField.COURSE in fields && it.isNotEmpty() },
            ).joinToString(" · ")
            line(second, 17.8, BibFont.REGULAR, 3.6)
            val third = listOfNotNull(
                s.startName.takeIf { InfoField.START_NAME in fields && it.isNotEmpty() }?.let { "${t.start} $it" },
                stage.distance(s.startName).takeIf { InfoField.DISTANCE in fields && it.isNotEmpty() },
            ).joinToString(" · ")
            line(third, 22.6, BibFont.REGULAR, 3.6)
        }
    }

    /**
     * Hjørnet der skal foldes, før sikkerhedsnålen sættes i: gråt trekantet felt med stiplet foldelinje
     * og en lille sikkerhedsnål. Retningen følger det hjørne af startnummeret rammen ligger nærmest.
     */
    private fun fold(c: BibCanvas, f: Frame) {
        val s = min(f.width, f.height)
        if (s <= 0) return
        val left = f.x + f.width / 2 < WIDTH / 2
        val top = f.y + f.height / 2 < HEIGHT / 2
        val cx = if (left) f.x else f.right
        val cy = if (top) f.y else f.bottom
        val dx = if (left) 1.0 else -1.0
        val dy = if (top) 1.0 else -1.0
        // Lokale koordinater (u ind langs den vandrette kant, v ind langs den lodrette) → startnummeret
        fun px(u: Double) = cx + dx * u
        fun py(v: Double) = cy + dy * v

        c.polygon(doubleArrayOf(px(0.0), px(s), px(0.0)), doubleArrayOf(py(0.0), py(0.0), py(s)), 0xE6E6E6)
        // Stiplet foldelinje
        val dashes = 7
        for (i in 0 until dashes) {
            val a = i / dashes.toDouble()
            val b = (i + 0.55) / dashes
            c.line(px(s * (1 - a)), py(s * a), px(s * (1 - b)), py(s * b), 0.25, 0x777777)
        }
        // Sikkerhedsnål parallelt med foldelinjen, midt i trekanten
        val r2 = sqrt(2.0)
        val (ux, uy) = 1 / r2 to -1 / r2
        val (nx, ny) = 1 / r2 to 1 / r2
        val m = s * 0.3
        val half = s * 0.27
        val gap = s * 0.075
        val color = 0x444444
        val w = max(0.15, s * 0.022)
        fun p(along: Double, across: Double) = px(m + ux * along + nx * across) to py(m + uy * along + ny * across)
        val (ax, ay) = p(-half, 0.0)
        val (bx, by) = p(half, 0.0)
        c.line(ax, ay, bx, by, w, color)
        val (a2x, a2y) = p(-half, gap)
        val (b2x, b2y) = p(half * 0.7, gap)
        c.line(a2x, a2y, b2x, b2y, w, color)
        val (ox, oy) = p(-half - gap * 0.5, gap * 0.5)
        c.circle(ox, oy, gap * 0.75, w, color)
        // Lukkehætten
        val head = listOf(p(half * 0.62, -gap * 0.4), p(half * 1.08, -gap * 0.4), p(half * 1.08, gap * 1.4), p(half * 0.62, gap * 1.4))
        c.polygon(head.map { it.first }.toDoubleArray(), head.map { it.second }.toDoubleArray(), color)
    }

    /** Stregkodens indhold: nummeret – ved stafet "nummer-tur" (som 10MILA's "568-4"). */
    fun barcodeText(bib: Bib): String = if (bib.leg.isEmpty()) bib.number else "${bib.number}-${bib.leg}"

    /** Stregkode med koden i læsbar tekst nedenunder, når rammen er høj nok. */
    private fun barcode(c: BibCanvas, text: String, frame: Frame, align: Align, ink: Int) {
        if (text.isEmpty()) return
        val caption = frame.height >= 9.0
        val f = if (caption) frame.copy(height = frame.height * 0.74) else frame
        val modules = Code128.modules(text)
        // Højst 0,5 mm pr. modul – bredere stregkoder læses ikke bedre
        val unit = min(0.5, f.width / modules.size)
        val width = modules.size * unit
        val left = when (align) {
            Align.LEFT -> f.x
            Align.CENTER -> f.x + (f.width - width) / 2
            Align.RIGHT -> f.right - width
        }
        var i = 0
        while (i < modules.size) {
            if (!modules[i]) { i++; continue }
            var j = i
            while (j < modules.size && modules[j]) j++
            c.rect(left + i * unit, f.y, (j - i) * unit, f.height, ink)
            i = j
        }
        if (caption) {
            val box = Frame(left, f.bottom + frame.height * 0.04, width, frame.height * 0.22)
            textIn(c, text, box, BibFont.REGULAR, Align.CENTER, ink)
        }
    }

    /** Første tur ("1", "01", "1A" …) – eller ingen tur. */
    fun firstLeg(leg: String): Boolean = leg.filter { it.isDigit() }.toIntOrNull()?.let { it <= 1 } ?: true

    fun zoneOf(project: BibProject): ZoneId =
        project.zone.ifEmpty { null }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    /** "11:41", eller "11:41:30" når starttiden ikke er på et helt minut. Uden starttid: "Fri start". */
    fun time(start: Long?, zone: ZoneId, texts: BibTexts): String {
        if (start == null) return texts.freeStart
        val t = Instant.ofEpochMilli(start).atZone(zone)
        return DateTimeFormatter.ofPattern(if (t.second == 0) "HH:mm" else "HH:mm:ss").format(t)
    }

    /** Sort tekst på lyse farver, hvid på mørke. */
    fun contrast(color: Int): Int {
        val lum = 0.299 * (color shr 16 and 0xFF) + 0.587 * (color shr 8 and 0xFF) + 0.114 * (color and 0xFF)
        return if (lum > 150) 0x000000 else 0xFFFFFF
    }

    private fun fitSize(c: BibCanvas, text: String, font: BibFont, size: Double, maxWidth: Double): Double {
        val w = c.textWidth(text, font, size)
        return if (w <= maxWidth || w == 0.0) size else size * maxWidth / w
    }

    /** Skriver teksten og gør den mindre, hvis den er bredere end [maxWidth]. */
    private fun fit(c: BibCanvas, text: String, x: Double, baseline: Double, font: BibFont, size: Double, maxWidth: Double, align: Align, color: Int = 0x000000) {
        if (text.isEmpty() || maxWidth <= 0) return
        c.text(text, x, baseline, font, fitSize(c, text, font, size, maxWidth), align, color)
    }
}
