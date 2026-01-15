package no.elhub.auth.features.requests

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import java.time.OffsetDateTime
import java.util.UUID

data class AuthorizationRequest(
    val id: UUID,
    val type: Type,
    val status: Status,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val approvedBy: AuthorizationParty? = null,
    var grantId: UUID? = null,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val validTo: LocalDate,
    val properties: List<AuthorizationRequestProperty>,
) {
    companion object {
        fun create(
            type: Type,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
            requestedTo: AuthorizationParty,
            validTo: LocalDate,
        ): AuthorizationRequest =
            AuthorizationRequest(
                id = UUID.randomUUID(),
                type = type,
                status = Status.Pending,
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
                createdAt = currentTimeWithTimeZone(),
                updatedAt = currentTimeWithTimeZone(),
                validTo = validTo,
                properties = emptyList(),
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
        MoveIn
    }
}
