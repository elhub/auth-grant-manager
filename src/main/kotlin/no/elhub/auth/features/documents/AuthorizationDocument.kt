package no.elhub.auth.features.documents

import no.elhub.auth.features.common.AuthorizationParty
import java.time.LocalDateTime
import java.util.UUID

data class AuthorizationDocument(
    val id: UUID,
    val title: String,
    val type: Type,
    val status: Status,
    val file: ByteArray,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val signedBy: AuthorizationParty,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun create(
            type: Type,
            file: ByteArray,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
            requestedTo: AuthorizationParty,
            signedBy: AuthorizationParty
        ): AuthorizationDocument = AuthorizationDocument(
            id = UUID.randomUUID(),
            title = type.name,
            type = type,
            status = Status.Pending,
            file = file,
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            requestedTo = requestedTo,
            signedBy = signedBy,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
    enum class Status {
        Expired,
        Pending,
        Rejected,
        Signed
    }

    enum class Type {
        ChangeOfSupplierConfirmation
    }
}
