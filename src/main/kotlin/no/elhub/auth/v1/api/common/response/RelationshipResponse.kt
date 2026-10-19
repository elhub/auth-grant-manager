package no.elhub.auth.v1.api.common.response

import kotlinx.serialization.Serializable
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantType

@Serializable
enum class PartyTypeResponse {
    Person,
    Organization,
    MarketEntity,
}

@Serializable
enum class GrantTypeResponse {
    ConsumableGrant,
    PersistentGrant,
}

@Serializable
enum class GrantSourceTypeResponse {
    AuthorizationDocument,
    AuthorizationRequest,
}

@Serializable
data class ResponseResourceIdentifier<T>(
    val type: T,
    val id: String,
)

@Serializable
data class ResponseRelationship<T>(
    val data: ResponseResourceIdentifier<T>,
)

@Serializable
data class ResponseGrantRelationship(
    val data: List<ResponseResourceIdentifier<GrantTypeResponse>>,
)

fun AuthorizationParty.toResponseRelationship() = ResponseRelationship(
    data = ResponseResourceIdentifier(
        type = when (type) {
            PartyType.Person -> PartyTypeResponse.Person
            PartyType.Organization -> PartyTypeResponse.Organization
            PartyType.OrganizationEntity -> PartyTypeResponse.MarketEntity
            PartyType.System -> error("System parties cannot be represented in v1 responses")
        },
        id = id,
    ),
)

fun AuthorizationParty.toApprovalRelationship(): ResponseRelationship<PartyTypeResponse> {
    check(type == PartyType.Person) { "Only persons can be represented as approvers in v1 responses" }
    return toResponseRelationship()
}

fun AuthorizationGrant.toResponseIdentifier() = ResponseResourceIdentifier(
    type = when (type) {
        AuthorizationGrantType.Consumable -> GrantTypeResponse.ConsumableGrant
        AuthorizationGrantType.Persistent -> GrantTypeResponse.PersistentGrant
    },
    id = id.toString(),
)
