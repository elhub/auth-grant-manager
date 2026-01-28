package no.elhub.auth.features.requests.update

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class UpdateError {
    data object NotAuthorizedError : UpdateError()
    data object RequestNotFound : UpdateError()
    data object PersistenceError : UpdateError()
    data object ScopeReadError : UpdateError()
    data object GrantCreationError : UpdateError()
    data object IllegalTransitionError : UpdateError()
    data object AlreadyProcessed : UpdateError()
    data object Expired : UpdateError()
}

fun UpdateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        UpdateError.PersistenceError,
        UpdateError.RequestNotFound,
        UpdateError.GrantCreationError,
        UpdateError.ScopeReadError, -> buildApiErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_server_error",
            title = "Internal server error",
            detail = "An internal error occurred."
        )

        UpdateError.IllegalTransitionError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "invalid_status_transition",
            title = "Invalid status transition",
            detail = "Only 'Accepted' and 'Rejected' statuses are allowed."
        )

        UpdateError.AlreadyProcessed -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "invalid_status_state",
            title = "Invalid status state",
            detail = "Request must be in 'Pending' status to update."
        )

        UpdateError.Expired -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "expired_status_transition",
            title = "Request has expired",
            detail = "Request validity period has passed"
        )

        UpdateError.NotAuthorizedError -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "not_authorized",
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )
    }
