package no.elhub.auth.v1.features.common.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantType

@Serializable
data class ResponseResourceIdentifier(
    val type: String,
    val id: String,
)

@Serializable
data class ResponseRelationship(
    val data: ResponseResourceIdentifier,
)

@Serializable
data class ResponseGrantRelationship(
    val data: List<ResponseResourceIdentifier>,
)

fun AuthorizationParty.toResponseRelationship() = ResponseRelationship(
    data = ResponseResourceIdentifier(
        type = when (type) {
            PartyType.Person -> "Person"
            PartyType.Organization -> "Organization"
            PartyType.OrganizationEntity -> "MarketEntity"
            PartyType.System -> error("System parties cannot be represented in v1 responses")
        },
        id = id,
    ),
)

fun AuthorizationParty.toApprovalRelationship(): ResponseRelationship {
    check(type == PartyType.Person) { "Only persons can be represented as approvers in v1 responses" }
    return toResponseRelationship()
}

fun AuthorizationGrant.toResponseIdentifier() = ResponseResourceIdentifier(
    type = when (type) {
        AuthorizationGrantType.Consumable -> "ConsumableGrant"
        AuthorizationGrantType.Persistent -> "PersistentGrant"
    },
    id = id.toString(),
)
