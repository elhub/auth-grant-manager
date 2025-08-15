package no.elhub.auth.features.requests

import kotlinx.datetime.LocalDateTime
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty

data class AuthorizationRequest(
    val id: String,
    val requestType: Type,
    val status: Status,
    val requestedBy: String,
    val requestedFrom: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val validTo: LocalDateTime,
    val properties: ArrayList<AuthorizationRequestProperty> = ArrayList()
) {
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
