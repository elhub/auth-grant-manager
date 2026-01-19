package no.elhub.auth.features.requests.create

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildErrorResponse
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
        CreateError.AuthorizationError -> buildErrorResponse(
            status = HttpStatusCode.Forbidden,
            code = "not_authorized",
            title = "Party Not Authorized",
            detail = "RequestedBy must match the authorized party",
        )

        CreateError.PersistenceError,
        CreateError.RequestedFromPartyError,
        CreateError.RequestedByPartyError,
        CreateError.MappingError -> buildErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_authorization_error",
            title = "Internal authorization error",
            detail = "An internal error occurred."
        )

        is CreateError.ValidationError -> buildErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Validation Error",
            code = this.reason.code,
            detail = this.reason.message,
        )
    }
