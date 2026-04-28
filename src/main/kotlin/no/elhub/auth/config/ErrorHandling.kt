package no.elhub.auth.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import no.elhub.auth.features.common.buildApiErrorResponse
import no.elhub.auth.features.common.toInternalServerApiErrorResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandling")

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val root = generateSequence(cause) { it.cause }.last()
            when (root) {
                is MissingFieldException -> {
                    logger.error("Missing required field in request body", root)
                    val detail = "Field '${root.missingFields}' is missing or invalid"
                    val (status, body) = buildApiErrorResponse(
                        HttpStatusCode.BadRequest,
                        "Missing required field in request body",
                        detail
                    )
                    call.respond(status, body)
                }

                else -> {
                    // domain/expected errors are handled via Arrow with Either, so reaching this block
                    // means an unexpected failure, and we return a generic 500 internal server error
                    logger.error("Unhandled exception", cause)
                    val (status, body) = toInternalServerApiErrorResponse()
                    call.respond(status, body)
                }
            }
        }
    }
}
