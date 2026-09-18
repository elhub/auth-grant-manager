package no.elhub.auth.v0.features.requests.update

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.requests.AuthorizationRequest
import java.util.UUID

class UpdateCommand(
    val requestId: UUID,
    val newStatus: AuthorizationRequest.Status,
    val authorizedParty: AuthorizationParty
)
