package no.elhub.auth.features.requests.common

import kotlinx.serialization.Serializable
import no.elhub.auth.features.requests.AuthorizationRequest

@Serializable
class AuthorizationRequestListResponse(
    val data: List<AuthorizationRequestResponse>
)

fun List<AuthorizationRequest>.toResponse() = AuthorizationRequestListResponse(
    data = this.map { request -> request.toResponse() }
)
