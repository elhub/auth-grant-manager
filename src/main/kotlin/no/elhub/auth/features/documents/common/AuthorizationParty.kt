package no.elhub.auth.features.documents.common

import java.time.Instant
import java.util.UUID

data class AuthorizationParty(
    val id: UUID,
    val type: ElhubResource,
    val resourceId: String,
    val createdAt: Instant

)

enum class ElhubResource {
    MeteringPoint,
    Organization,
    OrganizationEntity,
    Person,
    System
}
