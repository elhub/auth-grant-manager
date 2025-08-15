package no.elhub.auth.features.grants

import kotlinx.datetime.LocalDateTime

data class AuthorizationGrant(
    val id: String,
    val grantStatus: Status,
    val grantedFor: Long,
    val grantedBy: Long,
    val grantedTo: Long,
    val grantedAt: LocalDateTime,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
) {
    enum class Status {
        Active,
        Exhausted,
        Expired,
        Revoked,
    }
}
