package no.elhub.auth.data.persist.tables

import no.elhub.auth.domain.shared.AuthorizationResourceType
import no.elhub.auth.domain.shared.PermissionType
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

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
