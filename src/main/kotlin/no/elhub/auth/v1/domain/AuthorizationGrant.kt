package no.elhub.auth.v1.domain

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class AuthorizationGrant(
    val id: UUID,
    val type: AuthorizationGrantType,
    val sourceType: AuthorizationGrantSourceType,
    val sourceId: UUID,
    val principal: AuthorizationParty,
    val grantedBy: AuthorizationParty,
    val grantedTo: AuthorizationParty,
    val approvedBy: AuthorizationParty,
    val status: AuthorizationGrantStatus,
    val validFrom: Instant,
    val validTo: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val scopes: List<GrantedAuthorizationScope>,
) {
    companion object {
        fun new(
            type: AuthorizationGrantType,
            sourceType: AuthorizationGrantSourceType,
            sourceId: UUID,
            principal: AuthorizationParty,
            grantedBy: AuthorizationParty,
            grantedTo: AuthorizationParty,
            approvedBy: AuthorizationParty,
            validFrom: Instant,
            validTo: Instant,
            scopes: List<GrantedAuthorizationScope>,
        ): AuthorizationGrant {
            val now = Clock.System.now()
            return AuthorizationGrant(
                id = UUID.randomUUID(),
                type = type,
                sourceType = sourceType,
                sourceId = sourceId,
                principal = principal,
                grantedBy = grantedBy,
                grantedTo = grantedTo,
                approvedBy = approvedBy,
                status = AuthorizationGrantStatus.Active,
                validFrom = validFrom,
                validTo = validTo,
                createdAt = now,
                updatedAt = now,
                scopes = scopes,
            )
        }
    }
}

enum class AuthorizationGrantType {
    Persistent,
    Consumable,
}

enum class AuthorizationGrantStatus {
    Active,
    Revoked,
    Exhausted,
    Expired,
}

enum class AuthorizationGrantSourceType {
    Document,
    Request,
}
