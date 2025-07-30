package no.elhub.auth.features.requests

import arrow.core.Either
import no.elhub.auth.model.AuthorizationRequest
import java.util.UUID

class AuthorizationRequestHandler {

    fun getAllRequests(): Either<AuthorizationRequestProblem, List<AuthorizationRequest>> = AuthorizationRequestRepository.findAll()

    fun getRequestById(id: UUID): Either<AuthorizationRequestProblem, AuthorizationRequest> = AuthorizationRequestRepository.findById(id)

    fun postRequest(request: PostAuthorizationRequestPayload): Either<AuthorizationRequestProblem, AuthorizationRequest> =
        AuthorizationRequestRepository.create(request)
}
