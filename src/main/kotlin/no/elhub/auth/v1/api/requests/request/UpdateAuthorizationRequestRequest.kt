package no.elhub.auth.v1.api.requests.request

import kotlinx.serialization.Serializable
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.TextLanguage
import no.elhub.devxp.jsonapi.model.JsonApiAttributes

@Serializable
data class UpdateAuthorizationRequestAttributes(
    val status: AuthorizationRequestStatus,
    val textLanguage: TextLanguage,
) : JsonApiAttributes

@Serializable
data class UpdateAuthorizationRequestData(
    val id: String,
    val type: String,
    val attributes: UpdateAuthorizationRequestAttributes,
)

@Serializable
data class JsonApiUpdateAuthorizationRequestRequest(
    val data: UpdateAuthorizationRequestData,
)
