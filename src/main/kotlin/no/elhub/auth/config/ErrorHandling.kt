package no.elhub.auth.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.ExperimentalSerializationApi
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.common.toInternalServerApiErrorResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandling")

class InvalidTraceIdException : Exception()

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<InvalidTraceIdException> { call, cause ->
            val (status, body) = buildApiErrorResponse(
                status = HttpStatusCode.BadRequest,
                title = "Invalid trace ID",
                detail = "Header 'ElhubTraceID' must be a valid UUID"
            )
            call.respond(status, body)
        }
        exception<Throwable> { call, cause ->
            val root = generateSequence(cause) { it.cause }.last()
            logger.error("Unhandled exception", cause)
            val (status, body) = toInternalServerApiErrorResponse()
            call.respond(status, body)
        }
    }
}
