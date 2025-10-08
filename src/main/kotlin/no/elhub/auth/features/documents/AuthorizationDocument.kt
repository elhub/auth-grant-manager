package no.elhub.auth.features.documents

import java.time.LocalDateTime
import java.util.*

data class AuthorizationDocument(
    val id: UUID,
    val title: String,
    val type: Type,
    val status: Status,
    val file: ByteArray,
    val requestedBy: String,
    val requestedFrom: UUID,
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
