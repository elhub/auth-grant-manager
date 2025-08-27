package no.elhub.auth.features.grants.getScopes

import java.util.UUID
import no.elhub.auth.features.grants.AuthorizationGrant

data class GetGrantScopesQuery(
    val id: UUID
)
