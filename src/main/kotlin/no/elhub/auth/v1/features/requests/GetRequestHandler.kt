package no.elhub.auth.v1.features.requests

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.repositories.RequestRepository
import java.util.UUID

class GetRequestHandler(
    private val requestRepository: RequestRepository,
) {
    suspend operator fun invoke(
        id: UUID,
        authorizedParty: AuthorizationParty,
    ): AuthorizationRequest {
        val request = requestRepository.find(id)

        if (authorizedParty != request.requestedBy && authorizedParty != request.requestedOf) {
            throw GetRequestError.NotAuthorized()
        }

        return request
    }
}
