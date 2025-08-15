package no.elhub.auth.features.common

import arrow.core.Either
import arrow.core.raise.either
import java.util.UUID

/**
 * Validates a query path id string and returns a UUID if valid, or an ApiError.BadRequest if invalid.
 *
 * @param id The id string to validate.
 * @return Either an ApiError.BadRequest or a valid UUID.
 */
fun validateId(id: String?): Either<ApiError.BadRequest, UUID> = either {
    if (id.isNullOrBlank()) {
        raise(ApiError.BadRequest(detail = "Missing or malformed id."))
    } else {
        try {
            UUID.fromString(id)
        } catch (_: IllegalArgumentException) {
            raise(ApiError.BadRequest(detail = "Missing or malformed id."))
        }
    }
}
