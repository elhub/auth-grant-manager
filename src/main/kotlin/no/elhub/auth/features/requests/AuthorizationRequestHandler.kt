package no.elhub.auth.features.requests

import no.elhub.auth.model.AuthorizationRequest
import java.util.UUID

class AuthorizationRequestHandler {

    fun getAllRequests(): List<AuthorizationRequest> = AuthorizationRequestRepository.findAll()

    fun getRequestById(id: UUID): AuthorizationRequest = AuthorizationRequestRepository.findById(id)

    fun postRequest(request: PostAuthorizationRequestPayload): AuthorizationRequest = AuthorizationRequestRepository.create(request)
}
