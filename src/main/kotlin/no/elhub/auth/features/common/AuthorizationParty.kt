package no.elhub.auth.features.common

enum class AuthorizationPartyResourceType {
    Organization,
    OrganizationEntity,
    Person
}

data class AuthorizationParty(val resourceId: String, val type: AuthorizationPartyResourceType)
