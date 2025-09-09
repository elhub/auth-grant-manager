package no.elhub.auth.features.documents.create

import com.github.mustachejava.DefaultMustacheFactory
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import java.io.ByteArrayOutputStream
import java.io.StringWriter

typealias PdfBytes = ByteArray

object PdfFactory {
    private val mustacheFactory: DefaultMustacheFactory = DefaultMustacheFactory("templates")

    object PdfMetaData {
        internal const val NIN = "NIN"
    }

    fun create(createDocumentCommand: CreateDocumentCommand): PdfBytes {
        val pdfData = createDocumentCommand.toPdfData()
        val htmlString = generateHtmlString(pdfData.template, pdfData.tags)
        return htmlToPdfBytes(htmlString).addMetadata(
            mapOf(PdfMetaData.NIN to pdfData.toBeSignedBy)
        )
    }

    private fun generateHtmlString(template: MustacheTemplateFile, variableTags: VariableTags): String {
        return StringWriter().apply {
            mustacheFactory
                .compile(template.fileName)
                .execute(this, variableTags)
                .flush()
        }.toString()
    }

    private fun htmlToPdfBytes(htmlString: String): ByteArray {
        return ByteArrayOutputStream().use { out ->
            PdfRendererBuilder()
                .withHtmlContent(htmlString, null)
                .toStream(out)
                .run()
            out.toByteArray()
        }
    }

    private fun PdfBytes.addMetadata(
        metadata: Map<String, String>) : PdfBytes {
        return ByteArrayOutputStream().use { out ->
            Loader.loadPDF(this).use { doc ->
                doc.documentInformation = PDDocumentInformation().apply {
                    for (metadataPair in metadata) {
                        setCustomMetadataValue(metadataPair.key, metadataPair.value)
                    }
                }
                doc.save(out)
            }
            out.toByteArray()
        }
    }
}

/**
 * Enum of all available Mustache templates.
 * The fileName has to match the resource file name in the resources/templates folder.
 */
enum class MustacheTemplateFile(val fileName: String) {
    ChangeOfSupplier("change_of_supplier.mustache")
}

/**
 * Represents the input required to render a Mustache template into a PDF.
 *
 * @param template The Mustache template file to use for rendering.
 * @param toBeSignedBy The national identity number of the person who will sign the document.
 * @param tags The variables to be interpolated into the Mustache template.
 *             The keys must exactly match the tag names (e.g. {{customerName}})
 *             defined in the template file, otherwise the placeholders will not be replaced.
 */
typealias VariableTags = Map<String, String>
data class PdfData(
    val template: MustacheTemplateFile,
    val toBeSignedBy: String,
    val tags: VariableTags
)


fun CreateDocumentCommand.toPdfData(): PdfData = when (this) {
    is CreateDocumentCommand.ChangeOfSupplier -> PdfData(
        template = MustacheTemplateFile.ChangeOfSupplier,
        toBeSignedBy = requestedFrom,
        tags = mapOf(
            "customerName" to requestedFromName,
            "nationalIdentityNumber" to requestedFrom,
            "meteringPointAddress" to meteringPointAddress,
            "meteringPointId" to meteringPointId,
            "balanceSupplierName" to requestedBy,
            "balanceSupplierContractName" to balanceSupplierContractName,
        )
    )
}
