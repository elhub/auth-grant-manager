package no.elhub.auth.features.requests.confirm

import no.elhub.auth.features.requests.AuthorizationRequest
import java.util.UUID

data class ConfirmCommand(
    val requestId: UUID,
    val newStatus: AuthorizationRequest.Status,
)
