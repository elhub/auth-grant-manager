package no.elhub.auth.features.documents.create

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class CreateDocumentError {
    data object FileGenerationError : CreateDocumentError()

    data class SignFileError(val cause: FileSigningError) : CreateDocumentError()

    data object AuthorizationError : CreateDocumentError()

    data object PersistenceError : CreateDocumentError()

    data object RequestedFromPartyError : CreateDocumentError()

    data object RequestedByPartyError : CreateDocumentError()

    data object RequestedToPartyError : CreateDocumentError()

    // To be used by value streams in during the business validation process. Auth Grant will return this message back to the API consumer
    data class BusinessValidationError(val message: String) : CreateDocumentError()
}

fun CreateDocumentError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorCollection> =
    when (this) {
        is CreateDocumentError.BusinessValidationError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            code = "business_validation_error",
            title = "Business validation error",
            detail = this.message
        )

        is CreateDocumentError.AuthorizationError -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            code = "not_authorized",
            title = "Party not authorized",
            detail = "RequestedBy must match the authorized party",
        )

        CreateDocumentError.FileGenerationError,
        is CreateDocumentError.SignFileError,
        CreateDocumentError.PersistenceError,
        CreateDocumentError.RequestedByPartyError,
        CreateDocumentError.RequestedFromPartyError,
        CreateDocumentError.RequestedToPartyError -> buildApiErrorResponse(
            status = HttpStatusCode.InternalServerError,
            code = "internal_authorization_error",
            title = "Internal authorization error",
            detail = "An internal error occurred."
        )
    }
