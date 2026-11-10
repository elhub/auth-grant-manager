package no.elhub.auth.v1.features.requests

import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.repositories.RequestRepository
import java.util.UUID

class GetRequestHandler(
    private val requestRepository: RequestRepository,
) {
    suspend operator fun invoke(
        id: UUID,
        authorizedAgent: AuthorizationAgent,
    ): AuthorizationRequest {
        val request = requestRepository.find(id)

        if (authorizedAgent != request.requestedBy && authorizedAgent != request.requestedOf) {
            throw GetRequestError.NotAuthorized()
        }

        return request
    }
}
