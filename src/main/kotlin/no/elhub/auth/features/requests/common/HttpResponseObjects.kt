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
data class AuthorizationRequestGetResponse(
    val data: JsonApiResponseResourceObjectWithRelationships<GetRequestResponseAttributes, GetRequestResponseRelationships>,
    val meta: JsonApiMeta
)

fun AuthorizationRequest.toResponseBody(): AuthorizationRequestGetResponse {
    val attributes = mapAttributes(this)
    val relationships = mapRelationships(this)
    val metaProperties = mapMetaProperties(this)

    return AuthorizationRequestGetResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationRequest",
            id = this.id,
            attributes = attributes,
            relationships = relationships
        ),
        meta = JsonApiMeta(metaProperties)
    )
}

fun mapAttributes(authorizationRequest: AuthorizationRequest): GetRequestResponseAttributes =
    GetRequestResponseAttributes(
        requestType = authorizationRequest.requestType.toString(),
        status = authorizationRequest.status.toString(),
        createdAt = authorizationRequest.createdAt.toString(),
        updatedAt = authorizationRequest.updatedAt.toString(),
        validTo = authorizationRequest.validTo.toString()
    )

fun mapRelationships(authorizationRequest: AuthorizationRequest): GetRequestResponseRelationships =
    GetRequestResponseRelationships(
        requestedBy = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = authorizationRequest.requestedBy.toString(),
                type = "Organization"
            )
        ),
        requestedFrom = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = authorizationRequest.requestedFrom.toString(),
                type = "Person"
            )
        )
    )

fun mapMetaProperties(authorizationRequest: AuthorizationRequest): JsonApiMeta = buildJsonObject {
    put("createdAt", JsonPrimitive(authorizationRequest.createdAt.toString()))
    authorizationRequest.properties.forEach { prop ->
//        when (prop.key) {
        put(prop.key, JsonPrimitive(prop.value))
//        }
    }
}
