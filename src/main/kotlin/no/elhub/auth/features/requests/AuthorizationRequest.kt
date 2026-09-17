package no.elhub.auth.features.requests

import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
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
    val properties: Map<String, String>,
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
                properties = emptyMap(),
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
