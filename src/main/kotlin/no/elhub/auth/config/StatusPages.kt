package no.elhub.auth.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.errors.ApiErrorJson
import no.elhub.auth.model.AuthorizationExceptions
import no.elhub.auth.model.RequestType
import org.slf4j.LoggerFactory

fun Application.configureStatusPages() {
    install(StatusPages) {

        val logger = LoggerFactory.getLogger(this::class.java)

        exception<AuthorizationExceptions.NotFoundException> { call, cause ->
            logger.error("Authorization with id=${cause.id} at ${call.request.uri} not found: ", cause)
            call.respond(
                HttpStatusCode.NotFound,
                ApiErrorJson.from(
                    ApiError.NotFound(detail = "Authorization with id=${cause.id} at ${call.request.uri} not found"),
                    call.url(),
                ),
            )
        }

        exception<AuthorizationExceptions.InvalidRequestTypeException> { call, cause ->
            logger.error("requestType is invalid ${call.request.uri}: ", cause)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorJson.from(
                    ApiError.BadRequest(detail = "RequestType is not ${RequestType.ChangeOfSupplierConfirmation} at ${call.request.uri}."),
                    call.url(),
                ),
            )
        }

        exception<AuthorizationExceptions.MissingIdException> { call, cause ->
            logger.error("Missing id at ${call.request.uri}: ", cause)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorJson.from(
                    ApiError.BadRequest(detail = "Missing id at ${call.request.uri}."),
                    call.url(),
                ),
            )
        }

        exception<AuthorizationExceptions.MalformedIdException> { call, cause ->
            logger.error("Malformed id at ${call.request.uri}:", cause)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorJson.from(
                    ApiError.BadRequest(detail = "Malformed id at ${call.request.uri}."),
                    call.url(),
                ),
            )
        }

        exception<Exception> { call, cause ->
            logger.error("Unexpected error at ${call.request.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorJson.from(
                    ApiError.InternalServerError(detail = "Unexpected error occurred at ${call.request.uri}."),
                    call.url(),
                ),
            )
        }

    }
}
