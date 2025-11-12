package no.elhub.auth.features.requests.create

import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.PartyType
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class CreateRequestAttributes(
    val requestType: String
) : JsonApiAttributes

@Serializable
data class CreateRequestRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
) : JsonApiRelationships

typealias Request = JsonApiRequest.SingleDocumentWithRelationships<CreateRequestAttributes, CreateRequestRelationships>

fun Request.toCommand() = Command(
    AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
    requester = AuthorizationParty(
        type = PartyType.valueOf(this.data.relationships.requestedBy.data.type),
        resourceId = this.data.relationships.requestedBy.data.id
    ),
    requestee = AuthorizationParty(
        type = PartyType.valueOf(this.data.relationships.requestedFrom.data.type),
        resourceId = this.data.relationships.requestedFrom.data.id
    ),
)
