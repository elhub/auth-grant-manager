package no.elhub.auth.v1.domain

import kotlinx.serialization.json.JsonObject
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
) {
    companion object {
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
    Accepted,
    Expired,
    Pending,
    Rejected,
}

enum class AuthorizationRequestType {
    ChangeOfEnergySupplierForOrganization,
    MoveInAndChangeOfEnergySupplierForOrganization,
}
