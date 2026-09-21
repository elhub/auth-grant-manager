package no.elhub.auth.v1.features.documents.create

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint

class RequestedScopeMapperTest : FunSpec({
    val meteringPointIds = setOf(MeteringPointId.create("707057500000000001"))

    test("maps supplier change scope to metering point constraints") {
        val scope = RequestedScope.ChangeOfEnergySupplierForOrganization(
            meteringPointIds = ResourceConstraint.MeteringPoints(meteringPointIds),
        )

        val resourceConstraints = scope.toResourceConstraints()

        resourceConstraints shouldContainExactly listOf(
            ResourceConstraint.MeteringPoints(meteringPointIds),
        )
    }

    test("maps move-in scope to metering point constraints") {
        val validFrom = LocalDate(2026, 10, 1)
        val scope = RequestedScope.MoveInAndChangeOfEnergySupplierForOrganization(
            meteringPointIds = ResourceConstraint.MeteringPoints(meteringPointIds),
            validFrom = validFrom,
        )

        val resourceConstraints = scope.toResourceConstraints()

        resourceConstraints shouldContainExactly listOf(
            ResourceConstraint.MeteringPoints(meteringPointIds),
        )
    }
})
