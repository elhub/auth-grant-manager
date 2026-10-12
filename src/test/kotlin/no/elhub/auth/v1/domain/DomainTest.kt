package no.elhub.auth.v1.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.v1.InputError
import no.elhub.auth.v1.features.documents.create.RequestedScope
import kotlin.time.Instant

class DomainTest : FunSpec({
    test("scope constraints accept matching attributes and reject mismatches, including after copying") {
        val values = listOf(
            ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001"))) to
                AuthorizationScopeConstraintAttribute.MeteringPointId,
            ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)) to
                AuthorizationScopeConstraintAttribute.ContractType,
            ResourceConstraint.ValidFrom(Instant.parse("2026-10-01T00:00:00Z")) to
                AuthorizationScopeConstraintAttribute.ValidFrom,
        )
        values.forEach { (value, attribute) ->
            val constraint = AuthorizationScopeConstraint(AuthorizationScopeConstraintKind.AllowedChanges, attribute, value)
            constraint.attribute shouldBe attribute
            constraint.value shouldBe value
            constraint.copy() shouldBe constraint

            values.filter { it.second != attribute }.forEach { (otherValue, otherAttribute) ->
                val error = shouldThrow<InputError.InvalidFieldValueError> {
                    AuthorizationScopeConstraint(AuthorizationScopeConstraintKind.AllowedChanges, otherAttribute, value)
                }
                error.detail shouldBe "Scope constraint attribute '${otherAttribute.apiName}' does not match value for '${attribute.apiName}'"
                shouldThrow<InputError.InvalidFieldValueError> { constraint.copy(attribute = otherAttribute) }
                shouldThrow<InputError.InvalidFieldValueError> { constraint.copy(value = otherValue) }
                constraint.copy(attribute = otherAttribute, value = otherValue).attribute shouldBe otherAttribute
            }
        }
    }

    test("scope constraint attributes map external names to Kotlin enum constants") {
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
