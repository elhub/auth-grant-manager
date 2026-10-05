package no.elhub.auth.v1.domain

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class AuthorizationGrant(
    val id: UUID,
    val sourceType: AuthorizationGrantSourceType,
    val sourceId: UUID,
    val grantedFor: AuthorizationParty,
    val grantedBy: AuthorizationParty,
    val grantedTo: AuthorizationParty,
    val status: AuthorizationGrantStatus,
    val validFrom: Instant,
    val validTo: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val scopes: List<GrantedAuthorizationScope>,
) {
    companion object {
        fun new(
            sourceType: AuthorizationGrantSourceType,
            sourceId: UUID,
            grantedFor: AuthorizationParty,
            grantedBy: AuthorizationParty,
            grantedTo: AuthorizationParty,
            validFrom: Instant,
            validTo: Instant,
            scopes: List<GrantedAuthorizationScope>,
        ): AuthorizationGrant {
            val now = Clock.System.now()
            return AuthorizationGrant(
                id = UUID.randomUUID(),
                sourceType = sourceType,
                sourceId = sourceId,
                grantedFor = grantedFor,
                grantedBy = grantedBy,
                grantedTo = grantedTo,
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
