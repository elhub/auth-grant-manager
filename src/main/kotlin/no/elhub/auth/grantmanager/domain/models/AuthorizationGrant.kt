package no.elhub.auth.grantmanager.domain.models

import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class AuthorizationGrant(
    val id: UUID,
    val grantedFor: String,
    val grantedBy: String,
    val grantedTo: String,
    val grantedAt: Instant,
    val validFrom: Instant,
    val validTo: Instant,
)
