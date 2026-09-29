package no.elhub.auth.common.documents.pdf

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.TemplateFunction
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kotlinx.datetime.number
import kotlinx.datetime.toJavaLocalDate
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
import java.time.format.DateTimeFormatter
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

class MustachePdfGenerator(
    cfg: PdfGeneratorConfig,
) : PdfGenerator {
    private val mustacheFactory: DefaultMustacheFactory = DefaultMustacheFactory(cfg.mustacheResourcePath)
    private val useTestPdfNotice = cfg.useTestPdfNotice

    object MustacheConstants {
        internal const val TEMPLATE_CHANGE_SUPPLIER_CONTRACT =
            "businessprocesses/changeofbalancesupplier/change_of_supplier.mustache"
        internal const val TEMPLATE_MOVE_IN =
            "businessprocesses/moveinandchangeofbalancesupplier/move_in.mustache"
        internal const val TEMPLATE_FRAMEWORK_AGREEMENT =
            "businessprocesses/frameworkagreement/framework_agreement.mustache"
        internal const val I18N_CHANGE_OF_SUPPLIER =
            "templates.businessprocesses.changeofbalancesupplier.i18n.messages"
        internal const val I18N_MOVE_IN =
            "templates.businessprocesses.moveinandchangeofbalancesupplier.i18n.messages"
        internal const val I18N_FRAMEWORK_AGREEMENT =
            "templates.businessprocesses.frameworkagreement.i18n.messages"
        internal const val I18N_COMMON = "templates.i18n.common.messages"
        internal const val VARIABLE_KEY_CUSTOMER_NAME = "customerName"
        internal const val VARIABLE_KEY_METERING_POINT_ADDRESS = "meteringPointAddress"
        internal const val VARIABLE_KEY_METERING_POINT_ID = "meteringPointId"
        internal const val VARIABLE_KEY_METER_NUMBER = "meterNumber"
        internal const val VARIABLE_KEY_BALANCE_SUPPLIER_NAME = "balanceSupplierName"
        internal const val VARIABLE_KEY_BALANCE_SUPPLIER_CONTRACT_NAME = "balanceSupplierContractName"
        internal const val VARIABLE_KEY_MOVE_IN_DATE = "moveInDate"
        internal const val VARIABLE_KEY_ORGANIZATION_NAME = "organizationName"
        internal const val VARIABLE_KEY_ORGANIZATION_NUMBER = "organizationNumber"
        internal const val VARIABLE_KEY_CONTRACT_REFERENCE = "contractReference"
        internal const val VARIABLE_KEY_START_DATE = "startDate"
        internal const val VARIABLE_KEY_END_DATE = "endDate"
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
        internal const val PDF_METADATA_KEY_TESTDOCUMENT = "testDocument"
    }

    override fun generate(content: AuthorizationDocumentPdfContent): ByteArray =
        try {
            val contractHtmlString = when (content) {
                is AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier -> generateChangeOfBalanceSupplierHtml(content)
                is AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplier -> generateMoveInAndChangeOfBalanceSupplierHtml(content)
                is AuthorizationDocumentPdfContent.FrameworkAgreement -> generateFrameworkAgreementHtml(content)
            }
            val pdfBytes = generatePdfFromHtml(contractHtmlString)
            if (useTestPdfNotice) {
                pdfBytes.addTestWatermark().addMetadataToPdf(
                    language = content.language,
                    customMetadata = mapOf(PdfConstants.PDF_METADATA_KEY_TESTDOCUMENT to "true")
                )
            } else {
                pdfBytes.addMetadataToPdf(language = content.language)
            }
        } catch (error: Exception) {
            throw PdfGenerationException(error)
        }

    private fun generateChangeOfBalanceSupplierHtml(content: AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier): String {
        val i18n = i18nTemplateFunction(content.language, MustacheConstants.I18N_CHANGE_OF_SUPPLIER)
        return StringWriter().apply {
            mustacheFactory
                .compile(MustacheConstants.TEMPLATE_CHANGE_SUPPLIER_CONTRACT)
                .execute(
                    this,
                    mapOf(
                        MustacheConstants.VARIABLE_KEY_CUSTOMER_NAME to content.customerName,
                        MustacheConstants.VARIABLE_KEY_METERING_POINT_ID to content.meteringPointId,
                        MustacheConstants.VARIABLE_KEY_METER_NUMBER to content.meterNumber,
                        MustacheConstants.VARIABLE_KEY_METERING_POINT_ADDRESS to content.meteringPointAddress,
                        MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_NAME to content.balanceSupplierName,
                        MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_CONTRACT_NAME to content.balanceSupplierContractName,
                        MustacheConstants.VARIABLE_KEY_HTML_LANG to content.language.code,
                        MustacheConstants.VARIABLE_KEY_I18N to i18n,
                    )
                ).flush()
        }.toString()
    }

    private fun generateMoveInAndChangeOfBalanceSupplierHtml(content: AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplier): String {
        val i18n = i18nTemplateFunction(content.language, MustacheConstants.I18N_MOVE_IN)
        val moveInDate = content.moveInDate?.let { formatNorwegianDate(it.year, it.month.number, it.day) }
        return StringWriter().apply {
            mustacheFactory
                .compile(MustacheConstants.TEMPLATE_MOVE_IN)
                .execute(
                    this,
                    mapOf(
                        MustacheConstants.VARIABLE_KEY_CUSTOMER_NAME to content.customerName,
                        MustacheConstants.VARIABLE_KEY_METERING_POINT_ID to content.meteringPointId,
                        MustacheConstants.VARIABLE_KEY_METER_NUMBER to content.meterNumber,
                        MustacheConstants.VARIABLE_KEY_METERING_POINT_ADDRESS to content.meteringPointAddress,
                        MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_NAME to content.balanceSupplierName,
                        MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_CONTRACT_NAME to content.balanceSupplierContractName,
                        MustacheConstants.VARIABLE_KEY_HTML_LANG to content.language.code,
                        MustacheConstants.VARIABLE_KEY_I18N to i18n,
                    )
                        .let { base ->
                            if (moveInDate == null) {
                                base
                            } else {
                                base + (MustacheConstants.VARIABLE_KEY_MOVE_IN_DATE to moveInDate)
                            }
                        }
                ).flush()
        }.toString()
    }

    private fun generateFrameworkAgreementHtml(content: AuthorizationDocumentPdfContent.FrameworkAgreement): String {
        val i18n = i18nTemplateFunction(content.language, MustacheConstants.I18N_FRAMEWORK_AGREEMENT)
        val dateFormatter =
            DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale.forLanguageTag(content.language.toPdfLanguage()))
        val startDate = content.startDate.toJavaLocalDate().format(dateFormatter)
        val endDate = content.endDate?.toJavaLocalDate()?.format(dateFormatter)

        val data =
            mapOf(
                MustacheConstants.VARIABLE_KEY_ORGANIZATION_NAME to content.organizationName,
                MustacheConstants.VARIABLE_KEY_ORGANIZATION_NUMBER to content.organizationNumber,
                MustacheConstants.VARIABLE_KEY_BALANCE_SUPPLIER_NAME to content.balanceSupplierName,
                MustacheConstants.VARIABLE_KEY_CONTRACT_REFERENCE to content.contractReference,
                MustacheConstants.VARIABLE_KEY_START_DATE to startDate,
                MustacheConstants.VARIABLE_KEY_HTML_LANG to content.language.code,
                MustacheConstants.VARIABLE_KEY_I18N to i18n,
            )
        val templateData =
            if (endDate == null) {
                data
            } else {
                data + (MustacheConstants.VARIABLE_KEY_END_DATE to endDate)
            }

        return StringWriter().apply {
            mustacheFactory
                .compile(MustacheConstants.TEMPLATE_FRAMEWORK_AGREEMENT)
                .execute(this, templateData)
                .flush()
        }.toString()
    }

    private fun formatNorwegianDate(year: Int, month: Int, day: Int): String =
        String.format(Locale.ROOT, "%02d.%02d.%04d", day, month, year)

    private fun generatePdfFromHtml(htmlString: String): ByteArray =
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

    private fun ByteArray.addMetadataToPdf(
        language: PdfLanguage,
        customMetadata: Map<String, String> = emptyMap()
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            Loader.loadPDF(this).use { doc ->
                doc.documentInformation = PDDocumentInformation().apply {
                    author = "Elhub AS"
                    producer = null
                    for (metadataPair in customMetadata) {
                        setCustomMetadataValue(metadataPair.key, metadataPair.value)
                    }
                }
                doc.documentCatalog.language = language.toPdfLanguage()
                doc.save(out)
            }
            out.toByteArray()
        }

    private fun fontSupplier(bytes: ByteArray): FSSupplier<InputStream> =
        FSSupplier { ByteArrayInputStream(bytes) }

    private fun i18nTemplateFunction(language: PdfLanguage, processBundleName: String): TemplateFunction {
        val locale = Locale.forLanguageTag(language.code)
        val processBundle = ResourceBundle.getBundle(processBundleName, locale)
        val commonBundle = ResourceBundle.getBundle(MustacheConstants.I18N_COMMON, locale)
        return TemplateFunction { key ->
            val normalizedKey = key.trim()
            when {
                processBundle.containsKey(normalizedKey) -> processBundle.getString(normalizedKey)
                commonBundle.containsKey(normalizedKey) -> commonBundle.getString(normalizedKey)
                else -> normalizedKey
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
