package no.elhub.auth.features.documents.confirm

import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.common.toInternalServerApiErrorResponse
import no.elhub.auth.features.common.toNotFoundApiErrorResponse
import no.elhub.auth.features.documents.common.SignatureValidationError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

sealed class ConfirmError {
    data class ValidateSignaturesError(val cause: SignatureValidationError) : ConfirmError()
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
        ConfirmError.DocumentNotFoundError -> toNotFoundApiErrorResponse("AuthorizationDocument could not be found")

        is ConfirmError.ValidateSignaturesError -> handleValidateSignatureError(this)

        ConfirmError.SignatoryNotAllowedToSignDocument -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Signatory is not allowed to sign this document",
            detail = "The signer is not authorized for this document."

        )

        ConfirmError.InvalidRequestedByError -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Party not authorized",
            detail = "RequestedBy must match the authorized party.",
        )

        ConfirmError.IllegalStateError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Invalid status state",
            detail = "AuthorizationDocument must be in 'Pending' status to confirm."
        )

        ConfirmError.ExpiredError -> buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "AuthorizationDocument has expired",
            detail = "Validity period has passed."
        )

        ConfirmError.SignatoryResolutionError,
        ConfirmError.DocumentReadError,
        ConfirmError.DocumentUpdateError,
        ConfirmError.ScopeReadError,
        ConfirmError.GrantCreationError,
        ConfirmError.RequestedByResolutionError, -> toInternalServerApiErrorResponse()
    }

fun handleValidateSignatureError(error: ConfirmError.ValidateSignaturesError): Pair<HttpStatusCode, JsonApiErrorCollection> = when (error.cause) {
    SignatureValidationError.ElhubSigningCertNotTrusted,
    SignatureValidationError.InvalidElhubSignature,
    SignatureValidationError.MissingElhubSignature -> buildApiErrorResponse(
        status = HttpStatusCode.BadRequest,
        title = "Elhub signature is not valid",
        detail = "The Elhub signature could not be validated. The document may have been tampered with."
    )

    SignatureValidationError.BankIdSigningCertNotFromExpectedRoot ->
        buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "End user signature validation failed",
            detail = "The end user signing certificate is not trusted."
        )

    SignatureValidationError.MissingBankIdTrustedTimestamp,
    SignatureValidationError.BankIdSigningCertNotValidAtTimestamp,
    SignatureValidationError.InvalidBankIdSignature ->
        buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "End user signature validation failed",
            detail = "The end user signature is invalid."
        )

    SignatureValidationError.MissingBankIdSignature ->
        buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "End user signature validation failed",
            detail = "The document is missing the end user signature."
        )

    SignatureValidationError.MissingNationalId ->
        buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "End user signature validation failed",
            detail = "Could not extract the Norwegian national identity number from the end user signing certificate."
        )

    SignatureValidationError.OriginalDocumentMismatch ->
        buildApiErrorResponse(
            status = HttpStatusCode.BadRequest,
            title = "Original document mismatch",
            detail = "The document provided for confirmation differs from the original generated document."
        )
}
