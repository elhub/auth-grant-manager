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
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
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
