package no.elhub.auth.features.requests.query

import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestResponse
import no.elhub.auth.features.requests.common.toResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

typealias AuthorizationRequestsResponse = List<AuthorizationRequestResponse>

fun List<AuthorizationRequest>.toResponse() = this.map { it.toResponse() }
