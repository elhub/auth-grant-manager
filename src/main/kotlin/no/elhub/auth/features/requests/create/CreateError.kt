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
            code = "not_authorized",
            title = "Party not authorized",
            detail = "RequestedBy must match the authorized party",
        )

        CreateError.PersistenceError,
        CreateError.RequestedFromPartyError,
        CreateError.RequestedByPartyError,
        CreateError.MappingError -> buildApiErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_authorization_error",
            title = "Internal authorization error",
            detail = "An internal error occurred."
        )

        is CreateError.ValidationError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Validation error",
            code = this.reason.code,
            detail = this.reason.message,
        )
    }
