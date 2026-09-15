package no.elhub.auth.features.requests.common

import kotlinx.serialization.json.JsonElement
import java.util.UUID

data class AuthorizationRequestProperty(
    val requestId: UUID,
    val key: String,
    val value: JsonElement,
)
