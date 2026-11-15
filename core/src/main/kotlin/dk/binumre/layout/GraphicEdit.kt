package dk.binumre.layout

import dk.binumre.model.Frame
import kotlin.math.abs
import kotlin.math.roundToLong

/** Hvor på en ramme musen (eller fingeren) har fat: hele rammen eller et hjørne. */
enum class Handle { MOVE, NW, NE, SW, SE, ROTATE }

/** Flytning og størrelsesændring af elementer og grafik i editoren – i mm, uafhængigt af brugerfladen. */
object GraphicEdit {
    const val MIN_SIZE = 3.0

    /**
     * Hvad der ligger under punktet: først hjørnerne på den valgte ramme (inden for [tolerance] mm),
     * ellers den mindste ramme der dækker punktet (så små elementer kan gribes oven på store; ved lige store
     * vinder den sidste). Null når punktet rammer ved siden af.
     */
    fun hit(frames: List<Frame>, x: Double, y: Double, selected: Int?, tolerance: Double): Pair<Int, Handle>? {
        frames.getOrNull(selected ?: -1)?.let { f ->
            val (hx, hy) = rotateHandle(f, tolerance)
            if (abs(hx - x) <= tolerance && abs(hy - y) <= tolerance) return selected!! to Handle.ROTATE
            val corners = listOf(
                Handle.NW to (f.x to f.y), Handle.NE to (f.right to f.y),
                Handle.SW to (f.x to f.bottom), Handle.SE to (f.right to f.bottom),
            )
            corners.firstOrNull { (_, p) -> abs(p.first - x) <= tolerance && abs(p.second - y) <= tolerance }
                ?.let { return selected!! to it.first }
        }
        var best = -1
        for ((i, f) in frames.withIndex()) {
            if (!f.contains(x, y)) continue
            if (best < 0 || f.width * f.height <= frames[best].width * frames[best].height) best = i
        }
        return if (best < 0) null else best to Handle.MOVE
    }

    /** Drejehåndtaget: over rammens midte (under, hvis rammen sidder helt oppe ved kanten). */
    fun rotateHandle(f: Frame, tolerance: Double): Pair<Double, Double> {
        val gap = tolerance * 2.5
        return f.centerX to if (f.y - gap >= 0) f.y - gap else f.bottom + gap
    }

    /**
     * Vinklen (grader med uret, 0 = op) fra rammens midte til punktet. Springer på plads ved hele kvarte
     * omgange (inden for 6°), ellers hele grader.
     */
    fun angle(f: Frame, x: Double, y: Double, handleBelow: Boolean = false): Double {
        val raw = Math.toDegrees(kotlin.math.atan2(x - f.centerX, f.centerY - y)) + if (handleBelow) 180.0 else 0.0
        val d = BibLayout.normalized(raw)
        val snap = Math.round(d / 90.0) * 90.0
        return BibLayout.normalized(if (abs(d - snap) <= 6.0) snap else Math.round(d).toDouble())
    }

    /** Rammen efter et træk på ([dx], [dy]) mm fra udgangspunktet [start]. Holdes inden for startnummeret. */
    fun drag(start: Frame, handle: Handle, dx: Double, dy: Double): Frame {
        var left = start.x
        var top = start.y
        var right = start.right
        var bottom = start.bottom
        when (handle) {
            Handle.MOVE -> {
                left = (left + dx).coerceIn(0.0, max0(BibLayout.WIDTH - start.width))
                top = (top + dy).coerceIn(0.0, max0(BibLayout.HEIGHT - start.height))
                right = left + start.width
                bottom = top + start.height
            }
            else -> {
                if (handle == Handle.NW || handle == Handle.SW) left = (left + dx).coerceIn(0.0, max0(right - MIN_SIZE))
                else right = (right + dx).coerceIn(left + MIN_SIZE, maxOf(BibLayout.WIDTH, left + MIN_SIZE))
                if (handle == Handle.NW || handle == Handle.NE) top = (top + dy).coerceIn(0.0, max0(bottom - MIN_SIZE))
                else bottom = (bottom + dy).coerceIn(top + MIN_SIZE, maxOf(BibLayout.HEIGHT, top + MIN_SIZE))
            }
        }
        return Frame(round(left), round(top), round(right - left), round(bottom - top))
    }

    /** Ramme sat via talfelter: holdes inden for startnummeret og mindst [MIN_SIZE]. */
    fun clamp(f: Frame): Frame {
        val w = f.width.coerceIn(MIN_SIZE, BibLayout.WIDTH)
        val h = f.height.coerceIn(MIN_SIZE, BibLayout.HEIGHT)
        return Frame(round(f.x.coerceIn(0.0, BibLayout.WIDTH - w)), round(f.y.coerceIn(0.0, BibLayout.HEIGHT - h)), round(w), round(h))
    }

    /** Ramme til et nyt billede på [pixelWidth] × [pixelHeight]: 40 mm bredt, forskudt lidt for hvert billede der er i forvejen. */
    fun place(pixelWidth: Int, pixelHeight: Int, existing: Int): Frame {
        val width = 40.0
        val height = if (pixelWidth > 0 && pixelHeight > 0) (width * pixelHeight / pixelWidth).coerceIn(MIN_SIZE, 60.0) else 20.0
        val offset = 8.0 + 6.0 * (existing % 12)
        return Frame(offset, offset, width, round(height))
    }

    /**
     * Standardplads efter billedets rolle: løbets logo øverst til venstre, arrangørens logo øverst til højre,
     * sponsorer i rækken mellem SI-nummer og stregkode (lidt forskudt for hver), andet midt på.
     * [stages]: antal etaper (løberens felt i bunden er højere ved flerdagesløb).
     */
    fun placeFor(role: dk.binumre.model.GraphicRole, pixelWidth: Int, pixelHeight: Int, existingOfRole: Int, stages: Int): Frame {
        val ratio = if (pixelWidth > 0 && pixelHeight > 0) pixelWidth.toDouble() / pixelHeight else 2.0
        fun fit(maxW: Double, maxH: Double): Pair<Double, Double> =
            if (maxW / maxH > ratio) round(maxH * ratio) to maxH else maxW to round(maxW / ratio)
        val split = BibLayout.HEIGHT - BibLayout.infoHeight(stages)
        return when (role) {
            dk.binumre.model.GraphicRole.EVENT_LOGO -> fit(34.0, 18.0).let { (w, h) -> Frame(16.0, 1.0, w, h) }
            dk.binumre.model.GraphicRole.CLUB_LOGO -> fit(34.0, 18.0).let { (w, h) -> Frame(round(BibLayout.WIDTH - 16.0 - w), 1.0, w, h) }
            dk.binumre.model.GraphicRole.SPONSOR -> fit(42.0, 12.0).let { (w, h) ->
                clamp(Frame(70.0 + 6.0 * (existingOfRole % 6), split - 14.5 - 4.0 * (existingOfRole % 3), w, h))
            }
            dk.binumre.model.GraphicRole.OTHER -> place(pixelWidth, pixelHeight, existingOfRole)
        }
    }

    private fun max0(v: Double) = maxOf(0.0, v)

    /** Afrunder til 0,1 mm. */
    private fun round(v: Double) = (v * 10).roundToLong() / 10.0
}
