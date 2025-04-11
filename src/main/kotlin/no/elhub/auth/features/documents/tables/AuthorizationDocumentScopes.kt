package no.elhub.auth.features.documents.tables

import no.elhub.auth.services.common.tables.AuthorizationScopes
import no.elhub.auth.services.documents.tables.AuthorizationDocuments
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object AuthorizationDocumentScopes : Table("auth.authorization_document_scope") {
    val authorizationDocumentId = uuid("authorization_document_id")
        .references(AuthorizationDocuments.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = long("authorization_scope_id")
        .references(AuthorizationScopes.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    override val primaryKey = PrimaryKey(authorizationDocumentId, authorizationScopeId)
}
