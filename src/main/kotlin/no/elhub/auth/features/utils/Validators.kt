package no.elhub.auth.features.utils

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.errors.DomainError
import no.elhub.auth.features.requests.PostAuthorizationRequestPayload
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

// TODO validateIdWithDomainError(...) will potentially replace validateId(...)
fun validateIdWithDomainError(id: String?): Either<DomainError, UUID> = either {
    val value = id?.takeIf { it.isNotBlank() }
        ?: raise(DomainError.ApiError.AuthorizationIdIsMissing)

    runCatching { UUID.fromString(value) }.getOrElse {
        raise(DomainError.ApiError.AuthorizationIdIsMalformed)
    }
}

fun validateAuthorizationRequest(authRequest: PostAuthorizationRequestPayload): Either<ApiError.BadRequest, PostAuthorizationRequestPayload> = either {
    ensure(authRequest.data.attributes.requestType == "ChangeOfSupplierConfirmation") {
        raise(ApiError.BadRequest(detail = "Invalid requestType: ${authRequest.data.attributes.requestType}."))
    }
    authRequest
}
