package no.elhub.auth.features.utils

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.requests.PostAuthorizationRequestPayload
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("Validators")

fun validateId(id: String?): Either<ApiError, UUID> = either {
    catch(
        { UUID.fromString(id) },
        {
            logger.error("Invalid id: $id")
            raise(ApiError.AuthorizationIdIsMalformed)
        }
    )
}

fun validateAuthorizationRequest(authRequest: PostAuthorizationRequestPayload): Either<ApiError, PostAuthorizationRequestPayload> = either {
    ensure(authRequest.data.attributes.requestType == "ChangeOfSupplierConfirmation") {
        logger.error("Request type is not correct: ${authRequest.data.attributes.requestType}")
        raise(ApiError.AuthorizationRequestTypeIsInvalid)
    }
    authRequest
}
