package no.elhub.auth.features.requests.common

import java.util.UUID

data class AuthorizationRequestProperty(
    val requestId: UUID,
    val key: String,
    val value: String,
)
