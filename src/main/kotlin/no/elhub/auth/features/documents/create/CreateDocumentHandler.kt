package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import java.time.LocalDateTime
import java.util.UUID

class CreateDocumentHandler(
    private val signingService: DocumentSigningService,
    private val repo: DocumentRepository
) {
    suspend operator fun invoke(command: CreateDocumentCommand): Either<CreateDocumentError, UUID> {
        val documentBytes = PdfFactory.create(command)

        val signedDocument = signingService.sign(documentBytes)
            .getOrElse { return CreateDocumentError.SigningError.left() }

        val documentToCreate = command.toAuthorizationDocument(signedDocument)
            .getOrElse { return CreateDocumentError.MappingError.left() }

        return repo.insert(documentToCreate)
            .getOrElse { return CreateDocumentError.PersistenceError.left() }
            .right()
    }
}

sealed class CreateDocumentError {
    data object DocumentGenerationError : CreateDocumentError()
    data object SigningDataGenerationError : CreateDocumentError()
    data object SignatureFetchingError : CreateDocumentError()
    data object SigningError : CreateDocumentError()
    data object MappingError : CreateDocumentError()
    data object PersistenceError : CreateDocumentError()
}

