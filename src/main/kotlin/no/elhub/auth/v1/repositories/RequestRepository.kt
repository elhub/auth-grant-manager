package no.elhub.auth.v1.repositories

import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import java.util.UUID

interface RequestRepository {
    suspend fun find(requestId: UUID): AuthorizationRequest
    suspend fun findAndSortByCreatedAt(
        party: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationRequestStatus>
    ): Page<AuthorizationRequest>

    suspend fun insert(request: AuthorizationRequest): AuthorizationRequest
    suspend fun reject(requestId: UUID): AuthorizationRequest
    suspend fun accept(requestId: UUID, grant: AuthorizationGrant): AuthorizationRequest
}

// TODO implement
