package no.elhub.auth.v1.domain

import kotlin.time.Instant

data class RequestedAuthorizationScope(
    val resourceType: ResourceType,
    val constraints: List<AuthorizationScopeConstraint>,
)

data class AuthorizationScopeConstraint(
    val constraintKind: AuthorizationScopeConstraintKind,
    val attribute: AuthorizationScopeConstraintAttribute,
    val value: ResourceConstraint,
) {
    init {
        if (attribute != value.attribute) {
            throw DomainError.InvalidScopeConstraint(
                "Scope constraint attribute '${attribute.apiName}' does not match value for '${value.attribute.apiName}'",
            )
        }
    }
}

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
    ContractType("type"),
    ValidFrom("validFrom");

    companion object {
        fun fromApiName(apiName: String): AuthorizationScopeConstraintAttribute =
            entries.find { it.apiName == apiName }
                ?: throw DomainError.UnsupportedScopeConstraintAttribute(apiName)
    }
}

enum class PermissionCapability {
    Read,
    Write,
}

enum class ResourceType {
    MeteringPointContract,
}

enum class MeteringPointContractType {
    EndUser,
    EnergySupplier,
}

sealed interface ResourceConstraint {
    val attribute: AuthorizationScopeConstraintAttribute

    data class ValidFrom(
        val value: Instant,
    ) : ResourceConstraint {
        override val attribute = AuthorizationScopeConstraintAttribute.ValidFrom
    }

    data class MeteringPoints(
        val ids: Set<MeteringPointId>,
    ) : ResourceConstraint {
        override val attribute = AuthorizationScopeConstraintAttribute.MeteringPointId

        init {
            require(ids.isNotEmpty()) { "At least one metering-point ID is required" }
        }
    }

    data class ContractTypes(
        val types: Set<MeteringPointContractType>,
    ) : ResourceConstraint {
        override val attribute = AuthorizationScopeConstraintAttribute.ContractType

        init {
            require(types.isNotEmpty()) { "At least one contract type is required" }
        }
    }
}
