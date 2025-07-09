package no.elhub.auth.grantmanager.presentation.features.errors

import kotlinx.serialization.Serializable
import no.elhub.auth.grantmanager.presentation.model.ResponseLink
import no.elhub.auth.grantmanager.presentation.model.ResponseMeta

@Serializable
class ApiErrorJson(
    val errors: List<ApiError>,
    val links: ResponseLink,
    val meta: ResponseMeta,
) {
    companion object {
        fun from(apiError: ApiError, selfLink: String): ApiErrorJson = ApiErrorJson(
            errors = listOf(apiError),
            links = ResponseLink(selfLink),
            meta = ResponseMeta()
        )
    }
}
