package no.elhub.auth.model

import kotlinx.serialization.Serializable
import no.elhub.auth.features.utils.Serializers
import no.elhub.auth.model.AuthorizationDocument.AuthorizationDocuments
import no.elhub.auth.utils.PGEnum
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

@Serializable
data class AuthorizationScope(
    val id: Long,
    val authorizedResourceType: AuthorizationResourceType,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    @Serializable(with = Serializers.InstantSerializer::class)
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

object AuthorizationScopes : LongIdTable(name = "auth.authorization_scope") {
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
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object AuthorizationGrantScopes : Table("auth.authorization_grant_scope") {
    val authorizationGrantId = uuid("authorization_grant_id")
        .references(AuthorizationGrant.Entity.id, onDelete = ReferenceOption.CASCADE)
    private val authorizationScopeId = long("authorization_scope_id")
        .references(AuthorizationScopes.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(authorizationGrantId, authorizationScopeId)
}

object AuthorizationDocumentScopes : Table("auth.authorization_document_scope") {
    val authorizationDocumentId = uuid("authorization_document_id")
        .references(AuthorizationDocuments.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = long("authorization_scope_id")
        .references(AuthorizationScopes.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(authorizationDocumentId, authorizationScopeId)
}
