package no.elhub.auth.features.common

import kotlinx.serialization.Serializable

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
