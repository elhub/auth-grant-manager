package no.elhub.auth.features.grants

import kotlinx.serialization.Serializable
import no.elhub.auth.model.AuthorizationGrant
import no.elhub.auth.model.ResponseLink
import no.elhub.auth.model.ResponseMeta

@Serializable
class AuthorizationGrantResponse(
    val data: AuthorizationGrantData,
    val links: ResponseLink,
    val meta: ResponseMeta,
) {
    companion object {
        fun from(
            request: AuthorizationGrant,
            selfLink: String,
        ): AuthorizationGrantResponse =
            AuthorizationGrantResponse(
                data = AuthorizationGrantData.from(request),
                links = ResponseLink(selfLink),
                meta = ResponseMeta(),
            )
    }
}
