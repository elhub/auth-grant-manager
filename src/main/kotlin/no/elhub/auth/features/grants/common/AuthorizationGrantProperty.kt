package no.elhub.auth.features.grants.common

import java.util.UUID

data class AuthorizationGrantProperty(
    val grantId: UUID,
    val key: String,
    val value: String
)
