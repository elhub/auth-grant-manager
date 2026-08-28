package no.elhub.auth.features.grants.update

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.common.toInternalServerApiErrorResponse
import no.elhub.auth.features.common.toNotFoundApiErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class ConsumeError {
    data object GrantNotFound : ConsumeError()
    data object PersistenceError : ConsumeError()
    data object NotAuthorized : ConsumeError()
    data object ExpiredError : ConsumeError()
    data class IllegalTransitionError(val detail: String) : ConsumeError()
    data object IllegalStateError : ConsumeError()
}

fun ConsumeError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        is ConsumeError.GrantNotFound -> toNotFoundApiErrorResponse("AuthorizationGrant could not be found")

        is ConsumeError.PersistenceError -> toInternalServerApiErrorResponse()

        is ConsumeError.NotAuthorized -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )

        is ConsumeError.IllegalStateError -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Illegal status state",
            detail = "AuthorizationGrant must be 'Active' to get consumed."
        )

        is ConsumeError.IllegalTransitionError -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Invalid status transition",
            detail = this.detail
        )

        is ConsumeError.ExpiredError -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "AuthorizationGrant has expired",
            detail = "Validity period has passed."
        )
    }
