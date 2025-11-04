package no.elhub.auth.features.common

enum class ElhubResourceType {
    MeteringPoint,
    Organization,
    OrganizationEntity,
    Person,
    System
}

data class AuthorizationParty(val resourceId: String, val type: ElhubResourceType)
