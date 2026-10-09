package no.elhub.auth.v1.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.v1.InputError

class DomainTest : FunSpec({
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

    test("requested scope describes its resource") {
        RequestedAuthorizationScope(
            resourceType = ResourceType.MeteringPointContract,
            constraints = listOf(
                AuthorizationScopeConstraint(
                    constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                    attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                    value = ResourceConstraint.MeteringPoints(
                        setOf(MeteringPointId.create("707057500000000001")),
                    ),
                ),
            ),
        ).resourceType shouldBe ResourceType.MeteringPointContract
    }

    test("metering-point IDs must contain exactly 18 digits") {
        MeteringPointId.create("707057500000000001").value shouldBe "707057500000000001"

        shouldThrow<InputError.InvalidMeteringPointId> { MeteringPointId.create("") }
        shouldThrow<InputError.InvalidMeteringPointId> { MeteringPointId.create("70705750000000001") }
        shouldThrow<InputError.InvalidMeteringPointId> { MeteringPointId.create("7070575000000000001") }
        shouldThrow<InputError.InvalidMeteringPointId> { MeteringPointId.create("70705750000000000A") }
    }
})
