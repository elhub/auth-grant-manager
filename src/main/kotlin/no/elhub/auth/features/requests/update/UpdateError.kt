package no.elhub.auth.features.requests.update

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class UpdateError {
    data object NotAuthorizedError : UpdateError()
    data object RequestNotFound : UpdateError()
    data object PersistenceError : UpdateError()
    data object ScopeReadError : UpdateError()
    data object GrantCreationError : UpdateError()
    data object IllegalTransitionError : UpdateError()
    data object IllegalStateError : UpdateError()
    data object ExpiredError : UpdateError()
}

fun UpdateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        UpdateError.PersistenceError,
        UpdateError.RequestNotFound,
        UpdateError.GrantCreationError,
        UpdateError.ScopeReadError, -> buildErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_authorization_error",
            title = "Internal authorization error",
            detail = "An internal error occurred."
        )

        UpdateError.IllegalTransitionError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "invalid_status_transition",
            title = "Invalid Status Transition",
            detail = "Only 'Accepted' and 'Rejected' statuses are allowed."
        )

        UpdateError.IllegalStateError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "invalid_status_state",
            title = "Invalid Status State",
            detail = "Request must be in 'Pending' status to update."
        )

        UpdateError.ExpiredError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "expired_status_transition",
            title = "Request Has Expired",
            detail = "Request validity period has passed"
        )

        UpdateError.NotAuthorizedError -> buildErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "not_authorized",
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )
    }
