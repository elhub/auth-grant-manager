package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import java.time.LocalDateTime
import java.util.UUID

class Handler(
    private val documentGenerator: DocumentGenerator,
    private val signingService: DocumentSigningService,
    private val signatureProvider: SignatureProvider,
    private val repo: DocumentRepository
) {
    suspend operator fun invoke(command: Command): Either<Error, AuthorizationDocument> {
        val documentBytes = documentGenerator.generate(
            nin = command.requestedTo,
            supplier = command.requestedBy,
            meteringPointId = command.meteringPoint
        ).getOrElse { return Error.DocumentGenerationError.left() }

        val dataToSign = signingService.getDataToSign(documentBytes)
            .getOrElse { return Error.SigningDataGenerationError.left() }

        val signature = signatureProvider.fetchSignature(dataToSign)
            .getOrElse { return Error.SignatureFetchingError.left() }

        val signedDocument = signingService.sign(documentBytes, signature)
            .getOrElse { return Error.SigningError.left() }

        val documentToCreate = command.toAuthorizationDocument(signedDocument)
            .getOrElse { return Error.MappingError.left() }

        return repo.insert(documentToCreate)
            .getOrElse { return Error.PersistenceError.left() }
            .right()
    }
}

sealed class Error {
    data object DocumentGenerationError : Error()
    data object SigningDataGenerationError : Error()
    data object SignatureFetchingError : Error()
    data object SigningError : Error()
    data object MappingError : Error()
    data object PersistenceError : Error()
}

fun Command.toAuthorizationDocument(pdfBytes: ByteArray): Either<Error.MappingError, AuthorizationDocument> =
    Either.catch {
        AuthorizationDocument(
            id = UUID.randomUUID(),
            title = "Title",
            pdfBytes = pdfBytes,
            type = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
            status = AuthorizationDocument.Status.Pending,
            requestedBy = this.requestedBy,
            requestedTo = this.requestedTo,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }.mapLeft { Error.MappingError }
