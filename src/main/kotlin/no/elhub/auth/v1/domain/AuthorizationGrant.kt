package no.elhub.auth.v1.domain

import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.now
import java.util.UUID
import kotlin.time.Instant

data class AuthorizationGrant(
    val id: UUID,
    val type: AuthorizationGrantType,
    val sourceType: AuthorizationGrantSourceType,
    val sourceId: UUID,
    val principal: AuthorizationAgent,
    val grantedBy: AuthorizationAgent,
    val grantedTo: AuthorizationAgent,
    val approvedBy: AuthorizationAgent,
    val status: AuthorizationGrantStatus,
    val validFrom: Instant,
    val validTo: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val scopes: List<GrantedAuthorizationScope>,
) {
    companion object {
        fun new(
            type: AuthorizationGrantType,
            sourceType: AuthorizationGrantSourceType,
            sourceId: UUID,
            principal: AuthorizationAgent,
            grantedBy: AuthorizationAgent,
            grantedTo: AuthorizationAgent,
            approvedBy: AuthorizationAgent,
            validFrom: Instant,
            validTo: Instant?,
            scopes: List<GrantedAuthorizationScope>,
            now: Instant = now(),
        ): AuthorizationGrant = AuthorizationGrant(
            id = UUID.randomUUID(),
            type = type,
            sourceType = sourceType,
            sourceId = sourceId,
            principal = principal,
            grantedBy = grantedBy,
            grantedTo = grantedTo,
            approvedBy = approvedBy,
            status = grantValidityStatus(validFrom, validTo, now),
            validFrom = validFrom,
            validTo = validTo,
            createdAt = now,
            updatedAt = now,
            scopes = scopes,
        )
    }
}

enum class AuthorizationGrantType {
    Persistent,
    Consumable,
}

enum class AuthorizationGrantStatus {
    Pending,
    Active,
    Revoked,
    Exhausted,
    Expired,
}

internal fun grantValidityStatus(validFrom: Instant, validTo: Instant?, now: Instant): AuthorizationGrantStatus = when {
    validTo != null && now >= validTo -> AuthorizationGrantStatus.Expired
    now < validFrom -> AuthorizationGrantStatus.Pending
    else -> AuthorizationGrantStatus.Active
}

enum class AuthorizationGrantSourceType {
    Document,
    Request,
}
