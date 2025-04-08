package no.elhub.auth.services.documents.tables

import no.elhub.auth.model.AuthorizationDocument
import no.elhub.auth.services.common.tables.PGEnum
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

object AuthorizationDocuments : UUIDTable("auth.authorization_document") {
    val title = varchar("title", 255)
    val type = customEnumeration(
        name = "type",
        sql = "document_type",
        fromDb = { AuthorizationDocument.DocumentType.valueOf(it as String) },
        toDb = { PGEnum("document_type", it) },
    )
    val file = binary("file")
    val status = customEnumeration(
        name = "status",
        sql = "authorization_document_status",
        fromDb = { AuthorizationDocument.AuthorizationDocumentStatus.valueOf(it as String) },
        toDb = { PGEnum("authorization_document_status", it) },
    )
    val requestedBy = varchar("requested_by", 16)
    val requestedTo = varchar("requested_to", 16)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
