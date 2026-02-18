package no.elhub.auth.features.common.auth

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.common.toInternalServerApiErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun AuthError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        AuthError.MissingAuthorizationHeader -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            title = "Missing authorization",
            detail = "Bearer token is required in the Authorization header."
        )

        AuthError.InvalidAuthorizationHeader -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            title = "Invalid authorization header",
            detail = "Authorization header must use the Bearer scheme."
        )

        AuthError.MissingSenderGlnHeader -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Missing senderGLN header",
            detail = "SenderGLN header is required for authorization."
        )

        AuthError.InvalidToken -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            title = "Invalid token",
            detail = "Token could not be verified."
        )

        AuthError.ActingFunctionNotSupported -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Unsupported party type",
            detail = "The party type you are authorized as is not supported for this endpoint."
        )

        AuthError.NotAuthorized -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            title = "Not authorized",
            detail = "Authentication is required or invalid."
        )

        AuthError.AccessDenied -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Forbidden",
            detail = "Access is denied for this endpoint."
        )

        AuthError.InvalidPdpResponseAuthInfoMissing,
        AuthError.InvalidPdpResponseActingGlnMissing,
        AuthError.InvalidPdpResponseAuthorizedFunctionsMissing,
        AuthError.UnknownError -> toInternalServerApiErrorResponse()
    }
