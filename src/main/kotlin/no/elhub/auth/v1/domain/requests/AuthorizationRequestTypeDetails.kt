package no.elhub.auth.v1.domain.requests

import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.domain.ResourceConstraint
import kotlin.time.Instant

sealed interface AuthorizationRequestTypeDetails {
    data class ChangeOfEnergySupplierForOrganization(
        val meteringPointIds: ResourceConstraint.MeteringPoints,
    ) : AuthorizationRequestTypeDetails

    data class MoveInAndChangeOfEnergySupplierForOrganization(
        val meteringPointIds: ResourceConstraint.MeteringPoints,
        val moveInDate: LocalDate,
    ) : AuthorizationRequestTypeDetails

    data class EnergySupplierFrameworkAgreement(
        val requestedGrantValidFrom: Instant?,
        val requestedGrantValidTo: Instant?,
    ) : AuthorizationRequestTypeDetails
}
