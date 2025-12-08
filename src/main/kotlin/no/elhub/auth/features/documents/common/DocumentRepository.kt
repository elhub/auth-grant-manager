package no.elhub.auth.features.documents.common

import arrow.core.Either
import arrow.core.raise.either
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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

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
    private val partyRepo: PartyRepository,
    private val documentPropertiesRepository: DocumentPropertiesRepository,
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

                val documentRow = AuthorizationDocumentTable.insertReturning {
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
                }.single()

                documentPropertiesRepository.insert(doc.properties, doc.id)

                val scopeId = AuthorizationScopeTable.insertAndGetId {
                    it[authorizedResourceType] = ElhubResource.MeteringPoint
                    it[authorizedResourceId] = "Something"
                    it[permissionType] = PermissionType.ChangeOfSupplier
                }

                AuthorizationDocumentScopeTable.insert {
                    it[authorizationDocumentId] = documentRow[AuthorizationDocumentTable.id].value
                    it[authorizationScopeId] = scopeId.value
                }

                documentRow.toAuthorizationDocument(requestedByParty, requestedFromParty, requestedToParty, doc.properties)
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun find(id: UUID): Either<RepositoryReadError, AuthorizationDocument> =
        either {
            transaction {
                val documentRow = AuthorizationDocumentTable
                    .selectAll()
                    .where { AuthorizationDocumentTable.id eq id }
                    .map { it }
                    .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

                val requestedByDbId = documentRow[AuthorizationDocumentTable.requestedBy]
                val requestedByParty = resolveParty(requestedByDbId).bind()

                val requestedFromDbId = documentRow[AuthorizationDocumentTable.requestedFrom]
                val requestedFromParty = resolveParty(requestedFromDbId).bind()

                val requestedToDbId = documentRow[AuthorizationDocumentTable.requestedTo]
                val requestedToParty = resolveParty(requestedToDbId).bind()

                val properties = documentPropertiesRepository.find(id)

                val signatory =
                    SignatoriesTable
                        .select(listOf(SignatoriesTable.signedBy))
                        .where {
                            (SignatoriesTable.authorizationDocumentId eq id) and
                                (SignatoriesTable.requestedFrom eq documentRow[AuthorizationDocumentTable.requestedFrom])
                        }
                        .singleOrNull()
                        ?.let {
                            resolveParty(it[SignatoriesTable.signedBy]).bind()
                        }

                documentRow.toAuthorizationDocument(
                    requestedBy = requestedByParty,
                    requestedFrom = requestedFromParty,
                    requestedTo = requestedToParty,
                    properties = properties,
                    signedBy = signatory
                )
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

                            SignatoriesTable.insert {
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

            val documentWithSignatoryRecords = (AuthorizationDocumentTable leftJoin SignatoriesTable)
                .select(AuthorizationDocumentTable.columns + SignatoriesTable.signedBy)
                .where { AuthorizationDocumentTable.requestedBy eq partyRecord.id }
                .toList()

            if (documentWithSignatoryRecords.isEmpty()) {
                return@transaction emptyList<AuthorizationDocument>()
            }

            val partyIds = documentWithSignatoryRecords.flatMap { row ->
                setOfNotNull(
                    row[AuthorizationDocumentTable.requestedBy],
                    row[AuthorizationDocumentTable.requestedFrom],
                    row[AuthorizationDocumentTable.requestedTo],
                    row[SignatoriesTable.signedBy]
                )
            }.toSet()

            val partiesById = AuthorizationPartyTable
                .selectAll()
                .where { AuthorizationPartyTable.id inList partyIds }
                .associate { row ->
                    val party = row.toAuthorizationParty()
                    party.id to party
                }

            documentWithSignatoryRecords.map { row ->
                val requestedByParty =
                    partiesById[row[AuthorizationDocumentTable.requestedBy]] ?: raise(RepositoryReadError.UnexpectedError)
                val requestedFromParty =
                    partiesById[row[AuthorizationDocumentTable.requestedFrom]] ?: raise(RepositoryReadError.UnexpectedError)
                val requestedToParty =
                    partiesById[row[AuthorizationDocumentTable.requestedTo]] ?: raise(RepositoryReadError.UnexpectedError)
                val signedByParty = partiesById[row[SignatoriesTable.signedBy]]
                val properties = documentPropertiesRepository.find(row[AuthorizationDocumentTable.id].value)

                row.toAuthorizationDocument(requestedByParty, requestedFromParty, requestedToParty, properties, signedByParty)
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

object SignatoriesTable : Table("auth.authorization_document_signatories") {
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
    properties: List<AuthorizationDocumentProperty>,
    signedBy: AuthorizationPartyRecord? = null
) = AuthorizationDocument(
    id = this[AuthorizationDocumentTable.id].value,
    title = this[AuthorizationDocumentTable.title],
    type = this[AuthorizationDocumentTable.type],
    status = this[AuthorizationDocumentTable.status],
    file = this[AuthorizationDocumentTable.file],
    requestedBy = AuthorizationParty(resourceId = requestedBy.resourceId, type = requestedBy.type),
    requestedFrom = AuthorizationParty(resourceId = requestedFrom.resourceId, type = requestedFrom.type),
    requestedTo = AuthorizationParty(resourceId = requestedTo.resourceId, type = requestedTo.type),
    signedBy = signedBy?.let { AuthorizationParty(resourceId = it.resourceId, type = it.type) },
    createdAt = this[AuthorizationDocumentTable.createdAt],
    updatedAt = this[AuthorizationDocumentTable.updatedAt],
    properties = properties
)
