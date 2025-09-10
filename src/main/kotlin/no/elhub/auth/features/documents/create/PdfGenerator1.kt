/*
package no.elhub.auth.features.documents.create

import com.github.mustachejava.DefaultMustacheFactory
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringWriter
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation

object PdfGenerator1 {
    private fun loadResource(path: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream(path)) {
            "Missing font resource: $path"
        }.readAllBytes()

    object Fonts {

        val all = listOf(
            FontDef(loadResource("/fonts/liberation-sans/LiberationSans-Regular.ttf"), "LiberationSans", 400, BaseRendererBuilder.FontStyle.NORMAL),
            FontDef(loadResource("/fonts/liberation-sans/LiberationSans-Bold.ttf"), "LiberationSans", 700, BaseRendererBuilder.FontStyle.NORMAL),
            FontDef(loadResource("/fonts/liberation-sans/LiberationSans-Italic.ttf"), "LiberationSans", 400, BaseRendererBuilder.FontStyle.ITALIC),
            FontDef(loadResource("/fonts/liberation-sans/LiberationSans-BoldItalic.ttf"), "LiberationSans", 700, BaseRendererBuilder.FontStyle.ITALIC),

            FontDef(loadResource("/fonts/libre-bodoni/LibreBodoni-Regular.ttf"), "LibreBodoni", 400, BaseRendererBuilder.FontStyle.NORMAL),
            FontDef(loadResource("/fonts/libre-bodoni/LibreBodoni-Bold.ttf"), "LibreBodoni", 700, BaseRendererBuilder.FontStyle.NORMAL),
            FontDef(loadResource("/fonts/libre-bodoni/LibreBodoni-Italic.ttf"), "LibreBodoni", 400, BaseRendererBuilder.FontStyle.ITALIC),
            FontDef(loadResource("/fonts/libre-bodoni/LibreBodoni-BoldItalic.ttf"), "LibreBodoni", 700, BaseRendererBuilder.FontStyle.ITALIC)
        )
    }

    private val colorProfile = loadResource("/fonts/sRGB_IEC61966-2-1.icc")
    private val mustacheFactory = DefaultMustacheFactory("templates")

    fun createChangeOfSupplierConfirmationPdf(
        meteringPointId: String,
        ssn: String,
        supplier: String
    ): ByteArray {
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
                .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_B)
                .useColorProfile(colorProfile)
                .withHtmlContent(contractHtmlString, null)
                .useFonts(Fonts.all)
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

        return pdfWithMeta
    }
}

*/
