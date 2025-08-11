package no.elhub.auth.grantmanager.data.services

import com.github.mustachejava.DefaultMustacheFactory
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import no.elhub.auth.grantmanager.domain.models.ChangeSupplierRequest
import no.elhub.auth.grantmanager.domain.models.SignableDocument
import no.elhub.auth.grantmanager.domain.services.DocumentGenerator
import no.elhub.auth.grantmanager.domain.services.DocumentSigningService
import no.elhub.auth.grantmanager.domain.services.SignedDocumentGenerator
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.UUID

private const val MUSTACHE_RESOURCES_PATH = "templates"
private const val MUSTACHE_TEMPLATE_CHANGE_SUPPLIER_CONTRACT = "contract.mustache"
private const val PDF_METADATA_KEY_SSN = "ssn"
private const val PDF_VARIABLE_KEY_SSN = "ssn"
private const val PDF_VARIABLE_KEY_SUPPLIER_ID = "balanceSupplierId"
private const val PDF_VARIABLE_KEY_METER_ID = "meteringPointId"
private const val TITLE = "Change Supplier Confirmation: "

class PdfDocumentGenerator(
    private val mustacheFactory: DefaultMustacheFactory = DefaultMustacheFactory(MUSTACHE_RESOURCES_PATH),
    private val documentSigningService: DocumentSigningService,
) : DocumentGenerator, SignedDocumentGenerator {
    override suspend fun generate(request: ChangeSupplierRequest): SignableDocument {

        val contractHtmlString = generateHtml("${request.meteringPoint.owner.ssn}", "${request.requester.organizationNumber}", "${request.meteringPoint.id}")

        val pdfBytes = generatePdfFromHtml(contractHtmlString)

        val pdfWithMetadata = addPdfMetadata(pdfBytes, mapOf(PDF_METADATA_KEY_SSN to "${request.meteringPoint.owner.ssn}"))

        return SignableDocument(UUID.randomUUID(), "$TITLE ${request.meteringPoint.id} to ${request.requester.name}", pdfWithMetadata)
    }

    override suspend fun generateAndSign(request: ChangeSupplierRequest): SignableDocument = documentSigningService.sign(generate(request))

    private fun generateHtml(ssn: String, supplierId: String, meteringPointId: String) =
        StringWriter().apply {
            mustacheFactory
                .compile(MUSTACHE_TEMPLATE_CHANGE_SUPPLIER_CONTRACT)
                .execute(
                    this,
                    mapOf(
                        PDF_VARIABLE_KEY_SSN to ssn,
                        PDF_VARIABLE_KEY_SUPPLIER_ID to supplierId,
                        PDF_VARIABLE_KEY_METER_ID to meteringPointId
                    )
                ).flush()
        }.toString()

    private fun generatePdfFromHtml(htmlString: String): ByteArray = ByteArrayOutputStream().use { out ->
        PdfRendererBuilder()
            .useFastMode()
            .withHtmlContent(htmlString, null)
            .toStream(out)
            .run()
        out.toByteArray()
    }

    private fun addPdfMetadata(bytes: ByteArray, metadata: Map<String, String>): ByteArray = ByteArrayOutputStream().use { out ->
        Loader.loadPDF(bytes).use { doc ->
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
