package dk.binumre.layout

import dk.binumre.model.Frame
import kotlin.math.abs
import kotlin.math.roundToLong

/** Hvor på en ramme musen (eller fingeren) har fat: hele rammen eller et hjørne. */
enum class Handle { MOVE, NW, NE, SW, SE }

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

    private fun max0(v: Double) = maxOf(0.0, v)

    /** Afrunder til 0,1 mm. */
    private fun round(v: Double) = (v * 10).roundToLong() / 10.0
}
