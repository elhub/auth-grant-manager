package no.elhub.auth.v1.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.v1.DomainError
import no.elhub.auth.v1.InputError
import no.elhub.auth.v1.features.documents.create.DocumentAction
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

    test("document action owns its document type") {
        DocumentAction.ChangeOfEnergySupplierForOrganization(
            meteringPointIds = ResourceConstraint.MeteringPoints(
                setOf(MeteringPointId.create("707057500000000001")),
            ),
        ).documentType shouldBe AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization

        DocumentAction.MoveInAndChangeOfEnergySupplierForOrganization(
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

    test("external agent identifiers are classified by digit length") {
        AgentExternalIdentifier.create("123456789") shouldBe
            AgentExternalIdentifier.OrganizationIdentifier("123456789")
        AgentExternalIdentifier.create("01019012345") shouldBe
            AgentExternalIdentifier.PersonIdentifier("01019012345")
        AgentExternalIdentifier.create("1234567890123") shouldBe
            AgentExternalIdentifier.MarketPartyIdentifier("1234567890123")
    }

    test("external agent identifier factory rejects unsupported lengths and non-digits") {
        listOf(
            "",
            "12345678",
            "1234567890",
            "123456789012",
            "12345678901234",
            "12345678A",
            "1234567890A",
            "123456789012A",
        )
            .forEach { value ->
                shouldThrow<DomainError.InvalidExternalAgentIdentifier> {
                    AgentExternalIdentifier.create(value)
                }
            }
    }

    test("external agent identifier variants enforce their own formats") {
        shouldThrow<DomainError.InvalidExternalAgentIdentifier> {
            AgentExternalIdentifier.OrganizationIdentifier("12345678A")
        }
        shouldThrow<DomainError.InvalidExternalAgentIdentifier> {
            AgentExternalIdentifier.PersonIdentifier("1234567890A")
        }
        shouldThrow<DomainError.InvalidExternalAgentIdentifier> {
            AgentExternalIdentifier.MarketPartyIdentifier("123456789012A")
        }
    }
})
