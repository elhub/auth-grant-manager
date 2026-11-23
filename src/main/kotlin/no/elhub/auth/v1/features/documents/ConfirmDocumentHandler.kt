package no.elhub.auth.v1.features.documents

import arrow.core.getOrElse
import no.elhub.auth.common.documents.pdf.PdfSignatureValidator
import no.elhub.auth.common.documents.pdf.PdfValidationException
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
import no.elhub.auth.v1.common.AuthorizationRulesService
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.logging.errorName
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.repositories.DocumentRepository
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid
import no.elhub.auth.v0.features.common.party.PartyService as AgentService

class ConfirmDocumentHandler(
    private val documentRepository: DocumentRepository,
    private val agentService: AgentService,
    private val pdfSignatureValidator: PdfSignatureValidator,
    private val authorizationRulesService: AuthorizationRulesService,
) {
    private val logger = LoggerFactory.getLogger(ConfirmDocumentHandler::class.java)

    suspend operator fun invoke(
        id: Uuid,
        authorizedAgent: AuthorizationAgent,
        signedFile: ByteArray,
    ): AuthorizationDocument {
        val confirmedDocument = try {
            confirm(id, authorizedAgent, signedFile)
        } catch (error: ConfirmDocumentError) {
            logger.warn(
                "event=authorization_document_confirm_failed documentId={} reason={}",
                id,
                error.errorName,
            )
            throw error
        } catch (error: PdfValidationException) {
            logger.warn(
                "event=authorization_document_confirm_failed documentId={} reason={}",
                id,
                error.errorName,
            )
            throw error
        }
        logger.info(
            "event=authorization_document_confirmed documentId={} type={} signatoryType={}",
            confirmedDocument.id,
            confirmedDocument.documentType,
            confirmedDocument.approvedBy?.type,
        )
        confirmedDocument.authorizationGrants.orEmpty().forEach { grant ->
            logger.info(
                "event=authorization_grant_created grantId={} grantType={} sourceType={} sourceId={}",
                grant.id,
                grant.type,
                grant.sourceType,
                grant.sourceId,
            )
        }
        return confirmedDocument
    }

    private suspend fun confirm(
        id: Uuid,
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

        if (!authorizationRulesService.isApproverAllowed(signatoryAgent, document.requestedOf, document.requestedPrincipal)) {
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
