package no.elhub.auth.features.requests

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
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
    val validTo: OffsetDateTime,
    val properties: List<AuthorizationRequestProperty>,
) {
    companion object {
        fun create(
            type: Type,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
            requestedTo: AuthorizationParty,
            validTo: OffsetDateTime,
        ): AuthorizationRequest =
            AuthorizationRequest(
                id = UUID.randomUUID(),
                type = type,
                status = Status.Pending,
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
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
        ChangeOfEnergySupplierForPerson,
        MoveInAndChangeOfEnergySupplierForPerson
    }
}
