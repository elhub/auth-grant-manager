package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository

class Handler(
    private val fileGenerator: FileGenerator,
    private val certificateProvider: CertificateProvider,
    private val fileSigningService: FileSigningService,
    private val signatureProvider: SignatureProvider,
    private val repo: DocumentRepository,
) {
    suspend operator fun invoke(command: Command): Either<CreateDocumentError, AuthorizationDocument> {
        val requestedFromParty = command.requestedFromIdentifier.toAuthorizationParty()
        val requestedByParty = command.requestedByIdentifier.toAuthorizationParty()
        val requestedToParty = command.requestedToIdentifier.toAuthorizationParty()
        val signedByParty = command.signedByIdentifier.toAuthorizationParty()

        val file = fileGenerator.generate(
            customerNin = command.requestedFromIdentifier.idValue,
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

        val documentToCreate = AuthorizationDocument.create(
            type = command.type,
            file = signedFile,
            requestedBy = requestedByParty,
            requestedFrom = requestedFromParty,
            requestedTo = requestedToParty,
            signedBy = signedByParty
        )

        val savedDocument = repo.insert(documentToCreate)
            .getOrElse { return CreateDocumentError.PersistenceError.left() }

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
        PartyIdentifierType.NationalIdentityNumber -> AuthorizationParty(resourceId = this.idValue, type = PartyType.Person)
        PartyIdentifierType.OrganizationNumber -> AuthorizationParty(resourceId = this.idValue, type = PartyType.Organization)
        PartyIdentifierType.GlobalLocationNumber -> AuthorizationParty(resourceId = this.idValue, type = PartyType.OrganizationEntity)
    }
