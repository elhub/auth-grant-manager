package no.elhub.auth.features.documents.create

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.common.toInternalServerApiErrorResponse
import no.elhub.auth.features.documents.common.SignatureSigningError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class CreateError {
    data object FileGenerationError : CreateError()
    data class SignFileError(val cause: SignatureSigningError) : CreateError()
    data object AuthorizationError : CreateError()
    data object PersistenceError : CreateError()
    data object RequestedPartyError : CreateError()
    data object InvalidNinError : CreateError()
    data class BusinessError(val error: BusinessProcessError) : CreateError()
}

fun CreateError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        is CreateError.AuthorizationError -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Party not authorized",
            detail = "RequestedBy must match the authorized party",
        )

        CreateError.InvalidNinError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Invalid national identity number",
            detail = "Provided national identity number is invalid"
        )

        CreateError.FileGenerationError,
        is CreateError.SignFileError,
        CreateError.PersistenceError,
        CreateError.RequestedPartyError -> toInternalServerApiErrorResponse()

        is CreateError.BusinessError -> {
            when (error.kind) {
                BusinessProcessError.Kind.UNEXPECTED_ERROR -> toInternalServerApiErrorResponse()
                BusinessProcessError.Kind.VALIDATION -> {
                    buildApiErrorResponse(
                        status = HttpStatusCode.BadRequest,
                        title = "Validation error",
                        detail = error.detail
                    )
                }
            }
        }
    }
