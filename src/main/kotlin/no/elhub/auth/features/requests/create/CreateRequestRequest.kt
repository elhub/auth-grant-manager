package no.elhub.auth.features.requests.create

import kotlinx.serialization.Serializable
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

typealias CreateRequestRequest = JsonApiRequest.SingleDocumentWithRelationships<CreateRequestAttributes, CreateRequestRelationships>

fun CreateRequestRequest.toCreateRequestCommand() = CreateRequestCommand(
    AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
    requester = this.data.relationships.requestedBy.data.id,
    requestee = this.data.relationships.requestedFrom.data.id,
)
