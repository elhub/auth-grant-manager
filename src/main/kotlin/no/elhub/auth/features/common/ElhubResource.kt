package no.elhub.auth.features.common

enum class ElhubResourceType {
    MeteringPoint,
    Organization,
    OrganizationEntity,
    Person,
    System
}

data class ElhubResource(val resourceId: String, val type: ElhubResourceType)
