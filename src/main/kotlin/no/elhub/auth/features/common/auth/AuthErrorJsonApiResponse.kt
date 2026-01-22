package no.elhub.auth.features.common.auth

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun AuthError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        AuthError.MissingAuthorizationHeader -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "missing_authorization",
            title = "Missing authorization",
            detail = "Bearer token is required in the Authorization header."
        )

        AuthError.InvalidAuthorizationHeader -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "invalid_authorization_header",
            title = "Invalid authorization header",
            detail = "Authorization header must use the Bearer scheme."
        )

        AuthError.MissingSenderGlnHeader -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "missing_sender_gln",
            title = "Missing senderGLN header",
            detail = "SenderGLN header is required for authorization."
        )

        AuthError.InvalidToken -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "invalid_token",
            title = "Invalid token",
            detail = "Token could not be verified."
        )

        AuthError.ActingFunctionNotSupported -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            code = "unsupported_party_type",
            title = "Unsupported party type",
            detail = "The party type you are authorized as is not supported for this endpoint."
        )

        AuthError.ValidationInfoMissing,
        AuthError.ActingGlnMissing,
        AuthError.ActingFunctionMissing,
        AuthError.UnexpectedError,
        AuthError.UnknownError -> buildApiErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_server_error",
            title = "Internal server error",
            detail = "An internal error occurred."
        )

        AuthError.NotAuthorized -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "not_authorized",
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )
    }
