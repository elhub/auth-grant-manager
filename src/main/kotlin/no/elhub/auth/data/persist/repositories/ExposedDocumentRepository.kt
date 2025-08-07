package no.elhub.auth.data.persist.repositories

import no.elhub.auth.data.persist.tables.AuthorizationDocumentScopeTable
import no.elhub.auth.data.persist.tables.AuthorizationDocumentTable
import no.elhub.auth.data.persist.tables.AuthorizationScopeTable
import no.elhub.auth.domain.document.AuthorizationDocument
import no.elhub.auth.domain.document.DocumentRepository
import no.elhub.auth.domain.shared.AuthorizationResourceType
import no.elhub.auth.domain.shared.PermissionType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class ExposedDocumentRepository : DocumentRepository {
    override fun insertDocument(doc: AuthorizationDocument) {
        transaction {
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
    }

    override fun getDocumentFile(id: UUID): ByteArray? = transaction {
        AuthorizationDocumentTable.select(listOf(AuthorizationDocumentTable.file))
            .where { AuthorizationDocumentTable.id eq id }
            .map { it[AuthorizationDocumentTable.file] }.singleOrNull()
    }
}
