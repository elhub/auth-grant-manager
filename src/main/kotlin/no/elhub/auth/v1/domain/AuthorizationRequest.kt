package no.elhub.auth.v1.domain

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class AuthorizationRequest(
    val id: UUID,
    val requestType: AuthorizationRequestType,
    val status: AuthorizationRequestStatus,
    val requestedScopes: List<RequestedAuthorizationScope>,
    val externalReference: String?,
    val validTo: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val approvedBy: AuthorizationParty?,
    val authorizationGrant: List<AuthorizationGrant>?,
) {
    companion object {
        fun new(
            requestType: AuthorizationRequestType,
            requestedScopes: List<RequestedAuthorizationScope>,
            externalReference: String?,
            validTo: Instant,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
            requestedTo: AuthorizationParty,
        ): AuthorizationRequest {
            val now = Clock.System.now()
            return AuthorizationRequest(
                id = UUID.randomUUID(),
                requestType = requestType,
                status = AuthorizationRequestStatus.Pending,
                requestedScopes = requestedScopes,
                externalReference = externalReference,
                validTo = validTo,
                createdAt = now,
                updatedAt = now,
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
                approvedBy = null,
                authorizationGrant = null,
            )
        }
    }
}

enum class AuthorizationRequestStatus {
    Accepted,
    Expired,
    Pending,
    Rejected,
    Revoked,
}

enum class AuthorizationRequestType {
    ChangeOfEnergySupplierForOrganization,
    ChangeOfEnergySupplierForPerson,
    MoveInAndChangeOfEnergySupplierForOrganization,
    MoveInAndChangeOfEnergySupplierForPerson,
}
