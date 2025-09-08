package no.elhub.auth.features.grants

import kotlinx.datetime.Instant

data class AuthorizationParty(
    val id: Long,
    val type: ElhubResource,
    val descriptor: String,
    val createdAt: Instant
)
