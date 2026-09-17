package no.elhub.auth.features.requests.create.command

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.requests.AuthorizationRequest
import java.time.OffsetDateTime

const val TEXT_VERSION_KEY = "textVersion"

interface RequestMetaMarker {
    fun toRequestMetaAttributes(): JsonObject
}

fun JsonObject.withTextVersion(version: String): JsonObject =
    JsonObject(this + (TEXT_VERSION_KEY to JsonPrimitive(version)))

data class RequestCommand(
    val type: AuthorizationRequest.Type,
    val validTo: OffsetDateTime,
    val scopes: List<CreateScopeData>,
    val meta: RequestMetaMarker,
)
