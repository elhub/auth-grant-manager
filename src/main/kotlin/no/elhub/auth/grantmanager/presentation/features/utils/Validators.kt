package no.elhub.auth.grantmanager.presentation.features.utils

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.grantmanager.presentation.features.errors.ApiError
import no.elhub.auth.grantmanager.presentation.features.requests.AuthorizationRequestRequest
import java.util.UUID

/**
 * Validates a query path id string and returns a UUID if valid, or an ApiError.BadRequest if invalid.
 *
 * @param id The id string to validate.
 * @return Either an ApiError.BadRequest or a valid UUID.
 */
fun validateId(id: String?): Either<ApiError.BadRequest, UUID> = either {
    if (id.isNullOrBlank()) {
        raise(ApiError.BadRequest(detail = "Missing or malformed ID"))
    } else {
        try {
            UUID.fromString(id)
        } catch (_: IllegalArgumentException) {
            raise(ApiError.BadRequest(detail = "Missing or malformed ID"))
        }
    }
}

fun validateAuthorizationRequest(authRequest: AuthorizationRequestRequest): Either<ApiError.BadRequest, AuthorizationRequestRequest> = either {
    val requestType = authRequest.data.attributes.requestType
    if (requestType != "ChangeOfSupplierConfirmation") {
        raise(ApiError.BadRequest(detail = "Invalid requestType: $requestType."))
    }
    authRequest
}
