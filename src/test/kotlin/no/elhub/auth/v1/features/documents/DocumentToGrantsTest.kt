package no.elhub.auth.v1.features.documents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
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
import kotlin.time.Duration.Companion.nanoseconds
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

    context("EnergySupplierFrameworkAgreement") {
        val now = Instant.parse("2026-10-26T10:00:00Z")
        val startOfToday = Instant.parse("2026-10-25T23:00:00Z")
        val contractTypeConstraint = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
            attribute = AuthorizationScopeConstraintAttribute.ContractType,
            value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier)),
        )
        val requestedScope = RequestedAuthorizationScope(
            resourceType = ResourceType.MeteringPointContract,
            constraints = listOf(contractTypeConstraint),
        )
        val document = AuthorizationDocument.new(
            documentType = AuthorizationDocumentType.EnergySupplierFrameworkAgreement,
            requestedScopes = listOf(requestedScope),
            externalReference = "framework-123",
            meta = JsonObject(emptyMap()),
            validTo = Instant.parse("2026-11-24T10:00:00Z"),
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            pdfBytes = byteArrayOf(),
        ).copy(validFrom = Instant.parse("2026-10-24T10:00:00Z"))

        test("creates exactly one persistent Document-source Write grant covering all metering points with unchanged role mapping") {
            val grant = document.toGrants(approvedBy, now).single()

            grant.type shouldBe AuthorizationGrantType.Persistent
            grant.status shouldBe AuthorizationGrantStatus.Active
            grant.sourceType shouldBe AuthorizationGrantSourceType.Document
            grant.sourceId shouldBe UUID.fromString(document.id)
            grant.principal shouldBe requestedPrincipal
            grant.grantedBy shouldBe requestedOf
            grant.grantedTo shouldBe requestedBy
            grant.approvedBy shouldBe approvedBy
            grant.scopes shouldBe listOf(
                GrantedAuthorizationScope(
                    capability = PermissionCapability.Write,
                    resourceType = ResourceType.MeteringPointContract,
                    constraints = listOf(contractTypeConstraint),
                ),
            )
            grant.validFrom shouldBe startOfToday
            grant.validTo shouldBe null
            grant.createdAt shouldBe now
            grant.updatedAt shouldBe now
        }

        val past = Instant.parse("2026-10-01T00:00:00Z")
        val earlierToday = Instant.parse("2026-10-26T05:00:00Z")
        val laterToday = Instant.parse("2026-10-26T15:00:00Z")
        val future = Instant.parse("2026-12-01T00:00:00Z")
        val laterFuture = Instant.parse("2027-12-01T00:00:00Z")

        listOf(
            Triple(null, null, startOfToday to null),
            Triple(null, future, startOfToday to future),
            Triple(past, null, startOfToday to null),
            Triple(past, future, startOfToday to future),
            Triple(startOfToday, null, startOfToday to null),
            Triple(earlierToday, null, earlierToday to null),
            Triple(now, null, now to null),
            Triple(now + 1.nanoseconds, null, (now + 1.nanoseconds) to null),
            Triple(laterToday, null, laterToday to null),
            Triple(future, null, future to null),
            Triple(future, laterFuture, future to laterFuture),
            Triple(future, future + 1.nanoseconds, future to (future + 1.nanoseconds)),
            Triple(null, laterToday, startOfToday to laterToday),
        ).forEach { (requestedFrom, requestedTo, expected) ->
            test("requested $requestedFrom..$requestedTo gives grant ${expected.first}..${expected.second}") {
                val grant = document.copy(
                    requestedGrantValidFrom = requestedFrom,
                    requestedGrantValidTo = requestedTo,
                ).toGrants(approvedBy, now).single()

                grant.validFrom shouldBe expected.first
                grant.validTo shouldBe expected.second
                grant.status.name shouldBe if (expected.first > now) "Pending" else "Active"
                grant.createdAt shouldBe now
                grant.updatedAt shouldBe now
            }
        }

        listOf(
            null to past,
            past to past,
            null to startOfToday,
            null to earlierToday,
            null to now,
            future to future,
            laterFuture to future,
        ).forEach { (requestedFrom, requestedTo) ->
            test("rejects requested $requestedFrom..$requestedTo with a document confirmation error") {
                shouldThrow<ConfirmDocumentError.InvalidGrantValidity> {
                    document.copy(
                        requestedGrantValidFrom = requestedFrom,
                        requestedGrantValidTo = requestedTo,
                    ).toGrants(approvedBy, now)
                }
            }
        }

        listOf(
            "2026-07-01T10:00:00Z" to "2026-06-30T22:00:00Z",
            "2026-10-26T23:30:00Z" to "2026-10-26T23:00:00Z",
            "2026-03-29T10:00:00Z" to "2026-03-28T23:00:00Z",
            "2026-10-25T10:00:00Z" to "2026-10-24T22:00:00Z",
        ).forEach { (approvalTime, expectedStart) ->
            test("uses the Oslo start of day when approved at $approvalTime") {
                val now = Instant.parse(approvalTime)
                val grant = document.toGrants(approvedBy, now).single()

                grant.validFrom shouldBe Instant.parse(expectedStart)
                grant.createdAt shouldBe now
                grant.updatedAt shouldBe now
            }
        }

        test("rejects missing or multiple requested scopes") {
            listOf(emptyList(), listOf(requestedScope, requestedScope)).forEach { scopes ->
                shouldThrow<InvalidDocumentScopes> {
                    document.copy(requestedScopes = scopes).toGrants(approvedBy, now)
                }
            }
        }

        val meteringPointConstraint = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
            attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
            value = ResourceConstraint.MeteringPoints(meteringPointIds),
        )
        val moveInConstraint = AuthorizationScopeConstraint(
            constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
            attribute = AuthorizationScopeConstraintAttribute.ValidFrom,
            value = ResourceConstraint.ValidFrom(moveInDate),
        )
        listOf(
            "missing contract types" to emptyList(),
            "duplicate contract types" to listOf(contractTypeConstraint, contractTypeConstraint),
            "AppliesTo contract types" to listOf(contractTypeConstraint.copy(constraintKind = AuthorizationScopeConstraintKind.AppliesTo)),
            "only EndUser" to listOf(
                contractTypeConstraint.copy(value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EndUser))),
            ),
            "only EnergySupplier" to listOf(
                contractTypeConstraint.copy(value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier))),
            ),
            "metering-point restriction" to listOf(contractTypeConstraint, meteringPointConstraint),
            "move-in date" to listOf(contractTypeConstraint, moveInConstraint),
        ).forEach { (description, constraints) ->
            test("rejects framework scope with $description") {
                shouldThrow<InvalidDocumentScopes> {
                    document.copy(requestedScopes = listOf(requestedScope.copy(constraints = constraints))).toGrants(approvedBy, now)
                }
            }
        }
    }

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
            val now = Instant.parse("2026-10-26T10:15:30Z")
            val grants = document.toGrants(approvedBy, now)

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
            grant.validFrom shouldBe Instant.parse("2026-10-25T23:00:00Z")
            grant.validTo shouldBe Instant.parse("2028-10-26T22:00:00Z")
            grant.createdAt shouldBe now
            grant.updatedAt shouldBe now
            (grant.validFrom == document.validFrom) shouldBe false
            (grant.validTo == document.validTo) shouldBe false
        }

        test("keeps two-year Oslo date-based validity independent of requested grant dates") {
            val now = Instant.parse("2026-10-26T10:15:30Z")
            val grant = document.copy(
                requestedGrantValidFrom = Instant.parse("2025-01-01T00:00:00Z"),
                requestedGrantValidTo = Instant.parse("2025-02-01T00:00:00Z"),
            ).toGrants(approvedBy, now).single()

            grant.validFrom shouldBe Instant.parse("2026-10-25T23:00:00Z")
            grant.validTo shouldBe Instant.parse("2028-10-26T22:00:00Z")
            grant.createdAt shouldBe now
            grant.updatedAt shouldBe now
        }

        test("several metering points produce separate EnergySupplier Write grants with the same validity window") {
            val scope = requestedScope.copy(
                constraints = listOf(
                    meteringPointConstraint.copy(value = ResourceConstraint.MeteringPoints(meteringPointIds)),
                    contractTypeConstraint,
                )
            )
            val multiPointDocument = document.copy(requestedScopes = listOf(scope))

            val now = Instant.parse("2026-10-26T10:15:30Z")
            val grants = multiPointDocument.toGrants(approvedBy, now)

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
                grant.validFrom shouldBe Instant.parse("2026-10-25T23:00:00Z")
                grant.validTo shouldBe Instant.parse("2028-10-26T22:00:00Z")
                grant.createdAt shouldBe now
                grant.updatedAt shouldBe now
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
            val now = Instant.parse("2026-10-26T10:15:30Z")
            val grants = document.toGrants(approvedBy, now)

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
            grant.validFrom shouldBe Instant.parse("2026-10-25T23:00:00Z")
            grant.validTo shouldBe Instant.parse("2028-10-26T22:00:00Z")
            grant.createdAt shouldBe now
            grant.updatedAt shouldBe now
            (grant.validFrom == document.validFrom) shouldBe false
            (grant.validTo == document.validTo) shouldBe false
        }

        test("keeps two-year Oslo date-based validity independent of requested grant dates") {
            val now = Instant.parse("2026-10-26T10:15:30Z")
            val grant = document.copy(
                requestedGrantValidFrom = Instant.parse("2025-01-01T00:00:00Z"),
                requestedGrantValidTo = Instant.parse("2025-02-01T00:00:00Z"),
            ).toGrants(approvedBy, now).single()

            grant.validFrom shouldBe Instant.parse("2026-10-25T23:00:00Z")
            grant.validTo shouldBe Instant.parse("2028-10-26T22:00:00Z")
            grant.createdAt shouldBe now
            grant.updatedAt shouldBe now
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

            val now = Instant.parse("2026-10-26T10:15:30Z")
            val grants = multiPointDocument.toGrants(approvedBy, now)

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
                grant.validFrom shouldBe Instant.parse("2026-10-25T23:00:00Z")
                grant.validTo shouldBe Instant.parse("2028-10-26T22:00:00Z")
                grant.createdAt shouldBe now
                grant.updatedAt shouldBe now
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
