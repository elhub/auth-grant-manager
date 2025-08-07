package no.elhub.auth.data.exposed.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object AuthorizationDocumentScopeTable : Table("auth.authorization_document_scope") {
    val authorizationDocumentId = uuid("authorization_document_id")
        .references(AuthorizationDocumentTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = long("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    override val primaryKey = PrimaryKey(authorizationDocumentId, authorizationScopeId)
}
