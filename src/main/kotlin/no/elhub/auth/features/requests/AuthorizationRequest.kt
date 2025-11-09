package no.elhub.auth.features.requests

import kotlinx.datetime.LocalDateTime
import no.elhub.auth.features.common.AuthorizationParty

data class AuthorizationRequest(
    val id: String,
    val requestType: Type,
    val status: Status,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
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
