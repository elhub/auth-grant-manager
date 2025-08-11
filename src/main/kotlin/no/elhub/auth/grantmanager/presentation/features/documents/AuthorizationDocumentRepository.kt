package no.elhub.auth.grantmanager.presentation.features.documents

import no.elhub.auth.grantmanager.data.models.AuthorizationDocumentDbEntity
import no.elhub.auth.grantmanager.presentation.model.AuthorizationDocumentScopes
import no.elhub.auth.grantmanager.presentation.model.AuthorizationResourceType
import no.elhub.auth.grantmanager.presentation.model.AuthorizationScopes
import no.elhub.auth.grantmanager.presentation.model.PermissionType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object ExposedAuthorizationDocumentRepository : AuthorizationDocumentRepository {
    fun insertDocument(doc: AuthorizationDocumentDbEntity) {
        transaction {
            val documentId = AuthorizationDocumentDbEntity.AuthorizationDocuments.insertAndGetId {
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
                it[authorizationDocumentDbEntityId] = documentId.value
                it[authorizationScopeId] = scopeId.value
            }
        }
    }

    fun getDocumentFile(id: UUID): ByteArray? = transaction {
        AuthorizationDocumentDbEntity.AuthorizationDocuments.select(listOf(AuthorizationDocumentDbEntity.AuthorizationDocuments.file)).where {
            AuthorizationDocumentDbEntity.AuthorizationDocuments.id eq id
        }
            .map { it[AuthorizationDocumentDbEntity.AuthorizationDocuments.file] }.singleOrNull()
    }
}
