package dk.binumre.desktop

import dk.binumre.model.Bib
import dk.binumre.model.BibProject
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.printing.PDFPageable
import java.awt.print.PrinterJob
import javax.print.PrintService

/**
 * Udskrift direkte på en printer – samme sider som PDF'en ([PdfWriter.document]), uden at gemme en fil.
 * [prepare] viser styresystemets printerdialog (på brugerfladens tråd); [Job.print] sender siderne (gerne
 * på en baggrundstråd).
 */
object Printer {

    class Job internal constructor(private val job: PrinterJob, private val doc: PDDocument) {
        val pages: Int get() = doc.numberOfPages
        val printerName: String get() = job.printService?.name.orEmpty()

        fun print() {
            doc.use { job.print() }
        }

        fun cancel() = doc.close()
    }

    /** Om computeren har en printer (på Windows altid mindst "Microsoft Print to PDF"). */
    fun available(): Boolean = javax.print.PrintServiceLookup.lookupPrintServices(null, null).isNotEmpty()

    /** Null når brugeren fortryder i printerdialogen. [service]: printeren uden dialog (bruges af tests). */
    fun prepare(bibs: List<Bib>, project: BibProject, jobName: String, service: PrintService? = null): Job? {
        val doc = PdfWriter.document(bibs, project)
        val job = PrinterJob.getPrinterJob()
        job.jobName = jobName
        job.setPageable(PDFPageable(doc))
        val ok = try {
            if (service != null) {
                job.printService = service
                true
            } else job.printDialog()
        } catch (e: Throwable) {
            doc.close()
            throw e
        }
        if (!ok) {
            doc.close()
            return null
        }
        return Job(job, doc)
    }
}
