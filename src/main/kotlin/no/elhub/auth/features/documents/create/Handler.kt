package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.PartyService
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
import no.elhub.auth.features.documents.common.DocumentPropertiesRepository
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.toAuthorizationDocumentType
import java.util.UUID

class Handler(
    private val fileGenerator: FileGenerator,
    private val certificateProvider: CertificateProvider,
    private val fileSigningService: FileSigningService,
    private val signatureProvider: SignatureProvider,
    private val documentRepository: DocumentRepository,
    private val documentPropertiesRepository: DocumentPropertiesRepository,
    private val partyService: PartyService
) {
    suspend operator fun invoke(command: DocumentCommand): Either<CreateDocumentError, AuthorizationDocument> {
        val requestedFromParty = partyService.resolve(command.requestedFrom)
            .getOrElse { return CreateDocumentError.RequestedFromPartyError.left() }

        val requestedByParty = partyService.resolve(command.requestedBy)
            .getOrElse { return CreateDocumentError.RequestedByPartyError.left() }

        val requestedToParty = partyService.resolve(command.requestedTo)
            .getOrElse { return CreateDocumentError.RequestedToPartyError.left() }

        val signedByParty = partyService.resolve(command.signedBy)
            .getOrElse { return CreateDocumentError.SignedByPartyError.left() }

        val file = fileGenerator.generate(
            signerNin = command.signedBy.idValue,
            documentMeta = command.meta
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

        val documentType = command.toAuthorizationDocumentType()

        val documentToCreate = AuthorizationDocument.create(
            type = documentType,
            file = signedFile,
            requestedBy = requestedByParty,
            requestedFrom = requestedFromParty,
            requestedTo = requestedToParty,
            signedBy = signedByParty
        )

        val savedDocument = documentRepository.insert(documentToCreate)
            .getOrElse { return CreateDocumentError.PersistenceError.left() }

        val documentProperties = command.meta
            .toMetaAttributes()
            .toDocumentProperties(savedDocument.id)

        documentPropertiesRepository.insert(documentProperties)

        return savedDocument.right()
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
    data object RequestedFromPartyError : CreateDocumentError()
    data object RequestedByPartyError : CreateDocumentError()
    data object RequestedToPartyError : CreateDocumentError()
    data object SignedByPartyError : CreateDocumentError()
    data object PersonError : CreateDocumentError()
}

fun Map<String, String>.toDocumentProperties(documentId: UUID) =
    this.map { (key, value) ->
        AuthorizationDocumentProperty(
            documentId = documentId,
            key = key,
            value = value
        )
    }.toList()
