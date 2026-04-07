package no.elhub.auth.features.grants.common

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.currentTimeUtc
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.SourceType
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.AuthorizationScope
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
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
    suspend fun findBySource(sourceType: SourceType, sourceId: UUID): Either<RepositoryReadError, AuthorizationGrant?>
    suspend fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>>
    suspend fun findAll(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationGrant>>
    suspend fun insert(grant: AuthorizationGrant): Either<RepositoryWriteError, AuthorizationGrant>
    suspend fun update(grantId: UUID, newStatus: Status): Either<RepositoryError, AuthorizationGrant>
}

class ExposedGrantRepository(
    private val partyRepository: PartyRepository,
    private val grantPropertiesRepository: GrantPropertiesRepository,
    private val transactionContext: TransactionContext,
) : GrantRepository {

    private val logger = LoggerFactory.getLogger(ExposedGrantRepository::class.java)

    override suspend fun findAll(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationGrant>> =
        transactionContext("grant_repo_find_all", { RepositoryReadError.UnexpectedError }) {
            val partyId = partyRepository.findOrInsert(type = party.type, partyId = party.id)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()
                .id
            AuthorizationGrantTable
                .selectAll()
                .where {
                    (AuthorizationGrantTable.grantedTo eq partyId) or (AuthorizationGrantTable.grantedFor eq partyId)
                }
                .map { g ->
                    findInternalGrant(g[AuthorizationGrantTable.id].value)
                        .mapLeft { RepositoryReadError.UnexpectedError }
                        .bind()
                }
        }

    override suspend fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant> =
        transactionContext("grant_repo_find", { RepositoryReadError.UnexpectedError }) {
            findInternalGrant(grantId).mapLeft { RepositoryReadError.UnexpectedError }.bind()
        }

    override suspend fun findBySource(
        sourceType: SourceType,
        sourceId: UUID
    ): Either<RepositoryReadError, AuthorizationGrant?> =
        transactionContext("grant_repo_find_by_source", { RepositoryReadError.UnexpectedError }) {
            AuthorizationGrantTable
                .selectAll()
                .where {
                    (AuthorizationGrantTable.sourceType eq sourceType) and (AuthorizationGrantTable.sourceId eq sourceId)
                }
                .singleOrNull()
                ?.let { grant ->
                    val grantedFor = partyRepository.find(grant[AuthorizationGrantTable.grantedFor])
                        .mapLeft { RepositoryReadError.UnexpectedError }.bind()
                    val grantedBy = partyRepository.find(grant[AuthorizationGrantTable.grantedBy])
                        .mapLeft { RepositoryReadError.UnexpectedError }.bind()
                    val grantedTo = partyRepository.find(grant[AuthorizationGrantTable.grantedTo])
                        .mapLeft { RepositoryReadError.UnexpectedError }.bind()
                    val scopes = findScopeIds(grant[AuthorizationGrantTable.id].value)
                        .mapLeft { RepositoryReadError.UnexpectedError }.bind()
                    val properties = grantPropertiesRepository.findBy(grant[AuthorizationGrantTable.id].value)
                    grant.toAuthorizationGrant(
                        grantedBy = grantedBy,
                        grantedFor = grantedFor,
                        grantedTo = grantedTo,
                        scopeIds = scopes,
                        properties = properties
                    )
                }
        }

    fun findScopeIds(grantId: UUID): Either<RepositoryReadError, List<UUID>> = either {
        AuthorizationGrantTable
            .selectAll()
            .where { AuthorizationGrantTable.id eq grantId }
            .singleOrNull() ?: run {
            logger.error("Grant not found")
            raise(RepositoryReadError.NotFoundError)
        }

        (AuthorizationGrantScopeTable innerJoin AuthorizationScopeTable)
            .selectAll()
            .where { AuthorizationGrantScopeTable.authorizationGrantId eq grantId }
            .map { row -> row[AuthorizationScopeTable.id].value }
    }

    override suspend fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>> =
        transactionContext<RepositoryReadError, List<AuthorizationScope>>(
            "grant_repo_find_scopes",
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
        transactionContext("grant_repo_insert", { RepositoryWriteError.UnexpectedError }) {
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
        transactionContext<RepositoryError, AuthorizationGrant>("grant_repo_update", { RepositoryWriteError.UnexpectedError }) {
            val rowsUpdated =
                AuthorizationGrantTable.update(
                    where = { AuthorizationGrantTable.id eq grantId }
                ) {
                    it[grantStatus] = newStatus
                    it[updatedAt] = currentTimeUtc()
                }

            if (rowsUpdated == 0) raise(RepositoryWriteError.UnexpectedError)

            findInternalGrant(grantId)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()
        }

    private suspend fun findInternalGrant(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant> =
        either {
            val grant = AuthorizationGrantTable
                .selectAll()
                .where { AuthorizationGrantTable.id eq grantId }
                .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

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

            val properties = grantPropertiesRepository.findBy(grantId = grant[AuthorizationGrantTable.id].value)

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
