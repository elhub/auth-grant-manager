package no.elhub.auth.features.grants

import kotlinx.datetime.LocalDateTime
import no.elhub.auth.features.common.AuthorizationParty

data class AuthorizationGrant(
    val id: String,
    val grantStatus: Status,
    val grantedFor: AuthorizationParty, // TODO should it be referring to UUID instead?
    val grantedBy: AuthorizationParty,
    val grantedTo: AuthorizationParty,
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
