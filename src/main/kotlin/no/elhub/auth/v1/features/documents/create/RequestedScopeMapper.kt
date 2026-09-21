package no.elhub.auth.v1.features.documents.create

import no.elhub.auth.v1.domain.ResourceConstraint

fun RequestedScope.toResourceConstraints(): List<ResourceConstraint> = when (this) {
    is RequestedScope.ChangeOfEnergySupplierForOrganization -> listOf(meteringPointIds)
    is RequestedScope.MoveInAndChangeOfEnergySupplierForOrganization -> listOf(meteringPointIds)
}
