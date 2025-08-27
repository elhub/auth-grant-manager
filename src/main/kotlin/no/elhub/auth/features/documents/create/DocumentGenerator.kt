package no.elhub.auth.features.documents.create

import com.github.mustachejava.DefaultMustacheFactory
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import java.io.ByteArrayOutputStream
import java.io.StringWriter

interface DocumentGenerator {
    fun generateDocument(meteringPointId: String, ssn: String, supplier: String): ByteArray
}

class PdfDocumentGenerator : DocumentGenerator {

    private val mustacheFactory = DefaultMustacheFactory("templates")

    override fun generateDocument(meteringPointId: String, ssn: String, supplier: String): ByteArray {
        // 1) Render HTML → PDF
        val contractHtmlString = StringWriter().apply {
            mustacheFactory
                .compile("contract.mustache")
                .execute(
                    this,
                    mapOf(
                        "ssn" to ssn,
                        "balanceSupplierId" to supplier,
                        "meteringPointId" to meteringPointId
                    )
                ).flush()
        }.toString()

        val pdfBytes = ByteArrayOutputStream().use { out ->
            PdfRendererBuilder()
                .useFastMode()
                .withHtmlContent(contractHtmlString, null)
                .toStream(out)
                .run()
            out.toByteArray()
        }

        // 2) Add metadata
        val pdfWithMeta = ByteArrayOutputStream().use { out ->
            Loader.loadPDF(pdfBytes).use { doc ->
                doc.documentInformation = PDDocumentInformation().apply {
                    setCustomMetadataValue("ssn", ssn)
                }
                doc.save(out)
            }
            out.toByteArray()
        }

        // 3) Sign and return
        println(pdfWithMeta)
        return pdfWithMeta
    }
}
