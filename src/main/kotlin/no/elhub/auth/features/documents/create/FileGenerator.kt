package no.elhub.auth.features.documents.create

import arrow.core.Either
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.filegenerator.SupportedLanguage

interface FileGenerator {
    fun generate(
        signerNin: String,
        documentMeta: DocumentMetaMarker,
        language: SupportedLanguage,
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray>
}

class DocumentGenerationError {
    data object ContentGenerationError
}
