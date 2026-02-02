package no.elhub.auth.features.documents.confirm

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.documents.common.SignatureValidationError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class ConfirmError {
    data class ValidateSignaturesError(
        val cause: SignatureValidationError,
    ) : ConfirmError()

    data object SignatoryNotAllowedToSignDocument : ConfirmError()

    data object SignatoryResolutionError : ConfirmError()

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

fun ConfirmError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        ConfirmError.DocumentNotFoundError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.NotFound,
                title = "Not found",
                detail = "Document could not be found",
            )
        }

        is ConfirmError.ValidateSignaturesError,
        ConfirmError.SignatoryNotAllowedToSignDocument,
        ConfirmError.SignatoryResolutionError,
        ConfirmError.DocumentReadError,
        ConfirmError.DocumentUpdateError,
        ConfirmError.ScopeReadError,
        ConfirmError.GrantCreationError,
        ConfirmError.RequestedByResolutionError,
        -> {
            buildApiErrorResponse(
                status = HttpStatusCode.InternalServerError,
                title = "Internal Server error",
                detail = "An internal error occurred.",
            )
        }

        ConfirmError.InvalidRequestedByError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.Forbidden,
                title = "Party not authorized",
                detail = "RequestedBy must match the authorized party",
            )
        }

        ConfirmError.IllegalStateError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.NotFound,
                title = "Invalid status state",
                detail = "Document must be in 'Pending' status to confirm.",
            )
        }

        ConfirmError.ExpiredError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.BadRequest,
                title = "Document has expired",
                detail = "Document validity period has passed",
            )
        }
    }
