package no.elhub.auth.features.grants

import no.elhub.auth.features.common.party.AuthorizationParty
import java.time.LocalDateTime
import java.util.UUID

data class AuthorizationGrant(
    val id: UUID,
    val grantStatus: Status,
    val grantedFor: AuthorizationParty,
    val grantedBy: AuthorizationParty,
    val grantedTo: AuthorizationParty,
    val grantedAt: LocalDateTime,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
    val sourceType: SourceType,
    val sourceId: UUID
) {
    companion object {
        fun create(
            grantedFor: AuthorizationParty,
            grantedBy: AuthorizationParty,
            grantedTo: AuthorizationParty,
            sourceType: SourceType,
            sourceId: UUID
        ): AuthorizationGrant =
            AuthorizationGrant(
                id = UUID.randomUUID(),
                grantStatus = Status.Active,
                grantedFor = grantedFor,
                grantedBy = grantedBy,
                grantedTo = grantedTo,
                grantedAt = LocalDateTime.now(),
                validFrom = LocalDateTime.now(),
                validTo = LocalDateTime.now().plusYears(1), // TODO this will be handled by the value stream
                sourceId = sourceId,
                sourceType = sourceType,
            )
    }

    enum class Status {
        Active,
        Exhausted,
        Expired,
        Revoked,
    }

    enum class SourceType {
        Document,
        Request
    }
}
