package no.elhub.auth.features.grants

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
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
