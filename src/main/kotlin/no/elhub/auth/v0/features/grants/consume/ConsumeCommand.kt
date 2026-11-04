package no.elhub.auth.v0.features.grants.consume

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.v0.features.grants.AuthorizationGrant
import java.util.UUID

data class ConsumeCommand(
    val grantId: UUID,
    val newStatus: AuthorizationGrant.Status,
    val authorizedParty: AuthorizationParty
)
