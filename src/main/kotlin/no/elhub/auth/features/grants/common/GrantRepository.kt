package no.elhub.auth.features.grants.common

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinLocalDateTime
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.AuthorizationPartyRecord
import no.elhub.auth.features.common.AuthorizationPartyTable
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.PartyRepository
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.common.RepositoryWriteError
import no.elhub.auth.features.common.toAuthorizationParty
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.ElhubResource
import no.elhub.auth.features.grants.PermissionType
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

interface GrantRepository {
    fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant>
    fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>>
    fun findAll(): Either<RepositoryReadError, List<AuthorizationGrant>>
    fun insert(
        grantedFor: AuthorizationParty,
        grantedBy: AuthorizationParty,
        grantedTo: AuthorizationParty,
        scopes: List<AuthorizationScope>
    ): Either<RepositoryWriteError, AuthorizationGrant>
}

class ExposedGrantRepository(
    private val partyRepository: PartyRepository
) : GrantRepository {

    private val logger = LoggerFactory.getLogger(ExposedGrantRepository::class.java)

    override fun findAll(): Either<RepositoryReadError, List<AuthorizationGrant>> = either {
        transaction {
            // 1) Get all grants
            val grantRows = AuthorizationGrantTable
                .selectAll()
                .toList()

            if (grantRows.isEmpty()) {
                return@transaction emptyList<AuthorizationGrant>()
            }

            // 2) Collect all distinct party ids we need (for/by/to)
            // TODO use authorization party repo here !!
            val partyIds: List<UUID> = grantRows
                .flatMap { g ->
                    listOf(
                        g[AuthorizationGrantTable.grantedFor],
                        g[AuthorizationGrantTable.grantedBy],
                        g[AuthorizationGrantTable.grantedTo]
                    )
                }
                .toSet() // distinct
                .toList()

            // 3) Fetch all parties in ONE query and index by id
            val partiesById: Map<UUID, AuthorizationPartyRecord> =
                AuthorizationPartyTable
                    .selectAll()
                    .where { AuthorizationPartyTable.id inList partyIds }
                    .associate { row ->
                        val party = row.toAuthorizationParty()
                        party.id to party
                    }

            // 4) Map each grant row -> domain object using the pre-fetched parties
            grantRows.map { g ->
                val grantedFor = partiesById[g[AuthorizationGrantTable.grantedFor]]
                    ?: raise(RepositoryReadError.NotFoundError)
                val grantedBy = partiesById[g[AuthorizationGrantTable.grantedBy]]
                    ?: raise(RepositoryReadError.NotFoundError)
                val grantedTo = partiesById[g[AuthorizationGrantTable.grantedTo]]
                    ?: raise(RepositoryReadError.NotFoundError)

                g.toAuthorizationGrant(grantedFor, grantedBy, grantedTo)
            }
        }
    }

    override fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant> = either {
        transaction {
            val grant = AuthorizationGrantTable
                .selectAll()
                .where { AuthorizationGrantTable.id eq grantId }
                .singleOrNull() ?: raise(RepositoryReadError.NotFoundError)

            val grantedFor = partyRepository.find(grant[AuthorizationGrantTable.grantedFor]).getOrElse { error("Failed to get grantedFor: $it") }
            val grantedBy = partyRepository.find(grant[AuthorizationGrantTable.grantedBy]).getOrElse { error("Failed to get grantedBy: $it") }
            val grantedTo = partyRepository.find(grant[AuthorizationGrantTable.grantedTo]).getOrElse { error("Failed to get grantedTo: $it") }

            grant.toAuthorizationGrant(grantedFor, grantedBy, grantedTo)
        }
    }

    override fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>> = either {
        transaction {
            val scopes = AuthorizationGrantTable
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
                                createdAt = Instant.parse(row[AuthorizationScopeTable.createdAt].toString())
                            )
                        }
                } ?: run {
                logger.error("Scope not found for authorization grant with id=$grantId")
                raise(RepositoryReadError.NotFoundError)
            }
            scopes
        }
    }
    override fun insert(
        grantedFor: AuthorizationParty,
        grantedBy: AuthorizationParty,
        grantedTo: AuthorizationParty,
        scopes: List<AuthorizationScope>
    ): Either<RepositoryWriteError, AuthorizationGrant> =
        either {
            transaction {
                val grantedForRecord = partyRepository.findOrInsert(grantedFor.type, grantedFor.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()
                val grantedByRecord = partyRepository.findOrInsert(grantedBy.type, grantedBy.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()
                val grantedToRecord = partyRepository.findOrInsert(grantedTo.type, grantedTo.resourceId)
                    .mapLeft { RepositoryWriteError.UnexpectedError }
                    .bind()

                val now = LocalDateTime.now()
                val grantId = UUID.randomUUID()
                val insertedGrant = AuthorizationGrantTable.insertReturning {
                    it[id] = grantId
                    it[grantStatus] = Status.Active
                    it[AuthorizationGrantTable.grantedFor] = grantedForRecord.id
                    it[AuthorizationGrantTable.grantedBy] = grantedByRecord.id
                    it[AuthorizationGrantTable.grantedTo] = grantedToRecord.id
                    it[grantedAt] = now
                    it[validFrom] = now
                    it[validTo] = now.plusYears(1)
                }.single()

                if (scopes.isNotEmpty()) {
                    AuthorizationGrantScopeTable.batchInsert(scopes) { scope ->
                        this[AuthorizationGrantScopeTable.authorizationGrantId] = grantId
                        this[AuthorizationGrantScopeTable.authorizationScopeId] = scope.id
                    }
                }

                insertedGrant.toAuthorizationGrant(grantedForRecord, grantedByRecord, grantedToRecord)
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }
}

object AuthorizationGrantScopeTable : Table("auth.authorization_grant_scope") {
    val authorizationGrantId = uuid("authorization_grant_id")
        .references(AuthorizationGrantTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = long("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    override val primaryKey = PrimaryKey(authorizationGrantId, authorizationScopeId)
}

object AuthorizationGrantTable : UUIDTable("authorization_grant") {
    val grantStatus =
        customEnumeration(
            name = "status",
            fromDb = { value -> Status.valueOf(value as String) },
            toDb = { PGEnum("authorization_grant_status", it) },
        )
    val grantedFor = uuid("granted_for").references(AuthorizationPartyTable.id)
    val grantedBy = uuid("granted_by").references(AuthorizationPartyTable.id)
    val grantedTo = uuid("granted_to").references(AuthorizationPartyTable.id)
    val grantedAt = datetime("granted_at").defaultExpression(CurrentDateTime)
    val validFrom = datetime("valid_from").defaultExpression(CurrentDateTime)
    val validTo = datetime("valid_to").defaultExpression(CurrentDateTime)
}

object AuthorizationScopeTable : LongIdTable(name = "auth.authorization_scope") {
    val authorizedResourceType = customEnumeration(
        name = "authorized_resource_type",
        sql = "authorization_resource",
        fromDb = { ElhubResource.valueOf(it as String) },
        toDb = { PGEnum("authorization_resource", it) }
    )
    val authorizedResourceId = varchar("authorized_resource_id", length = 64)
    val permissionType = customEnumeration(
        name = "permission_type",
        sql = "permission_type",
        fromDb = { PermissionType.valueOf(it as String) },
        toDb = { PGEnum("permission_type", it) }
    )
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
}

fun ResultRow.toAuthorizationGrant(grantedFor: AuthorizationPartyRecord, grantedBy: AuthorizationPartyRecord, grantedTo: AuthorizationPartyRecord) =
    AuthorizationGrant(
        id = this[AuthorizationGrantTable.id].toString(),
        grantStatus = this[AuthorizationGrantTable.grantStatus],
        grantedFor = grantedFor.toAuthorizationParty(),
        grantedBy = grantedBy.toAuthorizationParty(),
        grantedTo = grantedTo.toAuthorizationParty(),
        grantedAt = this[AuthorizationGrantTable.grantedAt].toKotlinLocalDateTime(),
        validFrom = this[AuthorizationGrantTable.validFrom].toKotlinLocalDateTime(),
        validTo = this[AuthorizationGrantTable.validTo].toKotlinLocalDateTime(),
    )

fun AuthorizationPartyRecord.toAuthorizationParty() = AuthorizationParty(resourceId = this.resourceId, type = this.type)
