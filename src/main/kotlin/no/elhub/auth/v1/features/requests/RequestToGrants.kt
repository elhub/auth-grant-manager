package no.elhub.auth.v1.features.requests

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.v1.common.GrantValidity
import no.elhub.auth.v1.common.consumableGrantValidity
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import no.elhub.auth.v1.domain.PermissionCapability
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import kotlin.time.Instant
import no.elhub.auth.v1.common.frameworkAgreementGrantValidity as calculateFrameworkAgreementGrantValidity

// The requested scopes are built by AuthorizationRequest.create for each request type, so their shape is trusted here.
fun AuthorizationRequest.toGrants(
    approvedBy: AuthorizationParty,
    now: Instant = now(),
): List<AuthorizationGrant> =
    when (requestType) {
        AuthorizationRequestType.ChangeOfEnergySupplierForOrganization ->
            changeOfEnergySupplierGrants(approvedBy, now)

        AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization ->
            moveInAndChangeOfEnergySupplierGrants(approvedBy, now)

        AuthorizationRequestType.EnergySupplierFrameworkAgreement ->
            energySupplierFrameworkAgreementGrants(approvedBy, now)
    }

/**
 * One Consumable grant per metering point, each allowing requestedBy to change the energy supplier contract on that
 * metering point. Valid from today through the calendar date two years ahead in Oslo.
 */
private fun AuthorizationRequest.changeOfEnergySupplierGrants(
    approvedBy: AuthorizationParty,
    now: Instant,
): List<AuthorizationGrant> {
    val meteringPoints = constraint(AuthorizationScopeConstraintAttribute.MeteringPointId)
    val contractTypes = constraint(AuthorizationScopeConstraintAttribute.ContractType)
    val validity = consumableGrantValidity(now)

    return (meteringPoints.value as ResourceConstraint.MeteringPoints).ids.map { meteringPointId ->
        AuthorizationGrant.new(
            now = now,
            type = AuthorizationGrantType.Consumable,
            sourceType = AuthorizationGrantSourceType.Request,
            sourceId = id,
            principal = requestedPrincipal,
            grantedBy = requestedOf,
            grantedTo = requestedBy,
            approvedBy = approvedBy,
            validFrom = validity.validFrom,
            validTo = validity.validTo,
            scopes = listOf(
                GrantedAuthorizationScope(
                    capability = PermissionCapability.Write,
                    resourceType = ResourceType.MeteringPointContract,
                    constraints = listOf(
                        meteringPoints.copy(value = ResourceConstraint.MeteringPoints(setOf(meteringPointId))),
                        contractTypes,
                    ),
                ),
            ),
        )
    }
}

/**
 * One Consumable grant per metering point, each allowing requestedBy to change the end user and energy supplier
 * contracts on that metering point from the move-in date. Valid from today through the calendar date two years ahead in Oslo.
 */
private fun AuthorizationRequest.moveInAndChangeOfEnergySupplierGrants(
    approvedBy: AuthorizationParty,
    now: Instant,
): List<AuthorizationGrant> {
    val meteringPoints = constraint(AuthorizationScopeConstraintAttribute.MeteringPointId)
    val contractTypes = constraint(AuthorizationScopeConstraintAttribute.ContractType)
    val moveInDate = constraint(AuthorizationScopeConstraintAttribute.ValidFrom)
    val validity = consumableGrantValidity(now)

    return (meteringPoints.value as ResourceConstraint.MeteringPoints).ids.map { meteringPointId ->
        AuthorizationGrant.new(
            now = now,
            type = AuthorizationGrantType.Consumable,
            sourceType = AuthorizationGrantSourceType.Request,
            sourceId = id,
            principal = requestedPrincipal,
            grantedBy = requestedOf,
            grantedTo = requestedBy,
            approvedBy = approvedBy,
            validFrom = validity.validFrom,
            validTo = validity.validTo,
            scopes = listOf(
                GrantedAuthorizationScope(
                    capability = PermissionCapability.Write,
                    resourceType = ResourceType.MeteringPointContract,
                    constraints = listOf(
                        meteringPoints.copy(value = ResourceConstraint.MeteringPoints(setOf(meteringPointId))),
                        contractTypes,
                        moveInDate,
                    ),
                ),
            ),
        )
    }
}

/**
 * Exactly one Persistent grant allowing requestedBy to change the end user and energy supplier contracts on all of the
 * principal's metering points. Valid according to [frameworkAgreementGrantValidity].
 */
private fun AuthorizationRequest.energySupplierFrameworkAgreementGrants(
    approvedBy: AuthorizationParty,
    now: Instant,
): List<AuthorizationGrant> {
    val contractTypes = constraint(AuthorizationScopeConstraintAttribute.ContractType)
    val validity = frameworkAgreementGrantValidity(requestedGrantValidFrom, requestedGrantValidTo, now)

    val grant = AuthorizationGrant.new(
        now = now,
        type = AuthorizationGrantType.Persistent,
        sourceType = AuthorizationGrantSourceType.Request,
        sourceId = id,
        principal = requestedPrincipal,
        grantedBy = requestedOf,
        grantedTo = requestedBy,
        approvedBy = approvedBy,
        validFrom = validity.validFrom,
        validTo = validity.validTo,
        scopes = listOf(
            GrantedAuthorizationScope(
                capability = PermissionCapability.Write,
                resourceType = ResourceType.MeteringPointContract,
                constraints = listOf(contractTypes),
            ),
        ),
    )
    return listOf(grant)
}

/**
 * - validFrom: the requested start, moved forward to the start of today if it is missing or in the past.
 * - validTo: the exclusive requested end, or no end date if none was requested.
 * - Fails if the requested end is at or before now or validFrom.
 */
fun frameworkAgreementGrantValidity(
    requestedValidFrom: Instant?,
    requestedValidTo: Instant?,
    now: Instant,
): GrantValidity = calculateFrameworkAgreementGrantValidity(requestedValidFrom, requestedValidTo, now) {
    throw UpdateRequestError.InvalidGrantValidity()
}

private fun AuthorizationRequest.constraint(attribute: AuthorizationScopeConstraintAttribute): AuthorizationScopeConstraint =
    requestedScopes.single().constraints.first { it.attribute == attribute }
