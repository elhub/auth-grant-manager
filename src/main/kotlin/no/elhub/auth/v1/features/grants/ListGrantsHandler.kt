package no.elhub.auth.v1.features.grants

import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.GrantAgentFilters
import no.elhub.auth.v1.repositories.GrantRepository

class ListGrantsHandler(
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(
        authorizedAgent: AuthorizationAgent,
        pagination: Pagination,
        statuses: List<AuthorizationGrantStatus>,
        agentFilters: GrantAgentFilters,
    ): Page<AuthorizationGrant> = grantRepository.findAll(authorizedAgent, pagination, statuses, agentFilters)
}
