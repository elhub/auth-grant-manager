package no.elhub.auth.v1.domain.requests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint

class AuthorizationRequestSpecificationTest : FunSpec({
    val meteringPoints = ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001")))
    val detailsByType = mapOf(
        AuthorizationRequestType.ChangeOfEnergySupplierForOrganization to
            AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(meteringPoints),
        AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization to
            AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization(meteringPoints, LocalDate(2026, 10, 1)),
        AuthorizationRequestType.EnergySupplierFrameworkAgreement to
            AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(null, null),
    )

    detailsByType.forEach { (requestType, details) ->
        test("derives $requestType from its details") {
            AuthorizationRequestSpecification(null, null, details).requestType shouldBe requestType
        }

        test("copying the specification with $requestType details updates its type") {
            val specification = AuthorizationRequestSpecification(
                null,
                null,
                AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(null, null),
            )

            specification.copy(details = details).requestType shouldBe requestType
        }
    }
})
