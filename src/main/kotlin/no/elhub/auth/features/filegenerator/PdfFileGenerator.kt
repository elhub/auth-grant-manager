package no.elhub.auth.features.filegenerator

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.TemplateFunction
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain.ChangeOfEnergySupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.domain.MoveInAndChangeOfEnergySupplierBusinessMeta
import no.elhub.auth.features.documents.create.DocumentGenerationError
import no.elhub.auth.features.documents.create.FileGenerator
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringWriter
import java.util.Locale
import java.util.ResourceBundle
import kotlin.math.PI

data class Font(
    val fontBytes: ByteArray,
    val family: String,
    val weight: Int = 400,
    val style: BaseRendererBuilder.FontStyle = BaseRendererBuilder.FontStyle.NORMAL,
)

data class PdfGeneratorConfig(
    val mustacheResourcePath: String,
    val useTestPdfNotice: Boolean,
)

class PdfGenerator(
    cfg: PdfGeneratorConfig,
) : FileGenerator {
    private val mustacheFactory: DefaultMustacheFactory = DefaultMustacheFactory(cfg.mustacheResourcePath)
    private val useTestPdfNotice = cfg.useTestPdfNotice

    object MustacheConstants {
        internal const val TEMPLATE_CHANGE_SUPPLIER_CONTRACT = "change_of_supplier.mustache"
        internal const val TEMPLATE_MOVE_IN = "move_in.mustache"
        internal const val VARIABLE_KEY_CUSTOMER_NIN = "customerNin"
        internal const val VARIABLE_KEY_CUSTOMER_NAME = "customerName"
        internal const val VARIABLE_KEY_METERING_POINT_ADDRESS = "meteringPointAddress"
        internal const val VARIABLE_KEY_METERING_POINT_ID = "meteringPointId"
        internal const val VARIABLE_KEY_BALANCE_SUPPLIER_NAME = "balanceSupplierName"
        internal const val VARIABLE_KEY_BALANCE_SUPPLIER_CONTRACT_NAME = "balanceSupplierContractName"
        internal const val VARIABLE_KEY_MOVE_IN_DATE = "moveInDate"
        internal const val VARIABLE_KEY_HTML_LANG = "htmlLang"
        internal const val VARIABLE_KEY_I18N = "i18n"
    }

    private fun loadClasspathResource(path: String): ByteArray =
        requireNotNull(object {}.javaClass.getResourceAsStream(path)) { "Missing resource: $path" }
            .readBytes()

    val fonts =
        listOf(
            Font(
                loadClasspathResource("/fonts/roboto/Roboto-Regular.ttf"),
                "Roboto",
                400,
                BaseRendererBuilder.FontStyle.NORMAL,
            ),
            Font(
                loadClasspathResource("/fonts/roboto/Roboto-Medium.ttf"),
                "Roboto",
                500,
                BaseRendererBuilder.FontStyle.NORMAL,
            ),
            Font(
                loadClasspathResource("/fonts/roboto/Roboto-Bold.ttf"),
                "Roboto",
                700,
                BaseRendererBuilder.FontStyle.NORMAL,
            ),
        )

    val colorProfile = loadClasspathResource("/fonts/sRGB.icc")

    object PdfConstants {
        internal const val PDF_METADATA_KEY_NIN = "signerNin"
        internal const val PDF_METADATA_KEY_TESTDOCUMENT = "testDocument"
    }

    override fun generate(
        signerNin: String,
        documentMeta: DocumentMetaMarker,
        language: SupportedLanguage,
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray> = either {
        val contractHtmlString = when (documentMeta) {
            is ChangeOfEnergySupplierBusinessMeta -> generateChangeOfEnergySupplierHtml(
                customerNin = signerNin,
                customerName = documentMeta.requestedFromName,
                meteringPointAddress = documentMeta.requestedForMeteringPointAddress,
                meteringPointId = documentMeta.requestedForMeteringPointId,
                balanceSupplierName = documentMeta.balanceSupplierName,
                balanceSupplierContractName = documentMeta.balanceSupplierContractName,
                language = language
            )

            is MoveInAndChangeOfEnergySupplierBusinessMeta -> generateMoveInAndChangeOfEnergySupplierHtml(
                customerName = documentMeta.requestedFromName,
                meteringPointAddress = documentMeta.requestedForMeteringPointAddress,
                meteringPointId = documentMeta.requestedForMeteringPointId,
                balanceSupplierName = documentMeta.balanceSupplierName,
                balanceSupplierContractName = documentMeta.balanceSupplierContractName,
                startDate = documentMeta.startDate?.let { formatNorwegianDate(it.year, it.monthNumber, it.dayOfMonth) },
                language = language
            )

            else -> return DocumentGenerationError.ContentGenerationError.left()
        }.getOrElse { return DocumentGenerationError.ContentGenerationError.left() }

        val pdfBytes =
            generatePdfFromHtml(contractHtmlString).getOrElse { return DocumentGenerationError.ContentGenerationError.left() }

        if (useTestPdfNotice) {
            return pdfBytes.addTestWatermark().addMetadataToPdf(
                mapOf(
                    PdfConstants.PDF_METADATA_KEY_NIN to signerNin,
                    PdfConstants.PDF_METADATA_KEY_TESTDOCUMENT to "true"
                )
            )
        }
        return pdfBytes.addMetadataToPdf(
            mapOf(PdfConstants.PDF_METADATA_KEY_NIN to signerNin)
        )
    }

    private fun generateChangeOfEnergySupplierHtml(
        customerNin: String,
        customerName: String,
        meteringPointAddress: String,
        meteringPointId: String,
        balanceSupplierName: String,
        balanceSupplierContractName: String,
        language: SupportedLanguage,
    ): Either<DocumentGenerationError.ContentGenerationError, String> = Either.catch {
        val i18n = i18nTemplateFunction(language)
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
                        MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_CONTRACT_NAME to balanceSupplierContractName,
                        MustacheConstants.VARIABLE_KEY_HTML_LANG to language.code,
                        MustacheConstants.VARIABLE_KEY_I18N to i18n,
                    )
                ).flush()
        }.toString()
    }.mapLeft { DocumentGenerationError.ContentGenerationError }

    private fun generateMoveInAndChangeOfEnergySupplierHtml(
        customerName: String,
        meteringPointAddress: String,
        meteringPointId: String,
        balanceSupplierName: String,
        balanceSupplierContractName: String,
        startDate: String?,
        language: SupportedLanguage,
    ): Either<DocumentGenerationError.ContentGenerationError, String> = Either.catch {
        val i18n = i18nTemplateFunction(language)
        StringWriter().apply {
            mustacheFactory
                .compile(MustacheConstants.TEMPLATE_MOVE_IN)
                .execute(
                    this,
                    mapOf(
                        MustacheConstants.VARIABLE_KEY_CUSTOMER_NAME to customerName,
                        MustacheConstants.VARIABLE_KEY_METERING_POINT_ID to meteringPointId,
                        MustacheConstants.VARIABLE_KEY_METERING_POINT_ADDRESS to meteringPointAddress,
                        MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_NAME to balanceSupplierName,
                        MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_CONTRACT_NAME to balanceSupplierContractName,
                        MustacheConstants.VARIABLE_KEY_HTML_LANG to language.code,
                        MustacheConstants.VARIABLE_KEY_I18N to i18n,
                    )
                        .let { base ->
                            if (startDate == null) {
                                base
                            } else {
                                base + (MustacheConstants.VARIABLE_KEY_MOVE_IN_DATE to startDate)
                            }
                        }
                ).flush()
        }.toString()
    }.mapLeft { DocumentGenerationError.ContentGenerationError }

    private fun formatNorwegianDate(year: Int, month: Int, day: Int): String =
        String.format(Locale.ROOT, "%02d.%02d.%04d", day, month, year)

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

    private fun i18nTemplateFunction(language: SupportedLanguage): TemplateFunction {
        val bundle = ResourceBundle.getBundle("templates.i18n.messages", Locale.forLanguageTag(language.code))
        return TemplateFunction { key ->
            val normalizedKey = key.trim()
            if (bundle.containsKey(normalizedKey)) {
                bundle.getString(normalizedKey)
            } else {
                normalizedKey
            }
        }
    }

    private fun PdfRendererBuilder.useFonts(fonts: List<Font>): PdfRendererBuilder {
        fonts.forEach { font ->
            this.useFont(fontSupplier(font.fontBytes), font.family, font.weight, font.style, true)
        }
        return this
    }

    private fun ByteArray.addTestWatermark(): ByteArray {
        val document = Loader.loadPDF(this)

        val graphicsState = PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = 0.4f
        }

        val text = "TESTDOKUMENT - IKKE JURIDISK BINDENDE"
        document.pages.forEach { page ->
            val box = page.mediaBox
            val centerX = box.width / 2
            val centerY = box.height / 2

            PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { content ->
                content.setGraphicsStateParameters(graphicsState)
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20f)
                content.setNonStrokingColor(1f, 0f, 0f)
                content.beginText()
                content.setTextMatrix(
                    Matrix.getRotateInstance(
                        PI / 6, // 30°
                        centerX - 200,
                        centerY
                    )
                )
                content.showText(text)
                content.endText()
            }
        }

        return ByteArrayOutputStream().use { out ->
            document.save(out)
            document.close()
            out.toByteArray()
        }
    }
}
