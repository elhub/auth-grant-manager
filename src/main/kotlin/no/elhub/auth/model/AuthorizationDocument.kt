package no.elhub.auth.model

import no.elhub.auth.features.documents.jsonApiSpec.PostAuthorizationDocument
import java.time.LocalDateTime
import java.util.*

data class AuthorizationDocument(
    val id: UUID,
    val title: String,
    val type: DocumentType,
    val status: AuthorizationDocumentStatus,
    val pdfBytes: ByteArray,
    val requestedBy: String,
    val requestedTo: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {

    companion object {
        fun of(postAuthorizationDocumentRequest: PostAuthorizationDocument.Request, pdf: ByteArray): AuthorizationDocument = AuthorizationDocument(
            id = UUID.randomUUID(),
            title = "Title",
            pdfBytes = pdf,
            type = DocumentType.ChangeOfSupplierConfirmation,
            status = AuthorizationDocumentStatus.Pending,
            requestedBy = postAuthorizationDocumentRequest.data.attributes.requestedBy,
            requestedTo = postAuthorizationDocumentRequest.data.attributes.requestedTo,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    enum class AuthorizationDocumentStatus {
        Expired,
        Pending,
        Rejected,
        Signed
    }

    enum class DocumentType {
        ChangeOfSupplierConfirmation
    }
}
