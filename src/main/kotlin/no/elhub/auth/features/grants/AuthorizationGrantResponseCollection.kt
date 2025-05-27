package no.elhub.auth.features.grants

import kotlinx.serialization.Serializable
import no.elhub.auth.model.AuthorizationGrant
import no.elhub.auth.model.ResponseLink
import no.elhub.auth.model.ResponseMeta

@Serializable
data class AuthorizationGrantResponseCollection(
    val data: List<AuthorizationGrantData>,
    val links: ResponseLink,
    val meta: ResponseMeta,
) {
    companion object {
        fun from(grants: List<AuthorizationGrant>, selfLink: String): AuthorizationGrantResponseCollection = AuthorizationGrantResponseCollection(
            data = grants.map { AuthorizationGrantData.from(it) },
            links = ResponseLink(selfLink),
            meta = ResponseMeta()
        )
    }
}
