package no.elhub.auth.features.grants

import kotlin.time.Instant

data class AuthorizationScope(
    val id: Long,
    val authorizedResourceType: AuthorizationResourceType,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    val createdAt: Instant
)

enum class AuthorizationResourceType {
    MeteringPoint,
    Organization,
    Person
}

enum class PermissionType {
    ChangeOfSupplier,
    FullDelegation,
    ReadAccess
}
