package no.elhub.auth.features.common

enum class PartyType {
    Organization,
    OrganizationEntity,
    Person
}

data class AuthorizationParty(val resourceId: String, val type: PartyType)
