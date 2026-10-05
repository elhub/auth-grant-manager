package no.elhub.auth.v1.domain

data class RequestedAuthorizationScope(
    val resourceType: ResourceType,
    val constraints: List<AuthorizationScopeConstraint>,
)

data class AuthorizationScopeConstraint(
    val constraintKind: AuthorizationScopeConstraintKind,
    val attribute: String,
    val value: ResourceConstraint,

)

data class GrantedAuthorizationScope(
    val capability: PermissionCapability,
    val resourceType: ResourceType,
    val constraints: List<AuthorizationScopeConstraint>,
)

enum class AuthorizationScopeConstraintKind {
    AppliesTo,
    AllowedChanges,
}

enum class PermissionCapability {
    Read,
    Write,
}

enum class ResourceType {
    MeteringPoint,
}

sealed interface ResourceConstraint {
    data class MeteringPoints(
        val ids: Set<MeteringPointId>,
    ) : ResourceConstraint {
        init {
            require(ids.isNotEmpty()) { "At least one metering-point ID is required" }
        }
    }
}
