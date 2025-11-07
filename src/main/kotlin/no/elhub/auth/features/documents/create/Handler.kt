package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.ElhubResourceType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import java.time.LocalDateTime
import java.util.UUID

class Handler(
    private val fileGenerator: FileGenerator,
    private val certificateProvider: CertificateProvider,
    private val fileSigningService: FileSigningService,
    private val signatureProvider: SignatureProvider,
    private val documentRepo: DocumentRepository,
    private val endUserService: EndUserService,
) {
    suspend operator fun invoke(command: Command): Either<CreateDocumentError, AuthorizationDocument> {
        val file = fileGenerator.generate(
            customerNin = command.requestedFrom.resourceId,
            customerName = command.requestedFromName,
            meteringPointAddress = command.meteringPointAddress,
            meteringPointId = command.meteringPointId,
            balanceSupplierName = command.balanceSupplierName,
            balanceSupplierContractName = command.balanceSupplierContractName
        ).getOrElse { return CreateDocumentError.FileGenerationError.left() }

        val certChain = certificateProvider.getCertificateChain()
            .getOrElse { return CreateDocumentError.CertificateRetrievalError.left() }

        val signingCert = certificateProvider.getCertificate()
            .getOrElse { return CreateDocumentError.CertificateRetrievalError.left() }

        val dataToSign = fileSigningService.getDataToSign(file, certChain, signingCert)
            .getOrElse { return CreateDocumentError.SigningDataGenerationError.left() }

        val signature = signatureProvider.fetchSignature(dataToSign)
            .getOrElse { return CreateDocumentError.SignatureFetchingError.left() }

        val signedFile = fileSigningService.embedSignatureIntoFile(file, signature, certChain, signingCert)
            .getOrElse { return CreateDocumentError.SigningError.left() }

        // replace external NIN with internal Elhub UUID for persons (organizations keep their GLN/org number as resourceId)
        val requestedFromParty = resolveRequestedFromParty(command.requestedFrom)
            .getOrElse { return it.left() }

        val documentToCreate = command.toAuthorizationDocument(signedFile, requestedFromParty)
            .getOrElse { return CreateDocumentError.MappingError.left() }

        val savedDocument = documentRepo.insert(documentToCreate)
            .getOrElse { return CreateDocumentError.PersistenceError.left() }

        return savedDocument.right()
    }

    private suspend fun resolveRequestedFromParty(requestedFrom: AuthorizationParty): Either<CreateDocumentError, AuthorizationParty> =
        if (requestedFrom.type == ElhubResourceType.Person) {
            endUserService.findOrCreateByNin(requestedFrom.resourceId)
                .map { endUser -> requestedFrom.copy(resourceId = endUser.internalId.toString()) }
                .mapLeft { CreateDocumentError.EndUserError }
        } else {
            requestedFrom.right()
        }
}

sealed class CreateDocumentError {
    data object FileGenerationError : CreateDocumentError()
    data object CertificateRetrievalError : CreateDocumentError()
    data object SigningDataGenerationError : CreateDocumentError()
    data object SignatureFetchingError : CreateDocumentError()
    data object SigningError : CreateDocumentError()
    data object MappingError : CreateDocumentError()
    data object PersistenceError : CreateDocumentError()
    data object PartyError : CreateDocumentError()
    data object EndUserError : CreateDocumentError()
}

fun Command.toAuthorizationDocument(file: ByteArray, requestedFrom: AuthorizationParty): Either<CreateDocumentError.MappingError, AuthorizationDocument> =
    Either.catch {
        AuthorizationDocument(
            id = UUID.randomUUID(),
            title = "Title",
            file = file,
            type = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
            status = AuthorizationDocument.Status.Pending,
            requestedBy = this.requestedBy,
            requestedFrom = requestedFrom,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }.mapLeft { CreateDocumentError.MappingError }
