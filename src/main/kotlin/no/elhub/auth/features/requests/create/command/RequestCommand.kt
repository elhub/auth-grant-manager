package no.elhub.auth.features.requests.create.command

import kotlinx.serialization.json.JsonPrimitive
import no.elhub.auth.features.common.AuthorizationMetadata
import no.elhub.auth.features.common.AuthorizationMetadataKeys
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.requests.AuthorizationRequest
import java.time.OffsetDateTime

const val TEXT_VERSION_KEY = AuthorizationMetadataKeys.TEXT_VERSION

interface RequestMetaMarker {
    fun toRequestMetaAttributes(): AuthorizationMetadata
}

fun AuthorizationMetadata.withTextVersion(version: String): AuthorizationMetadata =
    this + (TEXT_VERSION_KEY to JsonPrimitive(version))

data class RequestCommand(
    val type: AuthorizationRequest.Type,
    val validTo: OffsetDateTime,
    val scopes: List<CreateScopeData>,
    val meta: RequestMetaMarker,
)
