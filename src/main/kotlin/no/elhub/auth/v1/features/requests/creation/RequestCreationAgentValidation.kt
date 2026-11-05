package no.elhub.auth.v1.features.requests.creation

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.domain.AuthorizationRequestType

fun validateRequestCreationAgents(
    requestType: AuthorizationRequestType,
    requestedBy: AuthorizationParty,
    requestedPrincipal: AuthorizationParty,
    requestedOf: AuthorizationParty,
): Unit = when (requestType) {
    AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
    AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization,
    AuthorizationRequestType.EnergySupplierFrameworkAgreement,
    -> validateOrganizationRequestAgents(requestType, requestedBy, requestedPrincipal, requestedOf)
}

private fun validateOrganizationRequestAgents(
    requestType: AuthorizationRequestType,
    requestedBy: AuthorizationParty,
    requestedPrincipal: AuthorizationParty,
    requestedOf: AuthorizationParty,
) {
    if (requestedBy.type != PartyType.OrganizationEntity) {
        throw RequestCreationAgentError.RequestedByNotAllowed(
            requestType = requestType,
            actualAgentType = requestedBy.type,
            expectedAgentType = PartyType.OrganizationEntity,
        )
    }
    if (requestedPrincipal.type != PartyType.Organization) {
        throw RequestCreationAgentError.RequestedPrincipalNotAllowed(
            requestType = requestType,
            actualAgentType = requestedPrincipal.type,
            expectedAgentType = PartyType.Organization,
        )
    }
    if (requestedOf.type != PartyType.Organization) {
        throw RequestCreationAgentError.RequestedOfNotAllowed(
            requestType = requestType,
            actualAgentType = requestedOf.type,
            expectedAgentType = PartyType.Organization,
        )
    }
}
