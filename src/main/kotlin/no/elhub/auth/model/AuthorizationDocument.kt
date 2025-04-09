package no.elhub.auth.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

data class AuthorizationDocument(
    val id: String,
    val title: String,
    val type: String,
    val status: String,
    // file: Blob
    val requestedBy: String,
    val requestedTo: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    @Serializable
    data class Response(
        val meta: ResponseMeta
    )
}
