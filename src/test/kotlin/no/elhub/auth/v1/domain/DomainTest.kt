package no.elhub.auth.v1.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.features.documents.create.RequestedScope

class DomainTest : FunSpec({
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

        shouldThrow<Errors.InvalidMeteringPointId> { MeteringPointId.create("") }
        shouldThrow<Errors.InvalidMeteringPointId> { MeteringPointId.create("70705750000000001") }
        shouldThrow<Errors.InvalidMeteringPointId> { MeteringPointId.create("7070575000000000001") }
        shouldThrow<Errors.InvalidMeteringPointId> { MeteringPointId.create("70705750000000000A") }
    }
})
