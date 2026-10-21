package no.elhub.auth.v1.features.documents

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

class InvalidDocumentScopes(detail: String) : IllegalStateException(detail)

fun AuthorizationDocument.toGrants(approvedBy: AuthorizationParty): List<AuthorizationGrant> =
    when (documentType) {
        AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization -> {
            val scope = requestedScopes.singleOrNull()
                ?: throw InvalidDocumentScopes("$documentType requires exactly one requested scope")
            if (scope.resourceType != ResourceType.MeteringPointContract) {
                throw InvalidDocumentScopes("$documentType requires a MeteringPointContract scope")
            }
            val meteringPointConstraint = scope.constraints.singleOrNull {
                it.constraintKind == AuthorizationScopeConstraintKind.AppliesTo &&
                    it.attribute == AuthorizationScopeConstraintAttribute.MeteringPointId
            } ?: throw InvalidDocumentScopes("$documentType requires exactly one AppliesTo metering-point constraint")
            val meteringPoints = meteringPointConstraint.value as? ResourceConstraint.MeteringPoints
                ?: throw InvalidDocumentScopes("$documentType requires metering-point IDs")
            val contractTypeConstraint = scope.constraints.singleOrNull {
                it.constraintKind == AuthorizationScopeConstraintKind.AllowedChanges &&
                    it.attribute == AuthorizationScopeConstraintAttribute.ContractType
            } ?: throw InvalidDocumentScopes("$documentType requires exactly one AllowedChanges contract-type constraint")
            if (contractTypeConstraint.value != ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier))) {
                throw InvalidDocumentScopes("$documentType requires EnergySupplier contract changes")
            }
            if (scope.constraints.size != 2) {
                throw InvalidDocumentScopes("$documentType has unexpected scope constraints")
            }

            val grantValidFrom = Clock.System.now()
            val grantValidTo = grantValidFrom.plus(1, DateTimeUnit.YEAR, TimeZone.UTC)
            meteringPoints.ids.map { meteringPointId ->
                toConsumableGrant(
                    approvedBy = approvedBy,
                    validFrom = grantValidFrom,
                    validTo = grantValidTo,
                    scope = GrantedAuthorizationScope(
                        capability = PermissionCapability.Write,
                        resourceType = ResourceType.MeteringPointContract,
                        constraints = listOf(
                            meteringPointConstraint.copy(value = ResourceConstraint.MeteringPoints(setOf(meteringPointId))),
                            contractTypeConstraint,
                        ),
                    ),
                )
            }
        }

        AuthorizationDocumentType.MoveInAndChangeOfEnergySupplierForOrganization -> {
            val scope = requestedScopes.singleOrNull()
                ?: throw InvalidDocumentScopes("$documentType requires exactly one requested scope")
            if (scope.resourceType != ResourceType.MeteringPointContract) {
                throw InvalidDocumentScopes("$documentType requires a MeteringPointContract scope")
            }
            val meteringPointConstraint = scope.constraints.singleOrNull {
                it.constraintKind == AuthorizationScopeConstraintKind.AppliesTo &&
                    it.attribute == AuthorizationScopeConstraintAttribute.MeteringPointId
            } ?: throw InvalidDocumentScopes("$documentType requires exactly one AppliesTo metering-point constraint")
            val meteringPoints = meteringPointConstraint.value as? ResourceConstraint.MeteringPoints
                ?: throw InvalidDocumentScopes("$documentType requires metering-point IDs")
            val contractTypeConstraint = scope.constraints.singleOrNull {
                it.constraintKind == AuthorizationScopeConstraintKind.AllowedChanges &&
                    it.attribute == AuthorizationScopeConstraintAttribute.ContractType
            } ?: throw InvalidDocumentScopes("$documentType requires exactly one AllowedChanges contract-type constraint")
            if (contractTypeConstraint.value != ResourceConstraint.ContractTypes(
                    setOf(MeteringPointContractType.EndUser, MeteringPointContractType.EnergySupplier),
                )
            ) {
                throw InvalidDocumentScopes("$documentType requires EndUser and EnergySupplier contract changes")
            }
            val moveInDateConstraint = scope.constraints.singleOrNull {
                it.constraintKind == AuthorizationScopeConstraintKind.AllowedChanges &&
                    it.attribute == AuthorizationScopeConstraintAttribute.ValidFrom
            } ?: throw InvalidDocumentScopes("$documentType requires exactly one AllowedChanges move-in-date constraint")
            if (moveInDateConstraint.value !is ResourceConstraint.ValidFrom) {
                throw InvalidDocumentScopes("$documentType requires a move-in date")
            }
            if (scope.constraints.size != 3) {
                throw InvalidDocumentScopes("$documentType has unexpected scope constraints")
            }

            val grantValidFrom = Clock.System.now()
            val grantValidTo = grantValidFrom.plus(1, DateTimeUnit.YEAR, TimeZone.UTC)
            meteringPoints.ids.map { meteringPointId ->
                toConsumableGrant(
                    approvedBy = approvedBy,
                    validFrom = grantValidFrom,
                    validTo = grantValidTo,
                    scope = GrantedAuthorizationScope(
                        capability = PermissionCapability.Write,
                        resourceType = ResourceType.MeteringPointContract,
                        constraints = listOf(
                            meteringPointConstraint.copy(value = ResourceConstraint.MeteringPoints(setOf(meteringPointId))),
                            contractTypeConstraint,
                            moveInDateConstraint,
                        ),
                    ),
                )
            }
        }
    }

private fun AuthorizationDocument.toConsumableGrant(
    approvedBy: AuthorizationParty,
    validFrom: Instant,
    validTo: Instant,
    scope: GrantedAuthorizationScope,
): AuthorizationGrant = AuthorizationGrant.new(
    type = AuthorizationGrantType.Consumable,
    sourceType = AuthorizationGrantSourceType.Document,
    sourceId = UUID.fromString(id),
    principal = requestedPrincipal,
    grantedBy = requestedOf,
    grantedTo = requestedBy,
    approvedBy = approvedBy,
    validFrom = validFrom,
    validTo = validTo,
    scopes = listOf(scope),
)
