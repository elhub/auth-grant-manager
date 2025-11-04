package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.PartyRepository
import java.time.LocalDateTime
import java.util.UUID

data class CreateDocumentResult(
    val document: AuthorizationDocument,
    val requestedByParty: AuthorizationParty,
    val requestedFromParty: AuthorizationParty
)

class Handler(
    private val fileGenerator: FileGenerator,
    private val certificateProvider: CertificateProvider,
    private val fileSigningService: FileSigningService,
    private val signatureProvider: SignatureProvider,
    private val repo: DocumentRepository,
    private val partyRepo: PartyRepository
) {
    suspend operator fun invoke(command: Command): Either<CreateDocumentError, CreateDocumentResult> {
        val file = fileGenerator.generate(
            customerNin = "TODO", // TODO resolve end-user nin from auth-persons service
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

        val requestedBy = partyRepo.findOrInsert(AuthorizationParty.ElhubResource.valueOf(command.requestedBy.type.name), command.requestedBy.resourceId)
            .getOrElse { return CreateDocumentError.PartyError.left() }

        val requestedFrom = partyRepo.findOrInsert(AuthorizationParty.ElhubResource.valueOf(command.requestedFrom.type.name), command.requestedFrom.resourceId)
            .getOrElse { return CreateDocumentError.PartyError.left() }

        val documentToCreate = toAuthorizationDocument(signedFile, requestedBy, requestedFrom)
            .getOrElse { return CreateDocumentError.MappingError.left() }

        val savedDocument = repo.insert(documentToCreate)
            .getOrElse { return CreateDocumentError.PersistenceError.left() }

        return CreateDocumentResult(
            document = savedDocument,
            requestedByParty = requestedBy,
            requestedFromParty = requestedFrom
        ).right()
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
}

fun toAuthorizationDocument(
    file: ByteArray,
    requestedBy: AuthorizationParty,
    requestedFrom: AuthorizationParty
): Either<CreateDocumentError.MappingError, AuthorizationDocument> =
    Either.catch {
        AuthorizationDocument(
            id = UUID.randomUUID(),
            title = "Title",
            file = file,
            type = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
            status = AuthorizationDocument.Status.Pending,
            requestedBy = requestedBy.id,
            requestedFrom = requestedFrom.id,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }.mapLeft { CreateDocumentError.MappingError }
