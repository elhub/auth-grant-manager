package no.elhub.auth.features.requests.common

import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.devxp.jsonapi.response.JsonApiResponse

typealias AuthorizationRequestListResponse = JsonApiResponse.CollectionDocumentWithRelationships<RequestResponseAttributes, RequestResponseRelationships>

fun List<AuthorizationRequest>.toResponse() = AuthorizationRequestListResponse(
    data = this.map { it.toResponse().data }
)
