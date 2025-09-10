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

typealias MustacheVariableTags = Map<String, String>

typealias PdfBytes = ByteArray

sealed interface PdfError {
    data object HtmlGenerationError : PdfError
    data object PdfGenerationError : PdfError
    data object MetaDataError : PdfError
}

fun interface PdfFactory {
    fun create(command: CreateDocumentCommand): Either<PdfError, PdfBytes>
}

class HtmlToPdfFactory : PdfFactory {
    private val mustacheFactory: DefaultMustacheFactory = DefaultMustacheFactory("templates")

    enum class MustacheTemplateFile(val fileName: String) {
        ChangeOfSupplier("change_of_supplier.mustache")
    }

    data class PdfData(
        val template: MustacheTemplateFile,
        val toBeSignedBy: String,
        val tags: MustacheVariableTags
    )

    object PdfMetaData {
        internal const val NIN = "NIN"
    }

    private fun CreateDocumentCommand.toPdfData(): PdfData = when (this) {
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

    override fun create(command: CreateDocumentCommand): Either<PdfError, PdfBytes> = either {
        val pdfData = command.toPdfData()
        val htmlString = generateHtmlString(pdfData.template, pdfData.tags).getOrElse { return PdfError.PdfGenerationError.left() }
        val pdfBytes = htmlToPdfBytes(htmlString).getOrElse { return PdfError.PdfGenerationError.left() }
        return pdfBytes.addMetadata(
            mapOf(PdfMetaData.NIN to pdfData.toBeSignedBy)
        )
    }

    private fun generateHtmlString(template: MustacheTemplateFile, variableTags: MustacheVariableTags): Either<PdfError.HtmlGenerationError, String> =
        Either.catch {
            StringWriter().apply {
                mustacheFactory
                    .compile(template.fileName)
                    .execute(this, variableTags)
                    .flush()
            }.toString()
        }.mapLeft { PdfError.HtmlGenerationError }

    private fun htmlToPdfBytes(htmlString: String): Either<PdfError.PdfGenerationError, PdfBytes> = Either.catch {
        ByteArrayOutputStream().use { out ->
            PdfRendererBuilder()
                .withHtmlContent(htmlString, null)
                .toStream(out)
                .run()
            out.toByteArray()
        }
    }.mapLeft { PdfError.PdfGenerationError }

    private fun PdfBytes.addMetadata(
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
    }.mapLeft { PdfError.MetaDataError }
}
