package no.elhub.auth.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

data class AuthorizationRequest(
    val id: String,
    val status: String,
    val requestedBy: String,
    val requestedTo: String,
    val grantedFor: String,
    val grantedBy: String,
    val createdAt: LocalDateTime,
    val validTo: LocalDateTime,
) {
    @Serializable
    data class Response(
        val meta: MetaResponse
    )
}
