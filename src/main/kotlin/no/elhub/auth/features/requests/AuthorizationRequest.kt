package no.elhub.auth.features.requests

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.party.AuthorizationParty
import java.time.LocalDateTime
import java.util.UUID

data class AuthorizationRequest(
    val id: UUID,
    val type: Type,
    val status: Status,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val approvedBy: AuthorizationParty? = null,
    val grantId: UUID? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val validTo: LocalDate,
    val properties: Map<String, String> = emptyMap(),
) {
    companion object {
        fun create(
            type: Type,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
            requestedTo: AuthorizationParty,
            validTo: LocalDate,
            properties: Map<String, String> = emptyMap(),
        ): AuthorizationRequest =
            AuthorizationRequest(
                id = UUID.randomUUID(),
                type = type,
                status = Status.Pending,
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                validTo = validTo,
                properties = properties,
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
}
