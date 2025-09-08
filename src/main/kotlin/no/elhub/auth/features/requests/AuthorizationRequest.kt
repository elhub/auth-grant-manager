package no.elhub.auth.features.requests

import kotlinx.datetime.LocalDateTime

data class AuthorizationRequest(
    val id: String,
    val requestType: Type,
    val status: Status,
    val requestedBy: String,
    val requestedFrom: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val validTo: LocalDateTime,
    val properties: List<Property> = ArrayList()
) {
    enum class Status {
        Accepted,
        Expired,
        Pending,
        Rejected,
    }

    enum class Type {
        ChangeOfSupplierConfirmation,
    }

    data class Property(
        val authorizationRequestId: String,
        val key: String,
        val value: String,
        val createdAt: LocalDateTime
    )
}
