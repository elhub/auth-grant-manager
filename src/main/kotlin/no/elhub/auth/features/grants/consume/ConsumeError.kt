package no.elhub.auth.features.grants.consume

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class ConsumeError {
    data object GrantNotFound : ConsumeError()
    data object PersistenceError : ConsumeError()
    data object NotAuthorized : ConsumeError()
    data object ExpiredError : ConsumeError()
    data object IllegalTransitionError : ConsumeError()
    data object IllegalStateError : ConsumeError()
}

fun ConsumeError.toConsumeErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        ConsumeError.GrantNotFound -> buildErrorResponse(
            status = HttpStatusCode.NotFound,
            code = "not_found",
            title = "Not found",
            detail = "Grant could not be found"
        )

        ConsumeError.PersistenceError -> buildErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_authorization_error",
            title = "Internal authorization error",
            detail = "An internal error occurred."
        )

        ConsumeError.NotAuthorized -> buildErrorResponse(
            status = HttpStatusCode.Unauthorized,
            code = "not_authorized",
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )

        ConsumeError.IllegalStateError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "illegal_status_state",
            title = "Illegal status state",
            detail = "Grant must be 'Active' to get consumed"
        )

        ConsumeError.IllegalTransitionError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "invalid_status_transition",
            title = "Invalid status transition",
            detail = "Only 'Exhausted' status is allowed."
        )

        ConsumeError.ExpiredError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "expired_status_transition",
            title = "Grant has expired",
            detail = "Grant validity period has passed"
        )
    }
