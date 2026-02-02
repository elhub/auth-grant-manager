package no.elhub.auth.features.common.party

import kotlinx.serialization.Serializable

enum class PartyType {
    Organization,
    OrganizationEntity,
    Person,
    System,
}

@Serializable
data class AuthorizationParty(
    val resourceId: String,
    val type: PartyType,
)

@Serializable
data class PartyIdentifier(
    val idType: PartyIdentifierType,
    val idValue: String,
)

@Serializable
enum class PartyIdentifierType {
    NationalIdentityNumber,
    OrganizationNumber,
    GlobalLocationNumber,
}
