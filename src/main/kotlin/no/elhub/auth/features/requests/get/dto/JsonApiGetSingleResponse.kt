package no.elhub.auth.features.requests.get.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
data class GetRequestSingleResponseAttributes(
    val status: String,
    val requestType: String,
    val validTo: String,
    val updatedAt: String,
    val createdAt: String,
) : JsonApiAttributes

@Serializable
data class GetRequestSingleResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val approvedBy: JsonApiRelationshipToOne? = null,
    val authorizationGrant: JsonApiRelationshipToOne? = null,
) : JsonApiRelationships

@Serializable
data class GetRequestSingleResponseLinks(
    val self: String
) : JsonApiResourceLinks

@Serializable
@JvmInline
value class GetRequestSingleResponseMeta(
    val values: Map<String, String>
) : JsonApiResourceMeta

typealias GetRequestSingleResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    GetRequestSingleResponseAttributes,
    GetRequestSingleResponseRelationships,
    GetRequestSingleResponseMeta,
    GetRequestSingleResponseLinks
    >

fun AuthorizationRequest.toGetSingleResponse() =
    GetRequestSingleResponse(
        data =
        JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
            type = "AuthorizationRequest",
            id = this.id.toString(),
            attributes = GetRequestSingleResponseAttributes(
                status = this.status.name,
                requestType = this.type.name,
                validTo = this.validTo.toString(),
                createdAt = requireNotNull(this.createdAt?.toTimeZoneOffsetString()) { "createdAt not set!" },
                updatedAt = requireNotNull(this.updatedAt?.toTimeZoneOffsetString()) { "updatedAt not set!" },
            ),
            relationships = GetRequestSingleResponseRelationships(
                requestedBy = this.requestedBy.toJsonApiRelationship(),
                requestedFrom = this.requestedFrom.toJsonApiRelationship(),
                requestedTo = this.requestedTo.toJsonApiRelationship(),
                approvedBy = this.approvedBy?.toJsonApiRelationship(),
                authorizationGrant = this.grantId?.let { grantId ->
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
            meta = GetRequestSingleResponseMeta(
                buildMap {
                    this@toGetSingleResponse.properties.forEach { prop ->
                        put(prop.key, prop.value)
                    }
                }
            ),
            links =
            GetRequestSingleResponseLinks(
                self = "${REQUESTS_PATH}/${this.id}"
            ),
        ),
        links = JsonApiLinks.ResourceObjectLink("${REQUESTS_PATH}/${this.id}"),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", this@toGetSingleResponse.createdAt?.toTimeZoneOffsetString())
            }
        )
    )
