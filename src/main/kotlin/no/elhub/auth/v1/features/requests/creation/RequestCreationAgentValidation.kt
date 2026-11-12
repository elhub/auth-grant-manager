package no.elhub.auth.v1.features.requests.creation

import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationRequestType

fun validateRequestCreationAgents(
    requestType: AuthorizationRequestType,
    requestedBy: AuthorizationAgent,
    requestedPrincipal: AuthorizationAgent,
    requestedOf: AuthorizationAgent,
): Unit = when (requestType) {
    AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
    AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization,
    AuthorizationRequestType.EnergySupplierFrameworkAgreement,
    -> validateOrganizationRequestAgents(requestType, requestedBy, requestedPrincipal, requestedOf)
}

private fun validateOrganizationRequestAgents(
    requestType: AuthorizationRequestType,
    requestedBy: AuthorizationAgent,
    requestedPrincipal: AuthorizationAgent,
    requestedOf: AuthorizationAgent,
) {
    if (requestedBy.type != AgentType.OrganizationEntity) {
        throw RequestCreationAgentError.RequestedByNotAllowed(
            requestType = requestType,
            actualAgentType = requestedBy.type,
            expectedAgentType = AgentType.OrganizationEntity,
        )
    }
    if (requestedPrincipal.type != AgentType.Organization) {
        throw RequestCreationAgentError.RequestedPrincipalNotAllowed(
            requestType = requestType,
            actualAgentType = requestedPrincipal.type,
            expectedAgentType = AgentType.Organization,
        )
    }
    if (requestedOf.type != AgentType.Organization) {
        throw RequestCreationAgentError.RequestedOfNotAllowed(
            requestType = requestType,
            actualAgentType = requestedOf.type,
            expectedAgentType = AgentType.Organization,
        )
    }
}
