package no.elhub.auth.v1.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.v1.InputError
import no.elhub.auth.v1.features.documents.create.RequestedScope

class DomainTest : FunSpec({
    test("scope constraint attributes map API names separately from database names") {
        val attribute = AuthorizationScopeConstraintAttribute.fromApiName("meteringPoint.id")
        attribute shouldBe AuthorizationScopeConstraintAttribute.MeteringPointId
        attribute.apiName shouldBe "meteringPoint.id"
        attribute.name shouldBe "MeteringPointId"

        listOf("MeteringPointId", "unknown", "").forEach { apiName ->
            shouldThrow<InputError.InvalidFieldValueError> {
                AuthorizationScopeConstraintAttribute.fromApiName(apiName)
            }
        }
    }

    test("metering-point constraints require at least one ID") {
        shouldThrow<IllegalArgumentException> {
            ResourceConstraint.MeteringPoints(emptySet())
        }
    }

    test("requested scope owns its document type") {
        RequestedScope.ChangeOfEnergySupplierForOrganization(
            meteringPointIds = ResourceConstraint.MeteringPoints(
                setOf(MeteringPointId.create("707057500000000001")),
            ),
        ).documentType shouldBe AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization

        RequestedScope.MoveInAndChangeOfEnergySupplierForOrganization(
            meteringPointIds = ResourceConstraint.MeteringPoints(
                setOf(MeteringPointId.create("707057500000000001")),
            ),
            validFrom = kotlinx.datetime.LocalDate(2026, 10, 1),
        ).documentType shouldBe AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization
    }

    test("metering-point IDs must contain exactly 18 digits") {
        MeteringPointId.create("707057500000000001").value shouldBe "707057500000000001"

        shouldThrow<InputError.InvalidMeteringPointId> { MeteringPointId.create("") }
        shouldThrow<InputError.InvalidMeteringPointId> { MeteringPointId.create("70705750000000001") }
        shouldThrow<InputError.InvalidMeteringPointId> { MeteringPointId.create("7070575000000000001") }
        shouldThrow<InputError.InvalidMeteringPointId> { MeteringPointId.create("70705750000000000A") }
    }
})
