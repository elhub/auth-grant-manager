package no.elhub.auth.features.grants.consume

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.grants.AuthorizationGrant
import java.util.UUID

data class ConsumeCommand(
    val grantId: UUID,
    val newStatus: AuthorizationGrant.Status,
    val authorizedParty: AuthorizationParty,
)
