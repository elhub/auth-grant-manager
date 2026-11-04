package no.elhub.auth.common.auth

import kotlinx.serialization.Serializable

enum class PartyType {
    Organization,
    OrganizationEntity,
    Person,
    System
}

@Serializable
data class AuthorizationParty(
    val id: String,
    val type: PartyType
)
