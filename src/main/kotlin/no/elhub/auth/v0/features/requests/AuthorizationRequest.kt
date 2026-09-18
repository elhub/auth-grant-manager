package no.elhub.auth.v0.features.requests

import no.elhub.auth.v0.features.common.currentTimeUtc
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.requests.common.AuthorizationRequestProperty
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
                createdAt = currentTimeUtc(),
                updatedAt = currentTimeUtc(),
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
        ChangeOfBalanceSupplierForPerson,
        MoveInAndChangeOfBalanceSupplierForPerson
    }
}
