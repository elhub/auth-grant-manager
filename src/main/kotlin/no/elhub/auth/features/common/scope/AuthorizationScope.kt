package no.elhub.auth.features.common.scope

import kotlinx.serialization.Serializable

@Serializable
data class CreateAuthorizationScope(
    val authorizedResourceType: ElhubResource,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
)

@Serializable
data class AuthorizationScope(
    val id: Long,
    val authorizedResourceType: ElhubResource,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    val createdAt: String
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
