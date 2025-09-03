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
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class GetRequestResponseAttributes(
    val requestType: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class GetRequestResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne
) : JsonApiRelationships

@Serializable
data class AuthorizationRequestResponse(
    val data: JsonApiResponseResourceObjectWithRelationships<GetRequestResponseAttributes, GetRequestResponseRelationships>,
    val meta: JsonApiMeta
)

fun AuthorizationRequest.toResponse() = AuthorizationRequestResponse(
    data = JsonApiResponseResourceObjectWithRelationships(
        id = this.id,
        type = (AuthorizationRequest::class).simpleName ?: "AuthorizationRequest",
        attributes = GetRequestResponseAttributes(
            requestType = this.requestType.toString(),
            status = this.status.toString(),
            createdAt = this.createdAt.toString(),
            updatedAt = this.updatedAt.toString(),
            validTo = this.validTo.toString()
        ),
        relationships = GetRequestResponseRelationships(
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
