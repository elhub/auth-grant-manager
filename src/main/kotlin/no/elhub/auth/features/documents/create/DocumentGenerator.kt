package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import com.github.mustachejava.DefaultMustacheFactory
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import java.io.ByteArrayOutputStream
import java.io.StringWriter

interface DocumentGenerator {
    fun generate(
        meteringPointId: String,
        ssn: String,
        supplier: String
    ): Either<DocumentGenerationError, ByteArray>
}

sealed class DocumentGenerationError {
    data object ContentGenerationError : DocumentGenerationError()
}

class PdfDocumentGenerator(
    private val mustacheFactory: DefaultMustacheFactory = DefaultMustacheFactory(MustacheConstants.RESOURCES_PATH),
) : DocumentGenerator {

    object MustacheConstants {
        internal const val RESOURCES_PATH = "templates"
        internal const val TEMPLATE_CHANGE_SUPPLIER_CONTRACT = "contract.mustache"
        internal const val VARIABLE_KEY_SSN = "ssn"
        internal const val VARIABLE_KEY_SUPPLIER_ID = "balanceSupplierId"
        internal const val VARIABLE_KEY_METER_ID = "meteringPointId"
    }

    object PdfConstants {
        internal const val PDF_METADATA_KEY_SSN = "ssn"
    }

    override fun generate(
        meteringPointId: String,
        ssn: String,
        supplier: String,
    ): Either<DocumentGenerationError, ByteArray> = either {
        val contractHtmlString =
            generateHtml(
                ssn,
                supplier,
                meteringPointId,
            ).getOrElse {
                return DocumentGenerationError.ContentGenerationError.left()
            }

        val pdfBytes =
            generatePdfFromHtml(contractHtmlString).getOrElse { return DocumentGenerationError.ContentGenerationError.left() }

        return pdfBytes.addMetadataToPdf(
            mapOf(PdfConstants.PDF_METADATA_KEY_SSN to ssn)
        )
    }

    private fun generateHtml(
        ssn: String,
        supplierId: String,
        meteringPointId: String
    ): Either<DocumentGenerationError, String> = Either.catch {
        StringWriter().apply {
            mustacheFactory
                .compile(MustacheConstants.TEMPLATE_CHANGE_SUPPLIER_CONTRACT)
                .execute(
                    this,
                    mapOf(
                        MustacheConstants.VARIABLE_KEY_SSN to ssn,
                        MustacheConstants.VARIABLE_KEY_SUPPLIER_ID to supplierId,
                        MustacheConstants.VARIABLE_KEY_METER_ID to meteringPointId
                    )
                ).flush()
        }.toString()
    }.mapLeft { PdfDocumentGenerationError.HtmlGenerationError }

    private fun generatePdfFromHtml(htmlString: String) = Either.catch {
        ByteArrayOutputStream().use { out ->
            PdfRendererBuilder()
                .useFastMode()
                .withHtmlContent(htmlString, null)
                .toStream(out)
                .run()
            out.toByteArray()
        }
    }.mapLeft { PdfDocumentGenerationError.PdfGenerationError }

    private fun ByteArray.addMetadataToPdf(
        metadata: Map<String, String>
    ) = Either.catch {
        ByteArrayOutputStream().use { out ->
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
    }.mapLeft { PdfDocumentGenerationError.MetadataError }
}

sealed class PdfDocumentGenerationError : DocumentGenerationError() {
    data object HtmlGenerationError : PdfDocumentGenerationError()
    data object PdfGenerationError : PdfDocumentGenerationError()
    data object MetadataError : PdfDocumentGenerationError()
}
