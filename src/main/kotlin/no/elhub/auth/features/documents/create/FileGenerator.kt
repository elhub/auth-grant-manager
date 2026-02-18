package no.elhub.auth.features.documents.create

import arrow.core.Either
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker

interface FileGenerator {
    fun generate(
        documentMeta: DocumentMetaMarker,
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray>
}

class DocumentGenerationError {
    data object ContentGenerationError
}
