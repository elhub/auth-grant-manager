package no.elhub.auth.features.requests.query

import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestResponseAttributes
import no.elhub.auth.features.requests.common.RequestResponseRelationships
import no.elhub.auth.features.requests.common.toResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponse

typealias AuthorizationRequestListResponse = JsonApiResponse.CollectionDocumentWithRelationships<RequestResponseAttributes, RequestResponseRelationships>

fun List<AuthorizationRequest>.toResponse() = AuthorizationRequestListResponse(
    data = this.map { it.toResponse().data }
)
