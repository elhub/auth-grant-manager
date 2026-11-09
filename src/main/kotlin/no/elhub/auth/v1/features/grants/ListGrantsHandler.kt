package no.elhub.auth.v1.features.grants

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.GrantPartyFilters
import no.elhub.auth.v1.repositories.GrantRepository

class ListGrantsHandler(
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(
        authorizedParty: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationGrantStatus>,
        partyFilters: GrantPartyFilters,
    ): Page<AuthorizationGrant> = grantRepository.findAll(authorizedParty, pagination, statuses, partyFilters)
}
