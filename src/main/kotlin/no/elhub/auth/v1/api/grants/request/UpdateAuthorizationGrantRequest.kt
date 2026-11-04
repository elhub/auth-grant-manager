package no.elhub.auth.v1.api.grants.request

import kotlinx.serialization.Serializable
import no.elhub.auth.v1.api.common.GrantResourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.devxp.jsonapi.model.JsonApiAttributes

@Serializable
data class UpdateAuthorizationGrantAttributes(
    val status: AuthorizationGrantStatus,
) : JsonApiAttributes

@Serializable
data class UpdateAuthorizationGrantData(
    val id: String,
    val type: GrantResourceType,
    val attributes: UpdateAuthorizationGrantAttributes,
)

@Serializable
data class JsonApiUpdateAuthorizationGrantRequest(
    val data: UpdateAuthorizationGrantData,
)
