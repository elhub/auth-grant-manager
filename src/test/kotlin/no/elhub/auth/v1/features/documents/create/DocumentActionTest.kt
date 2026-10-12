package no.elhub.auth.v1.features.documents.create

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType

class DocumentActionTest : FunSpec({
    val meteringPointIds = setOf(MeteringPointId.create("707057500000000001"))

    test("maps supplier change scope to metering point and energy-supplier contract constraints") {
        val scope = DocumentAction.ChangeOfEnergySupplierForOrganization(
            meteringPointIds = ResourceConstraint.MeteringPoints(meteringPointIds),
        )

        val requestedScope = scope.toRequestedAuthorizationScope()

        requestedScope shouldBe
            RequestedAuthorizationScope(
                resourceType = ResourceType.MeteringPointContract,
                constraints = listOf(
                    AuthorizationScopeConstraint(
                        constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                        attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                        value = ResourceConstraint.MeteringPoints(meteringPointIds),
                    ),
                    AuthorizationScopeConstraint(
                        constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                        attribute = AuthorizationScopeConstraintAttribute.ContractType,
                        value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
                    ),
                ),
            )
    }

    test("maps move-in scope to metering point, contract-type and valid-from constraints") {
        val validFrom = LocalDate(2026, 10, 1)
        val scope = DocumentAction.MoveInAndChangeOfEnergySupplierForOrganization(
            meteringPointIds = ResourceConstraint.MeteringPoints(meteringPointIds),
            validFrom = validFrom,
        )

        val requestedScope = scope.toRequestedAuthorizationScope()

        requestedScope shouldBe
            RequestedAuthorizationScope(
                resourceType = ResourceType.MeteringPointContract,
                constraints = listOf(
                    AuthorizationScopeConstraint(
                        constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                        attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                        value = ResourceConstraint.MeteringPoints(meteringPointIds),
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
                        value = ResourceConstraint.ValidFrom(
                            validFrom.atStartOfDayIn(TimeZone.of("Europe/Oslo")),
                        ),
                    ),
                ),
            )
    }
})
