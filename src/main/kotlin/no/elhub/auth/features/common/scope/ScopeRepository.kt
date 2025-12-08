package no.elhub.auth.features.common.scope

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.PGEnum
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll

interface ScopeRepository {
    fun findOrCreateScope(scope: CreateAuthorizationScope): Either<RepositoryWriteError, Long>
}

class ExposedScopeRepository : ScopeRepository {

    override fun findOrCreateScope(scope: CreateAuthorizationScope): Either<RepositoryWriteError, Long> =
        either<RepositoryWriteError, Long> {
            val existingScope = AuthorizationScopeTable
                .selectAll()
                .where {
                    (AuthorizationScopeTable.authorizedResourceType eq scope.authorizedResourceType) and
                        (AuthorizationScopeTable.authorizedResourceId eq scope.authorizedResourceId) and
                        (AuthorizationScopeTable.permissionType eq scope.permissionType)
                }
                .singleOrNull()

            if (existingScope != null) {
                existingScope[AuthorizationScopeTable.id].value
            } else {
                AuthorizationScopeTable.insertAndGetId {
                    it[authorizedResourceType] = scope.authorizedResourceType
                    it[authorizedResourceId] = scope.authorizedResourceId
                    it[permissionType] = scope.permissionType
                }.value
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }
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
