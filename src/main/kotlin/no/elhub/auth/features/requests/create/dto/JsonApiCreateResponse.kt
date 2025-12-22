package no.elhub.auth.features.requests.create.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.toTimeZoneOffsetString
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

@Serializable
@JvmInline
value class CreateRequestResponseMeta(
    val values: Map<String, String>
) : JsonApiResourceMeta

@Serializable
data class CreateRequestResponseLinks(
    val self: String,
) : JsonApiResourceLinks

typealias CreateRequestResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    CreateRequestResponseAttributes,
    CreateRequestResponseRelationShips,
    CreateRequestResponseMeta,
    CreateRequestResponseLinks
    >

fun AuthorizationRequest.toCreateResponse() =
    CreateRequestResponse(
        data =
        JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
            type = "AuthorizationRequest",
            id = this.id.toString(),
            attributes =
            CreateRequestResponseAttributes(
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
            meta = CreateRequestResponseMeta(
                buildMap {
                    put("createdAt", this@toCreateResponse.createdAt.toTimeZoneOffsetString())
                    put("updatedAt", this@toCreateResponse.updatedAt.toTimeZoneOffsetString())
                    this@toCreateResponse.properties.forEach { prop ->
                        put(prop.key, prop.value)
                    }
                }
            ),
            links =
            CreateRequestResponseLinks(
                self = "${REQUESTS_PATH}/${this.id}"
            ),
        ),
        links = JsonApiLinks.ResourceObjectLink(REQUESTS_PATH),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", this@toCreateResponse.createdAt.toTimeZoneOffsetString())
            }
        )
    )
