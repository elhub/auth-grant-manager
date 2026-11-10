package no.elhub.auth.v1.features.documents

import arrow.core.getOrElse
import no.elhub.auth.common.documents.pdf.PdfSignatureValidator
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.isApproverAllowed
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.repositories.DocumentRepository
import org.slf4j.LoggerFactory
import java.util.UUID
import no.elhub.auth.v0.features.common.party.PartyService as AgentService

class ConfirmDocumentHandler(
    private val documentRepository: DocumentRepository,
    private val agentService: AgentService,
    private val pdfSignatureValidator: PdfSignatureValidator,
) {
    private val logger = LoggerFactory.getLogger(ConfirmDocumentHandler::class.java)

    suspend operator fun invoke(
        id: UUID,
        authorizedAgent: AuthorizationAgent,
        signedFile: ByteArray,
    ): AuthorizationDocument {
        val document = documentRepository.find(id)

        if (authorizedAgent != document.requestedBy) {
            throw ConfirmDocumentError.NotAuthorized()
        }

        if (document.status != AuthorizationDocumentStatus.Pending) {
            throw ConfirmDocumentError.InvalidStatus(document.status)
        }

        val signatory = pdfSignatureValidator.validateSignaturesAndReturnSignatory(signedFile, document.pdfBytes)
        val signatoryAgent = agentService.resolve(
            PartyIdentifier(
                idType = PartyIdentifierType.NationalIdentityNumber,
                idValue = signatory.nationalIdentityNumber,
            ),
        ).getOrElse { throw ConfirmDocumentError.SignatoryResolutionFailed() }

        if (!isApproverAllowed(signatoryAgent, document.requestedOf, document.requestedPrincipal)) {
            logger.warn("Approver not allowed for AuthorizationDocument {} (approver type: {})", id, signatoryAgent.type)
            throw ConfirmDocumentError.SignatoryNotAllowed()
        }

        val grants = document.toGrants(approvedBy = signatoryAgent)

        return documentRepository.confirm(
            documentId = id,
            signedFile = signedFile,
            signatory = signatoryAgent,
            grants = grants,
        )
    }
}
