package no.elhub.auth.features.requests.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class RequestResponseAttributes(
    val requestType: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class RequestResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne
) : JsonApiRelationships

typealias AuthorizationRequestResponse = JsonApiResponse.SingleDocumentWithRelationships<RequestResponseAttributes, RequestResponseRelationships>

fun AuthorizationRequest.toResponse() = AuthorizationRequestResponse(
    data = JsonApiResponseResourceObjectWithRelationships(
        id = this.id,
        type = (AuthorizationRequest::class).simpleName ?: "AuthorizationRequest",
        attributes = RequestResponseAttributes(
            requestType = this.requestType.toString(),
            status = this.status.toString(),
            createdAt = this.createdAt.toString(),
            updatedAt = this.updatedAt.toString(),
            validTo = this.validTo.toString()
        ),
        relationships = RequestResponseRelationships(
            requestedBy = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = this.requestedBy.toString(),
                    type = "Organization"
                )
            ),
            requestedFrom = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = this.requestedFrom.toString(),
                    type = "Person"
                )
            )
        )

    ),
    meta = JsonApiMeta(
        buildJsonObject {
            put(
                "createdAt",
                JsonPrimitive(this@toResponse.createdAt.toString())
            )

            this@toResponse.properties.forEach { prop -> put(prop.key, JsonPrimitive(prop.value)) }
        }
    )
)
