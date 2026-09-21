package no.elhub.auth.v1.features.documents.create

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.DocumentLanguage

data class CreateAuthorizationDocumentCommand(
    val requestedScope: RequestedScope,
    val externalReference: String?,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val language: DocumentLanguage,
)

class CreateDocumentBusinessHandler(
    private val coreHandler: CreateDocumentCoreHandler,
) {
    suspend operator fun invoke(command: CreateAuthorizationDocumentCommand): AuthorizationDocument {
        if (!isAllowedToActOnBehalfOf(command.requestedTo, command.requestedFrom)) {
            throw Errors.RequestedToRequestedFromMismatch
        }

        // This will later be resolved by business handlers implemented by the relevant value streams.
        // They must validate that requestedBy may create this document type and is a balance supplier.
        // They must also validate that requestedFrom has the correct party type, such as an organization.
        if (!isRequestedScopeAllowed(command.requestedFrom, command.requestedBy, command.requestedScope)) {
            throw Errors.RequestedScopeNotAllowed
        }

        val authorizationScopes = command.requestedScope.toAuthorizationScopes()
        // Remember to fetch PDF metadata and generate the document once the creation flow is implemented.
        val pdfBytes = ByteArray(0)

        return coreHandler(
            DocumentInput(
                requestedFrom = command.requestedFrom,
                requestedTo = command.requestedTo,
                requestedBy = command.requestedBy,
                documentType = command.requestedScope.documentType,
                language = command.language,
                scopes = command.requestedScope.toAuthorizationScopes(),
                externalReference = command.externalReference,
                pdfBytes = pdfBytes,
            ),
        )
    }

    private fun isAllowedToActOnBehalfOf(
        requestedTo: AuthorizationParty,
        requestedFrom: AuthorizationParty,
    ): Boolean {
        // Temporary placeholder until authorization lookup is implemented.
        return true
    }

    private fun isRequestedScopeAllowed(
        requestedFrom: AuthorizationParty,
        requestedBy: AuthorizationParty,
        requestedScope: RequestedScope,
    ): Boolean {
        // Temporary placeholder until requested-scope authorization is implemented.
        return true
    }

}
