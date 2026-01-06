package no.elhub.auth.features.requests.update

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.requests.AuthorizationRequest
import java.util.UUID

class UpdateCommand(
    val requestId: UUID,
    val newStatus: AuthorizationRequest.Status,
    val authorizedParty: AuthorizationParty
)
