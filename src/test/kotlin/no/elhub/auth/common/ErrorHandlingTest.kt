package no.elhub.auth.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.elhub.auth.common.documents.pdf.PdfGenerationException
import no.elhub.auth.common.documents.pdf.PdfSigningException
import no.elhub.auth.common.documents.pdf.PdfValidationException
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.features.documents.ConfirmDocumentError
import no.elhub.auth.v1.features.documents.InvalidDocumentScopes
import no.elhub.auth.v1.features.grants.UpdateGrantError
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.auth.v1.repositories.RepositoryWriteError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

class ErrorHandlingTest : FunSpec({
    data class ErrorCase(
        val cause: Throwable,
        val status: HttpStatusCode,
        val title: String,
        val detail: String,
    )

    val elhubSignatureDetail = "The Elhub signature could not be validated. The AuthorizationDocument may have been tampered with."
    val endUserTitle = "End user signature validation failed"
    val invalidSignatureDetail = "The end user signature is invalid."
    val validationCases = listOf(
        PdfValidationException.ElhubSigningCertNotTrusted,
        PdfValidationException.InvalidElhubSignature,
        PdfValidationException.ElhubSignatureModifiedAfterSigning,
        PdfValidationException.MissingElhubSignature,
    ).map { ErrorCase(it, HttpStatusCode.UnprocessableEntity, "Elhub signature is not valid", elhubSignatureDetail) } +
        listOf(
            PdfValidationException.MissingTrustedTimestamp,
            PdfValidationException.BankIdSigningCertNotValidAtTimestamp,
            PdfValidationException.BankIdCertificateRevoked,
            PdfValidationException.BankIdSignatureNotPadesLT,
            PdfValidationException.InvalidBankIdSignature,
        ).map { ErrorCase(it, HttpStatusCode.UnprocessableEntity, endUserTitle, invalidSignatureDetail) } +
        listOf(
            ErrorCase(
                PdfValidationException.BankIdSigningCertNotFromExpectedRoot,
                HttpStatusCode.UnprocessableEntity,
                endUserTitle,
                "The end user signing certificate is not trusted.",
            ),
            ErrorCase(
                PdfValidationException.MissingBankIdSignature,
                HttpStatusCode.UnprocessableEntity,
                endUserTitle,
                "The AuthorizationDocument is missing the end user signature.",
            ),
            ErrorCase(
                PdfValidationException.MissingNationalId,
                HttpStatusCode.UnprocessableEntity,
                endUserTitle,
                "Could not extract the Norwegian national identity number from the end user signing certificate.",
            ),
            ErrorCase(
                PdfValidationException.OriginalDocumentMismatch,
                HttpStatusCode.UnprocessableEntity,
                "Original AuthorizationDocument mismatch",
                "The AuthorizationDocument provided for confirmation differs from the original generated AuthorizationDocument.",
            ),
        )

    val confirmationCases = listOf(
        ErrorCase(
            ConfirmDocumentError.NotAuthorized(),
            HttpStatusCode.Forbidden,
            "Party not authorized",
            "The authorized party is not permitted to confirm this document.",
        ),
        ErrorCase(
            ConfirmDocumentError.InvalidStatus(AuthorizationDocumentStatus.Approved),
            HttpStatusCode.Conflict,
            "Invalid document status",
            "AuthorizationDocument cannot be confirmed from status 'Approved'. Only Pending documents can be confirmed.",
        ),
        ErrorCase(
            ConfirmDocumentError.SignatoryResolutionFailed(),
            HttpStatusCode.UnprocessableEntity,
            "Signatory resolution failed",
            "The document signatory could not be resolved.",
        ),
        ErrorCase(
            ConfirmDocumentError.SignatoryNotAllowed(),
            HttpStatusCode.Forbidden,
            "Signatory not allowed",
            "The signer does not match the document's requestedOf party.",
        ),
        ErrorCase(
            RepositoryReadError.NotFoundError("sensitive read detail"),
            HttpStatusCode.NotFound,
            "Not found error",
            "The requested resource could not be found",
        ),
        ErrorCase(
            RepositoryWriteError.NotFoundError("sensitive write detail"),
            HttpStatusCode.NotFound,
            "Not found error",
            "The requested resource could not be found",
        ),
        ErrorCase(
            RepositoryWriteError.ConflictError("Already confirmed"),
            HttpStatusCode.Conflict,
            "Document conflict",
            "Already confirmed",
        ),
        ErrorCase(
            RepositoryWriteError.ExpiredError("Document expired"),
            HttpStatusCode.Gone,
            "Document expired",
            "Document expired",
        ),
    )

    val grantUpdateCases = listOf(
        ErrorCase(
            UpdateGrantError.NotAuthorized(),
            HttpStatusCode.Forbidden,
            "Not allowed to update",
            "Only systems may update an AuthorizationGrant.",
        ),
        ErrorCase(
            UpdateGrantError.InvalidTransition(),
            HttpStatusCode.UnprocessableEntity,
            "Invalid status transition",
            "Only active AuthorizationGrants may be revoked, and only active consumable grants may be exhausted.",
        ),
        ErrorCase(
            UpdateGrantError.Expired(),
            HttpStatusCode.UnprocessableEntity,
            "AuthorizationGrant has expired",
            "AuthorizationGrant has expired.",
        ),
    )

    (validationCases + confirmationCases + grantUpdateCases).forEach { case ->
        test("maps ${case.cause::class.qualifiedName} to ${case.status.value} JSON:API error") {
            testApplication {
                application {
                    configureErrorHandling()
                    routing { get("/error") { throw case.cause } }
                }

                val response = client.get("/error")
                response.status shouldBe case.status
                response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                val error = Json.decodeFromString<JsonApiErrorCollection>(response.bodyAsText()).errors.single()
                error.status shouldBe case.status.value.toString()
                error.title shouldBe case.title
                error.detail shouldBe case.detail
            }
        }
    }

    listOf(
        PdfGenerationException(IllegalStateException("secret generation error")),
        PdfSigningException.SigningDataGenerationError,
        PdfSigningException.AddSignatureToSignatureError,
        PdfSigningException.SignatureFetchingError,
        RepositoryReadError.UnexpectedError("secret database read error"),
        RepositoryWriteError.UnexpectedError("secret database write error"),
        InvalidDocumentScopes("secret stored document scope error"),
    ).forEach { cause ->
        test("hides details of ${cause::class.qualifiedName} behind a generic 500 JSON:API error") {
            testApplication {
                application {
                    configureErrorHandling()
                    routing { get("/error") { throw cause } }
                }

                val response = client.get("/error")
                response.status shouldBe HttpStatusCode.InternalServerError
                response.contentType()?.withoutParameters() shouldBe jsonApiContentType
                val error = Json.decodeFromString<JsonApiErrorCollection>(response.bodyAsText()).errors.single()
                error.status shouldBe "500"
                error.title shouldBe "Internal server error"
                error.detail shouldBe "An internal server error occurred"
            }
        }
    }
})
