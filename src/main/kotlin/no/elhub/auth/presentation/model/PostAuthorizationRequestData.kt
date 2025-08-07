package no.elhub.auth.presentation.model

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.request.JsonApiRequest

@Serializable
data class PostRequestPayloadAttributes(
    val requestType: String
) : JsonApiAttributes

@Serializable
data class PostRequestPayloadRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
) : JsonApiRelationships

typealias PostAuthorizationRequestPayload = JsonApiRequest.SingleDocumentWithRelationships<PostRequestPayloadAttributes, PostRequestPayloadRelationships>
