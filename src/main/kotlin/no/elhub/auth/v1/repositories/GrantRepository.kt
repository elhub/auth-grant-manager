package no.elhub.auth.v1.repositories

import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.GrantedAuthorizationScope
import java.util.UUID

interface GrantRepository {
    suspend fun find(grantId: UUID): AuthorizationGrant
    suspend fun findBySourceIds(
        sourceType: AuthorizationGrantSourceType,
        sourceIds: List<UUID>
    ): Map<UUID, AuthorizationGrant>

    suspend fun findScopes(grantId: UUID): List<GrantedAuthorizationScope>
    suspend fun findAll(
        party: AuthorizationParty,
        pagination: Pagination
    ): Page<AuthorizationGrant>

    suspend fun insert(grant: AuthorizationGrant): AuthorizationGrant
    suspend fun update(grantId: UUID, newStatus: AuthorizationGrantStatus): AuthorizationGrant
}
