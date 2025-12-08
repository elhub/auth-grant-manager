package no.elhub.auth.features.documents.common

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.datetime.Instant
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.AuthorizationPartyRecord
import no.elhub.auth.features.common.AuthorizationPartyTable
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.PartyRepository
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.scope.AuthorizationScope
import no.elhub.auth.features.common.scope.AuthorizationScopeTable
import no.elhub.auth.features.common.scope.ElhubResource
import no.elhub.auth.features.common.scope.PermissionType
import no.elhub.auth.features.common.toAuthorizationParty
import no.elhub.auth.features.documents.AuthorizationDocument
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
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.*

interface DocumentRepository {
    fun find(id: UUID): Either<RepositoryReadError, AuthorizationDocument>
    fun insert(doc: AuthorizationDocument): Either<RepositoryWriteError, AuthorizationDocument>
    fun findAll(requestedBy: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationDocument>>
    fun confirm(
        documentId: UUID,
        signedFile: ByteArray,
        requestedFrom: AuthorizationParty,
        signatory: AuthorizationParty
    ): Either<RepositoryWriteError, AuthorizationDocument>

    fun findScopes(documentId: UUID): Either<RepositoryReadError, List<AuthorizationScope>>
}

class ExposedDocumentRepository(
    private val partyRepo: PartyRepository
) : DocumentRepository {

    override fun insert(doc: AuthorizationDocument): Either<RepositoryWriteError, AuthorizationDocument> =
        either {
            transaction {
                val requestedByParty = partyRepo.findOrInsert(doc.requestedBy.type, doc.requestedBy.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

                val requestedFromParty = partyRepo.findOrInsert(doc.requestedFrom.type, doc.requestedFrom.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

                val requestedToParty = partyRepo.findOrInsert(doc.requestedTo.type, doc.requestedTo.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

                val document = AuthorizationDocumentTable.insertReturning {
                    it[id] = doc.id
                    it[title] = doc.title
                    it[type] = doc.type
                    it[status] = doc.status
                    it[file] = doc.file
                    it[requestedBy] = requestedByParty.id
                    it[requestedFrom] = requestedFromParty.id
                    it[requestedTo] = requestedToParty.id
                    it[createdAt] = doc.createdAt
                    it[updatedAt] = doc.updatedAt
                }.map { it.toAuthorizationDocument(requestedByParty, requestedFromParty, requestedToParty) }
                    .single()

                val scopeId = AuthorizationScopeTable.insertAndGetId {
                    it[authorizedResourceType] = ElhubResource.MeteringPoint
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
        either {
            transaction {
                val documentRow = AuthorizationDocumentTable
                    .selectAll()
                    .where { AuthorizationDocumentTable.id eq id }
                    .map { it }
                    .singleOrNull() ?: return@transaction null

                val requestedByDbId = documentRow[AuthorizationDocumentTable.requestedBy]
                val requestedByParty = resolveParty(requestedByDbId).bind()

                val requestedFromDbId = documentRow[AuthorizationDocumentTable.requestedFrom]
                val requestedFromParty = resolveParty(requestedFromDbId).bind()

                val requestedToDbId = documentRow[AuthorizationDocumentTable.requestedTo]
                val requestedToParty = resolveParty(requestedToDbId).bind()

                documentRow.toAuthorizationDocument(requestedByParty, requestedFromParty, requestedToParty)
            }
        }

    override fun confirm(
        documentId: UUID,
        signedFile: ByteArray,
        requestedFrom: AuthorizationParty,
        signatory: AuthorizationParty
    ): Either<RepositoryWriteError, AuthorizationDocument> =
        either {
            val updatedCount =
                Either
                    .catch {
                        transaction {
                            val signatoryRecord = partyRepo.findOrInsert(signatory.type, signatory.resourceId).bind()
                            val requestedFromRecord = partyRepo.findOrInsert(requestedFrom.type, requestedFrom.resourceId).bind()

                            AuthorizationDocumentSignatoriesTable.insert {
                                it[authorizationDocumentId] = documentId
                                it[this.requestedFrom] = requestedFromRecord.id
                                it[signedBy] = signatoryRecord.id
                            }

                            AuthorizationDocumentTable.update(
                                where = { AuthorizationDocumentTable.id eq documentId }
                            ) {
                                it[status] = AuthorizationDocument.Status.Signed
                                it[file] = signedFile
                                it[updatedAt] = LocalDateTime.now()
                            }
                        }
                    }.mapLeft {
                        RepositoryWriteError.UnexpectedError
                    }.bind()

            if (updatedCount == 0) {
                raise(RepositoryWriteError.NotFoundError)
            }
            find(documentId)
                .mapLeft { readError ->
                    when (readError) {
                        RepositoryReadError.NotFoundError -> RepositoryWriteError.NotFoundError
                        RepositoryReadError.UnexpectedError -> RepositoryWriteError.UnexpectedError
                    }
                }.bind()
        }

    override fun findScopes(documentId: UUID): Either<RepositoryReadError, List<AuthorizationScope>> =
        Either.catch {
            transaction {
                (AuthorizationDocumentScopeTable innerJoin AuthorizationScopeTable)
                    .selectAll()
                    .where { AuthorizationDocumentScopeTable.authorizationDocumentId eq documentId }
                    .map { row ->
                        AuthorizationScope(
                            id = row[AuthorizationScopeTable.id].value,
                            authorizedResourceType = row[AuthorizationScopeTable.authorizedResourceType],
                            authorizedResourceId = row[AuthorizationScopeTable.authorizedResourceId],
                            permissionType = row[AuthorizationScopeTable.permissionType],
                            createdAt = row[AuthorizationScopeTable.createdAt].toString()
                        )
                    }
            }
        }.mapLeft { RepositoryReadError.UnexpectedError }

    override fun findAll(requestedBy: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationDocument>> = either {
        transaction {
            val partyRecord = partyRepo.findOrInsert(type = requestedBy.type, resourceId = requestedBy.resourceId)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()

            val documentsRecords = AuthorizationDocumentTable
                .selectAll()
                .where { AuthorizationDocumentTable.requestedBy eq partyRecord.id }
                .toList()

            if (documentsRecords.isEmpty()) {
                return@transaction emptyList()
            }

            val partyIds = documentsRecords.flatMap { row ->
                listOf(
                    row[AuthorizationDocumentTable.requestedBy],
                    row[AuthorizationDocumentTable.requestedFrom],
                    row[AuthorizationDocumentTable.requestedTo],
                )
            }.toSet().toList()

            val partiesById = AuthorizationPartyTable
                .selectAll()
                .where { AuthorizationPartyTable.id inList partyIds }
                .associate { row ->
                    val party = row.toAuthorizationParty()
                    party.id to party
                }

            documentsRecords.map { row ->
                val requestedByParty =
                    partiesById[row[AuthorizationDocumentTable.requestedBy]] ?: raise(RepositoryReadError.UnexpectedError)
                val requestedFromParty =
                    partiesById[row[AuthorizationDocumentTable.requestedFrom]] ?: raise(RepositoryReadError.UnexpectedError)
                val requestedToParty =
                    partiesById[row[AuthorizationDocumentTable.requestedTo]] ?: raise(RepositoryReadError.UnexpectedError)

                row.toAuthorizationDocument(requestedByParty, requestedFromParty, requestedToParty)
            }
        }
    }

    private fun resolveParty(
        partyId: UUID
    ): Either<RepositoryReadError.UnexpectedError, AuthorizationPartyRecord> =
        partyRepo.find(partyId)
            .mapLeft { RepositoryReadError.UnexpectedError }
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
    val requestedBy = uuid("requested_by").references(AuthorizationPartyTable.id)
    val requestedFrom = uuid("requested_from").references(AuthorizationPartyTable.id)
    val requestedTo = uuid("requested_to").references(AuthorizationPartyTable.id)
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

object AuthorizationDocumentSignatoriesTable : Table("auth.authorization_document_signatories") {
    val authorizationDocumentId = uuid("authorization_document_id")
        .references(AuthorizationDocumentTable.id, onDelete = ReferenceOption.CASCADE)
    val requestedFrom = uuid("requested_from")
        .references(AuthorizationPartyTable.id)
    val signedBy = uuid("signed_by")
        .references(AuthorizationPartyTable.id)
    val signedAt = timestamp("signed_at").clientDefault { java.time.Instant.now() }

    override val primaryKey = PrimaryKey(authorizationDocumentId, requestedFrom)
}

fun ResultRow.toAuthorizationDocument(
    requestedBy: AuthorizationPartyRecord,
    requestedFrom: AuthorizationPartyRecord,
    requestedTo: AuthorizationPartyRecord,
) = AuthorizationDocument(
    id = this[AuthorizationDocumentTable.id].value,
    title = this[AuthorizationDocumentTable.title],
    type = this[AuthorizationDocumentTable.type],
    status = this[AuthorizationDocumentTable.status],
    file = this[AuthorizationDocumentTable.file],
    requestedBy = AuthorizationParty(resourceId = requestedBy.resourceId, type = requestedBy.type),
    requestedFrom = AuthorizationParty(resourceId = requestedFrom.resourceId, type = requestedFrom.type),
    requestedTo = AuthorizationParty(resourceId = requestedTo.resourceId, type = requestedTo.type),
    createdAt = this[AuthorizationDocumentTable.createdAt],
    updatedAt = this[AuthorizationDocumentTable.updatedAt],
)
