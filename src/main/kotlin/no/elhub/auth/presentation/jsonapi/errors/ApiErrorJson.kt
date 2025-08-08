package no.elhub.auth.presentation.jsonapi.errors

import kotlinx.serialization.Serializable
import no.elhub.auth.presentation.jsonapi.ResponseLink
import no.elhub.auth.presentation.jsonapi.ResponseMeta

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
