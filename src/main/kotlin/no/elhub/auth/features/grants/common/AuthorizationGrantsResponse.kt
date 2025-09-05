package no.elhub.auth.features.grants.common

import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.devxp.jsonapi.response.JsonApiResponse

typealias AuthorizationGrantListResponse = JsonApiResponse.CollectionDocumentWithRelationships<GrantResponseAttributes, GrantRelationships>

fun List<AuthorizationGrant>.toResponse() = AuthorizationGrantListResponse(
    data = this.map { it.toResponse().data }
)
