package no.elhub.auth.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.common.toDeserializationApiErrorResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandling")

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val root = generateSequence(cause) { it.cause }.last()
            when (root) {
                is SerializationException -> {
                    logger.error("Failed to deserialize request body", root)
                    val (status, body) = toDeserializationApiErrorResponse()
                    call.respond(status, body)
                }

                else -> {
                    // domain/expected errors are handled via Arrow with Either, so reaching this block
                    // means an unexpected failure, and we return a generic 500 internal server error
                    logger.error("Unhandled exception", cause)
                    val (status, body) = buildApiErrorResponse(
                        status = HttpStatusCode.InternalServerError,
                        code = "internal_error",
                        title = "Internal server error",
                        detail = "An unexpected error occurred"
                    )
                    call.respond(status, body)
                }
            }
        }
    }
}
