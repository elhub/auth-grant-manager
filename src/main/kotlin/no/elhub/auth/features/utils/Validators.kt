package no.elhub.auth.features.utils

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.requests.PostAuthorizationRequestPayload
import no.elhub.auth.model.AuthorizationExceptions
import no.elhub.auth.model.AuthorizationRequest
import java.util.UUID

/**
 * Validates a query path id string and returns a UUID if valid, or an ApiError.BadRequest if invalid.
 *
 * @param id The id string to validate.
 * @return Either an ApiError.BadRequest or a valid UUID.
 */
fun validateId(id: String?): UUID =
    if (id.isNullOrBlank()) {
        throw AuthorizationExceptions.MissingIdException()
    } else {
        try {
            UUID.fromString(id)
        } catch (_: IllegalArgumentException) {
            throw AuthorizationExceptions.MalformedIdException()
        }
    }


fun validateAuthorizationRequest(authRequest: PostAuthorizationRequestPayload): PostAuthorizationRequestPayload {
    return if (authRequest.data.attributes.requestType != "ChangeOfSupplierConfirmation") {
        throw AuthorizationExceptions.InvalidRequestTypeException()
    } else {
        authRequest
    }
}
