package no.elhub.auth.v1.features.documents.creation

import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.domain.AuthorizationDocumentType

sealed class DocumentCreationAgentError(
    val documentType: AuthorizationDocumentType,
    val actualAgentType: AgentType,
    val expectedAgentType: AgentType,
) : Errors() {
    class RequestedByNotAllowed(
        documentType: AuthorizationDocumentType,
        actualAgentType: AgentType,
        expectedAgentType: AgentType,
    ) : DocumentCreationAgentError(documentType, actualAgentType, expectedAgentType)

    class RequestedOfNotAllowed(
        documentType: AuthorizationDocumentType,
        actualAgentType: AgentType,
        expectedAgentType: AgentType,
    ) : DocumentCreationAgentError(documentType, actualAgentType, expectedAgentType)

    class RequestedPrincipalNotAllowed(
        documentType: AuthorizationDocumentType,
        actualAgentType: AgentType,
        expectedAgentType: AgentType,
    ) : DocumentCreationAgentError(documentType, actualAgentType, expectedAgentType)
}
