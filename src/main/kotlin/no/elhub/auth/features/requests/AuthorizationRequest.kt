package no.elhub.auth.features.requests

import no.elhub.auth.features.common.AuthorizationParty
import java.time.LocalDateTime
import java.util.UUID

data class AuthorizationRequest(
    val id: UUID,
    val type: Type,
    val status: Status,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val validTo: LocalDateTime,
) {
    companion object {
        fun create(
            type: Type,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
        ): AuthorizationRequest = AuthorizationRequest(
            id = UUID.randomUUID(),
            type = type,
            status = Status.Pending,
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            validTo = LocalDateTime.now().plusDays(30) // TODO not clear about this field - why 30 days ?
        )
    }

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
