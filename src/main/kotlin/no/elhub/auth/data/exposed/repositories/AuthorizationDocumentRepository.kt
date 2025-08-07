package no.elhub.auth.data.exposed.repositories

import java.util.*
import no.elhub.auth.data.exposed.tables.AuthorizationDocumentScopeTable
import no.elhub.auth.data.exposed.tables.AuthorizationDocumentTable
import no.elhub.auth.data.exposed.tables.AuthorizationScopeTable
import no.elhub.auth.domain.document.AuthorizationDocument
import no.elhub.auth.domain.shared.AuthorizationResourceType
import no.elhub.auth.domain.shared.PermissionType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction

object AuthorizationDocumentRepository {
    fun insertDocument(doc: AuthorizationDocument) {
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

    fun getDocumentFile(id: UUID): ByteArray? = transaction {
        AuthorizationDocumentTable.select(listOf(AuthorizationDocumentTable.file))
            .where { AuthorizationDocumentTable.id eq id }
            .map { it[AuthorizationDocumentTable.file] }.singleOrNull()
    }
}
