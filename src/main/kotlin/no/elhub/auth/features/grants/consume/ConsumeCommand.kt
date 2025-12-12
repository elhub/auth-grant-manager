package no.elhub.auth.features.grants.consume

import no.elhub.auth.features.grants.AuthorizationGrant
import java.util.*

data class ConsumeCommand(
    val grantId: UUID,
    val newStatus: AuthorizationGrant.Status
)
