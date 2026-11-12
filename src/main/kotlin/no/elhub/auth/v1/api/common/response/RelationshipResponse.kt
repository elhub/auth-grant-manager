package no.elhub.auth.v1.api.common.response

import kotlinx.serialization.Serializable
import no.elhub.auth.v1.api.common.GrantResourceType
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantType

@Serializable
enum class AgentTypeResponse {
    Person,
    Organization,
    MarketEntity,
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
    val data: List<ResponseResourceIdentifier<GrantResourceType>>,
)

fun AuthorizationAgent.toResponseRelationship() = ResponseRelationship(
    data = ResponseResourceIdentifier(
        type = when (type) {
            AgentType.Person -> AgentTypeResponse.Person
            AgentType.Organization -> AgentTypeResponse.Organization
            AgentType.OrganizationEntity -> AgentTypeResponse.MarketEntity
            AgentType.System -> error("System agents cannot be represented in v1 responses")
        },
        id = id,
    ),
)

fun AuthorizationAgent.toApprovalRelationship(): ResponseRelationship<AgentTypeResponse> {
    check(type == AgentType.Person) { "Only persons can be represented as approvers in v1 responses" }
    return toResponseRelationship()
}

fun AuthorizationGrant.toResponseIdentifier() = ResponseResourceIdentifier(
    type = when (type) {
        AuthorizationGrantType.Consumable -> GrantResourceType.ConsumableGrant
        AuthorizationGrantType.Persistent -> GrantResourceType.PersistentGrant
    },
    id = id.toString(),
)
