package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.documents.common.DocumentRepository
import java.util.UUID

class CreateDocumentHandler(
    private val signingService: PdfSigningService,
    private val pdfFactory: PdfFactory,
    private val repo: DocumentRepository
) {
    suspend operator fun invoke(command: CreateDocumentCommand): Either<CreateDocumentError, UUID> {
        val pdfBytes = pdfFactory.create(command).getOrElse { return CreateDocumentError.DocumentGenerationError.left() }
        val signedPdf = signingService.sign(pdfBytes).getOrElse { return CreateDocumentError.SigningError.left() }
        val documentToCreate = command.toAuthorizationDocument(signedPdf)
        return repo.insert(documentToCreate)
            .getOrElse { return CreateDocumentError.PersistenceError.left() }
            .right()
    }
}

sealed class CreateDocumentError {
    data object DocumentGenerationError : CreateDocumentError()
    data object SigningError : CreateDocumentError()
    data object PersistenceError : CreateDocumentError()
}
