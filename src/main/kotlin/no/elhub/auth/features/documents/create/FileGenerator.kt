package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import com.github.mustachejava.DefaultMustacheFactory
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringWriter

interface FileGenerator {
    fun generate(
        signerNin: String,
        documentMeta: DocumentMetaMarker
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray>
}

class DocumentGenerationError {
    data object ContentGenerationError
}
