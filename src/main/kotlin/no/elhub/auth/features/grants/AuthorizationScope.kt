package no.elhub.auth.features.grants

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AuthorizationScope(
    val id: Long,
    val authorizedResourceType: ElhubResource,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    val createdAt: Instant
)

@Serializable
enum class ElhubResource {
    MeteringPoint,
    Organization,
    OrganizationEntity,
    Person,
    System
}

@Serializable
enum class PermissionType {
    ChangeOfSupplier,
    FullDelegation,
    ReadAccess
}
