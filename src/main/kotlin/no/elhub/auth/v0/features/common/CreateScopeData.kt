package no.elhub.auth.v0.features.common

import no.elhub.auth.v0.features.grants.AuthorizationScope

data class CreateScopeData(
    val authorizedResourceType: AuthorizationScope.AuthorizationResource,
    val authorizedResourceId: String,
    val permissionType: AuthorizationScope.PermissionType,
)
