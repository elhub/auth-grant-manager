package no.elhub.auth.features.grants.common

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.AuthorizationPartyRecord
import no.elhub.auth.features.common.party.AuthorizationPartyTable
import no.elhub.auth.features.common.party.PartyRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.SourceType
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.AuthorizationScope
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

interface GrantRepository {
    fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant>
    fun findBySource(sourceType: SourceType, sourceId: UUID): Either<RepositoryReadError, AuthorizationGrant?>
    fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>>
    fun findAll(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationGrant>>
    fun insert(grant: AuthorizationGrant, scopeIds: List<UUID>): Either<RepositoryWriteError, AuthorizationGrant>
    fun update(grantId: UUID, newStatus: Status): Either<RepositoryError, AuthorizationGrant>
}

class ExposedGrantRepository(
    private val partyRepository: PartyRepository
) : GrantRepository {

    private val logger = LoggerFactory.getLogger(ExposedGrantRepository::class.java)

    override fun findAll(party: AuthorizationParty): Either<RepositoryReadError, List<AuthorizationGrant>> = either {
        val partyId = partyRepository.findOrInsert(type = party.type, resourceId = party.resourceId)
            .mapLeft { RepositoryReadError.UnexpectedError }
            .bind()
            .id

        val grantRows = AuthorizationGrantTable
            .selectAll()
            .where {
                (AuthorizationGrantTable.grantedTo eq partyId) or (AuthorizationGrantTable.grantedFor eq partyId)
            }
            .toList()

        if (grantRows.isEmpty()) {
            return@either emptyList()
        }

        val partyIds: List<UUID> = grantRows
            .flatMap { g ->
                listOf(
                    g[AuthorizationGrantTable.grantedFor],
                    g[AuthorizationGrantTable.grantedBy],
                    g[AuthorizationGrantTable.grantedTo]
                )
            }
            .toSet()
            .toList()

        val partiesById: Map<UUID, AuthorizationPartyRecord> = partyIds.associateWith { partyId ->
            val party = partyRepository.find(partyId)
                .mapLeft { RepositoryReadError.UnexpectedError }
                .bind()
            party
        }

        grantRows.map { g ->
            val grantedFor = partiesById[g[AuthorizationGrantTable.grantedFor]]
                ?: raise(RepositoryReadError.NotFoundError)
            val grantedBy = partiesById[g[AuthorizationGrantTable.grantedBy]]
                ?: raise(RepositoryReadError.NotFoundError)
            val grantedTo = partiesById[g[AuthorizationGrantTable.grantedTo]]
                ?: raise(RepositoryReadError.NotFoundError)

            g.toAuthorizationGrant(
                grantedBy = grantedBy,
                grantedFor = grantedFor,
                grantedTo = grantedTo
            )
        }
    }

    override fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant> = either {
        val grant = AuthorizationGrantTable
            .selectAll()
            .where { AuthorizationGrantTable.id eq grantId }
            .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

        val grantedFor = partyRepository.find(grant[AuthorizationGrantTable.grantedFor])
            .getOrElse { error("Failed to get grantedFor: $it") }
        val grantedBy = partyRepository.find(grant[AuthorizationGrantTable.grantedBy])
            .getOrElse { error("Failed to get grantedBy: $it") }
        val grantedTo = partyRepository.find(grant[AuthorizationGrantTable.grantedTo])
            .getOrElse { error("Failed to get grantedTo: $it") }

        grant.toAuthorizationGrant(
            grantedBy = grantedBy,
            grantedFor = grantedFor,
            grantedTo = grantedTo
        )
    }

    override fun findBySource(
        sourceType: SourceType,
        sourceId: UUID
    ): Either<RepositoryReadError, AuthorizationGrant?> =
        either {
            val grant = AuthorizationGrantTable
                .selectAll()
                .where {
                    (AuthorizationGrantTable.sourceType eq sourceType) and (AuthorizationGrantTable.sourceId eq sourceId)
                }
                .singleOrNull()
                ?: return@either null

            val grantedFor = partyRepository.find(grant[AuthorizationGrantTable.grantedFor]).bind()
            val grantedBy = partyRepository.find(grant[AuthorizationGrantTable.grantedBy]).bind()
            val grantedTo = partyRepository.find(grant[AuthorizationGrantTable.grantedTo]).bind()

            grant.toAuthorizationGrant(
                grantedBy = grantedBy,
                grantedFor = grantedFor,
                grantedTo = grantedTo
            )
        }

    override fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>> = either {
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

    override fun insert(
        grant: AuthorizationGrant,
        scopeIds: List<UUID>,
    ): Either<RepositoryWriteError, AuthorizationGrant> =
        either {
            val grantedByParty =
                partyRepository
                    .findOrInsert(grant.grantedBy.type, grant.grantedBy.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

            val grantedForParty =
                partyRepository
                    .findOrInsert(grant.grantedFor.type, grant.grantedFor.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

            val grantedToParty =
                partyRepository
                    .findOrInsert(grant.grantedTo.type, grant.grantedTo.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

            val authorizationGrant =
                AuthorizationGrantTable
                    .insertReturning {
                        it[id] = grant.id
                        it[grantStatus] = grant.grantStatus
                        it[grantedBy] = grantedByParty.id
                        it[grantedFor] = grantedForParty.id
                        it[grantedTo] = grantedToParty.id
                        it[grantedAt] = grant.grantedAt
                        it[validFrom] = grant.validFrom
                        it[validTo] = grant.validTo
                        it[sourceType] = grant.sourceType
                        it[sourceId] = grant.sourceId
                    }.map {
                        it.toAuthorizationGrant(
                            grantedBy = grantedByParty,
                            grantedFor = grantedForParty,
                            grantedTo = grantedToParty
                        )
                    }.single()

            if (scopeIds.isNotEmpty()) {
                AuthorizationGrantScopeTable.batchInsert(scopeIds) { scopeId ->
                    this[AuthorizationGrantScopeTable.authorizationGrantId] = authorizationGrant.id
                    this[AuthorizationGrantScopeTable.authorizationScopeId] = scopeId
                }
            }

            authorizationGrant
        }.mapLeft { RepositoryWriteError.UnexpectedError }

    override fun update(grantId: UUID, newStatus: Status): Either<RepositoryError, AuthorizationGrant> = either {
        val rowsUpdated = AuthorizationGrantTable.update(
            where = { AuthorizationGrantTable.id eq grantId }
        ) {
            it[grantStatus] = newStatus
            // TODO consider add a updatedAt field
        }

        if (rowsUpdated == 0) {
            raise(RepositoryWriteError.UnexpectedError)
        }

        val grant = AuthorizationGrantTable
            .selectAll()
            .where { AuthorizationGrantTable.id eq grantId }
            .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

        findInternalGrant(grant)
            .mapLeft { readError ->
                when (readError) {
                    is RepositoryReadError.NotFoundError, RepositoryReadError.UnexpectedError -> RepositoryReadError.UnexpectedError
                }
            }.bind()
    }

    // TODO use this method in find() and findAll() as well to avoid duplicates
    private fun findInternalGrant(grant: ResultRow): Either<RepositoryReadError, AuthorizationGrant> =
        either {
            val grantedByDbId = grant[AuthorizationGrantTable.grantedBy]
            val grantedForDbId = grant[AuthorizationGrantTable.grantedFor]
            val grantedToDbId = grant[AuthorizationGrantTable.grantedTo]

            val grantedByParty =
                partyRepository
                    .find(grantedByDbId)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()

            val grantedForParty =
                partyRepository
                    .find(grantedForDbId)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()

            val grantedToParty =
                partyRepository
                    .find(grantedToDbId)
                    .mapLeft { RepositoryReadError.UnexpectedError }
                    .bind()

            grant.toAuthorizationGrant(
                grantedBy = grantedByParty,
                grantedFor = grantedForParty,
                grantedTo = grantedToParty
            )
        }
}

object AuthorizationGrantScopeTable : Table("auth.authorization_grant_scope") {
    val authorizationGrantId = uuid("authorization_grant_id")
        .references(AuthorizationGrantTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = uuid("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
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
    val grantedFor = uuid("granted_for").references(AuthorizationPartyTable.id)
    val grantedBy = uuid("granted_by").references(AuthorizationPartyTable.id)
    val grantedTo = uuid("granted_to").references(AuthorizationPartyTable.id)
    val grantedAt = timestampWithTimeZone("granted_at").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
    val validFrom = timestampWithTimeZone("valid_from").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
    val validTo = timestampWithTimeZone("valid_to").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
    val sourceType =
        customEnumeration(
            name = "source_type",
            sql = "auth.grant_source_type",
            fromDb = { value -> SourceType.valueOf(value as String) },
            toDb = { PGEnum("grant_source_type", it) },
        )
    val sourceId = uuid("source_id")
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
        sql = "permission_type",
        fromDb = { AuthorizationScope.PermissionType.valueOf(it as String) },
        toDb = { PGEnum("permission_type", it) }
    )
    val createdAt = timestampWithTimeZone("created_at").default(OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
}

fun ResultRow.toAuthorizationGrant(
    grantedBy: AuthorizationPartyRecord,
    grantedFor: AuthorizationPartyRecord,
    grantedTo: AuthorizationPartyRecord
) = AuthorizationGrant(
    id = this[AuthorizationGrantTable.id].value,
    grantStatus = this[AuthorizationGrantTable.grantStatus],
    grantedFor = grantedFor.toAuthorizationParty(),
    grantedBy = grantedBy.toAuthorizationParty(),
    grantedTo = grantedTo.toAuthorizationParty(),
    grantedAt = this[AuthorizationGrantTable.grantedAt],
    validFrom = this[AuthorizationGrantTable.validFrom],
    validTo = this[AuthorizationGrantTable.validTo],
    sourceType = this[AuthorizationGrantTable.sourceType],
    sourceId = this[AuthorizationGrantTable.sourceId],
)

fun AuthorizationPartyRecord.toAuthorizationParty() = AuthorizationParty(resourceId = this.resourceId, type = this.type)
