package no.elhub.auth.v0.features.grants

import no.elhub.auth.v0.features.common.currentTimeUtc
import java.time.OffsetDateTime
import java.util.UUID

data class AuthorizationScope(
    val id: UUID,
    val authorizedResourceType: AuthorizationResource,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
    val createdAt: OffsetDateTime
) {
    companion object {
        fun create(
            authorizationResourceType: AuthorizationResource,
            authorizedResourceId: String,
            permissionType: PermissionType,
        ): AuthorizationScope =
            AuthorizationScope(
                id = UUID.randomUUID(),
                authorizedResourceType = authorizationResourceType,
                authorizedResourceId = authorizedResourceId,
                permissionType = permissionType,
                createdAt = currentTimeUtc()
            )
    }

    enum class AuthorizationResource {
        MeteringPoint,
        Organization,
        OrganizationEntity,
        Person,
        System
    }

    enum class PermissionType {
        ChangeOfBalanceSupplierForPerson,
        MoveInAndChangeOfBalanceSupplierForPerson
    }
}
