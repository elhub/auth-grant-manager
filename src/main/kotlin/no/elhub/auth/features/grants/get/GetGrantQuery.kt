package no.elhub.auth.features.grants.get

import java.util.UUID
import no.elhub.auth.features.grants.AuthorizationGrant

data class GetGrantQuery(
    val id: UUID
)
