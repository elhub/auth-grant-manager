package no.elhub.auth.v1.features.documents.create

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.InputError
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.RequestedAuthorizationScope

class CreateDocumentBusinessHandler {
    fun createAuthorizationDocument(
        documentType: AuthorizationDocumentType,
        requestedScope: RequestedAuthorizationScope,
        externalReference: String?,
        requestedBy: AuthorizationParty,
        requestedFrom: AuthorizationParty,
        requestedTo: AuthorizationParty,
        language: DocumentLanguage,
    ): AuthorizationDocument {
        if (!isRequestedScopeAllowedForDocumentType(documentType, requestedScope)) {
            throw InputError.RequestedScopeNotAllowed()
        }

        validatePartiesForDocumentType(
            documentType = documentType,
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            requestedTo = requestedTo,
        )

        val resolvedScope = resolveRequestedScope(
            documentType = documentType,
            requestedFrom = requestedFrom,
            requestedScope = requestedScope,
        )

        if (!hasDelegatedAuthorityToActOnBehalfOf(requestedTo, requestedFrom)) {
            throw InputError.RequestedToRequestedFromMismatch()
        }

        // This will later be resolved by business handlers implemented by the relevant value streams.
        // They must validate that requestedBy may create this document type and is a balance supplier.
        // They must also validate that requestedFrom has the correct party type, such as an organization.
        if (!canAuthorizeRequestedScope(documentType, requestedFrom, resolvedScope)) {
            throw InputError.RequestedScopeNotAllowed()
        }

        // Remember to fetch PDF metadata and generate the document once the creation flow is implemented.
        val pdfBytes = ByteArray(0)

        return AuthorizationDocument.new(
            documentType = documentType,
            requestedScopes = listOf(
                RequestedAuthorizationScope(
                    resourceType = resolvedScope.resourceType,
                    constraints = resolvedScope.constraints,
                ),
            ),
            externalReference = externalReference,
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            requestedTo = requestedTo,
            pdfBytes = pdfBytes,
        )
    }

    private fun validatePartiesForDocumentType(
        documentType: AuthorizationDocumentType,
        requestedBy: AuthorizationParty,
        requestedFrom: AuthorizationParty,
        requestedTo: AuthorizationParty,
    ) {
        // Temporary placeholder until Elhub party and role validation is implemented.
        if (!arePartiesValidForDocumentType(documentType, requestedBy, requestedFrom, requestedTo)) {
            throw InputError.PartiesNotAllowedForDocumentType()
        }
    }

    private fun arePartiesValidForDocumentType(
        documentType: AuthorizationDocumentType,
        requestedBy: AuthorizationParty,
        requestedFrom: AuthorizationParty,
        requestedTo: AuthorizationParty,
    ): Boolean {
        // Temporary placeholder until Elhub party and role validation is implemented.
        return true
    }

    private fun isRequestedScopeAllowedForDocumentType(
        documentType: AuthorizationDocumentType,
        requestedScope: RequestedAuthorizationScope,
    ): Boolean {
        // Temporary placeholder until document-type scope validation is implemented.
        return true
    }

    private fun resolveRequestedScope(
        documentType: AuthorizationDocumentType,
        requestedFrom: AuthorizationParty,
        requestedScope: RequestedAuthorizationScope,
    ): RequestedAuthorizationScope {
        // Temporary placeholder until scope constraints can be populated from Elhub.
        return requestedScope
    }

    private fun hasDelegatedAuthorityToActOnBehalfOf(
        requestedTo: AuthorizationParty,
        requestedFrom: AuthorizationParty,
    ): Boolean {
        // Temporary placeholder until authorization lookup is implemented.
        return true
    }

    private fun canAuthorizeRequestedScope(
        documentType: AuthorizationDocumentType,
        requestedFrom: AuthorizationParty,
        requestedScope: RequestedAuthorizationScope,
    ): Boolean {
        // Temporary placeholder until requested-scope authorization is implemented.
        return true
    }
}
