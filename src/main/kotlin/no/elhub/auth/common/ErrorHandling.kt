package no.elhub.auth.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.features.common.buildApiErrorResponse
import no.elhub.auth.v0.features.common.toInternalServerApiErrorResponse
import no.elhub.auth.v0.features.common.toNotFoundApiErrorResponse
import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.domain.DomainError
import no.elhub.auth.v1.features.documents.GetDocumentError
import no.elhub.auth.v1.repositories.RepositoryReadError
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandling")

class InvalidTraceIdException : Exception()

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<InvalidTraceIdException> { call, _ ->
            val (status, body) = buildApiErrorResponse(
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
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            val (status, body) = toInternalServerApiErrorResponse()
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
        }
    }
}

// TODO revise messages and map all errors
private fun Errors.toApiErrorResponse() = when (this) {
    is RepositoryReadError.NotFoundError -> toNotFoundApiErrorResponse()

    is RepositoryReadError.UnexpectedError -> toInternalServerApiErrorResponse()

    is GetDocumentError.NotAuthorized -> buildApiErrorResponse(
        status = HttpStatusCode.Forbidden,
        title = "Party not authorized",
        detail = "The party is not allowed to access this resource",
    )

    is DomainError.InvalidExternalAgentIdentifier -> buildApiErrorResponse(
        status = HttpStatusCode.BadRequest,
        title = "Invalid external agent identifier",
        detail = detail,
    )

    is InputError.MissingInputError -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Missing input",
        detail = detail,
    )

    is InputError.MalformedInputError -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Malformed input",
        detail = detail,
    )

    is InputError.IdMismatchError -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid authorization document request",
        detail = detail,
    )

    is InputError.MissingFieldError -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Missing required field",
        detail = detail,
    )

    is InputError.InvalidFieldValueError -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid field value",
        detail = detail,
    )

    is DomainError.InvalidScopeConstraint -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid field value",
        detail = detail,
    )

    is DomainError.UnsupportedScopeConstraintAttribute -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid field value",
        detail = detail,
    )

    is InputError.ContentTooLargeError -> buildApiErrorResponse(
        status = HttpStatusCode.PayloadTooLarge,
        title = "Content too large",
        detail = detail,
    )

    is DomainError.InvalidMeteringPointId -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid metering point id",
        detail = detail,
    )

    is InputError.InvalidCreateAuthorizationDocumentPayload -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid authorization document request",
        detail = detail,
    )

    is InputError.RequestedOfRequestedPrincipalMismatch -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid authorization recipient",
        detail = "RequestedOf is not allowed to represent RequestedPrincipal",
    )

    is InputError.RequestedScopeNotAllowed -> buildApiErrorResponse(
        status = HttpStatusCode.Forbidden,
        title = "Requested scope is not authorized",
        detail = "RequestedBy is not allowed to request the specified scope from RequestedPrincipal",
    )

    else -> throw Exception("Unimplemented")
}
