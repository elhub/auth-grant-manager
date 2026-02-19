package no.elhub.auth.features.requests.update.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
data class UpdateRequestResponseAttributes(
    val status: String,
    val requestType: String,
    val validTo: String,
    val createdAt: String,
    val updatedAt: String,
) : JsonApiAttributes

@Serializable
data class UpdateRequestResponseRelationShips(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val approvedBy: JsonApiRelationshipToOne? = null,
    val authorizationGrant: JsonApiRelationshipToOne? = null,
) : JsonApiRelationships

@Serializable
@JvmInline
value class UpdateRequestResponseMeta(
    val values: Map<String, String>
) : JsonApiResourceMeta

@Serializable
data class UpdateRequestResponseLinks(
    val self: String,
) : JsonApiResourceLinks

typealias UpdateRequestResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    UpdateRequestResponseAttributes,
    UpdateRequestResponseRelationShips,
    UpdateRequestResponseMeta,
    UpdateRequestResponseLinks
    >

fun AuthorizationRequest.toUpdateResponse() = UpdateRequestResponse(
    data =
    JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
        type = "AuthorizationRequest",
        id = this.id.toString(),
        attributes = UpdateRequestResponseAttributes(
            status = this.status.name,
            requestType = this.type.name,
            validTo = this.validTo.toTimeZoneOffsetString(),
            createdAt = this.createdAt.toTimeZoneOffsetString(),
            updatedAt = this.updatedAt.toTimeZoneOffsetString(),
        ),
        relationships = UpdateRequestResponseRelationShips(
            requestedBy = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedBy.type.name,
                    id = this.requestedBy.id
                )
            ),
            requestedFrom = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedFrom.type.name,
                    id = this.requestedFrom.id
                )
            ),
            requestedTo = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedTo.type.name,
                    id = this.requestedTo.id
                )
            ),
            approvedBy = this.approvedBy?.let {
                JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        type = it.type.name,
                        id = it.id
                    )
                )
            },
            authorizationGrant = this.grantId?.let {
                JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = it.toString(),
                        type = "AuthorizationGrant"
                    ),
                    links = JsonApiLinks.RelationShipLink(
                        self = "$GRANTS_PATH/$it"
                    )
                )
            },
        ),
        meta = UpdateRequestResponseMeta(
            buildMap {
                this@toUpdateResponse.properties.forEach { prop ->
                    put(prop.key, prop.value)
                }
            }
        ),
        links =
        UpdateRequestResponseLinks(
            self = "${REQUESTS_PATH}/${this.id}"
        ),
    ),
    links = JsonApiLinks.ResourceObjectLink("${REQUESTS_PATH}/${this.id}"),
    meta = JsonApiMeta(
        buildJsonObject {
            put("createdAt", this@toUpdateResponse.createdAt.toTimeZoneOffsetString())
        }
    )
)
