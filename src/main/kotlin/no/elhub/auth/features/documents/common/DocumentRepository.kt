package no.elhub.auth.features.documents.common

import java.util.UUID
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationResourceType
import no.elhub.auth.features.grants.PermissionType
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime


interface DocumentRepository {
    operator fun get(id: UUID): ByteArray?
    fun insert(doc: AuthorizationDocument)
}

class ExposedDocumentRepository : DocumentRepository {

    override fun insert(doc: AuthorizationDocument) {
        val documentId = AuthorizationDocumentTable.insertAndGetId {
            it[id] = doc.id
            it[title] = doc.title
            it[type] = doc.type
            it[status] = doc.status
            it[file] = doc.pdfBytes
            it[requestedBy] = doc.requestedBy
            it[requestedTo] = doc.requestedTo
            it[createdAt] = doc.createdAt
            it[updatedAt] = doc.updatedAt
        }

        val scopeId = AuthorizationScopeTable.insertAndGetId {
            it[authorizedResourceType] = AuthorizationResourceType.MeteringPoint
            it[authorizedResourceId] = "Something"
            it[permissionType] = PermissionType.ChangeOfSupplier
        }

        AuthorizationDocumentScopeTable.insert {
            it[authorizationDocumentId] = documentId.value
            it[authorizationScopeId] = scopeId.value
        }
    }

    override fun get(id: UUID): ByteArray? = transaction {
        AuthorizationDocumentTable.select(listOf(AuthorizationDocumentTable.file))
            .where { AuthorizationDocumentTable.id eq id }
            .map { it[AuthorizationDocumentTable.file] }.singleOrNull()
    }
}

object AuthorizationDocumentTable : UUIDTable("auth.authorization_document") {
    val title = varchar("title", 255)
    val type = customEnumeration(
        name = "type",
        sql = "document_type",
        fromDb = { AuthorizationDocument.Type.valueOf(it as String) },
        toDb = { PGEnum("document_type", it) },
    )
    val file = binary("file")
    val status = customEnumeration(
        name = "status",
        sql = "authorization_document_status",
        fromDb = { AuthorizationDocument.Status.valueOf(it as String) },
        toDb = { PGEnum("authorization_document_status", it) },
    )
    val requestedBy = varchar("requested_by", 16)
    val requestedTo = varchar("requested_to", 16)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

object AuthorizationDocumentScopeTable : Table("auth.authorization_document_scope") {
    val authorizationDocumentId = uuid("authorization_document_id")
        .references(AuthorizationDocumentTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = long("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    override val primaryKey = PrimaryKey(authorizationDocumentId, authorizationScopeId)
}


