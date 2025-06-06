package no.elhub.auth.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ResponseMeta(
    val createdAt: Instant = Clock.System.now(),
)
