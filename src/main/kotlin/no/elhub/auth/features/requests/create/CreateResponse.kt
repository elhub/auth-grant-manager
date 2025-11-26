package no.elhub.auth.features.requests.create

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.AuthorizationRequestResponseHey
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class CreateRequestResponseAttributes(
    val status: String,
    val requestType: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class CreateRequestResponseRelationShips(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
) : JsonApiRelationships

data class CreateRequestResponseMeta(
    val createdAt: String,
) : JsonApiResourceMeta

typealias CreateRequestResponseResource = JsonApiResponseResourceObjectWithRelationships<CreateRequestResponseAttributes, CreateRequestResponseRelationShips>
typealias CreateRequestResponse = JsonApiResponse.SingleDocumentWithRelationships<CreateRequestResponseAttributes, CreateRequestResponseRelationShips>

fun AuthorizationRequest.toCreateResponse() = CreateRequestResponse(
    data = CreateRequestResponseResource(
        type = "AuthorizationRequest",
        id = this.id.toString(),
        attributes = CreateRequestResponseAttributes(
            status = this.status.name,
            requestType = this.type.name,
            validTo = this.validTo.toString()
        ),
        relationships = CreateRequestResponseRelationShips(
            requestedBy = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedBy.type.name,
                    id = this.requestedBy.resourceId
                )
            ),
            requestedFrom = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedFrom.type.name,
                    id = this.requestedFrom.resourceId
                )
            ),
            requestedTo = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedTo.type.name,
                    id = this.requestedTo.resourceId
                )
            ),
        ),
        links = JsonApiLinks.ResourceObjectLink("/authorization-requests/${this.id}"),
        meta = JsonApiMeta(
            buildJsonObject {
                properties.forEach { prop ->
                    put(prop.key, prop.value)
                }
            }
        )
    ),
    links = JsonApiLinks.ResourceObjectLink("/authorization-requests"),
    meta = JsonApiMeta(
        buildJsonObject {
            put("createdAt", "test")
        }
    )
)
