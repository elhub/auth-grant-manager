package no.elhub.auth.features.common.auth

import io.ktor.http.HttpStatusCode
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

fun AuthError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> {
    fun build(status: HttpStatusCode, title: String, detail: String) =
        status to JsonApiErrorCollection(
            listOf(
                JsonApiErrorObject(
                    status = status.value.toString(),
                    title = title,
                    detail = detail
                )
            )
        )

    return when (this) {
        AuthError.MissingAuthorizationHeader -> build(
            HttpStatusCode.Unauthorized,
            title = "Missing authorization",
            detail = "Bearer token is required in the Authorization header."
        )

        AuthError.InvalidAuthorizationHeader -> build(
            HttpStatusCode.Unauthorized,
            title = "Invalid authorization header",
            detail = "Authorization header must use the Bearer scheme."
        )

        AuthError.MissingSenderGlnHeader -> build(
            HttpStatusCode.BadRequest,
            title = "Missing SenderGLN header",
            detail = "SenderGLN header is required for authorization."
        )

        AuthError.InvalidToken -> build(
            HttpStatusCode.Unauthorized,
            title = "Invalid token",
            detail = "Token could not be verified."
        )

        AuthError.ActingFunctionNotSupported -> build(
            HttpStatusCode.Forbidden,
            title = "Unsupported party type",
            detail = "The party type you are authorized as is not supported for this endpoint."
        )

        AuthError.ValidationInfoMissing,
        AuthError.ActingGlnMissing,
        AuthError.ActingFunctionMissing,
        AuthError.UnexpectedError,
        AuthError.UnknownError -> build(
            HttpStatusCode.InternalServerError,
            title = "Internal authorization error",
            detail = "An internal error occurred."
        )

        AuthError.NotAuthorized -> build(
            status = HttpStatusCode.Unauthorized,
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )
    }
}
