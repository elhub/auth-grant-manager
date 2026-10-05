package no.elhub.auth.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.ExperimentalSerializationApi
import no.elhub.auth.v0.features.common.buildApiErrorResponse
import no.elhub.auth.v0.features.common.toInternalServerApiErrorResponse
import no.elhub.auth.v1.Errors
import no.elhub.auth.v1.InputError
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
            call.respond(status, body)
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            val (status, body) = toInternalServerApiErrorResponse()
            call.respond(status, body)
        }
    }
}

// TODO revise messages and map all errors
private fun Errors.toApiErrorResponse() = when (this) {
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

    is InputError.ContentTooLargeError -> buildApiErrorResponse(
        status = HttpStatusCode.PayloadTooLarge,
        title = "Content too large",
        detail = detail,
    )

    is InputError.InvalidMeteringPointId -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid metering point id",
        detail = detail,
    )

    is InputError.InvalidCreateAuthorizationDocumentPayload -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid authorization document request",
        detail = detail,
    )

    is InputError.RequestedToRequestedFromMismatch -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Invalid authorization recipient",
        detail = "RequestedTo is not allowed to represent RequestedFrom",
    )

    is InputError.RequestedScopeNotAllowed -> buildApiErrorResponse(
        status = HttpStatusCode.Forbidden,
        title = "Requested scope is not authorized",
        detail = "RequestedBy is not allowed to request the specified scope from RequestedFrom",
    )

    else -> throw Exception("Unimplemented")
}
