package no.elhub.auth.v1.features.documents.creation

import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.domain.AuthorizationDocumentType

sealed class DocumentCreationAgentError(
    val documentType: AuthorizationDocumentType,
    val actualAgentType: PartyType,
    val expectedAgentType: PartyType,
) : Errors() {
    class RequestedByNotAllowed(
        documentType: AuthorizationDocumentType,
        actualAgentType: PartyType,
        expectedAgentType: PartyType,
    ) : DocumentCreationAgentError(documentType, actualAgentType, expectedAgentType)

    class RequestedOfNotAllowed(
        documentType: AuthorizationDocumentType,
        actualAgentType: PartyType,
        expectedAgentType: PartyType,
    ) : DocumentCreationAgentError(documentType, actualAgentType, expectedAgentType)

    class RequestedPrincipalNotAllowed(
        documentType: AuthorizationDocumentType,
        actualAgentType: PartyType,
        expectedAgentType: PartyType,
    ) : DocumentCreationAgentError(documentType, actualAgentType, expectedAgentType)
}
