package no.elhub.auth.v1.features.documents.create

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.DocumentLanguage

class CreateDocumentBusinessHandler {
    suspend fun createAuthorizationDocument(
        requestedScope: RequestedScope,
        externalReference: String?,
        requestedBy: AuthorizationParty,
        requestedFrom: AuthorizationParty,
        requestedTo: AuthorizationParty,
        language: DocumentLanguage,
    ): AuthorizationDocument {
        if (!isAllowedToActOnBehalfOf(requestedTo, requestedFrom)) {
            throw Errors.RequestedToRequestedFromMismatch
        }

        // This will later be resolved by business handlers implemented by the relevant value streams.
        // They must validate that requestedBy may create this document type and is a balance supplier.
        // They must also validate that requestedFrom has the correct party type, such as an organization.
        if (!isRequestedScopeAllowed(requestedFrom, requestedBy, requestedScope)) {
            throw Errors.RequestedScopeNotAllowed
        }

        // Remember to fetch PDF metadata and generate the document once the creation flow is implemented.
        val pdfBytes = ByteArray(0)

        return AuthorizationDocument.new(
            documentType = requestedScope.documentType,
            resourceConstraints = requestedScope.toResourceConstraints(),
            externalReference = externalReference,
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            requestedTo = requestedTo,
            pdfBytes = pdfBytes,
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
