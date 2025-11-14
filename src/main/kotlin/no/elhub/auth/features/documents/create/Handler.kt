package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.PartyType
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
    private val documentPropertiesRepository: DocumentPropertiesRepository
) {
    suspend operator fun invoke(command: DocumentCommand): Either<CreateDocumentError, AuthorizationDocument> {
        val requestedFromParty = command.requestedFrom.toAuthorizationParty()
        val requestedByParty = command.requestedBy.toAuthorizationParty()
        val requestedToParty = command.requestedTo.toAuthorizationParty()
        val signedByParty = command.signedBy.toAuthorizationParty()

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
    data object PartyError : CreateDocumentError()
}

fun PartyIdentifier.toAuthorizationParty(): AuthorizationParty =
    when (this.idType) {
        PartyIdentifierType.NationalIdentityNumber -> AuthorizationParty(
            resourceId = this.idValue,
            type = PartyType.Person
        )

        PartyIdentifierType.OrganizationNumber -> AuthorizationParty(
            resourceId = this.idValue,
            type = PartyType.Organization
        )

        PartyIdentifierType.GlobalLocationNumber -> AuthorizationParty(
            resourceId = this.idValue,
            type = PartyType.OrganizationEntity
        )
    }

fun Map<String, String>.toDocumentProperties(documentId: UUID) =
    this.map { (key, value) ->
        AuthorizationDocumentProperty(
            documentId = documentId,
            key = key,
            value = value
        )
    }.toList()
