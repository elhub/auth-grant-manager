package no.elhub.auth.features.grants

import kotlinx.datetime.LocalDateTime
import no.elhub.auth.features.common.AuthorizationParty
import java.util.UUID

data class AuthorizationGrant(
    val id: String,
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
