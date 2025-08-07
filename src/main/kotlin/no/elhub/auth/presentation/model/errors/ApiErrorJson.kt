package no.elhub.auth.presentation.model.errors

import kotlinx.serialization.Serializable
import no.elhub.auth.presentation.model.ResponseLink
import no.elhub.auth.presentation.model.ResponseMeta

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
