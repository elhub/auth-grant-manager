package no.elhub.auth.v1.features.documents

import arrow.core.getOrElse
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.documents.pdf.PdfSignatureValidator
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.repositories.DocumentRepository
import java.util.UUID

class ConfirmDocumentHandler(
    private val documentRepository: DocumentRepository,
    private val partyService: PartyService,
    private val pdfSignatureValidator: PdfSignatureValidator,
) {
    suspend operator fun invoke(
        id: UUID,
        authorizedParty: AuthorizationParty,
        signedFile: ByteArray,
    ): AuthorizationDocument {
        val document = documentRepository.find(id)

        if (authorizedParty != document.requestedBy) {
            throw ConfirmDocumentError.NotAuthorized()
        }

        if (document.status != AuthorizationDocumentStatus.Pending) {
            throw ConfirmDocumentError.InvalidStatus(document.status)
        }

        val signatory = pdfSignatureValidator.validateSignaturesAndReturnSignatory(signedFile, document.pdfBytes)
        val signatoryParty = partyService.resolve(
            PartyIdentifier(
                idType = PartyIdentifierType.NationalIdentityNumber,
                idValue = signatory.nationalIdentityNumber,
            ),
        ).getOrElse { throw ConfirmDocumentError.SignatoryResolutionFailed() }

        if (signatoryParty != document.requestedOf) {
            throw ConfirmDocumentError.SignatoryNotAllowed()
        }

        val grants = document.toGrants(approvedBy = signatoryParty)

        return documentRepository.confirm(
            documentId = id,
            signedFile = signedFile,
            signatory = signatoryParty,
            grants = grants,
        )
    }
}
