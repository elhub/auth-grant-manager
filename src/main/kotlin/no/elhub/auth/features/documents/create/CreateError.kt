package no.elhub.auth.features.documents.create

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.documents.common.SignatureSigningError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class CreateError {
    data object FileGenerationError : CreateError()

    data class SignFileError(
        val cause: SignatureSigningError,
    ) : CreateError()

    data object AuthorizationError : CreateError()

    data object PersistenceError : CreateError()

    data object RequestedPartyError : CreateError()

    data object InvalidNinError : CreateError()

    // To be used by value streams in during the business validation process. Auth Grant will return this message back to the API consumer
    data class BusinessValidationError(
        val message: String,
    ) : CreateError()
}

fun CreateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        is CreateError.BusinessValidationError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.BadRequest,
                title = "Business validation error",
                detail = this.message,
            )
        }

        is CreateError.AuthorizationError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.Forbidden,
                title = "Party not authorized",
                detail = "RequestedBy must match the authorized party",
            )
        }

        CreateError.InvalidNinError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.BadRequest,
                title = "Invalid national identity number",
                detail = "Provided national identity number is invalid",
            )
        }

        CreateError.FileGenerationError,
        is CreateError.SignFileError,
        CreateError.PersistenceError,
        CreateError.RequestedPartyError,
        -> {
            buildApiErrorResponse(
                status = HttpStatusCode.InternalServerError,
                title = "Internal server error",
                detail = "An internal error occurred.",
            )
        }
    }
