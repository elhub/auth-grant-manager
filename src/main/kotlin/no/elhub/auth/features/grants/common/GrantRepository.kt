package no.elhub.auth.features.grants.common

import arrow.core.Either
import java.util.UUID
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.AuthorizationGrantProblem
import arrow.core.left
import arrow.core.right
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import kotlinx.datetime.toKotlinLocalDateTime
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.grants.AuthorizationParty
import no.elhub.auth.features.grants.AuthorizationResourceType
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.ElhubResource
import no.elhub.auth.features.grants.PermissionType
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp


interface GrantRepository {
    operator fun get(grantId: UUID): Either<AuthorizationGrantProblem, GrantWithParties>
    fun getScopes(grantId: UUID): Either<AuthorizationGrantProblem, List<AuthorizationScope>>
    fun all(): Either<AuthorizationGrantProblem, GrantsWithParties>

    data class GrantsWithParties(
        val grants: List<AuthorizationGrant>,
        val parties: Map<Long, AuthorizationParty>
    )

    data class GrantWithParties(
        val grant: AuthorizationGrant?,
        val parties: Map<Long, AuthorizationParty>
    )

}

class ExposedGrantRepository : GrantRepository {

    private val logger = LoggerFactory.getLogger(ExposedGrantRepository::class.java)


    private fun mapRowToParty(row: ResultRow): AuthorizationParty =
        AuthorizationParty(
            id = row[AuthorizationPartyTable.id].value,
            type = ElhubResource.valueOf(row[AuthorizationPartyTable.type].toString()),
            descriptor = row[AuthorizationPartyTable.descriptor],
            createdAt = Instant.parse(row[AuthorizationPartyTable.createdAt].toString())
        )

    override fun all(): Either<AuthorizationGrantProblem, GrantRepository.GrantsWithParties> = try {
        transaction {
            // fetch all grants
            val grants = AuthorizationGrantTable
                .selectAll()
                .toList()
                .map { it.toAuthorizationGrant() }

            // collect all unique party IDs reference by the grants
            val partyIds = grants.flatMap { listOf(it.grantedFor, it.grantedBy, it.grantedTo) }.toSet()

            // fetch all parties in one query and map by ID
            val parties = AuthorizationPartyTable
                .selectAll()
                .where { AuthorizationPartyTable.id inList partyIds }
                .associate { row ->
                    val party = mapRowToParty(row)
                    party.id to party
                }

            GrantRepository.GrantsWithParties(grants, parties)
        }.right()
    } catch (sqlEx: SQLException) {
        logger.error("SQL error occurred during fetch all grants and parties: ${sqlEx.message}")
        AuthorizationGrantProblem.DataBaseError.left()
    } catch (exp: Exception) {
        logger.error("Unknown error occurred during fetch all grants and parties: ${exp.message}")
        AuthorizationGrantProblem.UnexpectedError.left()
    }

    override fun get(grantId: UUID): Either<AuthorizationGrantProblem, GrantRepository.GrantWithParties> =
        try {
            val result = transaction {
                // fetch grant
                val grant = AuthorizationGrantTable
                    .selectAll()
                    .where { AuthorizationGrantTable.id eq grantId }
                    .singleOrNull()?.toAuthorizationGrant()

                if (grant == null) {
                    null // signal NotFoundError after transaction
                } else {
                    // fetch all parties in one query and map by ID
                    val partyIds =
                        grant.let { listOf(grant.grantedFor, it.grantedBy, grant.grantedTo).toSet() } ?: emptySet()
                    val parties = AuthorizationPartyTable
                        .selectAll()
                        .where { AuthorizationPartyTable.id inList partyIds }
                        .associate { row ->
                            val party = mapRowToParty(row)
                            party.id to party
                        }

                    GrantRepository.GrantWithParties(grant, parties)
                }
            }
            result?.right() ?: AuthorizationGrantProblem.NotFoundError.left()
        } catch (sqlEx: SQLException) {
            logger.error("SQL error occurred during fetch grant by id': ${sqlEx.message}")
            AuthorizationGrantProblem.DataBaseError.left()
        } catch (exp: Exception) {
            logger.error("Unknown error occurred during fetch grant by id: ${exp.message}")
            AuthorizationGrantProblem.UnexpectedError.left()
        }

    override fun getScopes(grantId: UUID): Either<AuthorizationGrantProblem, List<AuthorizationScope>> = try {
        transaction {
            // check if grant exist
            AuthorizationGrantTable
                .selectAll()
                .where { AuthorizationGrantTable.id eq grantId }
                .singleOrNull()
                ?.let {
                    // fetch all related scopes via join if grant exists
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
                }
        }?.right() ?: AuthorizationGrantProblem.NotFoundError.left()
    } catch (sqlEx: SQLException) {
        logger.error("SQL error occurred during fetch scope by id': ${sqlEx.message}")
        AuthorizationGrantProblem.DataBaseError.left()
    } catch (exp: Exception) {
        logger.error("Unknown error occurred during fetch scope by id: ${exp.message}")
        AuthorizationGrantProblem.UnexpectedError.left()
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

fun ResultRow.toAuthorizationGrant() = AuthorizationGrant(
    id = this[AuthorizationGrantTable.id].toString(),
    grantStatus = this[AuthorizationGrantTable.grantStatus],
    grantedFor = this[AuthorizationGrantTable.grantedFor],
    grantedBy = this[AuthorizationGrantTable.grantedBy],
    grantedTo = this[AuthorizationGrantTable.grantedTo],
    grantedAt = this[AuthorizationGrantTable.grantedAt].toKotlinLocalDateTime(),
    validFrom = this[AuthorizationGrantTable.validFrom].toKotlinLocalDateTime(),
    validTo = this[AuthorizationGrantTable.validTo].toKotlinLocalDateTime(),
)
