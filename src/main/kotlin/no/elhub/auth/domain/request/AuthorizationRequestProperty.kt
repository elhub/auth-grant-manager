package no.elhub.auth.domain.request

import kotlinx.datetime.LocalDateTime

data class AuthorizationRequestProperty(
    val authorizationRequestId: String,
    val key: String,
    val value: String,
    val createdAt: LocalDateTime
)
