package no.elhub.auth.features.documents

import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import java.time.OffsetDateTime
import java.util.UUID

data class AuthorizationDocument(
    val id: UUID,
    val type: Type,
    val status: Status,
    val file: ByteArray,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val signedBy: AuthorizationParty? = null,
    val grantId: UUID? = null,
    val properties: Map<String, String>,
    val validTo: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
) {
    companion object {
        fun create(
            type: Type,
            file: ByteArray,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
            requestedTo: AuthorizationParty,
            validTo: OffsetDateTime,
            properties: Map<String, String>,
        ): AuthorizationDocument = AuthorizationDocument(
            id = UUID.randomUUID(),
            type = type,
            status = Status.Pending,
            file = file,
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            requestedTo = requestedTo,
            properties = properties,
            validTo = validTo,
            createdAt = currentTimeUtc(),
            updatedAt = currentTimeUtc()
        )
    }

    enum class Status {
        Expired,
        Pending,
        Rejected,
        Signed,
    }

    enum class Type {
        ChangeOfBalanceSupplierForPerson,
        MoveInAndChangeOfBalanceSupplierForPerson
    }
}
