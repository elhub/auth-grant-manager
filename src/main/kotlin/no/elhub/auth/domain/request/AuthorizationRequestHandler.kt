package no.elhub.auth.domain.request

import arrow.core.Either
import no.elhub.auth.data.persist.repositories.AuthorizationRequestRepository
import no.elhub.auth.presentation.model.PostAuthorizationRequestPayload
import java.util.UUID

class AuthorizationRequestHandler {

    fun getAllRequests(): Either<AuthorizationRequestProblemList, List<AuthorizationRequest>> = AuthorizationRequestRepository.findAll()

    fun getRequestById(id: UUID): Either<AuthorizationRequestProblemById, AuthorizationRequest> = AuthorizationRequestRepository.findById(id)

    fun postRequest(request: PostAuthorizationRequestPayload): Either<AuthorizationRequestProblemCreate, AuthorizationRequest> =
        AuthorizationRequestRepository.create(request)
}
