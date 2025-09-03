package no.elhub.auth.features.grants.common

import arrow.core.Either
import arrow.core.raise.either
import java.util.UUID
import no.elhub.auth.features.grants.AuthorizationGrant
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import kotlinx.datetime.toKotlinLocalDateTime
import no.elhub.auth.features.grants.AuthorizationGrant.Status
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationParty
import no.elhub.auth.features.grants.AuthorizationResourceType
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.ElhubResource
import no.elhub.auth.features.grants.PermissionType
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.orWhere


interface GrantRepository {
    fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant>
    fun findScopes(grantId: UUID): Either<RepositoryReadError, List<AuthorizationScope>>
    fun findAll(): Either<RepositoryReadError, List<AuthorizationGrant>>
}

class ExposedGrantRepository : GrantRepository {

    private val logger = LoggerFactory.getLogger(ExposedGrantRepository::class.java)


    override fun findAll(): Either<RepositoryReadError, List<AuthorizationGrant>> = either {
        transaction {
            AuthorizationGrantTable
                .join(
                    AuthorizationPartyTable,
                    JoinType.LEFT,
                    onColumn = AuthorizationGrantTable.grantedBy,
                    otherColumn = AuthorizationPartyTable.id
                )
                .join(
                    AuthorizationPartyTable,
                    JoinType.LEFT,
                    onColumn = AuthorizationGrantTable.grantedFor,
                    otherColumn = AuthorizationPartyTable.id
                )
                .join(
                    AuthorizationPartyTable,
                    JoinType.LEFT,
                    onColumn = AuthorizationGrantTable.grantedTo,
                    otherColumn = AuthorizationPartyTable.id
                )
                .selectAll()
                .map { it.toAuthorizationGrant() }
        }
    }

    override fun find(grantId: UUID): Either<RepositoryReadError, AuthorizationGrant> = either {
        transaction {
            AuthorizationGrantTable
                .join(
                    AuthorizationPartyTable,
                    JoinType.LEFT,
                    onColumn = AuthorizationGrantTable.grantedBy,
                    otherColumn = AuthorizationPartyTable.id
                )
                .join(
                    AuthorizationPartyTable,
                    JoinType.LEFT,
                    onColumn = AuthorizationGrantTable.grantedFor,
                    otherColumn = AuthorizationPartyTable.id
                )
                .join(
                    AuthorizationPartyTable,
                    JoinType.LEFT,
                    onColumn = AuthorizationGrantTable.grantedTo,
                    otherColumn = AuthorizationPartyTable.id
                )
                .selectAll()
                .singleOrNull { AuthorizationGrantTable.id == grantId }
                ?.toAuthorizationGrant()
                ?: run {
                    logger.error("Authorization grant not found for id=$grantId")
                    raise(RepositoryReadError.NotFoundError)
                }
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

fun ResultRow.toAuthorizationGrant() = AuthorizationGrant(
    id = this[AuthorizationGrantTable.id].toString(),
    grantStatus = this[AuthorizationGrantTable.grantStatus],
    grantedFor = this.toAuthorizationParty(),
    grantedBy = this.toAuthorizationParty(),
    grantedTo = this.toAuthorizationParty(),
    grantedAt = this[AuthorizationGrantTable.grantedAt].toKotlinLocalDateTime(),
    validFrom = this[AuthorizationGrantTable.validFrom].toKotlinLocalDateTime(),
    validTo = this[AuthorizationGrantTable.validTo].toKotlinLocalDateTime(),
)
