package no.elhub.auth.v1.features.requests.creation

import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.domain.AuthorizationRequestType

sealed class RequestCreationAgentError(
    val requestType: AuthorizationRequestType,
    val actualAgentType: PartyType,
    val expectedAgentType: PartyType,
) : Errors() {
    class RequestedByNotAllowed(
        requestType: AuthorizationRequestType,
        actualAgentType: PartyType,
        expectedAgentType: PartyType,
    ) : RequestCreationAgentError(requestType, actualAgentType, expectedAgentType)

    class RequestedOfNotAllowed(
        requestType: AuthorizationRequestType,
        actualAgentType: PartyType,
        expectedAgentType: PartyType,
    ) : RequestCreationAgentError(requestType, actualAgentType, expectedAgentType)

    class RequestedPrincipalNotAllowed(
        requestType: AuthorizationRequestType,
        actualAgentType: PartyType,
        expectedAgentType: PartyType,
    ) : RequestCreationAgentError(requestType, actualAgentType, expectedAgentType)
}
