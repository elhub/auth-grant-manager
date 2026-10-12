package no.elhub.auth.v1.features.documents.create

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType

sealed interface DocumentAction {
    val documentType: AuthorizationDocumentType
    fun toRequestedAuthorizationScope(): RequestedAuthorizationScope

    data class ChangeOfEnergySupplierForOrganization(
        val meteringPointIds: ResourceConstraint.MeteringPoints,
    ) : DocumentAction {
        override val documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization

        override fun toRequestedAuthorizationScope() = RequestedAuthorizationScope(
            resourceType = ResourceType.MeteringPointContract,
            constraints = listOf(
                AuthorizationScopeConstraint(
                    constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                    attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                    value = meteringPointIds,
                ),
                AuthorizationScopeConstraint(
                    constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                    attribute = AuthorizationScopeConstraintAttribute.ContractType,
                    value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
                ),
            ),
        )
    }

    data class MoveInAndChangeOfEnergySupplierForOrganization(
        val meteringPointIds: ResourceConstraint.MeteringPoints,
        val validFrom: LocalDate,
    ) : DocumentAction {
        override val documentType = AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization

        override fun toRequestedAuthorizationScope() = RequestedAuthorizationScope(
            resourceType = ResourceType.MeteringPointContract,
            constraints = listOf(
                AuthorizationScopeConstraint(
                    constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                    attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                    value = meteringPointIds,
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
}
