package no.elhub.auth.features.documents.common

import no.elhub.auth.config.TransactionContext
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

interface DocumentPropertiesRepository {
    suspend fun insert(properties: List<AuthorizationDocumentProperty>, documentId: UUID)
    suspend fun find(documentId: UUID): List<AuthorizationDocumentProperty>
}

class ExposedDocumentPropertiesRepository(private val transactionContext: TransactionContext) : DocumentPropertiesRepository {
    override suspend fun insert(properties: List<AuthorizationDocumentProperty>, documentId: UUID) {
        if (properties.isEmpty()) return
        transactionContext("document_prop_repo_insert", { RepositoryWriteError.UnexpectedError }) {
            AuthorizationDocumentPropertyTable.batchInsert(properties) { property ->
                this[AuthorizationDocumentPropertyTable.documentId] = documentId
                this[AuthorizationDocumentPropertyTable.key] = property.key
                this[AuthorizationDocumentPropertyTable.value] = property.value
            }
        }
    }

    override suspend fun find(documentId: UUID): List<AuthorizationDocumentProperty> =
        transactionContext("document_prop_repo_find", { RepositoryReadError.UnexpectedError }) {
            AuthorizationDocumentPropertyTable
                .selectAll()
                .where { AuthorizationDocumentPropertyTable.documentId eq documentId }
                .map { it.toAuthorizationDocumentProperty() }
        }.fold({ emptyList() }, { it })
}

object AuthorizationDocumentPropertyTable : Table("auth.authorization_document_property") {
    val documentId = javaUUID("authorization_document_id")
        .references(AuthorizationDocumentTable.id, onDelete = ReferenceOption.CASCADE)
    val key = varchar("key", length = 64)
    val value = text("value")
    override val primaryKey = PrimaryKey(documentId, key)
}

private fun ResultRow.toAuthorizationDocumentProperty() = AuthorizationDocumentProperty(
    key = this[AuthorizationDocumentPropertyTable.key],
    value = this[AuthorizationDocumentPropertyTable.value]
)
