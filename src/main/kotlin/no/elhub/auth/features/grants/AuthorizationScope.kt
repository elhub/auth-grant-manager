package no.elhub.auth.features.grants

import no.elhub.auth.features.common.currentTimeWithTimeZone
import java.time.OffsetDateTime

data class AuthorizationScope(
    val id: Long,
    val authorizedResourceType: ElhubResource,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    val createdAt: OffsetDateTime
) {
    companion object {
        fun create(
            id: Long,
            authorizationResourceType: ElhubResource,
            authorizedResourceId: String,
            permissionType: PermissionType,
        ): AuthorizationScope =
            AuthorizationScope(
                id = id,
                authorizedResourceType = authorizationResourceType,
                authorizedResourceId = authorizedResourceId,
                permissionType = permissionType,
                createdAt = currentTimeWithTimeZone()
            )
    }

    enum class ElhubResource {
        MeteringPoint,
        Organization,
        OrganizationEntity,
        Person,
        System
    }

    enum class PermissionType {
        ChangeOfSupplier,
        MoveIn,
        FullDelegation,
        ReadAccess
    }
}
