package no.elhub.auth.features.requests

import kotlinx.serialization.Serializable
import no.elhub.auth.model.AuthorizationRequest
import no.elhub.auth.model.ResponseLink
import no.elhub.auth.model.ResponseMeta

@Serializable
class AuthorizationRequestResponse(
    val data: AuthorizationRequestData,
    val links: ResponseLink,
    val meta: ResponseMeta,
) {
    companion object {
        fun from(request: AuthorizationRequest, selfLink: String): AuthorizationRequestResponse = AuthorizationRequestResponse(
            data = AuthorizationRequestData.from(request),
            links = ResponseLink(selfLink),
            meta = ResponseMeta()
        )
    }
}
