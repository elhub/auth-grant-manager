package no.elhub.auth.v1.domain

data class AuthorizationScope(
    val capability: PermissionCapability,
    val resourceType: ResourceType,
    val appliesTo: List<ResourceConstraint>,
) {
    init {
        require(appliesTo.isNotEmpty()) { "At least one resource constraint is required" }
    }
}

enum class PermissionCapability {
    READ,
    WRITE,
}

enum class ResourceType {
    MeteringPoint,
}
