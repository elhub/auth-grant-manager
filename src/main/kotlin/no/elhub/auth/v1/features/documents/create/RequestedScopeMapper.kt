package no.elhub.auth.v1.features.documents.create

import no.elhub.auth.v1.domain.AuthorizationScope
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType

fun RequestedScope.toAuthorizationScopes(): List<AuthorizationScope> = when (this) {
    is RequestedScope.ChangeOfEnergySupplierForOrganization -> listOf(AuthorizationScope(
        capability = PermissionCapability.WRITE,
        resourceType = ResourceType.MeteringPoint,
        appliesTo = listOf(ResourceConstraint.MeteringPoints(meteringPointIds)),
    ))

    is RequestedScope.MoveInAndChangeOfEnergySupplierForOrganization -> listOf(AuthorizationScope(
        capability = PermissionCapability.WRITE,
        resourceType = ResourceType.MeteringPoint,
        appliesTo = listOf(ResourceConstraint.MeteringPoints(meteringPointIds)),
    ))
}
