package no.elhub.auth.grantmanager.application.grants.createGrant

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class Command(
    val grantedFor: String,
    val grantedBy: String,
    val grantedTo: String,
    val validFrom: Instant?,
    val validTo: Instant
)
