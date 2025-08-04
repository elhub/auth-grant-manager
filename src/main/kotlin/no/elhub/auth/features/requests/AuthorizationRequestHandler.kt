package no.elhub.auth.features.requests

import arrow.core.Either
import no.elhub.auth.model.AuthorizationRequest
import java.util.UUID

class AuthorizationRequestHandler {

    fun getAllRequests(): Either<AuthorizationRequestProblemList, List<AuthorizationRequest>> = AuthorizationRequestRepository.findAll()

    fun getRequestById(id: UUID): Either<AuthorizationRequestProblemById, AuthorizationRequest> = AuthorizationRequestRepository.findById(id)

    fun postRequest(request: PostAuthorizationRequestPayload): Either<AuthorizationRequestProblemCreate, AuthorizationRequest> =
        AuthorizationRequestRepository.create(request)
}
