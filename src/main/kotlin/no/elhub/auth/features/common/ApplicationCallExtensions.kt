package no.elhub.auth.features.common

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException

@OptIn(ExperimentalSerializationApi::class)
internal suspend inline fun <reified T : Any> ApplicationCall.receiveEither(): Either<InputError, T> =
    try {
        receive<T>().right()
    } catch (e: Exception) {
        val root = generateSequence(e as Throwable) { it.cause }.last()
        when (root) {
            is MissingFieldException ->
                InputError.MissingFieldError(root.missingFields).left()

            is SerializationException ->
                InputError.InvalidFieldValueError(formatDeserializationDetail(root.message ?: "Invalid request body"))
                    .left()

            else -> throw e
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
