package no.elhub.auth.features.requests.query.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.dto.toJsonApiRelationship
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.REQUESTS_PATH
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.model.JsonApiResourceLinks
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks

@Serializable
data class GetRequestCollectionResponseAttributes(
    val status: String,
    val requestType: String,
    val validTo: String
) : JsonApiAttributes

@Serializable
data class GetRequestCollectionResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val approvedBy: JsonApiRelationshipToOne? = null,
    val grant: JsonApiRelationshipToOne? = null
) : JsonApiRelationships

@Serializable
data class GetRequestCollectionResponseLinks(
    val self: String
) : JsonApiResourceLinks

@Serializable
@JvmInline
value class GetRequestCollectionResponseMeta(
    val values: Map<String, String>
) : JsonApiResourceMeta

typealias GetRequestCollectionResponse = JsonApiResponse.CollectionDocumentWithRelationshipsAndMetaAndLinks<
    GetRequestCollectionResponseAttributes,
    GetRequestCollectionResponseRelationships,
    GetRequestCollectionResponseMeta,
    GetRequestCollectionResponseLinks
    >

fun List<AuthorizationRequest>.toGetCollectionResponse() =
    GetRequestCollectionResponse(
        data = this.map { request ->
            JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
                id = request.id.toString(),
                type = "AuthorizationRequest",
                attributes = GetRequestCollectionResponseAttributes(
                    status = request.status.toString(),
                    requestType = request.type.toString(),
                    validTo = request.validTo.toString()
                ),
                relationships = GetRequestCollectionResponseRelationships(
                    requestedBy = request.requestedBy.toJsonApiRelationship(),
                    requestedFrom = request.requestedFrom.toJsonApiRelationship(),
                    requestedTo = request.requestedTo.toJsonApiRelationship(),
                    approvedBy = request.approvedBy?.toJsonApiRelationship(),
                    grant = request.grantId?.let { grantId ->
                        JsonApiRelationshipToOne(
                            data = JsonApiRelationshipData(
                                id = grantId.toString(),
                                type = "AuthorizationGrant"
                            ),
                            links = JsonApiLinks.RelationShipLink(
                                self = "$GRANTS_PATH/$grantId"
                            )
                        )
                    }
                ),
                meta = GetRequestCollectionResponseMeta(
                    buildMap {
                        put("createdAt", request.createdAt.toTimeZoneOffsetString())
                        put("updatedAt", request.updatedAt.toTimeZoneOffsetString())
                        request.properties.forEach { prop ->
                            put(prop.key, prop.value)
                        }
                    }
                ),
                links = GetRequestCollectionResponseLinks(
                    self = "${REQUESTS_PATH}/${request.id}",
                )
            )
        },
        links = JsonApiLinks.ResourceObjectLink(REQUESTS_PATH),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", currentTimeWithTimeZone().toTimeZoneOffsetString())
            }
        )
    )
