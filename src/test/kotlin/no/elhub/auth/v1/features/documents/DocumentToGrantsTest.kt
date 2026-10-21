package no.elhub.auth.v1.features.documents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

class DocumentToGrantsTest : FunSpec({
    val meteringPointIds = setOf(
        MeteringPointId.create("707057500000000001"),
        MeteringPointId.create("707057500000000002"),
        MeteringPointId.create("707057500000000003"),
    )
    val requestedBy = AuthorizationParty("7080005053246", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("999888777", PartyType.Organization)
    val requestedOf = AuthorizationParty(UUID.randomUUID().toString(), PartyType.Person)
    val approvedBy = AuthorizationParty(UUID.randomUUID().toString(), PartyType.Person)
    val moveInDate = Instant.parse("2026-09-30T22:00:00Z")

    context("ChangeOfEnergySupplierForOrganization") {
        val meteringPointConstraint = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
            attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
            value = ResourceConstraint.MeteringPoints(setOf(meteringPointIds.first())),
        )
        val contractTypeConstraint = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
            attribute = AuthorizationScopeConstraintAttribute.ContractType,
            value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
        )
        val requestedScope = RequestedAuthorizationScope(
            resourceType = ResourceType.MeteringPointContract,
            constraints = listOf(meteringPointConstraint, contractTypeConstraint),
        )
        val document = AuthorizationDocument.new(
            documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
            requestedScopes = listOf(requestedScope),
            externalReference = "contract-123",
            meta = JsonObject(emptyMap()),
            validTo = Instant.parse("2026-10-24T10:00:00Z"),
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            pdfBytes = byteArrayOf(),
        ).copy(validFrom = Instant.parse("2026-09-24T10:00:00Z"))

        test("one metering point produces one EnergySupplier Write grant") {
            val before = Clock.System.now()
            val grants = document.toGrants(approvedBy)
            val after = Clock.System.now()

            grants.size shouldBe 1
            val grant = grants.single()
            grant.scopes shouldBe listOf(
                GrantedAuthorizationScope(
                    capability = PermissionCapability.Write,
                    resourceType = ResourceType.MeteringPointContract,
                    constraints = listOf(
                        AuthorizationScopeConstraint(
                            constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                            attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                            value = ResourceConstraint.MeteringPoints(setOf(meteringPointIds.first())),
                        ),
                        AuthorizationScopeConstraint(
                            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                            attribute = AuthorizationScopeConstraintAttribute.ContractType,
                            value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
                        ),
                    ),
                ),
            )
            grant.type shouldBe AuthorizationGrantType.Consumable
            grant.status shouldBe AuthorizationGrantStatus.Active
            grant.sourceType shouldBe AuthorizationGrantSourceType.Document
            grant.sourceId shouldBe UUID.fromString(document.id)
            grant.principal shouldBe requestedPrincipal
            grant.grantedBy shouldBe requestedOf
            grant.grantedTo shouldBe requestedBy
            grant.approvedBy shouldBe approvedBy
            (grant.validFrom in before..after) shouldBe true
            grant.validTo shouldBe grant.validFrom.plus(1, DateTimeUnit.YEAR, TimeZone.UTC)
            (grant.validFrom == document.validFrom) shouldBe false
            (grant.validTo == document.validTo) shouldBe false
        }

        test("several metering points produce separate EnergySupplier Write grants with the same validity window") {
            val scope = requestedScope.copy(
                constraints = listOf(
                    meteringPointConstraint.copy(value = ResourceConstraint.MeteringPoints(meteringPointIds)),
                    contractTypeConstraint,
                )
            )
            val multiPointDocument = document.copy(requestedScopes = listOf(scope))

            val before = Clock.System.now()
            val grants = multiPointDocument.toGrants(approvedBy)
            val after = Clock.System.now()

            grants.size shouldBe meteringPointIds.size
            grants.map { it.id }.toSet().size shouldBe meteringPointIds.size
            grants.map { it.scopes }.shouldContainExactlyInAnyOrder(
                meteringPointIds.map { id ->
                    listOf(
                        GrantedAuthorizationScope(
                            capability = PermissionCapability.Write,
                            resourceType = ResourceType.MeteringPointContract,
                            constraints = listOf(
                                AuthorizationScopeConstraint(
                                    constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                                    attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                                    value = ResourceConstraint.MeteringPoints(setOf(id)),
                                ),
                                AuthorizationScopeConstraint(
                                    constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                                    attribute = AuthorizationScopeConstraintAttribute.ContractType,
                                    value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
                                ),
                            ),
                        )
                    )
                },
            )
            grants.map { it.validFrom }.toSet().size shouldBe 1
            grants.map { it.validTo }.toSet().size shouldBe 1
            grants.forEach { grant ->
                grant.sourceId shouldBe UUID.fromString(document.id)
                (grant.validFrom in before..after) shouldBe true
                grant.validTo shouldBe grant.validFrom.plus(1, DateTimeUnit.YEAR, TimeZone.UTC)
            }
        }

        test("rejects a document without exactly one requested scope") {
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = emptyList()).toGrants(approvedBy) }
            shouldThrow<InvalidDocumentScopes> {
                document.copy(requestedScopes = listOf(requestedScope, requestedScope)).toGrants(approvedBy)
            }
        }

        test("rejects a scope without metering points") {
            val invalidScope = requestedScope.copy(constraints = listOf(contractTypeConstraint))
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = listOf(invalidScope)).toGrants(approvedBy) }
        }

        test("rejects duplicate metering-point constraints") {
            val invalidScope = requestedScope.copy(constraints = requestedScope.constraints + meteringPointConstraint)
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = listOf(invalidScope)).toGrants(approvedBy) }
        }

        test("rejects a scope with EndUser instead of EnergySupplier") {
            val invalidScope = requestedScope.copy(
                constraints = listOf(
                    meteringPointConstraint,
                    contractTypeConstraint.copy(value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EndUser))),
                )
            )
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = listOf(invalidScope)).toGrants(approvedBy) }
        }

        test("rejects an unexpected move-in-date constraint") {
            val moveInConstraint = AuthorizationScopeConstraint(
                constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                attribute = AuthorizationScopeConstraintAttribute.ValidFrom,
                value = ResourceConstraint.ValidFrom(moveInDate),
            )
            val invalidScope = requestedScope.copy(constraints = requestedScope.constraints + moveInConstraint)
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = listOf(invalidScope)).toGrants(approvedBy) }
        }
    }

    context("MoveInAndChangeOfEnergySupplierForOrganization") {
        val meteringPointConstraint = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
            attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
            value = ResourceConstraint.MeteringPoints(setOf(meteringPointIds.first())),
        )
        val contractTypeConstraint = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
            attribute = AuthorizationScopeConstraintAttribute.ContractType,
            value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier)),
        )
        val moveInConstraint = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
            attribute = AuthorizationScopeConstraintAttribute.ValidFrom,
            value = ResourceConstraint.ValidFrom(moveInDate),
        )
        val requestedScope = RequestedAuthorizationScope(
            resourceType = ResourceType.MeteringPointContract,
            constraints = listOf(meteringPointConstraint, contractTypeConstraint, moveInConstraint),
        )
        val document = AuthorizationDocument.new(
            documentType = AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization,
            requestedScopes = listOf(requestedScope),
            externalReference = "contract-123",
            meta = JsonObject(emptyMap()),
            validTo = Instant.parse("2026-10-24T10:00:00Z"),
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            pdfBytes = byteArrayOf(),
        ).copy(validFrom = Instant.parse("2026-09-24T10:00:00Z"))

        test("one metering point produces one EndUser and EnergySupplier Write grant with the move-in date") {
            val before = Clock.System.now()
            val grants = document.toGrants(approvedBy)
            val after = Clock.System.now()

            grants.size shouldBe 1
            val grant = grants.single()
            grant.scopes shouldBe listOf(
                GrantedAuthorizationScope(
                    capability = PermissionCapability.Write,
                    resourceType = ResourceType.MeteringPointContract,
                    constraints = listOf(
                        AuthorizationScopeConstraint(
                            constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                            attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                            value = ResourceConstraint.MeteringPoints(setOf(meteringPointIds.first())),
                        ),
                        AuthorizationScopeConstraint(
                            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                            attribute = AuthorizationScopeConstraintAttribute.ContractType,
                            value = ResourceConstraint.ContractTypes(
                                setOf(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier),
                            ),
                        ),
                        AuthorizationScopeConstraint(
                            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                            attribute = AuthorizationScopeConstraintAttribute.ValidFrom,
                            value = ResourceConstraint.ValidFrom(moveInDate),
                        ),
                    ),
                ),
            )
            grant.type shouldBe AuthorizationGrantType.Consumable
            grant.status shouldBe AuthorizationGrantStatus.Active
            grant.sourceType shouldBe AuthorizationGrantSourceType.Document
            grant.sourceId shouldBe UUID.fromString(document.id)
            grant.principal shouldBe requestedPrincipal
            grant.grantedBy shouldBe requestedOf
            grant.grantedTo shouldBe requestedBy
            grant.approvedBy shouldBe approvedBy
            (grant.validFrom in before..after) shouldBe true
            grant.validTo shouldBe grant.validFrom.plus(1, DateTimeUnit.YEAR, TimeZone.UTC)
            (grant.validFrom == document.validFrom) shouldBe false
            (grant.validTo == document.validTo) shouldBe false
        }

        test("several metering points produce separate EndUser and EnergySupplier Write grants with the move-in date") {
            val scope = requestedScope.copy(
                constraints = listOf(
                    meteringPointConstraint.copy(value = ResourceConstraint.MeteringPoints(meteringPointIds)),
                    contractTypeConstraint,
                    moveInConstraint,
                )
            )
            val multiPointDocument = document.copy(requestedScopes = listOf(scope))

            val before = Clock.System.now()
            val grants = multiPointDocument.toGrants(approvedBy)
            val after = Clock.System.now()

            grants.size shouldBe meteringPointIds.size
            grants.map { it.id }.toSet().size shouldBe meteringPointIds.size
            grants.map { it.scopes }.shouldContainExactlyInAnyOrder(
                meteringPointIds.map { id ->
                    listOf(
                        GrantedAuthorizationScope(
                            capability = PermissionCapability.Write,
                            resourceType = ResourceType.MeteringPointContract,
                            constraints = listOf(
                                AuthorizationScopeConstraint(
                                    constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                                    attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                                    value = ResourceConstraint.MeteringPoints(setOf(id)),
                                ),
                                AuthorizationScopeConstraint(
                                    constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                                    attribute = AuthorizationScopeConstraintAttribute.ContractType,
                                    value = ResourceConstraint.ContractTypes(
                                        setOf(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier),
                                    ),
                                ),
                                AuthorizationScopeConstraint(
                                    constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                                    attribute = AuthorizationScopeConstraintAttribute.ValidFrom,
                                    value = ResourceConstraint.ValidFrom(moveInDate),
                                ),
                            ),
                        )
                    )
                },
            )
            grants.map { it.validFrom }.toSet().size shouldBe 1
            grants.map { it.validTo }.toSet().size shouldBe 1
            grants.forEach { grant ->
                grant.sourceId shouldBe UUID.fromString(document.id)
                (grant.validFrom in before..after) shouldBe true
                grant.validTo shouldBe grant.validFrom.plus(1, DateTimeUnit.YEAR, TimeZone.UTC)
            }
        }

        test("rejects a document without exactly one requested scope") {
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = emptyList()).toGrants(approvedBy) }
            shouldThrow<InvalidDocumentScopes> {
                document.copy(requestedScopes = listOf(requestedScope, requestedScope)).toGrants(approvedBy)
            }
        }

        test("rejects a scope without metering points") {
            val invalidScope = requestedScope.copy(constraints = listOf(contractTypeConstraint, moveInConstraint))
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = listOf(invalidScope)).toGrants(approvedBy) }
        }

        test("rejects duplicate move-in-date constraints") {
            val invalidScope = requestedScope.copy(constraints = requestedScope.constraints + moveInConstraint)
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = listOf(invalidScope)).toGrants(approvedBy) }
        }

        test("rejects a scope without a move-in date") {
            val invalidScope = requestedScope.copy(constraints = listOf(meteringPointConstraint, contractTypeConstraint))
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = listOf(invalidScope)).toGrants(approvedBy) }
        }

        test("rejects a scope with only EnergySupplier contract changes") {
            val invalidScope = requestedScope.copy(
                constraints = listOf(
                    meteringPointConstraint,
                    contractTypeConstraint.copy(value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier))),
                    moveInConstraint,
                )
            )
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = listOf(invalidScope)).toGrants(approvedBy) }
        }

        test("rejects an unexpected extra scope constraint") {
            val extraConstraint = moveInConstraint.copy(constraintKind = AuthorizationScopeConstraintKind.AppliesTo)
            val invalidScope = requestedScope.copy(constraints = requestedScope.constraints + extraConstraint)
            shouldThrow<InvalidDocumentScopes> { document.copy(requestedScopes = listOf(invalidScope)).toGrants(approvedBy) }
        }
    }
})
