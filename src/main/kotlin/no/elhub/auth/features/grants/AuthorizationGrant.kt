package no.elhub.auth.features.grants

import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import java.time.OffsetDateTime
import java.util.UUID

data class AuthorizationGrant(
    val id: UUID,
    val grantStatus: Status,
    val grantedFor: AuthorizationParty,
    val grantedBy: AuthorizationParty,
    val grantedTo: AuthorizationParty,
    val grantedAt: OffsetDateTime,
    val validFrom: OffsetDateTime,
    val validTo: OffsetDateTime,
    val sourceType: SourceType,
    val sourceId: UUID,
    val scopeIds: List<UUID>,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null
) {
    companion object {
        fun create(
            grantedFor: AuthorizationParty,
            grantedBy: AuthorizationParty,
            grantedTo: AuthorizationParty,
            sourceType: SourceType,
            sourceId: UUID,
            scopeIds: List<UUID>,
        ): AuthorizationGrant =
            AuthorizationGrant(
                id = UUID.randomUUID(),
                grantStatus = Status.Active,
                grantedFor = grantedFor,
                grantedBy = grantedBy,
                grantedTo = grantedTo,
                grantedAt = currentTimeWithTimeZone(),
                validFrom = currentTimeWithTimeZone(),
                validTo = currentTimeWithTimeZone().plusYears(1), // TODO this will be handled by the value stream
                sourceId = sourceId,
                sourceType = sourceType,
                scopeIds = scopeIds
            )
    }

    enum class Status {
        Active,
        Exhausted,
        Revoked,
    }

    enum class SourceType {
        Document,
        Request
    }
}
