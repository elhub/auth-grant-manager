package no.elhub.auth.v1.features.documents.creation

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v1.domain.AuthorizationDocumentType

fun validateDocumentCreationAgents(
    documentType: AuthorizationDocumentType,
    requestedBy: AuthorizationParty,
    requestedPrincipal: AuthorizationParty,
    requestedOf: AuthorizationParty,
): Unit = when (documentType) {
    AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
    AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization,
    AuthorizationDocumentType.EnergySupplierFrameworkAgreement,
    -> validateOrganizationDocumentAgents(documentType, requestedBy, requestedPrincipal, requestedOf)
}

private fun validateOrganizationDocumentAgents(
    documentType: AuthorizationDocumentType,
    requestedBy: AuthorizationParty,
    requestedPrincipal: AuthorizationParty,
    requestedOf: AuthorizationParty,
) {
    if (requestedBy.type != PartyType.OrganizationEntity) {
        throw DocumentCreationAgentError.RequestedByNotAllowed(
            documentType = documentType,
            actualAgentType = requestedBy.type,
            expectedAgentType = PartyType.OrganizationEntity,
        )
    }
    if (requestedPrincipal.type != PartyType.Organization) {
        throw DocumentCreationAgentError.RequestedPrincipalNotAllowed(
            documentType = documentType,
            actualAgentType = requestedPrincipal.type,
            expectedAgentType = PartyType.Organization,
        )
    }
    if (requestedOf.type != PartyType.Organization) {
        throw DocumentCreationAgentError.RequestedOfNotAllowed(
            documentType = documentType,
            actualAgentType = requestedOf.type,
            expectedAgentType = PartyType.Organization,
        )
    }
}
