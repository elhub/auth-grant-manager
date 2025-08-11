package no.elhub.auth.grantmanager.data.models

import no.elhub.auth.grantmanager.presentation.features.documents.PostAuthorizationDocumentRequest
import no.elhub.auth.grantmanager.presentation.utils.PGEnum
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.util.UUID

data class AuthorizationDocumentDbEntity(
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
        fun of(postAuthorizationDocumentRequest: PostAuthorizationDocumentRequest, pdf: ByteArray): AuthorizationDocumentDbEntity = AuthorizationDocumentDbEntity(
            id = UUID.randomUUID(),
            title = "Title",
            pdfBytes = pdf,
            type = DocumentType.ChangeOfSupplierConfirmation,
            status = AuthorizationDocumentStatus.Pending,
            requestedBy = postAuthorizationDocumentRequest.data.relationships.requestedBy.data.id,
            requestedTo = postAuthorizationDocumentRequest.data.relationships.requestedTo.data.id,
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

    object AuthorizationDocuments : UUIDTable("auth.authorization_document") {
        val title = varchar("title", 255)
        val type = customEnumeration(
            name = "type",
            sql = "document_type",
            fromDb = { DocumentType.valueOf(it as String) },
            toDb = { PGEnum("document_type", it) },
        )
        val file = binary("file")
        val status = customEnumeration(
            name = "status",
            sql = "authorization_document_status",
            fromDb = { AuthorizationDocumentStatus.valueOf(it as String) },
            toDb = { PGEnum("authorization_document_status", it) },
        )
        val requestedBy = varchar("requested_by", 16)
        val requestedTo = varchar("requested_to", 16)
        val createdAt = datetime("created_at")
        val updatedAt = datetime("updated_at")
    }
}
