package no.elhub.auth.v1.features.requests.creation

import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.domain.AuthorizationRequestType

sealed class RequestCreationAgentError(
    val requestType: AuthorizationRequestType,
    val actualAgentType: AgentType,
    val expectedAgentType: AgentType,
) : Errors() {
    class RequestedByNotAllowed(
        requestType: AuthorizationRequestType,
        actualAgentType: AgentType,
        expectedAgentType: AgentType,
    ) : RequestCreationAgentError(requestType, actualAgentType, expectedAgentType)

    class RequestedOfNotAllowed(
        requestType: AuthorizationRequestType,
        actualAgentType: AgentType,
        expectedAgentType: AgentType,
    ) : RequestCreationAgentError(requestType, actualAgentType, expectedAgentType)

    class RequestedPrincipalNotAllowed(
        requestType: AuthorizationRequestType,
        actualAgentType: AgentType,
        expectedAgentType: AgentType,
    ) : RequestCreationAgentError(requestType, actualAgentType, expectedAgentType)
}
