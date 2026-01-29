package no.elhub.auth.features.requests.create

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class CreateError {
    data object MappingError : CreateError()
    data object AuthorizationError : CreateError()
    data object PersistenceError : CreateError()
    data object RequestedFromPartyError : CreateError()
    data object RequestedByPartyError : CreateError()

    data class ValidationError(
        val reason: RequestTypeValidationError,
    ) : CreateError()
}

fun CreateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        CreateError.AuthorizationError -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Party not authorized",
            detail = "RequestedBy must match the authorized party",
        )

        CreateError.PersistenceError,
        CreateError.RequestedFromPartyError,
        CreateError.RequestedByPartyError,
        CreateError.MappingError -> buildApiErrorResponse(
            status = HttpStatusCode.InternalServerError,
            title = "Internal server error",
            detail = "An internal error occurred."
        )

        is CreateError.ValidationError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Validation error",
            detail = this.reason.message,
        )
    }
