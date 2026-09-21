package no.elhub.auth.v1.features.documents.create

import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.MeteringPointId

sealed interface RequestedScope {
    val documentType: AuthorizationDocumentType

    data class ChangeOfEnergySupplierForOrganization(
        val meteringPointIds: Set<MeteringPointId>,
    ) : RequestedScope {
        override val documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization
    }

    data class MoveInAndChangeOfEnergySupplierForOrganization(
        val meteringPointIds: Set<MeteringPointId>,
        val validFrom: LocalDate,
    ) : RequestedScope {
        override val documentType = AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization
    }
}
