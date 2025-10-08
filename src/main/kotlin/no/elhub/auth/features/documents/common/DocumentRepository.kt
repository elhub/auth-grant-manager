package no.elhub.auth.features.documents.common

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.grants.AuthorizationResourceType
import no.elhub.auth.features.grants.PermissionType
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

interface DocumentRepository {
    fun find(id: UUID): Either<RepositoryReadError, AuthorizationDocument>
    fun insert(doc: AuthorizationDocument): Either<RepositoryWriteError, AuthorizationDocument>
    fun findAll(): Either<RepositoryReadError, List<AuthorizationDocument>>
}

class ExposedDocumentRepository : DocumentRepository {

    override fun insert(doc: AuthorizationDocument): Either<RepositoryWriteError, AuthorizationDocument> =
        Either.catch {
            transaction {
                val document = AuthorizationDocumentTable.insertReturning {
                    it[id] = doc.id
                    it[type] = doc.type
                    it[status] = doc.status
                    it[file] = doc.file
                    it[requestedBy] = doc.requestedBy
                    it[requestedFrom] = doc.requestedFrom
                    it[createdAt] = doc.createdAt
                    it[updatedAt] = doc.updatedAt
                }.map { it.toAuthorizationDocument() }
                    .single()

                val scopeId = AuthorizationScopeTable.insertAndGetId {
                    it[authorizedResourceType] = AuthorizationResourceType.MeteringPoint
                    it[authorizedResourceId] = "Something"
                    it[permissionType] = PermissionType.ChangeOfSupplier
                }

                AuthorizationDocumentScopeTable.insert {
                    it[authorizationDocumentId] = document.id
                    it[authorizationScopeId] = scopeId.value
                }

                document
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun find(id: UUID): Either<RepositoryReadError, AuthorizationDocument> = findOrNull(id).fold(
        { error -> error.left() },
        { authorizationDocument ->
            authorizationDocument?.right() ?: RepositoryReadError.NotFoundError.left()
        }
    )

    private fun findOrNull(id: UUID): Either<RepositoryReadError, AuthorizationDocument?> =
        Either.catch {
            transaction {
                AuthorizationDocumentTable
                    .selectAll()
                    .where { AuthorizationDocumentTable.id eq id }
                    .map { it.toAuthorizationDocument() }
                    .singleOrNull()
            }
        }.mapLeft { RepositoryReadError.UnexpectedError }

    override fun findAll() = TODO()
}

object AuthorizationDocumentTable : UUIDTable("auth.authorization_document") {
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
    val requestedFrom = varchar("requested_from", 16)
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

fun ResultRow.toAuthorizationDocument() = AuthorizationDocument(
    id = this[AuthorizationDocumentTable.id].value,
    type = this[AuthorizationDocumentTable.type],
    status = this[AuthorizationDocumentTable.status],
    file = this[AuthorizationDocumentTable.file],
    requestedBy = this[AuthorizationDocumentTable.requestedBy],
    requestedFrom = this[AuthorizationDocumentTable.requestedFrom],
    createdAt = this[AuthorizationDocumentTable.createdAt],
    updatedAt = this[AuthorizationDocumentTable.updatedAt],
)
