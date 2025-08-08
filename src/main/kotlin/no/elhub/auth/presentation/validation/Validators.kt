package no.elhub.auth.presentation.validation

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.domain.request.AuthorizationRequest
import no.elhub.auth.presentation.jsonapi.PostAuthorizationRequestPayload
import no.elhub.auth.presentation.jsonapi.errors.ApiError
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

fun validateAuthorizationRequest(authRequest: AuthorizationRequest): Either<ApiError.BadRequest, AuthorizationRequest> = either {
    val requestType = authRequest.requestType
    if (requestType.name != "ChangeOfSupplierConfirmation") {
        raise(ApiError.BadRequest(detail = "Invalid requestType: $requestType."))
    }
    authRequest
}

fun validateAuthorizationRequest(authRequest: PostAuthorizationRequestPayload): Either<ApiError.BadRequest, PostAuthorizationRequestPayload> = either {
    val requestType = authRequest.data.attributes.requestType
    if (requestType != "ChangeOfSupplierConfirmation") {
        raise(ApiError.BadRequest(detail = "Invalid requestType: $requestType."))
    }
    authRequest
}
