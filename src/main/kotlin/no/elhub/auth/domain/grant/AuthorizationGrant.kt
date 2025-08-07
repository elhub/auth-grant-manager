package no.elhub.auth.domain.grant

import kotlinx.datetime.LocalDateTime

data class AuthorizationGrant(
    val id: String,
    val grantStatus: GrantStatus,
    val grantedFor: Long,
    val grantedBy: Long,
    val grantedTo: Long,
    val grantedAt: LocalDateTime,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
)
