package no.elhub.auth.features.documents.confirm

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class ConfirmError {
    data object DocumentNotFoundError : ConfirmError()
    data object DocumentReadError : ConfirmError()
    data object DocumentUpdateError : ConfirmError()
    data object ScopeReadError : ConfirmError()
    data object GrantCreationError : ConfirmError()
    data object RequestedByResolutionError : ConfirmError()
    data object InvalidRequestedByError : ConfirmError()
    data object IllegalStateError : ConfirmError()
    data object ExpiredError : ConfirmError()
}

fun ConfirmError.toConfirmErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        ConfirmError.DocumentNotFoundError -> buildErrorResponse(
            status = HttpStatusCode.NotFound,
            code = "not_found",
            title = "Not found",
            detail = "Document could not be found"
        )

        ConfirmError.DocumentReadError,
        ConfirmError.DocumentUpdateError,
        ConfirmError.ScopeReadError,
        ConfirmError.GrantCreationError,
        ConfirmError.RequestedByResolutionError -> buildErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_authorization_error",
            title = "Internal authorization error",
            detail = "An internal error occurred."
        )

        ConfirmError.InvalidRequestedByError -> buildErrorResponse(
            status = HttpStatusCode.Forbidden,
            code = "not_authorized",
            title = "Party Not Authorized",
            detail = "RequestedBy must match the authorized party",
        )

        ConfirmError.IllegalStateError -> buildErrorResponse(
            status = HttpStatusCode.NotFound,
            code = "invalid_status_state",
            title = "Invalid Status State",
            detail = "Document must be in 'Pending' status to confirm."
        )

        ConfirmError.ExpiredError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "expired_status_transition",
            title = "Document Has Expired",
            detail = "Document validity period has passed"
        )
    }
