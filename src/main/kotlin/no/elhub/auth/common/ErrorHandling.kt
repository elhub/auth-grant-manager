package no.elhub.auth.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import no.elhub.auth.common.documents.pdf.PdfGenerationException
import no.elhub.auth.common.documents.pdf.PdfSigningException
import no.elhub.auth.common.documents.pdf.PdfValidationException
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.features.common.buildApiErrorResponse
import no.elhub.auth.v0.features.common.toInternalServerApiErrorResponse
import no.elhub.auth.v0.features.common.toNotFoundApiErrorResponse
import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.domain.DomainError
import no.elhub.auth.v1.features.documents.ConfirmDocumentError
import no.elhub.auth.v1.features.documents.GetDocumentError
import no.elhub.auth.v1.features.documents.InvalidDocumentScopes
import no.elhub.auth.v1.features.grants.GetGrantError
import no.elhub.auth.v1.features.requests.GetRequestError
import no.elhub.auth.v1.features.requests.UpdateRequestError
import no.elhub.auth.v1.repositories.RepositoryReadError
import no.elhub.auth.v1.repositories.RepositoryWriteError
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandling")

class InvalidTraceIdException : Exception()

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<InvalidTraceIdException> { call, _ ->
            val (status, body) =
                buildApiErrorResponse(
                    status = HttpStatusCode.BadRequest,
                    title = "Invalid trace ID",
                    detail = "Header 'ElhubTraceId' must be a valid UUID",
                )
            call.respond(status, body)
        }
        exception<Errors> { call, cause ->
            val (status, body) = cause.toApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
        }
        exception<PdfValidationException> { call, cause ->
            val (status, body) = cause.toApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
        }
        exception<PdfSigningException> { call, cause ->
            logger.error("Failed to sign PDF", cause)
            val (status, body) = toInternalServerApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
        }
        exception<PdfGenerationException> { call, cause ->
            logger.error("Failed to generate PDF", cause)
            val (status, body) = toInternalServerApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
        }
        exception<InvalidDocumentScopes> { call, cause ->
            logger.error("Invalid authorization document scopes", cause)
            val (status, body) = toInternalServerApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            val (status, body) = toInternalServerApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
        }
    }
}

private fun PdfValidationException.toApiErrorResponse() =
    when (this) {
        PdfValidationException.ElhubSigningCertNotTrusted,
        PdfValidationException.InvalidElhubSignature,
        PdfValidationException.ElhubSignatureModifiedAfterSigning,
        PdfValidationException.MissingElhubSignature,
        -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Elhub signature is not valid",
                detail = "The Elhub signature could not be validated. The AuthorizationDocument may have been tampered with.",
            )
        }

        PdfValidationException.BankIdSigningCertNotFromExpectedRoot -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "End user signature validation failed",
                detail = "The end user signing certificate is not trusted.",
            )
        }

        PdfValidationException.MissingTrustedTimestamp,
        PdfValidationException.BankIdSigningCertNotValidAtTimestamp,
        PdfValidationException.BankIdCertificateRevoked,
        PdfValidationException.BankIdSignatureNotPadesLT,
        PdfValidationException.InvalidBankIdSignature,
        -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "End user signature validation failed",
                detail = "The end user signature is invalid.",
            )
        }

        PdfValidationException.MissingBankIdSignature -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "End user signature validation failed",
                detail = "The AuthorizationDocument is missing the end user signature.",
            )
        }

        PdfValidationException.MissingNationalId -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "End user signature validation failed",
                detail = "Could not extract the Norwegian national identity number from the end user signing certificate.",
            )
        }

        PdfValidationException.OriginalDocumentMismatch -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Original AuthorizationDocument mismatch",
                detail = "The AuthorizationDocument provided for confirmation differs from the original generated AuthorizationDocument.",
            )
        }
    }

// TODO revise messages and map all errors
private fun Errors.toApiErrorResponse() =
    when (this) {
        is RepositoryReadError.NotFoundError -> {
            toNotFoundApiErrorResponse()
        }

        is RepositoryReadError.UnexpectedError -> {
            toInternalServerApiErrorResponse()
        }

        is RepositoryWriteError.NotFoundError -> toNotFoundApiErrorResponse()

        is RepositoryWriteError.ConflictError -> buildApiErrorResponse(
            status = HttpStatusCode.Conflict,
            title = "Document conflict",
            detail = detail,
        )

        is RepositoryWriteError.ExpiredError -> buildApiErrorResponse(
            status = HttpStatusCode.Gone,
            title = "Document expired",
            detail = detail,
        )

        is RepositoryWriteError.UnexpectedError -> toInternalServerApiErrorResponse()

        is ConfirmDocumentError.NotAuthorized -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Party not authorized",
            detail = detail,
        )

        is ConfirmDocumentError.InvalidStatus -> buildApiErrorResponse(
            status = HttpStatusCode.Conflict,
            title = "Invalid document status",
            detail = detail,
        )

        is ConfirmDocumentError.SignatoryResolutionFailed -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Signatory resolution failed",
            detail = detail,
        )

        is ConfirmDocumentError.SignatoryNotAllowed -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Signatory not allowed",
            detail = detail,
        )

        is GetDocumentError.NotAuthorized -> {
            buildApiErrorResponse(
                status = HttpStatusCode.Forbidden,
                title = "Party not authorized",
                detail = "The party is not allowed to access this resource",
            )
        }

        is GetRequestError.NotAuthorized -> {
            buildApiErrorResponse(
                status = HttpStatusCode.Forbidden,
                title = "Party not authorized",
                detail = "The party is not allowed to access this resource",
            )
        }

        is GetGrantError.NotAuthorized -> {
            buildApiErrorResponse(
                status = HttpStatusCode.Forbidden,
                title = "Party not authorized",
                detail = "The party is not allowed to access this resource",
            )
        }

        is UpdateRequestError.NotAuthorized -> buildApiErrorResponse(
            status = HttpStatusCode.Forbidden,
            title = "Not allowed to update",
            detail = detail,
        )

        is UpdateRequestError.InvalidTransition -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Invalid status transition",
            detail = detail,
        )

        is UpdateRequestError.AlreadyProcessed -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Invalid status state",
            detail = detail,
        )

        is UpdateRequestError.Expired -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "AuthorizationRequest has expired",
            detail = detail,
        )

        is ConfirmDocumentError.InvalidGrantValidity -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Invalid grant validity",
            detail = detail,
        )

        is UpdateRequestError.InvalidGrantValidity -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Invalid grant validity",
            detail = detail,
        )

        is UpdateRequestError.UnsupportedRequestedOf -> buildApiErrorResponse(
            status = HttpStatusCode.UnprocessableEntity,
            title = "Unsupported requestedOf party",
            detail = detail,
        )

        is DomainError.InvalidExternalAgentIdentifier -> {
            buildApiErrorResponse(
                status = HttpStatusCode.BadRequest,
                title = "Invalid external agent identifier",
                detail = detail,
            )
        }

        is InputError.MissingInputError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Missing input",
                detail = detail,
            )
        }

        is InputError.MalformedInputError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Malformed input",
                detail = detail,
            )
        }

        is InputError.IdMismatchError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Invalid authorization document request",
                detail = detail,
            )
        }

        is InputError.MissingFieldError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Missing required field",
                detail = detail,
            )
        }

        is InputError.InvalidFieldValueError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Invalid field value",
                detail = detail,
            )
        }

        is DomainError.InvalidScopeConstraint -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Invalid field value",
                detail = detail,
            )
        }

        is DomainError.UnsupportedScopeConstraintAttribute -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Invalid field value",
                detail = detail,
            )
        }

        is InputError.ContentTooLargeError -> {
            buildApiErrorResponse(
                status = HttpStatusCode.PayloadTooLarge,
                title = "Content too large",
                detail = detail,
            )
        }

        is DomainError.InvalidMeteringPointId -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Invalid metering point id",
                detail = detail,
            )
        }

        is InputError.InvalidCreateAuthorizationDocumentPayload -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Invalid authorization document request",
                detail = detail,
            )
        }

        is InputError.InvalidCreateAuthorizationRequestPayload -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Invalid authorization request",
                detail = detail,
            )
        }

        is InputError.RequestedOfRequestedPrincipalMismatch -> {
            buildApiErrorResponse(
                status = HttpStatusCode.UnprocessableEntity,
                title = "Invalid authorization recipient",
                detail = "RequestedOf is not allowed to represent RequestedPrincipal",
            )
        }

        is InputError.RequestedScopeNotAllowed -> {
            buildApiErrorResponse(
                status = HttpStatusCode.Forbidden,
                title = "Requested scope is not authorized",
                detail = "RequestedBy is not allowed to request the specified scope from RequestedPrincipal",
            )
        }

        else -> {
            throw Exception("Unimplemented")
        }
    }
