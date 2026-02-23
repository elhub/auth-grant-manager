package no.elhub.auth.features.common

import arrow.core.Either
import arrow.core.left
import java.util.UUID

/**
 * Validates a query path id string and returns a UUID if valid, or an ApiError.BadRequest if invalid.
 *
 * @param id The id string to validate.
 * @return Either an ApiError.BadRequest or a valid UUID.
 */
fun validatePathId(id: String?): Either<InputError, UUID> = Either.catch {
    if (id.isNullOrBlank()) {
        return InputError.MissingInputError.left()
    }

    UUID.fromString(id)
}.mapLeft {
    return InputError.MalformedInputError.left()
}

fun validateDataId(dataId: String?, pathId: UUID): Either<InputError, UUID> {
    if (dataId.isNullOrBlank()) return InputError.MissingInputError.left()
    return Either.catch {
        val parsed = UUID.fromString(dataId)
        if (parsed != pathId) throw IllegalArgumentException()
        parsed
    }.mapLeft { InputError.IdMismatchError }
}
