package no.elhub.auth.features.utils

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.errors.DomainError
import no.elhub.auth.features.requests.PostAuthorizationRequestPayload
import java.util.UUID

fun validateId(id: String?): Either<DomainError, UUID> = either {
    catch(
        { UUID.fromString(id) },
        { raise(DomainError.ApiError.AuthorizationIdIsMalformed) }
    )
}

fun validateAuthorizationRequest(authRequest: PostAuthorizationRequestPayload): Either<DomainError, PostAuthorizationRequestPayload> = either {
    ensure(authRequest.data.attributes.requestType == "ChangeOfSupplierConfirmation") {
        raise(DomainError.ApiError.AuthorizationRequestTypeIsInvalid)
    }
    authRequest
}
