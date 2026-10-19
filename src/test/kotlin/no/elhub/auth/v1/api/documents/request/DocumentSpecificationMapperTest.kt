package no.elhub.auth.v1.api.documents.request

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.documents.DocumentSpecification

class DocumentSpecificationMapperTest : FunSpec({
    val validMeteringPointId = "707057500000000001"
    val supplierChangeType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization
    val moveInType = AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization
    val invalidMeteringPointDetail =
        "requestedScopes[0].appliesTo meteringPoint.id values must be valid 18-digit metering-point IDs"
    val emptyMeteringPointsDetail =
        "requestedScopes[0].appliesTo meteringPoint.id must contain at least one metering point"
    val appliesToDetail =
        "requestedScopes[0].appliesTo must contain exactly one meteringPoint.id constraint"
    val resourceTypeDetail = "requestedScopes[0].resourceType must be 'MeteringPointContract'"
    val oneScopeDetail = "requestedScopes must contain exactly one scope for this document type"
    val validFromConstraintDetail =
        "requestedScopes[0].allowedChanges must contain exactly one validFrom constraint"

    fun meteringPointConstraint(values: List<String> = listOf(validMeteringPointId)) =
        ScopeConstraint(attribute = "meteringPoint.id", value = values)

    fun supplierScope(
        resourceType: String = "MeteringPointContract",
        appliesTo: List<ScopeConstraint> = listOf(meteringPointConstraint()),
        allowedChanges: List<ScopeConstraint> = emptyList(),
    ) = RequestedScope(resourceType, appliesTo, allowedChanges)

    fun moveInScope(
        resourceType: String = "MeteringPointContract",
        appliesTo: List<ScopeConstraint> = listOf(meteringPointConstraint()),
        validFrom: List<String> = listOf("2026-10-01"),
        allowedChanges: List<ScopeConstraint> = listOf(ScopeConstraint("validFrom", validFrom)),
    ) = RequestedScope(resourceType, appliesTo, allowedChanges)

    fun invalidPayload(block: () -> Unit, expectedDetail: String) {
        shouldThrow<InputError.InvalidCreateAuthorizationDocumentPayload>(block).detail shouldBe expectedDetail
    }

    context("ChangeOfEnergySupplierForOrganization") {
        test("maps metering-point scope to its typed specification") {
            val specification = toDocumentSpecification(
                supplierChangeType,
                listOf(
                    supplierScope(
                        appliesTo = listOf(
                            meteringPointConstraint(listOf(validMeteringPointId, validMeteringPointId)),
                        ),
                    ),
                ),
            )

            specification shouldBe DocumentSpecification.ChangeOfEnergySupplierForOrganization(
                meteringPointIds = ResourceConstraint.MeteringPoints(
                    setOf(MeteringPointId.create(validMeteringPointId)),
                ),
            )
        }

        test("requires exactly one requested scope") {
            invalidPayload({ toDocumentSpecification(supplierChangeType, emptyList()) }, oneScopeDetail)
            invalidPayload(
                { toDocumentSpecification(supplierChangeType, listOf(supplierScope(), supplierScope())) },
                oneScopeDetail,
            )
        }

        test("requires MeteringPointContract resource type") {
            invalidPayload(
                { toDocumentSpecification(supplierChangeType, listOf(supplierScope(resourceType = "OtherResource"))) },
                resourceTypeDetail,
            )
        }

        test("requires exactly one meteringPoint.id appliesTo constraint") {
            invalidPayload(
                { toDocumentSpecification(supplierChangeType, listOf(supplierScope(appliesTo = emptyList()))) },
                appliesToDetail,
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        supplierChangeType,
                        listOf(supplierScope(appliesTo = listOf(ScopeConstraint("other", listOf("x"))))),
                    )
                },
                appliesToDetail,
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        supplierChangeType,
                        listOf(supplierScope(appliesTo = listOf(meteringPointConstraint(), meteringPointConstraint()))),
                    )
                },
                appliesToDetail,
            )
        }

        test("requires at least one valid metering-point ID") {
            invalidPayload(
                {
                    toDocumentSpecification(
                        supplierChangeType,
                        listOf(supplierScope(appliesTo = listOf(meteringPointConstraint(listOf("invalid"))))),
                    )
                },
                invalidMeteringPointDetail,
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        supplierChangeType,
                        listOf(supplierScope(appliesTo = listOf(meteringPointConstraint(emptyList())))),
                    )
                },
                emptyMeteringPointsDetail,
            )
        }

        test("does not accept allowedChanges") {
            invalidPayload(
                {
                    toDocumentSpecification(
                        supplierChangeType,
                        listOf(supplierScope(allowedChanges = listOf(ScopeConstraint("type", listOf("EnergySupplier"))))),
                    )
                },
                "requestedScopes[0].allowedChanges is not supported for ChangeOfEnergySupplierForOrganization",
            )
        }

        test("reports the first failing validation rule") {
            invalidPayload(
                {
                    toDocumentSpecification(
                        supplierChangeType,
                        listOf(
                            supplierScope(
                                resourceType = "OtherResource",
                                allowedChanges = listOf(ScopeConstraint("type", listOf("EnergySupplier"))),
                            ),
                        ),
                    )
                },
                resourceTypeDetail,
            )
        }
    }

    context("MoveInAndChangeOfEnergySupplierForOrganization") {
        test("maps validFrom and metering points to its typed specification") {
            val specification = toDocumentSpecification(moveInType, listOf(moveInScope()))

            specification shouldBe DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization(
                meteringPointIds = ResourceConstraint.MeteringPoints(
                    setOf(MeteringPointId.create(validMeteringPointId)),
                ),
                validFrom = LocalDate(2026, 10, 1),
            )
        }

        test("converts timestamps to the Oslo calendar date") {
            val specification = toDocumentSpecification(
                moveInType,
                listOf(moveInScope(validFrom = listOf("2026-09-30T22:30:00Z"))),
            )

            specification shouldBe DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization(
                meteringPointIds = ResourceConstraint.MeteringPoints(
                    setOf(MeteringPointId.create(validMeteringPointId)),
                ),
                validFrom = LocalDate(2026, 10, 1),
            )
        }

        test("infers the allowed contract types from the document type") {
            val specification = toDocumentSpecification(moveInType, listOf(moveInScope()))

            specification.toRequestedAuthorizationScope().constraints
                .single { it.attribute == AuthorizationScopeConstraintAttribute.ContractType }
                .value shouldBe ResourceConstraint.ContractTypes(
                setOf(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier),
            )
        }

        test("requires exactly one requested scope") {
            invalidPayload({ toDocumentSpecification(moveInType, emptyList()) }, oneScopeDetail)
            invalidPayload(
                { toDocumentSpecification(moveInType, listOf(moveInScope(), moveInScope())) },
                oneScopeDetail,
            )
        }

        test("requires MeteringPointContract resource type") {
            invalidPayload(
                { toDocumentSpecification(moveInType, listOf(moveInScope(resourceType = "OtherResource"))) },
                resourceTypeDetail,
            )
        }

        test("requires exactly one meteringPoint.id appliesTo constraint") {
            invalidPayload(
                { toDocumentSpecification(moveInType, listOf(moveInScope(appliesTo = emptyList()))) },
                appliesToDetail,
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        moveInType,
                        listOf(moveInScope(appliesTo = listOf(ScopeConstraint("other", listOf("x"))))),
                    )
                },
                appliesToDetail,
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        moveInType,
                        listOf(moveInScope(appliesTo = listOf(meteringPointConstraint(), meteringPointConstraint()))),
                    )
                },
                appliesToDetail,
            )
        }

        test("requires at least one valid metering-point ID") {
            invalidPayload(
                {
                    toDocumentSpecification(
                        moveInType,
                        listOf(moveInScope(appliesTo = listOf(meteringPointConstraint(listOf("invalid"))))),
                    )
                },
                invalidMeteringPointDetail,
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        moveInType,
                        listOf(moveInScope(appliesTo = listOf(meteringPointConstraint(emptyList())))),
                    )
                },
                emptyMeteringPointsDetail,
            )
        }

        test("allows exactly one validFrom allowedChanges constraint") {
            invalidPayload(
                { toDocumentSpecification(moveInType, listOf(moveInScope(allowedChanges = emptyList()))) },
                validFromConstraintDetail,
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        moveInType,
                        listOf(
                            moveInScope(
                                allowedChanges = listOf(
                                    ScopeConstraint("validFrom", listOf("2026-10-01")),
                                    ScopeConstraint("extra", listOf("x")),
                                ),
                            ),
                        ),
                    )
                },
                validFromConstraintDetail,
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        moveInType,
                        listOf(
                            moveInScope(
                                allowedChanges = listOf(
                                    ScopeConstraint("type", listOf("EnergySupplier", "EndUser")),
                                    ScopeConstraint("validFrom", listOf("2026-10-01")),
                                ),
                            ),
                        ),
                    )
                },
                validFromConstraintDetail,
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        moveInType,
                        listOf(
                            moveInScope(
                                allowedChanges = listOf(
                                    ScopeConstraint("validFrom", listOf("2026-10-01")),
                                    ScopeConstraint("validFrom", listOf("2026-10-02")),
                                ),
                            ),
                        ),
                    )
                },
                validFromConstraintDetail,
            )
        }

        test("requires exactly one validFrom value") {
            invalidPayload(
                { toDocumentSpecification(moveInType, listOf(moveInScope(validFrom = emptyList()))) },
                "requestedScopes[0].allowedChanges validFrom must contain exactly one date",
            )
            invalidPayload(
                {
                    toDocumentSpecification(
                        moveInType,
                        listOf(moveInScope(validFrom = listOf("2026-10-01", "2026-10-02"))),
                    )
                },
                "requestedScopes[0].allowedChanges validFrom must contain exactly one date",
            )
        }

        test("validFrom must be an ISO date or timestamp") {
            invalidPayload(
                { toDocumentSpecification(moveInType, listOf(moveInScope(validFrom = listOf("not-a-date")))) },
                "requestedScopes[0].allowedChanges validFrom must be an ISO date or timestamp",
            )
        }

        test("reports the first failing validation rule") {
            invalidPayload(
                {
                    toDocumentSpecification(
                        moveInType,
                        listOf(moveInScope(appliesTo = emptyList(), allowedChanges = emptyList())),
                    )
                },
                appliesToDetail,
            )
        }
    }
})
