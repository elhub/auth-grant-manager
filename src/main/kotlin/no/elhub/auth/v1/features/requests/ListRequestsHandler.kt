package no.elhub.auth.v1.features.requests

import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.repositories.RequestRepository

class ListRequestsHandler(
    private val requestRepository: RequestRepository,
) {
    suspend operator fun invoke(
        authorizedAgent: AuthorizationAgent,
        pagination: Pagination,
        statuses: List<AuthorizationRequestStatus>,
    ): Page<AuthorizationRequest> = requestRepository.findAndSortByCreatedAt(authorizedAgent, pagination, statuses)
}
