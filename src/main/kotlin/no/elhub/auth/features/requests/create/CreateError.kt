package no.elhub.auth.features.requests.create

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class CreateError {
    data object MappingError : CreateError()
    data object AuthorizationError : CreateError()
    data object PersistenceError : CreateError()
    data object RequestedPartyError : CreateError()
    data object InvalidNinError : CreateError()

    data class ValidationError(
        val reason: RequestTypeValidationError,
    ) : CreateError()
}

fun CreateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        CreateError.AuthorizationError -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            code = "not_authorized",
            title = "Party not authorized",
            detail = "RequestedBy must match the authorized party",
        )

        CreateError.InvalidNinError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "invalid_nin",
            title = "Party invalid",
            detail = "The nin in the request is invalid",
        )

        CreateError.PersistenceError,
        CreateError.RequestedPartyError,
        CreateError.MappingError -> buildApiErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_server_error",
            title = "Internal server error",
            detail = "An internal error occurred."
        )

        is CreateError.ValidationError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Validation error",
            code = this.reason.code,
            detail = this.reason.message,
        )
    }
