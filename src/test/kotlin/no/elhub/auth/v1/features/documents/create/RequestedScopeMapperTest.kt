package no.elhub.auth.v1.features.documents.create

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType

class RequestedScopeMapperTest : FunSpec({
    val meteringPointIds = setOf(MeteringPointId.create("707057500000000001"))

    test("maps supplier change scope to a writable metering point scope") {
        val scope = RequestedScope.ChangeOfEnergySupplierForOrganization(meteringPointIds)

        val authorizationScope = scope.toAuthorizationScopes().single()

        authorizationScope.capability shouldBe PermissionCapability.WRITE
        authorizationScope.resourceType shouldBe ResourceType.MeteringPoint
        authorizationScope.appliesTo shouldContainExactly listOf(
            ResourceConstraint.MeteringPoints(meteringPointIds),
        )
    }

    test("maps move-in scope to a writable scope with valid-from") {
        val validFrom = LocalDate(2026, 10, 1)
        val scope = RequestedScope.MoveInAndChangeOfEnergySupplierForOrganization(
            meteringPointIds = meteringPointIds,
            validFrom = validFrom,
        )

        val authorizationScope = scope.toAuthorizationScopes().single()

        authorizationScope.capability shouldBe PermissionCapability.WRITE
        authorizationScope.resourceType shouldBe ResourceType.MeteringPoint
        authorizationScope.appliesTo shouldContainExactly listOf(
            ResourceConstraint.MeteringPoints(meteringPointIds),
        )
    }
})
