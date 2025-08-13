package no.elhub.auth.features.requests

import arrow.core.Either
import no.elhub.auth.features.errors.RepositoryError
import no.elhub.auth.model.AuthorizationRequest
import java.util.UUID

class AuthorizationRequestHandler {

    fun getAllRequests(): Either<RepositoryError, List<AuthorizationRequest>> = AuthorizationRequestRepository.findAll()

    fun getRequestById(id: UUID): Either<RepositoryError, AuthorizationRequest> = AuthorizationRequestRepository.findById(id)

    fun postRequest(request: PostAuthorizationRequestPayload): Either<RepositoryError, AuthorizationRequest> =
        AuthorizationRequestRepository.create(request)
}
