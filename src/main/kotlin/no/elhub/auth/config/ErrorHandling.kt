package no.elhub.auth.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
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

                is SerializationException -> {
                    logger.error("Failed to deserialize request body: {}", root.localizedMessage)
                    val (status, body) = buildApiErrorResponse(
                        HttpStatusCode.BadRequest,
                        "Invalid field value in request body",
                        formatDeserializationDetail(root.message ?: "Invalid request body")
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

private fun formatDeserializationDetail(raw: String): String {
    // kotlinx.serialization appends the path to the first line of the message in one of two formats:
    //   - enum errors (StreamingJsonDecoder):  "... at path $.field"   (no colon)
    //   - lexer/structural errors:             "... at path: $.field"  (with colon)
    // Both use the stable $.field.subField notation.
    val firstLine = raw.lineSequence().first()
    val path = firstLine
        .substringAfterLast("at path: ", missingDelimiterValue = "")
        .ifEmpty { firstLine.substringAfterLast("at path ", missingDelimiterValue = "") }
        .ifEmpty { null }

    // Enum errors follow the pattern: "<SerialName> does not contain element with name '<value>'"
    val invalidEnumValue = firstLine
        .substringAfter("does not contain element with name '", missingDelimiterValue = "")
        .substringBefore("'")
        .ifEmpty { null }

    return when {
        path != null && invalidEnumValue != null ->
            "Invalid value '$invalidEnumValue' for field '${path.substringAfterLast(".")}' at $path"

        path != null ->
            "Invalid value for field '${path.substringAfterLast(".")}' at $path"

        else -> "Invalid request body"
    }
}
