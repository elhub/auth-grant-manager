package no.elhub.auth.domain.grant

enum class GrantStatus {
    Active,
    Exhausted,
    Expired,
    Revoked,
}
