package no.elhub.auth.features.grants.common

import kotlinx.serialization.json.JsonElement
import java.util.UUID

data class AuthorizationGrantProperty(
    val grantId: UUID,
    val key: String,
    val value: JsonElement,
)
