package no.elhub.auth.v0.features.grants.common

import java.util.UUID

data class AuthorizationGrantProperty(
    val grantId: UUID,
    val key: String,
    val value: String
)
