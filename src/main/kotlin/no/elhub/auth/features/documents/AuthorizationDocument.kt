package no.elhub.auth.features.documents

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.documents.common.AuthorizationDocumentProperty
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
    val properties: List<AuthorizationDocumentProperty>,
    val validTo: OffsetDateTime,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null
) {
    companion object {
        fun create(
            type: Type,
            file: ByteArray,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
            requestedTo: AuthorizationParty,
            validTo: OffsetDateTime,
            properties: List<AuthorizationDocumentProperty>,
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
        )
    }
    enum class Status {
        Expired,
        Pending,
        Rejected,
        Signed
    }

    enum class Type {
        ChangeOfEnergySupplierForPerson,
        MoveInAndChangeOfEnergySupplierForPerson
    }
}
