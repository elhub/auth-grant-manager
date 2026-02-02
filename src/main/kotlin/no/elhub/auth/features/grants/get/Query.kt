package no.elhub.auth.features.grants.get

import no.elhub.auth.features.common.party.AuthorizationParty
import java.util.UUID

data class Query(
    val id: UUID,
    val authorizedParty: AuthorizationParty,
)
