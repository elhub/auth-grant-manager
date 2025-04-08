package no.elhub.auth.utils

import com.lowagie.text.Document
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream

object PdfGenerator {

    fun createChangeOfSupplierConfirmationPdf(
        snn: String,
        supplier: String
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val document = Document()
        PdfWriter.getInstance(document, outputStream)
        document.open()
        document.add(Paragraph("$snn is consenting to change supplier to $supplier."))
        document.close()
        return outputStream.toByteArray()
    }
}
