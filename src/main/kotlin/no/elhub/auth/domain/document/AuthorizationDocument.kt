package no.elhub.auth.domain.document

import java.time.LocalDateTime
import java.util.*

typealias PdfBytes = ByteArray

data class AuthorizationDocument(
    val id: UUID,
    val title: String,
    val type: DocumentType,
    val status: AuthorizationDocumentStatus,
    val pdfBytes: PdfBytes,
    val requestedBy: String,
    val requestedTo: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {

    companion object {
        fun of(createDocumentCommand: CreateAuthorizationDocumentCommand, pdf: PdfBytes): AuthorizationDocument =
            AuthorizationDocument(
                id = UUID.randomUUID(),
                title = "Title",
                pdfBytes = pdf,
                type = DocumentType.ChangeOfSupplierConfirmation,
                status = AuthorizationDocumentStatus.Pending,
                requestedBy = createDocumentCommand.requestedBy,
                requestedTo = createDocumentCommand.requestedTo,
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

data class CreateAuthorizationDocumentCommand(
    val type: AuthorizationDocument.DocumentType,
    val requestedBy: String,
    val requestedTo: String,
    val meteringPoint: String
)
