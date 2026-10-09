package no.elhub.auth.v1.domain

import no.elhub.auth.v1.InputError
import kotlin.time.Instant

data class RequestedAuthorizationScope(
    val resourceType: ResourceType,
    val constraints: List<AuthorizationScopeConstraint>,
)

data class AuthorizationScopeConstraint(
    val constraintKind: AuthorizationScopeConstraintKind,
    val attribute: AuthorizationScopeConstraintAttribute,
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

enum class AuthorizationScopeConstraintAttribute(val apiName: String) {
    MeteringPointId("meteringPoint.id"),
    ValidFrom("validFrom");

    companion object {
        fun fromApiName(apiName: String): AuthorizationScopeConstraintAttribute =
            entries.find { it.apiName == apiName }
                ?: throw InputError.InvalidFieldValueError("Unsupported scope constraint attribute: $apiName")
    }
}

enum class PermissionCapability {
    Read,
    Write,
}

enum class ResourceType {
    MeteringPointContract,
}

sealed interface ResourceConstraint {
    data class MeteringPoints(
        val ids: Set<MeteringPointId>,
    ) : ResourceConstraint {
        init {
            require(ids.isNotEmpty()) { "At least one metering-point ID is required" }
        }
    }

    data class ValidFrom(
        val value: Instant,
    ) : ResourceConstraint
}
