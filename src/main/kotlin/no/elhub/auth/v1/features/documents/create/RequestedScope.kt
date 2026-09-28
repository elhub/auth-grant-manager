package no.elhub.auth.v1.features.documents.create

import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.ResourceConstraint

sealed interface RequestedScope {
    val documentType: AuthorizationDocumentType

    data class ChangeOfEnergySupplierForOrganization(
        val meteringPointIds: ResourceConstraint.MeteringPoints,
    ) : RequestedScope {
        override val documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization
    }

    data class MoveInAndChangeOfEnergySupplierForOrganization(
        val meteringPointIds: ResourceConstraint.MeteringPoints,
        val validFrom: LocalDate,
    ) : RequestedScope {
        override val documentType = AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization
    }
}
