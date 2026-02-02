package no.elhub.auth.features.documents.common

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.util.UUID

interface DocumentPropertiesRepository {
    fun insert(
        properties: List<AuthorizationDocumentProperty>,
        documentId: UUID,
    )

    fun find(documentId: UUID): List<AuthorizationDocumentProperty>
}

class ExposedDocumentPropertiesRepository : DocumentPropertiesRepository {
    override fun insert(
        properties: List<AuthorizationDocumentProperty>,
        documentId: UUID,
    ) {
        if (properties.isEmpty()) return
        AuthorizationDocumentPropertyTable.batchInsert(properties) { property ->
            this[AuthorizationDocumentPropertyTable.documentId] = documentId
            this[AuthorizationDocumentPropertyTable.key] = property.key
            this[AuthorizationDocumentPropertyTable.value] = property.value
        }
    }

    override fun find(documentId: UUID): List<AuthorizationDocumentProperty> =
        AuthorizationDocumentPropertyTable
            .selectAll()
            .where { AuthorizationDocumentPropertyTable.documentId eq documentId }
            .map { it.toAuthorizationDocumentProperty() }
}

object AuthorizationDocumentPropertyTable : Table("auth.authorization_document_property") {
    val documentId =
        uuid("authorization_document_id")
            .references(AuthorizationDocumentTable.id, onDelete = ReferenceOption.CASCADE)
    val key = varchar("key", length = 64)
    val value = text("value")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(documentId, key)
}

private fun ResultRow.toAuthorizationDocumentProperty() =
    AuthorizationDocumentProperty(
        key = this[AuthorizationDocumentPropertyTable.key],
        value = this[AuthorizationDocumentPropertyTable.value],
    )
