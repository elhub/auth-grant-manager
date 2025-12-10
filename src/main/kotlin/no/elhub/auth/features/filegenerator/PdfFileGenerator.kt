package no.elhub.auth.features.filegenerator

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import com.github.mustachejava.DefaultMustacheFactory
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.ChangeOfSupplierBusinessMeta
import no.elhub.auth.features.documents.create.DocumentGenerationError
import no.elhub.auth.features.documents.create.FileGenerator
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringWriter

data class Font(
    val fontBytes: ByteArray,
    val family: String,
    val weight: Int = 400,
    val style: BaseRendererBuilder.FontStyle = BaseRendererBuilder.FontStyle.NORMAL,
)

data class PdfGeneratorConfig(
    val mustacheResourcePath: String,
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

    private fun loadClasspathResource(path: String): ByteArray =
        requireNotNull(object {}.javaClass.getResourceAsStream(path)) { "Missing resource: $path" }
            .readBytes()

    val fonts =
        listOf(
            Font(
                loadClasspathResource("/fonts/liberation-sans/LiberationSans-Regular.ttf"),
                "LiberationSans",
                400,
                BaseRendererBuilder.FontStyle.NORMAL,
            ),
            Font(
                loadClasspathResource("/fonts/liberation-sans/LiberationSans-Bold.ttf"),
                "LiberationSans",
                700,
                BaseRendererBuilder.FontStyle.NORMAL,
            ),
            Font(
                loadClasspathResource("/fonts/liberation-sans/LiberationSans-Italic.ttf"),
                "LiberationSans",
                400,
                BaseRendererBuilder.FontStyle.ITALIC,
            ),
            Font(
                loadClasspathResource("/fonts/liberation-sans/LiberationSans-BoldItalic.ttf"),
                "LiberationSans",
                700,
                BaseRendererBuilder.FontStyle.ITALIC,
            ),
            Font(
                loadClasspathResource("/fonts/libre-bodoni/LibreBodoni-Regular.ttf"),
                "LibreBodoni",
                400,
                BaseRendererBuilder.FontStyle.NORMAL,
            ),
            Font(
                loadClasspathResource("/fonts/libre-bodoni/LibreBodoni-Bold.ttf"),
                "LibreBodoni",
                700,
                BaseRendererBuilder.FontStyle.NORMAL,
            ),
            Font(
                loadClasspathResource("/fonts/libre-bodoni/LibreBodoni-Italic.ttf"),
                "LibreBodoni",
                400,
                BaseRendererBuilder.FontStyle.ITALIC,
            ),
            Font(
                loadClasspathResource("/fonts/libre-bodoni/LibreBodoni-BoldItalic.ttf"),
                "LibreBodoni",
                700,
                BaseRendererBuilder.FontStyle.ITALIC,
            ),
        )

    val colorProfile = loadClasspathResource("/fonts/sRGB.icc")

    object PdfConstants {
        internal const val PDF_METADATA_KEY_NIN = "signerNin"
    }

    override fun generate(
        signerNin: String,
        documentMeta: DocumentMetaMarker
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray> = either {
        val contractHtmlString = when (documentMeta) {
            is ChangeOfSupplierBusinessMeta -> generateChangeOfSupplierHtml(
                customerNin = signerNin,
                customerName = documentMeta.requestedFromName,
                meteringPointAddress = documentMeta.requestedForMeteringPointAddress,
                meteringPointId = documentMeta.requestedForMeteringPointId,
                balanceSupplierName = documentMeta.balanceSupplierName,
                balanceSupplierContractName = documentMeta.balanceSupplierContractName
            )

            else -> return DocumentGenerationError.ContentGenerationError.left()
        }.getOrElse { return DocumentGenerationError.ContentGenerationError.left() }

        val pdfBytes =
            generatePdfFromHtml(contractHtmlString).getOrElse { return DocumentGenerationError.ContentGenerationError.left() }

        return pdfBytes.addMetadataToPdf(
            mapOf(PdfConstants.PDF_METADATA_KEY_NIN to signerNin)
        )
    }

    private fun generateChangeOfSupplierHtml(
        customerNin: String,
        customerName: String,
        meteringPointAddress: String,
        meteringPointId: String,
        balanceSupplierName: String,
        balanceSupplierContractName: String
    ): Either<DocumentGenerationError.ContentGenerationError, String> = Either.catch {
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
    }.mapLeft { DocumentGenerationError.ContentGenerationError }

    private fun generatePdfFromHtml(htmlString: String) = Either.catch {
        ByteArrayOutputStream().use { out ->
            PdfRendererBuilder()
                .withHtmlContent(htmlString, null)
                .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_B)
                .useColorProfile(colorProfile)
                .useFonts(fonts)
                .toStream(out)
                .run()
            out.toByteArray()
        }
    }.mapLeft { DocumentGenerationError.ContentGenerationError }

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
    }.mapLeft { DocumentGenerationError.ContentGenerationError }

    private fun fontSupplier(bytes: ByteArray): FSSupplier<InputStream> =
        FSSupplier { ByteArrayInputStream(bytes) }

    private fun PdfRendererBuilder.useFonts(fonts: List<Font>): PdfRendererBuilder {
        fonts.forEach { font ->
            this.useFont(fontSupplier(font.fontBytes), font.family, font.weight, font.style, true)
        }
        return this
    }
}
