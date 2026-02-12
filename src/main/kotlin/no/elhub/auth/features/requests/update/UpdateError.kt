package no.elhub.auth.features.requests.update

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.common.toInternalServerApiErrorResponse
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
        UpdateError.ScopeReadError, -> toInternalServerApiErrorResponse()

        UpdateError.IllegalTransitionError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Invalid status transition",
            detail = "Only 'Accepted' and 'Rejected' statuses are allowed."
        )

        UpdateError.AlreadyProcessed -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Invalid status state",
            detail = "Request must be in 'Pending' status to update."
        )

        UpdateError.Expired -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Request has expired",
            detail = "Request validity period has passed"
        )

        UpdateError.NotAuthorizedError -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )
    }
