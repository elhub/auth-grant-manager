package no.elhub.auth.domain.shared

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import no.elhub.auth.data.exposed.tables.AuthorizationGrantTable
import no.elhub.auth.data.exposed.tables.AuthorizationScopeTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

@Serializable
data class AuthorizationScope(
    val id: Long,
    val authorizedResourceType: AuthorizationResourceType,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    val createdAt: Instant
)

@Serializable
enum class AuthorizationResourceType {
    MeteringPoint,
    Organization,
    Person
}

@Serializable
enum class PermissionType {
    ChangeOfSupplier,
    FullDelegation,
    ReadAccess
}


object AuthorizationGrantScopeTable : Table("auth.authorization_grant_scope") {
    val authorizationGrantId = uuid("authorization_grant_id")
        .references(AuthorizationGrantTable.id, onDelete = ReferenceOption.CASCADE)
    private val authorizationScopeId = long("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    override val primaryKey = PrimaryKey(authorizationGrantId, authorizationScopeId)
}


