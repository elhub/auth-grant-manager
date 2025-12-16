package no.elhub.auth.features.grants.getScopes

import no.elhub.auth.features.common.party.PartyIdentifier
import java.util.UUID

data class Query(
    val id: UUID,
    val grantedTo: PartyIdentifier
)
