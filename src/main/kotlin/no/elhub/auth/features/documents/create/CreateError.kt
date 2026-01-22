package no.elhub.auth.features.documents.create

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.documents.common.SignatureSigningError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class CreateError {
    data object FileGenerationError : CreateError()
    data class SignFileError(val cause: SignatureSigningError) : CreateError()
    data object AuthorizationError : CreateError()
    data object PersistenceError : CreateError()
    data object RequestedPartyError : CreateError()

    // To be used by value streams in during the business validation process. Auth Grant will return this message back to the API consumer
    data class BusinessValidationError(val message: String) : CreateError()
}

fun CreateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        is CreateError.BusinessValidationError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "business_validation_error",
            title = "Business validation error",
            detail = this.message
        )

        is CreateError.AuthorizationError -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            code = "not_authorized",
            title = "Party not authorized",
            detail = "RequestedBy must match the authorized party",
        )

        CreateError.FileGenerationError,
        is CreateError.SignFileError,
        CreateError.PersistenceError,
        CreateError.RequestedPartyError -> buildApiErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_server_error",
            title = "Internal server error",
            detail = "An internal error occurred."
        )
    }
