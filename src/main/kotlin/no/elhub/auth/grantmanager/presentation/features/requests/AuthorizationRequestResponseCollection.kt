package no.elhub.auth.grantmanager.presentation.features.requests

import kotlinx.serialization.Serializable
import no.elhub.auth.grantmanager.presentation.model.AuthorizationRequest
import no.elhub.auth.grantmanager.presentation.model.ResponseLink
import no.elhub.auth.grantmanager.presentation.model.ResponseMeta

@Serializable
class AuthorizationRequestResponseCollection(
    val data: List<AuthorizationRequestData>,
    val links: ResponseLink,
    val meta: ResponseMeta,
) {
    companion object {
        fun from(requests: List<AuthorizationRequest>, selfLink: String): AuthorizationRequestResponseCollection = AuthorizationRequestResponseCollection(
            data = requests.map { AuthorizationRequestData.from(it) }.toList(),
            links = ResponseLink(selfLink),
            meta = ResponseMeta()
        )
    }
}
