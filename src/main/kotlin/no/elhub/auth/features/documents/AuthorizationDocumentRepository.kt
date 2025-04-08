package no.elhub.auth.features.documents

import no.elhub.auth.features.documents.domain.AuthorizationResourceType
import no.elhub.auth.features.documents.domain.PermissionType
import no.elhub.auth.features.documents.tables.AuthorizationDocumentScopes
import no.elhub.auth.model.AuthorizationDocument
import no.elhub.auth.services.common.tables.AuthorizationScopes
import no.elhub.auth.services.documents.tables.AuthorizationDocuments
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object AuthorizationDocumentRepository {
    fun insertDocument(doc: AuthorizationDocument) {
        transaction {
            val documentId = AuthorizationDocuments.insertAndGetId {
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

            val scopeId = AuthorizationScopes.insertAndGetId {
                it[authorizedResourceType] = AuthorizationResourceType.MeteringPoint
                it[authorizedResourceId] = "Something"
                it[permissionType] = PermissionType.ChangeOfSupplier
            }

            AuthorizationDocumentScopes.insert {
                it[authorizationDocumentId] = documentId.value
                it[authorizationScopeId] = scopeId.value
            }
        }
    }

    fun getDocumentFile(id: UUID): ByteArray? = transaction {
        AuthorizationDocuments.select(listOf(AuthorizationDocuments.file)).where { AuthorizationDocuments.id eq id }
            .map { it[AuthorizationDocuments.file] }.singleOrNull()
    }
}
