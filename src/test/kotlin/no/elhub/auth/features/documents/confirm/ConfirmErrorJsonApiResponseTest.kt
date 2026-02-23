package no.elhub.auth.features.documents.confirm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.documents.common.SignatureValidationError

class ConfirmErrorJsonApiResponseTest : FunSpec({

    data class Expectation(val status: HttpStatusCode, val title: String, val detail: String)

    fun assertMapping(error: ConfirmError, expected: Expectation) {
        val (status, response) = error.toApiErrorResponse()

        status shouldBe expected.status
        response.errors.size shouldBe 1
        response.errors.first().apply {
            this.status shouldBe expected.status.value.toString()
            title shouldBe expected.title
            detail shouldBe expected.detail
        }
    }

    listOf(
        ConfirmError.DocumentNotFoundError to Expectation(
            status = HttpStatusCode.NotFound,
            title = "Not found error",
            detail = "AuthorizationDocument could not be found."
        ),
        ConfirmError.SignatoryNotAllowedToSignDocument to Expectation(
            status = HttpStatusCode.Forbidden,
            title = "Signatory is not allowed to sign",
            detail = "The signer is not authorized for this AuthorizationDocument."
        ),
        ConfirmError.InvalidRequestedByError to Expectation(
            status = HttpStatusCode.Forbidden,
            title = "Party not authorized",
            detail = "RequestedBy must match the authorized party."
        ),
        ConfirmError.IllegalStateError to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "Invalid status state",
            detail = "AuthorizationDocument must be in 'Pending' status to confirm."
        ),
        ConfirmError.ExpiredError to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "AuthorizationDocument has expired",
            detail = "Validity period has passed."
        )
    ).forEach { (error, expected) ->
        test("maps ${error::class.simpleName} to ${expected.status}") {
            assertMapping(error, expected)
        }
    }

    listOf(
        ConfirmError.SignatoryResolutionError,
        ConfirmError.DocumentReadError,
        ConfirmError.DocumentUpdateError,
        ConfirmError.ScopeReadError,
        ConfirmError.GrantCreationError,
        ConfirmError.RequestedByResolutionError
    ).forEach { error ->
        test("maps ${error::class.simpleName} to InternalServerError") {
            assertMapping(
                error,
                Expectation(
                    status = HttpStatusCode.InternalServerError,
                    title = "Internal server error",
                    detail = "An internal server error occurred"
                )
            )
        }
    }

    listOf(
        SignatureValidationError.ElhubSigningCertNotTrusted to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "Elhub signature is not valid",
            detail = "The Elhub signature could not be validated. The AuthorizationDocument may have been tampered with."
        ),
        SignatureValidationError.InvalidElhubSignature to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "Elhub signature is not valid",
            detail = "The Elhub signature could not be validated. The AuthorizationDocument may have been tampered with."
        ),
        SignatureValidationError.MissingElhubSignature to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "Elhub signature is not valid",
            detail = "The Elhub signature could not be validated. The AuthorizationDocument may have been tampered with."
        ),
        SignatureValidationError.BankIdSigningCertNotFromExpectedRoot to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "End user signature validation failed",
            detail = "The end user signing certificate is not trusted."
        ),
        SignatureValidationError.InvalidBankIdSignature to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "End user signature validation failed",
            detail = "The end user signature is invalid."
        ),
        SignatureValidationError.MissingBankIdSignature to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "End user signature validation failed",
            detail = "The AuthorizationDocument is missing the end user signature."
        ),
        SignatureValidationError.MissingNationalId to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "End user signature validation failed",
            detail = "Could not extract the Norwegian national identity number from the end user signing certificate."
        ),
        SignatureValidationError.OriginalDocumentMismatch to Expectation(
            status = HttpStatusCode.BadRequest,
            title = "Original AuthorizationDocument mismatch",
            detail = "The AuthorizationDocument provided for confirmation differs from the original generated AuthorizationDocument."
        )
    ).forEach { (cause, expected) ->
        test("maps ValidateSignaturesError(${cause::class.simpleName}) to ${expected.status}") {
            assertMapping(ConfirmError.ValidateSignaturesError(cause), expected)
        }
    }
})
