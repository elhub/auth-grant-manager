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
    private val documentGenerator: DocumentGenerator,
    private val certProvider: CertificateProvider,
    private val signingService: DocumentSigningService,
    private val signatureProvider: SignatureProvider,
    private val repo: DocumentRepository
) {
    suspend operator fun invoke(command: CreateDocumentCommand): Either<CreateDocumentError, UUID> {
        val documentBytes = documentGenerator.generate(
            nin = command.requestedTo,
            supplier = command.requestedBy,
            meteringPointId = command.meteringPoint
        ).getOrElse { return CreateDocumentError.DocumentGenerationError.left() }

        val certChain = certProvider.getCertificateChain()
            .getOrElse { return CreateDocumentError.CertificateRetrievalError.left() }

        val signingCert = certProvider.getCertificate()
            .getOrElse { return CreateDocumentError.CertificateRetrievalError.left() }

        val dataToSign = signingService.getDataToSign(documentBytes, certChain, signingCert)
            .getOrElse { return CreateDocumentError.SigningDataGenerationError.left() }

        val signature = signatureProvider.fetchSignature(dataToSign)
            .getOrElse { return CreateDocumentError.SignatureFetchingError.left() }

        val signedDocument = signingService.sign(documentBytes, signature, certChain, signingCert)
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
    data object CertificateRetrievalError : CreateDocumentError()
    data object SigningDataGenerationError : CreateDocumentError()
    data object SignatureFetchingError : CreateDocumentError()
    data object SigningError : CreateDocumentError()
    data object MappingError : CreateDocumentError()
    data object PersistenceError : CreateDocumentError()
}

fun CreateDocumentCommand.toAuthorizationDocument(pdfBytes: ByteArray): Either<CreateDocumentError.MappingError, AuthorizationDocument> =
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
    }.mapLeft { CreateDocumentError.MappingError }
