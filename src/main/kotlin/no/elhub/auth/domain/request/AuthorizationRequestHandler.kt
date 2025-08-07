package no.elhub.auth.domain.request

import arrow.core.Either
import java.util.UUID
import no.elhub.auth.data.exposed.repositories.AuthorizationRequestRepository
import no.elhub.auth.presentation.model.PostAuthorizationRequestPayload

class AuthorizationRequestHandler {

    fun getAllRequests(): Either<AuthorizationRequestProblemList, List<AuthorizationRequest>> = AuthorizationRequestRepository.findAll()

    fun getRequestById(id: UUID): Either<AuthorizationRequestProblemById, AuthorizationRequest> = AuthorizationRequestRepository.findById(id)

    fun postRequest(request: PostAuthorizationRequestPayload): Either<AuthorizationRequestProblemCreate, AuthorizationRequest> =
        AuthorizationRequestRepository.create(request)
}
