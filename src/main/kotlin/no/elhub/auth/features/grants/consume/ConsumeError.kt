package no.elhub.auth.features.grants.consume

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class ConsumeError {
    data object GrantNotFound : ConsumeError()
    data object PersistenceError : ConsumeError()
    data object NotAuthorized : ConsumeError()
    data object ExpiredError : ConsumeError()
    data object IllegalTransitionError : ConsumeError()
    data object IllegalStateError : ConsumeError()
}

fun ConsumeError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        ConsumeError.GrantNotFound -> buildApiErrorResponse(
            status = HttpStatusCode.NotFound,
            title = "Not found",
            detail = "Grant could not be found"
        )

        ConsumeError.PersistenceError -> buildApiErrorResponse(
            status = HttpStatusCode.InternalServerError,
            title = "Internal server error",
            detail = "An internal error occurred."
        )

        ConsumeError.NotAuthorized -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )

        ConsumeError.IllegalStateError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Illegal status state",
            detail = "Grant must be 'Active' to get consumed"
        )

        ConsumeError.IllegalTransitionError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Invalid status transition",
            detail = "Only 'Exhausted' status is allowed."
        )

        ConsumeError.ExpiredError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Grant has expired",
            detail = "Grant validity period has passed"
        )
    }
