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

interface FileGenerator {
    fun generate(
        customerNin: String,
        customerName: String,
        meteringPointAddress: String,
        meteringPointId: String,
        balanceSupplierName: String,
        balanceSupplierContractName: String
    ): Either<DocumentGenerationError, ByteArray>
}

sealed class DocumentGenerationError {
    data object ContentGenerationError : DocumentGenerationError()
}

data class PdfGeneratorConfig(
    val mustacheResourcePath: String
)

class PdfGenerator(
    cfg: PdfGeneratorConfig,
) : FileGenerator {

    private val mustacheFactory: DefaultMustacheFactory = DefaultMustacheFactory(cfg.mustacheResourcePath)

    object MustacheConstants {
        internal const val TEMPLATE_CHANGE_SUPPLIER_CONTRACT = "change_of_supplier.mustache"
        internal const val VARIABLE_KEY_CUSTOMER_NIN = "customerNin"
        internal const val VARIABLE_KEY_CUSTOMER_NAME = "customerName"
        internal const val VARIABLE_KEY_METERING_POINT_ADDRESS = "meteringPointAddress"
        internal const val VARIABLE_KEY_METERING_POINT_ID = "meteringPointId"
        internal const val VARIABLE_KEY_BALANCE_SUPPLIER_NAME = "balanceSupplierName"
        internal const val VARIABLE_KEY_BALANCE_SUPPLIER_CONTRACT_NAME = "balanceSupplierContractName"
    }

    object PdfConstants {
        internal const val PDF_METADATA_KEY_NIN = "signerNin"
    }

    override fun generate(
        customerNin: String,
        customerName: String,
        meteringPointAddress: String,
        meteringPointId: String,
        balanceSupplierName: String,
        balanceSupplierContractName: String
    ): Either<DocumentGenerationError, ByteArray> = either {
        val contractHtmlString =
            generateHtml(
                customerNin,
                customerName,
                meteringPointAddress,
                meteringPointId,
                balanceSupplierName,
                balanceSupplierContractName
            ).getOrElse {
                return DocumentGenerationError.ContentGenerationError.left()
            }

        val pdfBytes =
            generatePdfFromHtml(contractHtmlString).getOrElse { return DocumentGenerationError.ContentGenerationError.left() }

        return pdfBytes.addMetadataToPdf(
            mapOf(PdfConstants.PDF_METADATA_KEY_NIN to customerNin)
        )
    }

    private fun generateHtml(
        customerNin: String,
        customerName: String,
        meteringPointAddress: String,
        meteringPointId: String,
        balanceSupplierName: String,
        balanceSupplierContractName: String
    ): Either<DocumentGenerationError, String> = Either.catch {
        StringWriter().apply {
            mustacheFactory
                .compile(MustacheConstants.TEMPLATE_CHANGE_SUPPLIER_CONTRACT)
                .execute(
                    this,
                    mapOf(
                        MustacheConstants.VARIABLE_KEY_CUSTOMER_NIN to customerNin,
                        MustacheConstants.VARIABLE_KEY_CUSTOMER_NAME to customerName,
                        MustacheConstants.VARIABLE_KEY_METERING_POINT_ID to meteringPointId,
                        MustacheConstants.VARIABLE_KEY_METERING_POINT_ADDRESS to meteringPointAddress,
                        MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_NAME to balanceSupplierName,
                        MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_CONTRACT_NAME to balanceSupplierContractName
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
