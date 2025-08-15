package no.elhub.auth.features.grants.getScopes

import arrow.core.Either
import arrow.core.right
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import java.util.UUID

class GetGrantScopesHandler(private val repo: GrantRepository) {
    suspend operator fun invoke(query: GetGrantScopesQuery) = repo.getScopes(query.id)
}
