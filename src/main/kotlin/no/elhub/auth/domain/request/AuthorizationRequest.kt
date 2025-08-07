package no.elhub.auth.domain.request

import kotlinx.datetime.LocalDateTime

data class AuthorizationRequest(
    val id: String,
    val requestType: RequestType,
    val status: RequestStatus,
    val requestedBy: String,
    val requestedFrom: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val validTo: LocalDateTime,
    val properties: ArrayList<AuthorizationRequestProperty> = ArrayList()
)
