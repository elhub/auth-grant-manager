package no.elhub.auth.v0.features.grants.getscopes

import no.elhub.auth.common.auth.AuthorizationParty
import java.util.UUID

data class Query(
    val id: UUID,
    val authorizedParty: AuthorizationParty
)
