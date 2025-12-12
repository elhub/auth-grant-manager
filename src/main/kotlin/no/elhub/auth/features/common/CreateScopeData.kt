package no.elhub.auth.features.common

import no.elhub.auth.features.grants.ElhubResource
import no.elhub.auth.features.grants.PermissionType

data class CreateScopeData(
    val authorizedResourceType: ElhubResource,
    val authorizedResourceId: String,
    val permissionType: PermissionType,
)
