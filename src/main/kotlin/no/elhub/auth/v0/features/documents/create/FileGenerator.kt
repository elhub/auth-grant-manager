package no.elhub.auth.v0.features.documents.create

import arrow.core.Either
import no.elhub.auth.v0.features.documents.create.command.DocumentMetaMarker

interface FileGenerator {
    fun generate(
        documentMeta: DocumentMetaMarker,
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray>
}

class DocumentGenerationError {
    data object ContentGenerationError
}
