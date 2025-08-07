package no.elhub.auth.presentation.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import no.elhub.auth.domain.request.AuthorizationRequest
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

@Serializable
data class AuthorizationRequestsGetResponse(
    val data: List<JsonApiResponseResourceObjectWithRelationships<GetRequestResponseAttributes, GetRequestResponseRelationships>>
)

fun AuthorizationRequest.toGetAuthorizationRequestResponse(): AuthorizationRequestGetResponse {
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

fun List<AuthorizationRequest>.toGetAuthorizationRequestsResponse(): AuthorizationRequestsGetResponse =
    AuthorizationRequestsGetResponse(
        data = this.map { authorizationRequest ->
            val attributes = mapAttributes(authorizationRequest)
            val relationships = mapRelationships(authorizationRequest)
            val metaProperties = mapMetaProperties(authorizationRequest)

            JsonApiResponseResourceObjectWithRelationships(
                type = "AuthorizationRequest",
                id = authorizationRequest.id,
                attributes = attributes,
                relationships = relationships,
                meta = metaProperties
            )
        }
    )

private fun mapAttributes(authorizationRequest: AuthorizationRequest): GetRequestResponseAttributes = GetRequestResponseAttributes(
    requestType = authorizationRequest.requestType.toString(),
    status = authorizationRequest.status.toString(),
    createdAt = authorizationRequest.createdAt.toString(),
    updatedAt = authorizationRequest.updatedAt.toString(),
    validTo = authorizationRequest.validTo.toString()
)

private fun mapRelationships(authorizationRequest: AuthorizationRequest): GetRequestResponseRelationships = GetRequestResponseRelationships(
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

private fun mapMetaProperties(authorizationRequest: AuthorizationRequest): JsonApiMeta = buildJsonObject {
    put("createdAt", JsonPrimitive(authorizationRequest.createdAt.toString()))
    authorizationRequest.properties.forEach { prop ->
//        when (prop.key) {
        put(prop.key, JsonPrimitive(prop.value))
//        }
    }
}
