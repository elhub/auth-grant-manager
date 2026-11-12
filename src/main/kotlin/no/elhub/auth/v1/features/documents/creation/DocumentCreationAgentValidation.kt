package no.elhub.auth.v1.features.documents.creation

import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationDocumentType

fun validateDocumentCreationAgents(
    documentType: AuthorizationDocumentType,
    requestedBy: AuthorizationAgent,
    requestedPrincipal: AuthorizationAgent,
    requestedOf: AuthorizationAgent,
): Unit = when (documentType) {
    AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
    AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization,
    AuthorizationDocumentType.EnergySupplierFrameworkAgreement,
    -> validateOrganizationDocumentAgents(documentType, requestedBy, requestedPrincipal, requestedOf)
}

private fun validateOrganizationDocumentAgents(
    documentType: AuthorizationDocumentType,
    requestedBy: AuthorizationAgent,
    requestedPrincipal: AuthorizationAgent,
    requestedOf: AuthorizationAgent,
) {
    if (requestedBy.type != AgentType.OrganizationEntity) {
        throw DocumentCreationAgentError.RequestedByNotAllowed(
            documentType = documentType,
            actualAgentType = requestedBy.type,
            expectedAgentType = AgentType.OrganizationEntity,
        )
    }
    if (requestedPrincipal.type != AgentType.Organization) {
        throw DocumentCreationAgentError.RequestedPrincipalNotAllowed(
            documentType = documentType,
            actualAgentType = requestedPrincipal.type,
            expectedAgentType = AgentType.Organization,
        )
    }
    if (requestedOf.type != AgentType.Organization) {
        throw DocumentCreationAgentError.RequestedOfNotAllowed(
            documentType = documentType,
            actualAgentType = requestedOf.type,
            expectedAgentType = AgentType.Organization,
        )
    }
}
