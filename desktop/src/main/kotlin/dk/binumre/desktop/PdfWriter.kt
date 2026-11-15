package dk.binumre.desktop

import dk.binumre.layout.Align
import dk.binumre.layout.BibCanvas
import dk.binumre.layout.BibFont
import dk.binumre.layout.BibLayout
import dk.binumre.model.Bib
import dk.binumre.model.BibProject
import dk.binumre.model.Sheet
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import java.io.File

private const val PT = 72f / 25.4f

/** Skriver startnumrene til en PDF: to A5 på hvert A4-ark (med skærelinje) eller ét A5 pr. side. */
object PdfWriter {

    /** [graphicsDir]: mappen som grafikfilernes stier er relative til (projektfilens mappe). */
    fun write(bibs: List<Bib>, project: BibProject, out: File, graphicsDir: File? = null) {
        document(bibs, project, graphicsDir).use { it.save(out) }
    }

    /** Dokumentet med startnumrene – til PDF-fil eller direkte udskrift ([Printer]). Kalderen lukker det. */
    fun document(bibs: List<Bib>, project: BibProject, graphicsDir: File? = null): PDDocument {
        val doc = PDDocument()
        try {
            val fonts = mapOf(
                BibFont.REGULAR to loadFont(doc, "DejaVuSans.ttf"),
                BibFont.BOLD to loadFont(doc, "DejaVuSans-Bold.ttf"),
            )
            val images = HashMap<String, PDImageXObject?>()
            fun image(file: String) = images.getOrPut(file) {
                val f = File(file).let { if (it.isAbsolute || graphicsDir == null) it else File(graphicsDir, file) }
                if (f.isFile) PDImageXObject.createFromFileByContent(f, doc) else null
            }
            val perPage = if (project.sheet == Sheet.A4_2UP) 2 else 1
            val size = if (perPage == 2) PDRectangle.A4 else PDRectangle((BibLayout.WIDTH * PT).toFloat(), (BibLayout.HEIGHT * PT).toFloat())
            val zone = BibLayout.zoneOf(project)

            for (group in bibs.chunked(perPage)) {
                val page = PDPage(size).also(doc::addPage)
                PDPageContentStream(doc, page).use { cs ->
                    for ((i, bib) in group.withIndex()) {
                        BibLayout.draw(PdfCanvas(cs, fonts, ::image, size.height, i * BibLayout.HEIGHT), bib, project, zone)
                    }
                    if (perPage == 2) {
                        // Skærelinje midt på arket
                        cs.setStrokingColor(0.6f, 0.6f, 0.6f)
                        cs.setLineWidth(0.3f)
                        cs.setLineDashPattern(floatArrayOf(3f, 3f), 0f)
                        cs.moveTo(0f, size.height / 2)
                        cs.lineTo(size.width, size.height / 2)
                        cs.stroke()
                    }
                }
            }
        } catch (e: Throwable) {
            doc.close()
            throw e
        }
        return doc
    }

    private fun loadFont(doc: PDDocument, name: String): PDFont =
        PdfWriter::class.java.getResourceAsStream("/fonts/$name").use { PDType0Font.load(doc, it!!, true) }
}

/** [BibCanvas] oven på en PDF-side. [top] er startnummerets øverste kant i mm fra sidens top. */
private class PdfCanvas(
    private val cs: PDPageContentStream,
    private val fonts: Map<BibFont, PDFont>,
    private val images: (String) -> PDImageXObject?,
    private val pageHeight: Float,
    private val top: Double,
) : BibCanvas {

    private fun px(x: Double) = (x * PT).toFloat()
    private fun py(y: Double) = pageHeight - ((top + y) * PT).toFloat()
    private fun rgb(c: Int) = floatArrayOf((c shr 16 and 0xFF) / 255f, (c shr 8 and 0xFF) / 255f, (c and 0xFF) / 255f)

    /** Tegn skriften ikke har, erstattes med "?" (ellers afviser PDFBox hele teksten). */
    private fun clean(text: String, font: PDFont): String = buildString {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val s = String(Character.toChars(cp))
            append(if (runCatching { font.encode(s) }.isSuccess) s else "?")
            i += Character.charCount(cp)
        }
    }

    override fun textWidth(text: String, font: BibFont, size: Double): Double {
        val f = fonts.getValue(font)
        return f.getStringWidth(clean(text, f)) / 1000.0 * size
    }

    override fun text(text: String, x: Double, baseline: Double, font: BibFont, size: Double, align: Align, color: Int) {
        val f = fonts.getValue(font)
        val s = clean(text, f)
        val w = f.getStringWidth(s) / 1000.0 * size
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2
            Align.RIGHT -> x - w
        }
        val (r, g, b) = rgb(color)
        cs.setNonStrokingColor(r, g, b)
        cs.beginText()
        cs.setFont(f, (size * PT).toFloat())
        cs.newLineAtOffset(px(left), py(baseline))
        cs.showText(s)
        cs.endText()
    }

    override fun rect(x: Double, y: Double, width: Double, height: Double, color: Int) {
        val (r, g, b) = rgb(color)
        cs.setNonStrokingColor(r, g, b)
        cs.addRect(px(x), py(y + height), px(width), px(height))
        cs.fill()
    }

    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, width: Double, color: Int) {
        val (r, g, b) = rgb(color)
        cs.setStrokingColor(r, g, b)
        cs.setLineWidth(px(width))
        cs.setLineDashPattern(floatArrayOf(), 0f)
        cs.moveTo(px(x1), py(y1))
        cs.lineTo(px(x2), py(y2))
        cs.stroke()
    }

    override fun image(file: String, x: Double, y: Double, width: Double, height: Double) {
        val img = images(file) ?: return
        val scale = minOf(width / img.width, height / img.height)
        val w = img.width * scale
        val h = img.height * scale
        cs.drawImage(img, px(x + (width - w) / 2), py(y + (height - h) / 2 + h), px(w), px(h))
    }

    override fun polygon(xs: DoubleArray, ys: DoubleArray, color: Int) {
        if (xs.isEmpty()) return
        val (r, g, b) = rgb(color)
        cs.setNonStrokingColor(r, g, b)
        cs.moveTo(px(xs[0]), py(ys[0]))
        for (i in 1 until xs.size) cs.lineTo(px(xs[i]), py(ys[i]))
        cs.closePath()
        cs.fill()
    }

    override fun circle(cx: Double, cy: Double, r: Double, width: Double, color: Int, fill: Boolean) {
        val (cr, cg, cb) = rgb(color)
        // Fire Bézier-kurver
        val k = 0.5523 * r
        val x = px(cx)
        val y = py(cy)
        val rr = px(r)
        val kk = px(k)
        cs.moveTo(x + rr, y)
        cs.curveTo(x + rr, y + kk, x + kk, y + rr, x, y + rr)
        cs.curveTo(x - kk, y + rr, x - rr, y + kk, x - rr, y)
        cs.curveTo(x - rr, y - kk, x - kk, y - rr, x, y - rr)
        cs.curveTo(x + kk, y - rr, x + rr, y - kk, x + rr, y)
        cs.closePath()
        if (fill) {
            cs.setNonStrokingColor(cr, cg, cb)
            cs.fill()
        } else {
            cs.setStrokingColor(cr, cg, cb)
            cs.setLineWidth(px(width))
            cs.setLineDashPattern(floatArrayOf(), 0f)
            cs.stroke()
        }
    }

    override fun rotated(degrees: Double, cx: Double, cy: Double, block: () -> Unit) {
        cs.saveGraphicsState()
        // PDF'ens y-akse går opad: med uret på siden er en negativ vinkel
        val x = px(cx)
        val y = py(cy)
        cs.transform(Matrix.getTranslateInstance(x, y))
        cs.transform(Matrix.getRotateInstance(Math.toRadians(-degrees), 0f, 0f))
        cs.transform(Matrix.getTranslateInstance(-x, -y))
        block()
        cs.restoreGraphicsState()
    }
}
