package no.elhub.auth.v0.features.requests.get

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import java.util.UUID

data class Query(
    val id: UUID,
    val authorizedParty: AuthorizationParty
)
