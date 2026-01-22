package no.elhub.auth.features.common.auth

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun AuthError.toAuthErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        AuthError.MissingAuthorizationHeader -> buildErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "missing_authorization",
            title = "Missing authorization",
            detail = "Bearer token is required in the Authorization header."
        )

        AuthError.InvalidAuthorizationHeader -> buildErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "invalid_authorization_header",
            title = "Invalid authorization header",
            detail = "Authorization header must use the Bearer scheme."
        )

        AuthError.MissingSenderGlnHeader -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "missing_sender_gln",
            title = "Missing senderGLN header",
            detail = "SenderGLN header is required for authorization."
        )

        AuthError.InvalidToken -> buildErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "invalid_token",
            title = "Invalid token",
            detail = "Token could not be verified."
        )

        AuthError.ActingFunctionNotSupported -> buildErrorResponse(
            status = HttpStatusCode.Forbidden,
            code = "unsupported_party_type",
            title = "Unsupported party type",
            detail = "The party type you are authorized as is not supported for this endpoint."
        )

        AuthError.ValidationInfoMissing,
        AuthError.ActingGlnMissing,
        AuthError.ActingFunctionMissing,
        AuthError.UnexpectedError,
        AuthError.UnknownError -> buildErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_authorization_error",
            title = "Internal authorization error",
            detail = "An internal error occurred."
        )

        AuthError.NotAuthorized -> buildErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "not_authorized",
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )
    }
