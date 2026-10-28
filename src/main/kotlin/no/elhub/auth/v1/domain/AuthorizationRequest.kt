package no.elhub.auth.v1.domain

import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.common.startOfDayOslo
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class AuthorizationRequest(
    val id: UUID,
    val requestType: AuthorizationRequestType,
    val status: AuthorizationRequestStatus,
    val requestedScopes: List<RequestedAuthorizationScope>,
    val externalReference: String?,
    val redirectUri: String?,
    val meta: JsonObject,
    val requestedGrantValidFrom: Instant? = null,
    val requestedGrantValidTo: Instant? = null,
    val validFrom: Instant,
    val validTo: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val requestedBy: AuthorizationParty,
    val requestedPrincipal: AuthorizationParty,
    val requestedOf: AuthorizationParty,
    val approvedBy: AuthorizationParty?,
    val authorizationGrants: List<AuthorizationGrant>?,
    val textLanguage: TextLanguage? = null,
) {
    companion object {
        fun create(
            requestedBy: AuthorizationParty,
            requestedPrincipal: AuthorizationParty,
            requestedOf: AuthorizationParty,
            details: AuthorizationRequestTypeDetails,
            validTo: Instant,
            meta: JsonObject,
            externalReference: String?,
            redirectUri: String?,
        ): AuthorizationRequest =
            when (details) {
                is AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization -> new(
                    requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
                    requestedScopes = listOf(
                        RequestedAuthorizationScope(
                            resourceType = ResourceType.MeteringPointContract,
                            constraints = listOf(
                                appliesToMeteringPoints(details.meteringPointIds),
                                allowedContractTypes(MeteringPointContractType.EnergySupplier),
                            ),
                        ),
                    ),
                    externalReference = externalReference,
                    meta = meta,
                    validTo = validTo,
                    requestedBy = requestedBy,
                    requestedPrincipal = requestedPrincipal,
                    requestedOf = requestedOf,
                    redirectUri = redirectUri,
                )

                is AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization -> new(
                    requestType = AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization,
                    requestedScopes = listOf(
                        RequestedAuthorizationScope(
                            resourceType = ResourceType.MeteringPointContract,
                            constraints = listOf(
                                appliesToMeteringPoints(details.meteringPointIds),
                                allowedContractTypes(
                                    MeteringPointContractType.EndUser,
                                    MeteringPointContractType.EnergySupplier,
                                ),
                                AuthorizationScopeConstraint(
                                    constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                                    attribute = AuthorizationScopeConstraintAttribute.ValidFrom,
                                    value = ResourceConstraint.ValidFrom(
                                        details.moveInDate.startOfDayOslo(),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    externalReference = externalReference,
                    meta = meta,
                    validTo = validTo,
                    requestedBy = requestedBy,
                    requestedPrincipal = requestedPrincipal,
                    requestedOf = requestedOf,
                    redirectUri = redirectUri,
                )

                is AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement -> new(
                    requestType = AuthorizationRequestType.EnergySupplierFrameworkAgreement,
                    requestedScopes = listOf(
                        RequestedAuthorizationScope(
                            resourceType = ResourceType.MeteringPointContract,
                            constraints = listOf(
                                allowedContractTypes(
                                    MeteringPointContractType.EndUser,
                                    MeteringPointContractType.EnergySupplier,
                                ),
                            ),
                        ),
                    ),
                    externalReference = externalReference,
                    meta = meta,
                    validTo = validTo,
                    requestedBy = requestedBy,
                    requestedPrincipal = requestedPrincipal,
                    requestedOf = requestedOf,
                    redirectUri = redirectUri,
                    requestedGrantValidFrom = details.requestedGrantValidFrom,
                    requestedGrantValidTo = details.requestedGrantValidTo,
                )
            }

        private fun appliesToMeteringPoints(meteringPointIds: ResourceConstraint.MeteringPoints) =
            AuthorizationScopeConstraint(
                constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                value = meteringPointIds,
            )

        private fun allowedContractTypes(vararg contractTypes: MeteringPointContractType) =
            AuthorizationScopeConstraint(
                constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                attribute = AuthorizationScopeConstraintAttribute.ContractType,
                value = ResourceConstraint.ContractTypes(contractTypes.toSet()),
            )

        fun new(
            requestType: AuthorizationRequestType,
            requestedScopes: List<RequestedAuthorizationScope>,
            externalReference: String?,
            meta: JsonObject,
            validTo: Instant,
            requestedBy: AuthorizationParty,
            requestedPrincipal: AuthorizationParty,
            requestedOf: AuthorizationParty,
            redirectUri: String? = null,
            requestedGrantValidFrom: Instant? = null,
            requestedGrantValidTo: Instant? = null,
        ): AuthorizationRequest {
            val now = Clock.System.now()
            return AuthorizationRequest(
                id = UUID.randomUUID(),
                requestType = requestType,
                status = AuthorizationRequestStatus.Pending,
                requestedScopes = requestedScopes,
                externalReference = externalReference,
                redirectUri = redirectUri,
                meta = meta,
                requestedGrantValidFrom = requestedGrantValidFrom,
                requestedGrantValidTo = requestedGrantValidTo,
                validFrom = now,
                validTo = validTo,
                createdAt = now,
                updatedAt = now,
                requestedBy = requestedBy,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
                approvedBy = null,
                authorizationGrants = null,
            )
        }
    }
}

enum class AuthorizationRequestStatus {
    Approved,
    Expired,
    Pending,
    Rejected,
}

enum class AuthorizationRequestType {
    ChangeOfEnergySupplierForOrganization,
    MoveInAndChangeOfEnergySupplierForOrganization,
    EnergySupplierFrameworkAgreement,
}
