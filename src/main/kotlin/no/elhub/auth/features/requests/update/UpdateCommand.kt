package no.elhub.auth.features.requests.update

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.requests.AuthorizationRequest
import java.util.UUID

class UpdateCommand(
    val requestId: UUID,
    val newStatus: AuthorizationRequest.Status,
)

sealed class UpdateRequestCommand {
    data class Accept(
        val requestId: UUID,
        val approvedBy: AuthorizationParty
    ) : UpdateRequestCommand()

    data class Reject(
        val requestId: UUID
    ) : UpdateRequestCommand()
}
