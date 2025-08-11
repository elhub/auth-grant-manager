package no.elhub.auth.features.requests

import arrow.core.Either
import no.elhub.auth.features.errors.DomainError
import no.elhub.auth.model.AuthorizationRequest
import java.util.UUID

class AuthorizationRequestHandler {

    fun getAllRequests(): Either<DomainError, List<AuthorizationRequest>> = AuthorizationRequestRepository.findAll()

    fun getRequestById(id: UUID): Either<DomainError, AuthorizationRequest> = AuthorizationRequestRepository.findById(id)

    fun postRequest(request: PostAuthorizationRequestPayload): Either<DomainError, AuthorizationRequest> =
        AuthorizationRequestRepository.create(request)
}
