package no.elhub.auth.features.documents.common

import arrow.core.Either
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.Page
import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.common.party.toAuthorizationParty
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentScopeTable.authorizationDocumentId
import no.elhub.auth.features.documents.common.AuthorizationDocumentScopeTable.authorizationScopeId
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.AuthorizationGrantProperty
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.authorizedResourceId
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.authorizedResourceType
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.permissionType
import no.elhub.auth.features.grants.common.GrantPropertiesRepository
import no.elhub.auth.features.grants.common.GrantRepository
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

sealed interface ConfirmWithGrantError {
    sealed interface DocumentError : ConfirmWithGrantError {
        data object NotFound : DocumentError
        data object Conflict : DocumentError
        data object Unexpected : DocumentError
    }

    data object GrantError : ConfirmWithGrantError
}

interface DocumentRepository {
    suspend fun find(id: UUID): Either<RepositoryReadError, AuthorizationDocument>
    suspend fun insert(
        doc: AuthorizationDocument,
        scopes: List<CreateScopeData>
    ): Either<RepositoryWriteError, AuthorizationDocument>

    suspend fun findAndSortByCreatedAt(
        requestedBy: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationDocument.Status>,
    ): Either<RepositoryReadError, Page<AuthorizationDocument>>

    suspend fun findScopeIds(documentId: UUID): Either<RepositoryReadError, List<UUID>>

    suspend fun confirmWithGrant(
        documentId: UUID,
        signedFile: ByteArray,
        requestedFrom: AuthorizationParty,
        signatory: AuthorizationParty,
        grant: AuthorizationGrant,
        grantProperties: List<AuthorizationGrantProperty>
    ): Either<ConfirmWithGrantError, AuthorizationDocument>
}

class ExposedDocumentRepository(
    private val partyRepo: PartyRepository,
    private val grantRepo: GrantRepository,
    private val documentPropertiesRepository: DocumentPropertiesRepository,
    private val grantPropertiesRepository: GrantPropertiesRepository,
    private val transactionContext: TransactionContext,
) : DocumentRepository {

    override suspend fun insert(
        doc: AuthorizationDocument,
        scopes: List<CreateScopeData>
    ): Either<RepositoryWriteError, AuthorizationDocument> =
        transactionContext<RepositoryWriteError, AuthorizationDocument>(
            "db_operations",
            "DocumentRepository",
            "insert",
            { RepositoryWriteError.UnexpectedError }
        ) {
            val requestedByParty = partyRepo.findOrInsert(doc.requestedBy.type, doc.requestedBy.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }.bind()
            val requestedFromParty = partyRepo.findOrInsert(doc.requestedFrom.type, doc.requestedFrom.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }.bind()
            val requestedToParty = partyRepo.findOrInsert(doc.requestedTo.type, doc.requestedTo.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }.bind()

            val documentRow = AuthorizationDocumentTable.insertReturning {
                it[id] = doc.id
                it[type] = doc.type
                it[status] = DatabaseStatus.Pending
                it[file] = doc.file
                it[requestedBy] = requestedByParty.id
                it[requestedFrom] = requestedFromParty.id
                it[requestedTo] = requestedToParty.id
                it[validTo] = doc.validTo
                it[createdAt] = doc.createdAt
                it[updatedAt] = doc.updatedAt
            }.single()

            documentPropertiesRepository.insert(doc.properties, doc.id)

            val scopeIds: List<UUID> = AuthorizationScopeTable
                .batchInsert(scopes) { scope ->
                    this[AuthorizationScopeTable.id] = UUID.randomUUID()
                    this[authorizedResourceType] = scope.authorizedResourceType
                    this[authorizedResourceId] = scope.authorizedResourceId
                    this[permissionType] = scope.permissionType
                }
                .map { it[AuthorizationScopeTable.id].value }

            AuthorizationDocumentScopeTable.batchInsert(scopeIds) { scopeId ->
                this[authorizationDocumentId] = documentRow[AuthorizationDocumentTable.id].value
                this[authorizationScopeId] = scopeId
            }
            documentRow.toAuthorizationDocument(requestedByParty, requestedFromParty, requestedToParty, doc.properties)
        }

    override suspend fun find(id: UUID): Either<RepositoryReadError, AuthorizationDocument> =
        transactionContext<RepositoryReadError, AuthorizationDocument>(
            "db_operations",
            "DocumentRepository",
            "find",
            { RepositoryReadError.UnexpectedError }
        ) {
            val documentRow = AuthorizationDocumentTable
                .selectAll()
                .where { AuthorizationDocumentTable.id eq id }
                .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

            val requestedByParty = resolveParty(documentRow[AuthorizationDocumentTable.requestedBy]).bind()
            val requestedFromParty = resolveParty(documentRow[AuthorizationDocumentTable.requestedFrom]).bind()
            val requestedToParty = resolveParty(documentRow[AuthorizationDocumentTable.requestedTo]).bind()
            val properties = documentPropertiesRepository.find(id)

            val signatory = SignatoriesTable
                .select(listOf(SignatoriesTable.signedBy))
                .where {
                    (SignatoriesTable.authorizationDocumentId eq id) and
                        (SignatoriesTable.requestedFrom eq documentRow[AuthorizationDocumentTable.requestedFrom])
                }
                .singleOrNull()
                ?.let { resolveParty(it[SignatoriesTable.signedBy]).bind() }

            documentRow.toAuthorizationDocument(
                requestedBy = requestedByParty,
                requestedFrom = requestedFromParty,
                requestedTo = requestedToParty,
                properties = properties,
                signedBy = signatory
            )
        }

    private suspend fun confirm(
        documentId: UUID,
        signedFile: ByteArray,
        requestedFrom: AuthorizationParty,
        signatory: AuthorizationParty
    ): Either<RepositoryWriteError, AuthorizationDocument> =
        transactionContext<RepositoryWriteError, AuthorizationDocument>(
            "db_operations",
            "DocumentRepository",
            "confirm",
            { RepositoryWriteError.UnexpectedError }
        ) {
            val signatoryRecord = partyRepo.findOrInsert(signatory.type, signatory.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()
            val requestedFromRecord = partyRepo.findOrInsert(requestedFrom.type, requestedFrom.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()

            SignatoriesTable.insert {
                it[authorizationDocumentId] = documentId
                it[this.requestedFrom] = requestedFromRecord.id
                it[signedBy] = signatoryRecord.id
            }

            val updatedCount = AuthorizationDocumentTable.update(
                where = { AuthorizationDocumentTable.id eq documentId }
            ) {
                it[file] = signedFile
                it[updatedAt] = currentTimeUtc()
            }

            if (updatedCount == 0) raise(RepositoryWriteError.NotFoundError)

            find(documentId)
                .mapLeft { readError ->
                    when (readError) {
                        RepositoryReadError.NotFoundError -> RepositoryWriteError.NotFoundError
                        RepositoryReadError.UnexpectedError -> RepositoryWriteError.UnexpectedError
                    }
                }.bind()
        }

    override suspend fun findScopeIds(documentId: UUID): Either<RepositoryReadError, List<UUID>> =
        transactionContext(
            "db_operations",
            "DocumenRepository",
            "findScopeIds",
            { RepositoryReadError.UnexpectedError }
        ) {
            (AuthorizationDocumentScopeTable innerJoin AuthorizationScopeTable)
                .select(AuthorizationScopeTable.id)
                .where { authorizationDocumentId eq documentId }
                .map { row -> row[AuthorizationScopeTable.id].value }
        }

    override suspend fun findAndSortByCreatedAt(
        requestedBy: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationDocument.Status>,
    ): Either<RepositoryReadError, Page<AuthorizationDocument>> =
        transactionContext<RepositoryReadError, Page<AuthorizationDocument>>(
            "db_operations",
            "DocumenRepository",
            "findAndSortByCreatedAt",
            { RepositoryReadError.UnexpectedError }
        ) {
            val partyRecord = partyRepo.findOrInsert(type = requestedBy.type, partyId = requestedBy.id)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()

            val whereClause = generateFilterByCondition(partyRecord.id, statuses)

            val totalItems = AuthorizationDocumentTable
                .selectAll()
                .where(whereClause)
                .count()

            val documentRows = AuthorizationDocumentTable
                .selectAll()
                .where(whereClause)
                .orderBy(AuthorizationDocumentTable.createdAt to SortOrder.DESC)
                .limit(pagination.size)
                .offset(pagination.offset)
                .toList()

            if (documentRows.isEmpty()) return@transactionContext Page(emptyList(), totalItems, pagination)

            val documentIds = documentRows.map { it[AuthorizationDocumentTable.id].value }

            val signatoryByDocumentId = SignatoriesTable
                .select(listOf(SignatoriesTable.authorizationDocumentId, SignatoriesTable.signedBy))
                .where { SignatoriesTable.authorizationDocumentId inList documentIds }
                .associate { it[SignatoriesTable.authorizationDocumentId] to it[SignatoriesTable.signedBy] }

            val partyIds = documentRows.flatMap { row ->
                setOfNotNull(
                    row[AuthorizationDocumentTable.requestedBy],
                    row[AuthorizationDocumentTable.requestedFrom],
                    row[AuthorizationDocumentTable.requestedTo],
                )
            }.toMutableSet().also { it.addAll(signatoryByDocumentId.values) }

            val partiesById = AuthorizationPartyTable
                .selectAll()
                .where { AuthorizationPartyTable.id inList partyIds }
                .associate { row ->
                    val party = row.toAuthorizationParty()
                    party.id to party
                }

            val items = documentRows.map { row ->
                val requestedByParty = partiesById[row[AuthorizationDocumentTable.requestedBy]]
                    ?: raise(RepositoryReadError.UnexpectedError)
                val requestedFromParty = partiesById[row[AuthorizationDocumentTable.requestedFrom]]
                    ?: raise(RepositoryReadError.UnexpectedError)
                val requestedToParty = partiesById[row[AuthorizationDocumentTable.requestedTo]]
                    ?: raise(RepositoryReadError.UnexpectedError)
                val docId = row[AuthorizationDocumentTable.id].value
                val signedByParty = signatoryByDocumentId[docId]?.let { partiesById[it] }
                val properties = documentPropertiesRepository.find(docId)

                row.toAuthorizationDocument(
                    requestedByParty,
                    requestedFromParty,
                    requestedToParty,
                    properties,
                    signedByParty
                )
            }

            Page(items = items, totalItems = totalItems, pagination = pagination)
        }

    // Match on party, and statuses if any are provided
    private fun generateFilterByCondition(partyId: UUID, statuses: List<AuthorizationDocument.Status>): Op<Boolean> {
        val partyCondition =
            (AuthorizationDocumentTable.requestedBy eq partyId) or (AuthorizationDocumentTable.requestedFrom eq partyId)
        if (statuses.isEmpty()) {
            return partyCondition
        }

        val statusCondition = statuses.map { status ->
            val signedDocIds = SignatoriesTable.select(SignatoriesTable.authorizationDocumentId)
            when (status) {
                AuthorizationDocument.Status.Signed ->
                    (AuthorizationDocumentTable.status eq DatabaseStatus.Pending) and
                        (AuthorizationDocumentTable.id inSubQuery signedDocIds)

                AuthorizationDocument.Status.Pending ->
                    (AuthorizationDocumentTable.status eq DatabaseStatus.Pending) and
                        (AuthorizationDocumentTable.validTo greater currentTimeUtc()) and
                        (AuthorizationDocumentTable.id notInSubQuery signedDocIds)

                AuthorizationDocument.Status.Expired ->
                    (AuthorizationDocumentTable.status eq DatabaseStatus.Pending) and
                        (AuthorizationDocumentTable.validTo lessEq currentTimeUtc()) and
                        (AuthorizationDocumentTable.id notInSubQuery signedDocIds)

                AuthorizationDocument.Status.Rejected ->
                    AuthorizationDocumentTable.status eq DatabaseStatus.Rejected
            }
        }.reduce { acc, op -> acc or op }
        return partyCondition and statusCondition
    }

    private suspend fun resolveParty(partyId: UUID): Either<RepositoryReadError.UnexpectedError, AuthorizationPartyRecord> =
        partyRepo.find(partyId).mapLeft { RepositoryReadError.UnexpectedError }

    override suspend fun confirmWithGrant(
        documentId: UUID,
        signedFile: ByteArray,
        requestedFrom: AuthorizationParty,
        signatory: AuthorizationParty,
        grant: AuthorizationGrant,
        grantProperties: List<AuthorizationGrantProperty>
    ): Either<ConfirmWithGrantError, AuthorizationDocument> =
        transactionContext<ConfirmWithGrantError, AuthorizationDocument>(
            "db_operations",
            "DocumenRepository",
            "confirmWithGrant",
            { ConfirmWithGrantError.DocumentError.Unexpected }
        ) {
            val confirmedDocument = confirm(documentId, signedFile, requestedFrom, signatory)
                .mapLeft { writeError ->
                    when (writeError) {
                        is RepositoryWriteError.NotFoundError -> ConfirmWithGrantError.DocumentError.NotFound
                        is RepositoryWriteError.ConflictError -> ConfirmWithGrantError.DocumentError.Conflict
                        is RepositoryWriteError.UnexpectedError -> ConfirmWithGrantError.DocumentError.Unexpected
                    }
                }.bind()

            grantRepo.insert(grant)
                .mapLeft { ConfirmWithGrantError.GrantError }
                .bind()

            grantPropertiesRepository.insert(grantProperties)
                .mapLeft { ConfirmWithGrantError.GrantError }
                .bind()

            confirmedDocument
        }
}

enum class DatabaseStatus {
    Pending,
    Rejected
}

fun DatabaseStatus.toDocumentStatus() =
    when (this) {
        DatabaseStatus.Pending -> AuthorizationDocument.Status.Pending
        DatabaseStatus.Rejected -> AuthorizationDocument.Status.Rejected
    }

object AuthorizationDocumentTable : UUIDTable("auth.authorization_document") {
    val type = customEnumeration(
        name = "type",
        sql = "authorization_document_type",
        fromDb = { AuthorizationDocument.Type.valueOf(it as String) },
        toDb = { PGEnum("authorization_document_type", it) },
    )
    val file = binary("file")
    val status = customEnumeration(
        name = "status",
        sql = "authorization_document_status",
        fromDb = { DatabaseStatus.valueOf(it as String) },
        toDb = { PGEnum("authorization_document_status", it) },
    )
    val requestedBy = javaUUID("requested_by").references(AuthorizationPartyTable.id)
    val requestedFrom = javaUUID("requested_from").references(AuthorizationPartyTable.id)
    val requestedTo = javaUUID("requested_to").references(AuthorizationPartyTable.id)
    val validTo = timestampWithTimeZone("valid_to").clientDefault { currentTimeUtc() }
    val createdAt = timestampWithTimeZone("created_at").clientDefault { currentTimeUtc() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault { currentTimeUtc() }
}

object AuthorizationDocumentScopeTable : Table("auth.authorization_document_scope") {
    val authorizationDocumentId = javaUUID("authorization_document_id")
        .references(AuthorizationDocumentTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = javaUUID("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(authorizationDocumentId, authorizationScopeId)
}

object SignatoriesTable : Table("auth.authorization_document_signatories") {
    val authorizationDocumentId = javaUUID("authorization_document_id")
        .references(AuthorizationDocumentTable.id, onDelete = ReferenceOption.CASCADE)
    val requestedFrom = javaUUID("requested_from")
        .references(AuthorizationPartyTable.id)
    val signedBy = javaUUID("signed_by")
        .references(AuthorizationPartyTable.id)
    val signedAt = timestampWithTimeZone("signed_at").clientDefault { currentTimeUtc() }

    override val primaryKey = PrimaryKey(authorizationDocumentId, requestedFrom)
}

fun ResultRow.toAuthorizationDocument(
    requestedBy: AuthorizationPartyRecord,
    requestedFrom: AuthorizationPartyRecord,
    requestedTo: AuthorizationPartyRecord,
    properties: List<AuthorizationDocumentProperty>,
    signedBy: AuthorizationPartyRecord? = null
): AuthorizationDocument {
    val dbStatus = this[AuthorizationDocumentTable.status]
    val validTo = this[AuthorizationDocumentTable.validTo]

    val status: AuthorizationDocument.Status = when {
        signedBy != null -> AuthorizationDocument.Status.Signed
        dbStatus == DatabaseStatus.Rejected -> AuthorizationDocument.Status.Rejected
        dbStatus == DatabaseStatus.Pending && validTo <= currentTimeUtc() -> AuthorizationDocument.Status.Expired
        else -> dbStatus.toDocumentStatus()
    }

    return AuthorizationDocument(
        id = this[AuthorizationDocumentTable.id].value,
        type = this[AuthorizationDocumentTable.type],
        status = status,
        file = this[AuthorizationDocumentTable.file],
        requestedBy = AuthorizationParty(id = requestedBy.resourceId, type = requestedBy.type),
        requestedFrom = AuthorizationParty(id = requestedFrom.resourceId, type = requestedFrom.type),
        requestedTo = AuthorizationParty(id = requestedTo.resourceId, type = requestedTo.type),
        signedBy = signedBy?.let { AuthorizationParty(id = it.resourceId, type = it.type) },
        createdAt = this[AuthorizationDocumentTable.createdAt],
        updatedAt = this[AuthorizationDocumentTable.updatedAt],
        validTo = validTo,
        properties = properties
    )
}
