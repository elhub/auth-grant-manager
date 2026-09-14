package no.elhub.auth.features.grants.update

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.common.toInternalServerApiErrorResponse
import no.elhub.auth.features.common.toNotFoundApiErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class UpdateError {
    data object GrantNotFound : UpdateError()
    data object PersistenceError : UpdateError()
    data object NotAuthorized : UpdateError()
    data object ExpiredError : UpdateError()
    data class IllegalTransitionError(val detail: String) : UpdateError()
    data object IllegalStateError : UpdateError()
}

fun UpdateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        is UpdateError.GrantNotFound -> toNotFoundApiErrorResponse("AuthorizationGrant could not be found")

        is UpdateError.PersistenceError -> toInternalServerApiErrorResponse()

        is UpdateError.NotAuthorized -> buildApiErrorResponse(
            status = HttpStatusCode.Unauthorized,
            title = "Not authorized",
            detail = "Not authorized for this endpoint."
        )

        is UpdateError.IllegalStateError -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Illegal status state",
            detail = "AuthorizationGrant must be 'Active' to get updated."
        )

        is UpdateError.IllegalTransitionError -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Invalid status transition",
            detail = this.detail
        )

        is UpdateError.ExpiredError -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "AuthorizationGrant has expired",
            detail = "Validity period has passed."
        )
    }
