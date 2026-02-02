package no.elhub.auth.features.documents.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.common.party.toAuthorizationParty
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.AuthorizationDocumentScopeTable.authorizationDocumentId
import no.elhub.auth.features.documents.common.AuthorizationDocumentScopeTable.authorizationScopeId
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.authorizedResourceId
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.authorizedResourceType
import no.elhub.auth.features.grants.common.AuthorizationScopeTable.permissionType
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

interface DocumentRepository {
    fun find(id: UUID): Either<RepositoryReadError, AuthorizationDocument>

    fun insert(
        doc: AuthorizationDocument,
        scopes: List<CreateScopeData>,
    ): Either<RepositoryWriteError, AuthorizationDocument>

    fun findAll(requestedBy: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationDocument>>

    fun confirm(
        documentId: UUID,
        signedFile: ByteArray,
        requestedFrom: AuthorizationParty,
        signatory: AuthorizationParty,
    ): Either<RepositoryWriteError, AuthorizationDocument>

    fun findScopeIds(documentId: UUID): Either<RepositoryReadError, List<UUID>>
}

class ExposedDocumentRepository(
    private val partyRepo: PartyRepository,
    private val documentPropertiesRepository: DocumentPropertiesRepository,
) : DocumentRepository {
    override fun insert(
        doc: AuthorizationDocument,
        scopes: List<CreateScopeData>,
    ): Either<RepositoryWriteError, AuthorizationDocument> =
        either {
            val requestedByParty =
                partyRepo
                    .findOrInsert(doc.requestedBy.type, doc.requestedBy.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

            val requestedFromParty =
                partyRepo
                    .findOrInsert(doc.requestedFrom.type, doc.requestedFrom.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

            val requestedToParty =
                partyRepo
                    .findOrInsert(doc.requestedTo.type, doc.requestedTo.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

            val documentRow =
                AuthorizationDocumentTable
                    .insertReturning {
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

            val scopeIds: List<UUID> =
                AuthorizationScopeTable
                    .batchInsert(scopes) { scope ->
                        this[AuthorizationScopeTable.id] = UUID.randomUUID()
                        this[authorizedResourceType] = scope.authorizedResourceType
                        this[authorizedResourceId] = scope.authorizedResourceId
                        this[permissionType] = scope.permissionType
                    }.map { it[AuthorizationScopeTable.id].value }

            AuthorizationDocumentScopeTable.batchInsert(scopeIds) { scopeId ->
                this[authorizationDocumentId] = documentRow[AuthorizationDocumentTable.id].value
                this[authorizationScopeId] = scopeId
            }

            documentRow.toAuthorizationDocument(requestedByParty, requestedFromParty, requestedToParty, doc.properties)
        }

    override fun find(id: UUID): Either<RepositoryReadError, AuthorizationDocument> =
        either {
            val documentRow =
                AuthorizationDocumentTable
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
                    }.singleOrNull()
                    ?.let {
                        resolveParty(it[SignatoriesTable.signedBy]).bind()
                    }

            documentRow.toAuthorizationDocument(
                requestedBy = requestedByParty,
                requestedFrom = requestedFromParty,
                requestedTo = requestedToParty,
                properties = properties,
                signedBy = signatory,
            )
        }

    override fun confirm(
        documentId: UUID,
        signedFile: ByteArray,
        requestedFrom: AuthorizationParty,
        signatory: AuthorizationParty,
    ): Either<RepositoryWriteError, AuthorizationDocument> =
        either {
            val updatedCount =
                Either
                    .catch {
                        val signatoryRecord = partyRepo.findOrInsert(signatory.type, signatory.resourceId).bind()
                        val requestedFromRecord =
                            partyRepo.findOrInsert(requestedFrom.type, requestedFrom.resourceId).bind()

                        SignatoriesTable.insert {
                            it[authorizationDocumentId] = documentId
                            it[this.requestedFrom] = requestedFromRecord.id
                            it[signedBy] = signatoryRecord.id
                        }

                        AuthorizationDocumentTable.update(
                            where = { AuthorizationDocumentTable.id eq documentId },
                        ) {
                            it[file] = signedFile
                            it[updatedAt] = OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
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

    override fun findScopeIds(documentId: UUID): Either<RepositoryReadError, List<UUID>> =
        Either
            .catch {
                (AuthorizationDocumentScopeTable innerJoin AuthorizationScopeTable)
                    .select(AuthorizationScopeTable.id)
                    .where { authorizationDocumentId eq documentId }
                    .map { row ->
                        row[AuthorizationScopeTable.id].value
                    }
            }.mapLeft { RepositoryReadError.UnexpectedError }

    override fun findAll(requestedBy: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationDocument>> =
        either {
            val partyRecord =
                partyRepo
                    .findOrInsert(type = requestedBy.type, partyId = requestedBy.resourceId)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()

            val documentWithSignatoryRecords =
                (AuthorizationDocumentTable leftJoin SignatoriesTable)
                    .select(AuthorizationDocumentTable.columns + SignatoriesTable.signedBy)
                    .where { (AuthorizationDocumentTable.requestedBy eq partyRecord.id) or (AuthorizationDocumentTable.requestedFrom eq partyRecord.id) }
                    .toList()

            if (documentWithSignatoryRecords.isEmpty()) {
                return@either emptyList()
            }

            val partyIds =
                documentWithSignatoryRecords
                    .flatMap { row ->
                        setOfNotNull(
                            row[AuthorizationDocumentTable.requestedBy],
                            row[AuthorizationDocumentTable.requestedFrom],
                            row[AuthorizationDocumentTable.requestedTo],
                            row[SignatoriesTable.signedBy],
                        )
                    }.toSet()

            val partiesById =
                AuthorizationPartyTable
                    .selectAll()
                    .where { AuthorizationPartyTable.id inList partyIds }
                    .associate { row ->
                        val party = row.toAuthorizationParty()
                        party.id to party
                    }

            documentWithSignatoryRecords.map { row ->
                val requestedByParty =
                    partiesById[row[AuthorizationDocumentTable.requestedBy]]
                        ?: raise(RepositoryReadError.UnexpectedError)
                val requestedFromParty =
                    partiesById[row[AuthorizationDocumentTable.requestedFrom]]
                        ?: raise(RepositoryReadError.UnexpectedError)
                val requestedToParty =
                    partiesById[row[AuthorizationDocumentTable.requestedTo]]
                        ?: raise(RepositoryReadError.UnexpectedError)
                val signedByParty = partiesById[row[SignatoriesTable.signedBy]]
                val properties = documentPropertiesRepository.find(row[AuthorizationDocumentTable.id].value)

                row.toAuthorizationDocument(
                    requestedByParty,
                    requestedFromParty,
                    requestedToParty,
                    properties,
                    signedByParty,
                )
            }
        }

    private fun resolveParty(partyId: UUID): Either<RepositoryReadError.UnexpectedError, AuthorizationPartyRecord> =
        partyRepo
            .find(partyId)
            .mapLeft { RepositoryReadError.UnexpectedError }
}

enum class DatabaseStatus {
    Pending,
    Rejected,
}

fun DatabaseStatus.toDocumentStatus() =
    when (this) {
        DatabaseStatus.Pending -> AuthorizationDocument.Status.Pending
        DatabaseStatus.Rejected -> AuthorizationDocument.Status.Rejected
    }

object AuthorizationDocumentTable : UUIDTable("auth.authorization_document") {
    val type =
        customEnumeration(
            name = "type",
            sql = "authorization_document_type",
            fromDb = { AuthorizationDocument.Type.valueOf(it as String) },
            toDb = { PGEnum("authorization_document_type", it) },
        )
    val file = binary("file")
    val status =
        customEnumeration(
            name = "status",
            sql = "authorization_document_status",
            fromDb = { DatabaseStatus.valueOf(it as String) },
            toDb = { PGEnum("authorization_document_status", it) },
        )
    val requestedBy = uuid("requested_by").references(AuthorizationPartyTable.id)
    val requestedFrom = uuid("requested_from").references(AuthorizationPartyTable.id)
    val requestedTo = uuid("requested_to").references(AuthorizationPartyTable.id)
    val validTo = timestampWithTimeZone("valid_to")
    val createdAt = timestampWithTimeZone("created_at").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
    val updatedAt = timestampWithTimeZone("updated_at").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
}

object AuthorizationDocumentScopeTable : Table("auth.authorization_document_scope") {
    val authorizationDocumentId =
        uuid("authorization_document_id")
            .references(AuthorizationDocumentTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId =
        uuid("authorization_scope_id")
            .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    override val primaryKey = PrimaryKey(authorizationDocumentId, authorizationScopeId)
}

object SignatoriesTable : Table("auth.authorization_document_signatories") {
    val authorizationDocumentId =
        uuid("authorization_document_id")
            .references(AuthorizationDocumentTable.id, onDelete = ReferenceOption.CASCADE)
    val requestedFrom =
        uuid("requested_from")
            .references(AuthorizationPartyTable.id)
    val signedBy =
        uuid("signed_by")
            .references(AuthorizationPartyTable.id)
    val signedAt = timestamp("signed_at").clientDefault { java.time.Instant.now() }

    override val primaryKey = PrimaryKey(authorizationDocumentId, requestedFrom)
}

fun ResultRow.toAuthorizationDocument(
    requestedBy: AuthorizationPartyRecord,
    requestedFrom: AuthorizationPartyRecord,
    requestedTo: AuthorizationPartyRecord,
    properties: List<AuthorizationDocumentProperty>,
    signedBy: AuthorizationPartyRecord? = null,
): AuthorizationDocument {
    val dbStatus = this[AuthorizationDocumentTable.status]
    val validTo = this[AuthorizationDocumentTable.validTo]

    val status: AuthorizationDocument.Status =
        when {
            signedBy != null -> AuthorizationDocument.Status.Signed
            dbStatus == DatabaseStatus.Rejected -> AuthorizationDocument.Status.Rejected
            dbStatus == DatabaseStatus.Pending && validTo <= OffsetDateTime.now(ZoneOffset.UTC) -> AuthorizationDocument.Status.Expired
            else -> dbStatus.toDocumentStatus()
        }

    return AuthorizationDocument(
        id = this[AuthorizationDocumentTable.id].value,
        type = this[AuthorizationDocumentTable.type],
        status = status,
        file = this[AuthorizationDocumentTable.file],
        requestedBy = AuthorizationParty(resourceId = requestedBy.resourceId, type = requestedBy.type),
        requestedFrom = AuthorizationParty(resourceId = requestedFrom.resourceId, type = requestedFrom.type),
        requestedTo = AuthorizationParty(resourceId = requestedTo.resourceId, type = requestedTo.type),
        signedBy = signedBy?.let { AuthorizationParty(resourceId = it.resourceId, type = it.type) },
        createdAt = this[AuthorizationDocumentTable.createdAt],
        updatedAt = this[AuthorizationDocumentTable.updatedAt],
        validTo = validTo,
        properties = properties,
    )
}
