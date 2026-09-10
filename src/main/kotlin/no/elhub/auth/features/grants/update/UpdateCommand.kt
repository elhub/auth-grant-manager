package no.elhub.auth.features.grants.update

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.grants.AuthorizationGrant
import java.util.UUID

data class UpdateCommand(
    val grantId: UUID,
    val newStatus: AuthorizationGrant.Status,
    val authorizedParty: AuthorizationParty
)
