package no.elhub.auth.features.grants.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.Page
import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.common.party.toAuthorizationParty
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.SourceType
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.AuthorizationScope
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.util.UUID

interface GrantRepository {
    suspend fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant>
    suspend fun findBySourceIds(
        sourceType: SourceType,
        sourceIds: List<UUID>
    ): Either<RepositoryReadError, Map<UUID, AuthorizationGrant>>

    suspend fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>>
    suspend fun findAll(
        party: AuthorizationParty,
        pagination: Pagination
    ): Either<RepositoryReadError, Page<AuthorizationGrant>>

    suspend fun insert(grant: AuthorizationGrant): Either<RepositoryWriteError, AuthorizationGrant>
    suspend fun update(grantId: UUID, newStatus: Status): Either<RepositoryError, AuthorizationGrant>
}

class ExposedGrantRepository(
    private val partyRepository: PartyRepository,
    private val grantPropertiesRepository: GrantPropertiesRepository,
    private val transactionContext: TransactionContext,
) : GrantRepository {

    private val logger = LoggerFactory.getLogger(ExposedGrantRepository::class.java)

    override suspend fun findAll(
        party: AuthorizationParty,
        pagination: Pagination
    ): Either<RepositoryReadError, Page<AuthorizationGrant>> =
        transactionContext<RepositoryReadError, Page<AuthorizationGrant>>(
            "db_operations",
            "GrantRepository",
            "findAll",
            { RepositoryReadError.UnexpectedError }
        ) {
            val partyId = partyRepository.findOrInsert(type = party.type, partyId = party.id)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()
                .id

            val whereClause =
                { (AuthorizationGrantTable.grantedTo eq partyId) or (AuthorizationGrantTable.grantedFor eq partyId) }

            val totalItems = AuthorizationGrantTable
                .selectAll()
                .where(whereClause)
                .count()

            val grantRows = AuthorizationGrantTable
                .selectAll()
                .where(whereClause)
                .orderBy(AuthorizationGrantTable.createdAt to SortOrder.DESC)
                .limit(pagination.size)
                .offset(pagination.offset)
                .toList()

            if (grantRows.isEmpty()) return@transactionContext Page(emptyList(), totalItems, pagination)

            val grantIds = grantRows.map { it[AuthorizationGrantTable.id].value }

            val allPartyIds = grantRows.flatMap {
                listOfNotNull(
                    it[AuthorizationGrantTable.grantedBy],
                    it[AuthorizationGrantTable.grantedFor],
                    it[AuthorizationGrantTable.grantedTo],
                )
            }.distinct()
            val partyMap: Map<UUID, AuthorizationPartyRecord> = AuthorizationPartyTable
                .selectAll()
                .where { AuthorizationPartyTable.id inList allPartyIds }
                .associate { it[AuthorizationPartyTable.id].value to it.toAuthorizationParty() }

            val scopesByGrantId: Map<UUID, List<UUID>> =
                (AuthorizationGrantScopeTable innerJoin AuthorizationScopeTable)
                    .selectAll()
                    .where { AuthorizationGrantScopeTable.authorizationGrantId inList grantIds }
                    .groupBy(
                        { it[AuthorizationGrantScopeTable.authorizationGrantId] },
                        { it[AuthorizationScopeTable.id].value }
                    )

            val propertiesByGrantId: Map<UUID, List<AuthorizationGrantProperty>> = AuthorizationGrantPropertyTable
                .selectAll()
                .where { AuthorizationGrantPropertyTable.grantId inList grantIds }
                .groupBy(
                    { it[AuthorizationGrantPropertyTable.grantId] },
                    { it.toAuthorizationGrantProperty() }
                )

            val items = grantRows.map { row ->
                val grantId = row[AuthorizationGrantTable.id].value
                val grantedBy = partyMap[row[AuthorizationGrantTable.grantedBy]]
                    ?: raise(RepositoryReadError.UnexpectedError)
                val grantedFor = partyMap[row[AuthorizationGrantTable.grantedFor]]
                    ?: raise(RepositoryReadError.UnexpectedError)
                val grantedTo = partyMap[row[AuthorizationGrantTable.grantedTo]]
                    ?: raise(RepositoryReadError.UnexpectedError)
                row.toAuthorizationGrant(
                    grantedBy = grantedBy,
                    grantedFor = grantedFor,
                    grantedTo = grantedTo,
                    scopeIds = scopesByGrantId[grantId] ?: emptyList(),
                    properties = propertiesByGrantId[grantId] ?: emptyList(),
                )
            }

            Page(items = items, totalItems = totalItems, pagination = pagination)
        }

    override suspend fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant> =
        transactionContext<RepositoryReadError, AuthorizationGrant>(
            "db_operations",
            "GrantRepository",
            "find",
            { RepositoryReadError.UnexpectedError }
        ) {
            val row = AuthorizationGrantTable
                .selectAll()
                .where { AuthorizationGrantTable.id eq grantId }
                .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

            findInternal(row).bind()
        }

    override suspend fun findBySourceIds(
        sourceType: SourceType,
        sourceIds: List<UUID>,
    ): Either<RepositoryReadError, Map<UUID, AuthorizationGrant>> =
        transactionContext<RepositoryReadError, Map<UUID, AuthorizationGrant>>(
            "db_operations",
            "GrantRepository",
            "findBySourceIds",
            { RepositoryReadError.UnexpectedError }
        ) {
            if (sourceIds.isEmpty()) return@transactionContext emptyMap()

            val grantRows = AuthorizationGrantTable
                .selectAll()
                .where {
                    (AuthorizationGrantTable.sourceType eq sourceType) and
                        (AuthorizationGrantTable.sourceId inList sourceIds)
                }
                .toList()

            if (grantRows.isEmpty()) return@transactionContext emptyMap()

            val grantIds = grantRows.map { it[AuthorizationGrantTable.id].value }

            val allPartyIds = grantRows.flatMap {
                listOfNotNull(
                    it[AuthorizationGrantTable.grantedBy],
                    it[AuthorizationGrantTable.grantedFor],
                    it[AuthorizationGrantTable.grantedTo],
                )
            }.distinct()
            val partyMap: Map<UUID, AuthorizationPartyRecord> = AuthorizationPartyTable
                .selectAll()
                .where { AuthorizationPartyTable.id inList allPartyIds }
                .associate { it[AuthorizationPartyTable.id].value to it.toAuthorizationParty() }

            val scopesByGrantId: Map<UUID, List<UUID>> =
                (AuthorizationGrantScopeTable innerJoin AuthorizationScopeTable)
                    .selectAll()
                    .where { AuthorizationGrantScopeTable.authorizationGrantId inList grantIds }
                    .groupBy(
                        { it[AuthorizationGrantScopeTable.authorizationGrantId] },
                        { it[AuthorizationScopeTable.id].value }
                    )

            val propertiesByGrantId: Map<UUID, List<AuthorizationGrantProperty>> = AuthorizationGrantPropertyTable
                .selectAll()
                .where { AuthorizationGrantPropertyTable.grantId inList grantIds }
                .groupBy(
                    { it[AuthorizationGrantPropertyTable.grantId] },
                    { it.toAuthorizationGrantProperty() }
                )

            grantRows.associate { row ->
                val grantId = row[AuthorizationGrantTable.id].value
                val grantedBy = partyMap[row[AuthorizationGrantTable.grantedBy]]
                    ?: raise(RepositoryReadError.UnexpectedError)
                val grantedFor = partyMap[row[AuthorizationGrantTable.grantedFor]]
                    ?: raise(RepositoryReadError.UnexpectedError)
                val grantedTo = partyMap[row[AuthorizationGrantTable.grantedTo]]
                    ?: raise(RepositoryReadError.UnexpectedError)
                val grant = row.toAuthorizationGrant(
                    grantedBy = grantedBy,
                    grantedFor = grantedFor,
                    grantedTo = grantedTo,
                    scopeIds = scopesByGrantId[grantId] ?: emptyList(),
                    properties = propertiesByGrantId[grantId] ?: emptyList(),
                )
                row[AuthorizationGrantTable.sourceId] to grant
            }
        }

    fun findScopeIds(grantId: UUID): Either<RepositoryReadError, List<UUID>> = either {
        (AuthorizationGrantScopeTable innerJoin AuthorizationScopeTable)
            .selectAll()
            .where { AuthorizationGrantScopeTable.authorizationGrantId eq grantId }
            .map { row -> row[AuthorizationScopeTable.id].value }
    }

    override suspend fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>> =
        transactionContext<RepositoryReadError, List<AuthorizationScope>>(
            "db_operations",
            "GrantRepository",
            "findScopes",
            { RepositoryReadError.UnexpectedError }
        ) {
            AuthorizationGrantTable
                .selectAll()
                .where { AuthorizationGrantTable.id eq grantId }
                .singleOrNull()
                ?.let {
                    (AuthorizationGrantScopeTable innerJoin AuthorizationScopeTable)
                        .selectAll()
                        .where { AuthorizationGrantScopeTable.authorizationGrantId eq grantId }
                        .map { row ->
                            AuthorizationScope(
                                id = row[AuthorizationScopeTable.id].value,
                                authorizedResourceId = row[AuthorizationScopeTable.authorizedResourceId],
                                authorizedResourceType = row[AuthorizationScopeTable.authorizedResourceType],
                                permissionType = row[AuthorizationScopeTable.permissionType],
                                createdAt = row[AuthorizationScopeTable.createdAt]
                            )
                        }
                } ?: run {
                logger.error("Scope not found for authorization grant with id=$grantId")
                raise(RepositoryReadError.NotFoundError)
            }
        }

    override suspend fun insert(
        grant: AuthorizationGrant,
    ): Either<RepositoryWriteError, AuthorizationGrant> =
        transactionContext<RepositoryWriteError, AuthorizationGrant>(
            "db_operations",
            "GrantRepository",
            "insert",
            { RepositoryWriteError.UnexpectedError }
        ) {
            val grantedByParty = partyRepository
                .findOrInsert(grant.grantedBy.type, grant.grantedBy.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()

            val grantedForParty = partyRepository
                .findOrInsert(grant.grantedFor.type, grant.grantedFor.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()

            val grantedToParty = partyRepository
                .findOrInsert(grant.grantedTo.type, grant.grantedTo.id)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()

            val authorizationGrant = AuthorizationGrantTable
                .insertReturning {
                    it[id] = grant.id
                    it[grantStatus] = grant.grantStatus
                    it[grantedBy] = grantedByParty.id
                    it[grantedFor] = grantedForParty.id
                    it[grantedTo] = grantedToParty.id
                    it[grantedAt] = grant.grantedAt
                    it[validFrom] = grant.validFrom
                    it[validTo] = grant.validTo
                    it[createdAt] = grant.createdAt
                    it[updatedAt] = grant.updatedAt
                    it[sourceType] = grant.sourceType
                    it[sourceId] = grant.sourceId
                }.map {
                    it.toAuthorizationGrant(
                        grantedBy = grantedByParty,
                        grantedFor = grantedForParty,
                        grantedTo = grantedToParty,
                        scopeIds = grant.scopeIds,
                        properties = grant.properties
                    )
                }.single()

            if (grant.scopeIds.isNotEmpty()) {
                AuthorizationGrantScopeTable.batchInsert(grant.scopeIds) { scopeId ->
                    this[AuthorizationGrantScopeTable.authorizationGrantId] = authorizationGrant.id
                    this[AuthorizationGrantScopeTable.authorizationScopeId] = scopeId
                }
            }

            authorizationGrant
        }

    override suspend fun update(grantId: UUID, newStatus: Status): Either<RepositoryError, AuthorizationGrant> =
        transactionContext<RepositoryError, AuthorizationGrant>(
            "db_operations",
            "GrantRepository",
            "update",
            { RepositoryWriteError.UnexpectedError }
        ) {
            val now = currentTimeUtc()
            val rowsUpdated =
                AuthorizationGrantTable.update(
                    where = {
                        (AuthorizationGrantTable.id eq grantId) and
                            (AuthorizationGrantTable.grantStatus eq Status.Active) and
                            (AuthorizationGrantTable.validTo greater now)
                    }
                ) {
                    it[grantStatus] = newStatus
                    it[updatedAt] = currentTimeUtc()
                }

            fetchUpdated(grantId, rowsUpdated).bind()
        }

    private suspend fun fetchUpdated(
        grantId: UUID,
        rowsUpdated: Int
    ): Either<RepositoryError, AuthorizationGrant> =
        either {
            val grant = AuthorizationGrantTable
                .selectAll()
                .where { AuthorizationGrantTable.id eq grantId }
                .singleOrNull() ?: raise(RepositoryWriteError.NotFoundError)

            if (rowsUpdated == 0) {
                val isProcessed = grant[AuthorizationGrantTable.grantStatus] != Status.Active
                if (isProcessed) raise(RepositoryWriteError.ConflictError)
                raise(RepositoryWriteError.ExpiredError)
            }

            findInternal(grant)
                .mapLeft { RepositoryWriteError.UnexpectedError }
                .bind()
        }

    private suspend fun findInternal(grant: ResultRow): Either<RepositoryReadError, AuthorizationGrant> =
        either {
            val grantId = grant[AuthorizationGrantTable.id].value

            val scopes = findScopeIds(grantId).bind()

            val grantedByDbId = grant[AuthorizationGrantTable.grantedBy]
            val grantedForDbId = grant[AuthorizationGrantTable.grantedFor]
            val grantedToDbId = grant[AuthorizationGrantTable.grantedTo]

            val grantedByParty =
                partyRepository
                    .find(grantedByDbId)
                    .mapLeft { error ->
                        logger.error("Failed to get grantedBy: $error")
                        RepositoryReadError.UnexpectedError
                    }
                    .bind()

            val grantedForParty =
                partyRepository
                    .find(grantedForDbId)
                    .mapLeft { error ->
                        logger.error("Failed to get grantedFor: $error")
                        RepositoryReadError.UnexpectedError
                    }
                    .bind()

            val grantedToParty =
                partyRepository
                    .find(grantedToDbId)
                    .mapLeft { error ->
                        logger.error("Failed to get grantedTo: $error")
                        RepositoryReadError.UnexpectedError
                    }
                    .bind()

            val properties = grantPropertiesRepository.findBy(grantId = grantId)

            grant.toAuthorizationGrant(
                grantedBy = grantedByParty,
                grantedFor = grantedForParty,
                grantedTo = grantedToParty,
                scopeIds = scopes,
                properties = properties
            )
        }
}

object AuthorizationGrantScopeTable : Table("auth.authorization_grant_scope") {
    val authorizationGrantId = javaUUID("authorization_grant_id")
        .references(AuthorizationGrantTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = javaUUID("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(authorizationGrantId, authorizationScopeId)
}

object AuthorizationGrantTable : UUIDTable("auth.authorization_grant") {
    val grantStatus =
        customEnumeration(
            name = "status",
            sql = "auth.authorization_grant_status",
            fromDb = { value -> Status.valueOf(value as String) },
            toDb = { PGEnum("authorization_grant_status", it) },
        )
    val grantedFor = javaUUID("granted_for").references(AuthorizationPartyTable.id)
    val grantedBy = javaUUID("granted_by").references(AuthorizationPartyTable.id)
    val grantedTo = javaUUID("granted_to").references(AuthorizationPartyTable.id)
    val grantedAt = timestampWithTimeZone("granted_at").clientDefault { currentTimeUtc() }
    val validFrom = timestampWithTimeZone("valid_from").clientDefault { currentTimeUtc() }
    val createdAt = timestampWithTimeZone("created_at").clientDefault { currentTimeUtc() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault { currentTimeUtc() }
    val validTo = timestampWithTimeZone("valid_to").clientDefault { currentTimeUtc() }
    val sourceType =
        customEnumeration(
            name = "source_type",
            sql = "auth.authorization_grant_source_type",
            fromDb = { value -> SourceType.valueOf(value as String) },
            toDb = { PGEnum("authorization_grant_source_type", it) },
        )
    val sourceId = javaUUID("source_id")
}

object AuthorizationScopeTable : UUIDTable(name = "auth.authorization_scope") {
    val authorizedResourceType = customEnumeration(
        name = "authorized_resource_type",
        sql = "authorization_resource",
        fromDb = { AuthorizationScope.AuthorizationResource.valueOf(it as String) },
        toDb = { PGEnum("authorization_resource", it) }
    )
    val authorizedResourceId = varchar("authorized_resource_id", length = 64)
    val permissionType = customEnumeration(
        name = "permission_type",
        sql = "authorization_permission_type",
        fromDb = { AuthorizationScope.PermissionType.valueOf(it as String) },
        toDb = { PGEnum("authorization_permission_type", it) }
    )
    val createdAt = timestampWithTimeZone("created_at").clientDefault { currentTimeUtc() }
}

fun ResultRow.toAuthorizationGrant(
    grantedBy: AuthorizationPartyRecord,
    grantedFor: AuthorizationPartyRecord,
    grantedTo: AuthorizationPartyRecord,
    scopeIds: List<UUID>,
    properties: List<AuthorizationGrantProperty>
) = AuthorizationGrant(
    id = this[AuthorizationGrantTable.id].value,
    grantStatus = this[AuthorizationGrantTable.grantStatus],
    grantedFor = grantedFor.toAuthorizationParty(),
    grantedBy = grantedBy.toAuthorizationParty(),
    grantedTo = grantedTo.toAuthorizationParty(),
    grantedAt = this[AuthorizationGrantTable.grantedAt],
    validFrom = this[AuthorizationGrantTable.validFrom],
    createdAt = this[AuthorizationGrantTable.createdAt],
    updatedAt = this[AuthorizationGrantTable.updatedAt],
    validTo = this[AuthorizationGrantTable.validTo],
    sourceType = this[AuthorizationGrantTable.sourceType],
    sourceId = this[AuthorizationGrantTable.sourceId],
    scopeIds = scopeIds,
    properties = properties
)

fun AuthorizationPartyRecord.toAuthorizationParty() = AuthorizationParty(id = this.resourceId, type = this.type)
