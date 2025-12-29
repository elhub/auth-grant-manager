package no.elhub.auth.features.common

import no.elhub.auth.features.grants.AuthorizationScope

data class CreateScopeData(
    val authorizedResourceType: AuthorizationScope.ElhubResource,
    val authorizedResourceId: String,
    val permissionType: AuthorizationScope.PermissionType,
)
