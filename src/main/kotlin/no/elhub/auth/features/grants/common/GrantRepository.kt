package no.elhub.auth.features.grants.common

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.toKotlinLocalDateTime
import kotlin.time.Instant
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import no.elhub.auth.features.grants.AuthorizationParty
import no.elhub.auth.features.grants.AuthorizationResourceType
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.ElhubResource
import no.elhub.auth.features.grants.PermissionType
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

interface GrantRepository {
    fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant>
    fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>>
    fun findAll(): Either<RepositoryReadError, List<AuthorizationGrant>>
}

class ExposedGrantRepository : GrantRepository {

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
            val partyIds: List<Long> = grantRows
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
            val partiesById: Map<Long, AuthorizationParty> =
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

            val partyIds = listOf(
                grant[AuthorizationGrantTable.grantedFor],
                grant[AuthorizationGrantTable.grantedBy],
                grant[AuthorizationGrantTable.grantedTo]
            )

            val parties = AuthorizationPartyTable
                .selectAll()
                .where { AuthorizationPartyTable.id inList partyIds }
                .associate { row ->
                    val party = row.toAuthorizationParty()
                    party.id to party
                }

            val grantedFor =
                parties[grant[AuthorizationGrantTable.grantedFor]] ?: raise(RepositoryReadError.NotFoundError)
            val grantedBy =
                parties[grant[AuthorizationGrantTable.grantedBy]] ?: raise(RepositoryReadError.NotFoundError)
            val grantedTo =
                parties[grant[AuthorizationGrantTable.grantedTo]] ?: raise(RepositoryReadError.NotFoundError)
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
}

object AuthorizationGrantScopeTable : Table("auth.authorization_grant_scope") {
    val authorizationGrantId = uuid("authorization_grant_id")
        .references(AuthorizationGrantTable.id, onDelete = ReferenceOption.CASCADE)
    private val authorizationScopeId = long("authorization_scope_id")
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
    val grantedFor = long("granted_for").references(AuthorizationPartyTable.id)
    val grantedBy = long("granted_by").references(AuthorizationPartyTable.id)
    val grantedTo = long("granted_to").references(AuthorizationPartyTable.id)
    val grantedAt = datetime("granted_at").defaultExpression(CurrentDateTime)
    val validFrom = datetime("valid_from").defaultExpression(CurrentDateTime)
    val validTo = datetime("valid_to").defaultExpression(CurrentDateTime)
}

object AuthorizationScopeTable : LongIdTable(name = "auth.authorization_scope") {
    val authorizedResourceType = customEnumeration(
        name = "authorized_resource_type",
        sql = "authorization_resource",
        fromDb = { AuthorizationResourceType.valueOf(it as String) },
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

object AuthorizationPartyTable : LongIdTable("authorization_party") {
    val type =
        customEnumeration(
            name = "type",
            fromDb = { value -> ElhubResource.valueOf(value as String) },
            toDb = { PGEnum("elhub_resource", it) }
        )
    val descriptor = varchar("descriptor", length = 256)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
}

fun ResultRow.toAuthorizationParty() = AuthorizationParty(
    id = this[AuthorizationPartyTable.id].value,
    type = ElhubResource.valueOf(this[AuthorizationPartyTable.type].toString()),
    descriptor = this[AuthorizationPartyTable.descriptor],
    createdAt = Instant.parse(this[AuthorizationPartyTable.createdAt].toString())
)

fun ResultRow.toAuthorizationGrant(
    grantedFor: AuthorizationParty,
    grantedBy: AuthorizationParty,
    grantedTo: AuthorizationParty
) = AuthorizationGrant(
    id = this[AuthorizationGrantTable.id].toString(),
    grantStatus = this[AuthorizationGrantTable.grantStatus],
    grantedFor = grantedFor,
    grantedBy = grantedBy,
    grantedTo = grantedTo,
    grantedAt = this[AuthorizationGrantTable.grantedAt].toKotlinLocalDateTime(),
    validFrom = this[AuthorizationGrantTable.validFrom].toKotlinLocalDateTime(),
    validTo = this[AuthorizationGrantTable.validTo].toKotlinLocalDateTime(),
)
